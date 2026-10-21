package com.example.pocketpet.system;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.example.pocketpet.Bg;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

/**
 * Foreground service used for two modes:
 *  - SLEEP: shows "Mochi is sleeping..." notification; runs indefinitely
 *    ticking energy up every second until the pet wakes (energy >= 100) or
 *    MainActivity calls stopService().
 *  - PLAY: legacy short tick (20 s) for play sessions.
 *
 * Energy refill rate while in service: +1.5 per tick (1 tick = 1 s).
 * The timestamp-based decay in PetRepository.decay() is the authoritative
 * source when the app is closed; the service tops that up while open.
 */
public class PetSessionService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int ticks = 0;
    private String mode = "SLEEP";

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mode = (intent != null && intent.getStringExtra("mode") != null)
                ? intent.getStringExtra("mode") : "SLEEP";

        NotificationHelper.ensureChannels(this);
        startForeground(1, NotificationHelper.session(this,
                "SLEEP".equals(mode) ? "Mochi is sleeping..." : "Your pet is playing..."));

        final PetRepository repo = new PetRepository(this);
        ticks = 0;
        handler.removeCallbacksAndMessages(null);
        handler.post(new Runnable() {
            @Override
            public void run() {
                ticks++;
                if ("SLEEP".equals(mode)) {
                    // Tick energy up; stop service when pet wakes (isSleeping == false)
                    // or energy reaches 100. The actual wake logic lives in PetRepository.
                    Bg.IO.execute(() -> {
                        Pet pet = repo.load();
                        if (!pet.isSleeping || pet.energy >= 100) {
                            // Pet has been woken externally or is now full
                            stopForeground(STOP_FOREGROUND_REMOVE);
                            stopSelf();
                            return;
                        }
                        repo.tick(mode);
                    });
                    handler.postDelayed(this, 1000);
                } else {
                    // Legacy PLAY mode: 20-tick limit
                    Bg.IO.execute(() -> repo.tick(mode));
                    if (ticks >= 20) {
                        stopForeground(STOP_FOREGROUND_REMOVE);
                        stopSelf();
                    } else {
                        handler.postDelayed(this, 1000);
                    }
                }
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
