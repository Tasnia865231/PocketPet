package com.example.pocketpet.system;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.example.pocketpet.Bg;
import com.example.pocketpet.data.PetRepository;

/** Foreground service for a short sleeping session: ongoing notification, energy +1.5 per second for 20s. */
public class PetSessionService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int ticks = 0;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String mode = intent != null && intent.getStringExtra("mode") != null
                ? intent.getStringExtra("mode") : "SLEEP";
        NotificationHelper.ensureChannels(this);
        startForeground(1, NotificationHelper.session(this,
                "SLEEP".equals(mode) ? "Your pet is sleeping..." : "Your pet is playing..."));
        final PetRepository repo = new PetRepository(this);
        ticks = 0;
        handler.removeCallbacksAndMessages(null);
        handler.post(new Runnable() {
            @Override
            public void run() {
                ticks++;
                Bg.IO.execute(() -> repo.tick(mode));
                if (ticks >= 20) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                } else {
                    handler.postDelayed(this, 1000);
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
