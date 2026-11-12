package com.example.pocketpet.system;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.example.pocketpet.Bg;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

/**
 * Mochi's engine. One service, used three ways:
 *
 *  1. BOUND: MainActivity binds to it while the app is on screen. The service ticks every few seconds, applies the
 *     time-based changes, runs PetWatcher and pushes the fresh Pet to the listener, and the activity asks it to
 *     start a sleep or wake Mochi up (startSleep / wakeUp) instead of touching the database itself.
 *  2. STARTED + FOREGROUND ("mode" = SLEEP): while Mochi sleeps it keeps running with the app closed, shows the
 *     "Mochi is sleeping" notification with an energy progress bar and books the wake-up alarm. When his energy is
 *     full it stops itself and PetWatcher sends "Mochi is awake!".
 *  3. Both at once is fine: it only stops when nobody is bound and nobody is sleeping.
 */
public class PetSessionService extends Service {
    public interface PetListener { void onPet(Pet pet); }

    public class PetBinder extends Binder {
        public PetSessionService getService() { return PetSessionService.this; }
    }

    private static final long TICK_BOUND_MS = 4000L, TICK_SLEEP_MS = 5000L;

    private final IBinder binder = new PetBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PetRepository repo;
    private PetListener listener;
    private boolean bound = false, sleepingForeground = false, ticking = false;

    @Override
    public void onCreate() {
        super.onCreate();
        repo = new PetRepository(this);
        NotificationHelper.ensureChannels(this);
    }

    // ------------------------------------------------------------------ binding
    @Override
    public IBinder onBind(Intent intent) {
        bound = true;
        startTicking();
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        bound = false;
        listener = null;
        if (!sleepingForeground) stopTicking();
        return false;
    }

    public void setListener(PetListener l) { listener = l; }

    /** Mochi falls asleep: saves it, starts the foreground sleep session and books the wake-up alarm. */
    public void startSleep(PetListener done) {
        Bg.IO.execute(() -> {
            Pet p = repo.edit(x -> repo.startSleep(x));
            // start it for real (not just bound) so it keeps running as a foreground service when the app is closed
            ContextCompat.startForegroundService(getApplicationContext(), new Intent(getApplicationContext(), PetSessionService.class).putExtra("mode", "SLEEP"));
            if (done != null) Bg.UI.post(() -> done.onPet(p));
        });
    }

    /** Mochi is woken up early (or normally): saves it and ends the sleep session. */
    public void wakeUp(boolean early, PetListener done) {
        Bg.IO.execute(() -> {
            Pet p = repo.edit(x -> repo.wake(x, early));
            endSleepSession();
            PetWatcher.check(this);
            if (done != null) Bg.UI.post(() -> done.onPet(p));
        });
    }

    // ------------------------------------------------------------------ started (foreground sleep session)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "SLEEP".equals(intent.getStringExtra("mode"))) {
            Bg.IO.execute(() -> beginSleepSession(repo.load()));
        }
        return START_NOT_STICKY;
    }

    private void beginSleepSession(Pet p) {
        sleepingForeground = true;
        long eta = (long) ((100f - p.energy) / PetRepository.SLEEP_ENERGY_REFILL * 3_600_000L) + 2000L;
        AlarmScheduler.scheduleWake(this, System.currentTimeMillis() + eta);
        main.post(() -> {
            startForeground(NotificationHelper.ID_SESSION, NotificationHelper.sleeping(this, p, minutesLeft(p)));
            startTicking();
        });
    }

    private void endSleepSession() {
        sleepingForeground = false;
        AlarmScheduler.cancelWake(this);
        main.post(() -> {
            stopForeground(true);
            if (!bound) { stopTicking(); stopSelf(); }
        });
    }

    private static int minutesLeft(Pet p) {
        return (int) Math.ceil((100f - p.energy) / PetRepository.SLEEP_ENERGY_REFILL * 60f);
    }

    // ------------------------------------------------------------------ the tick
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!ticking) return;
            Bg.IO.execute(() -> {
                if (sleepingForeground) repo.tick("SLEEP");
                PetWatcher.check(PetSessionService.this);          // wake-up, level-up, low mood / hunger / energy
                final Pet p = repo.load();
                main.post(() -> {
                    if (listener != null) listener.onPet(p);
                    if (sleepingForeground) {
                        if (!p.isSleeping) endSleepSession();
                        else {
                            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
                            nm.notify(NotificationHelper.ID_SESSION, NotificationHelper.sleeping(PetSessionService.this, p, minutesLeft(p)));
                        }
                    }
                });
            });
            main.postDelayed(this, sleepingForeground ? TICK_SLEEP_MS : TICK_BOUND_MS);
        }
    };

    private void startTicking() {
        if (ticking) return;
        ticking = true;
        main.post(tick);
    }

    private void stopTicking() {
        ticking = false;
        main.removeCallbacks(tick);
    }

    @Override
    public void onDestroy() {
        stopTicking();
        super.onDestroy();
    }
}
