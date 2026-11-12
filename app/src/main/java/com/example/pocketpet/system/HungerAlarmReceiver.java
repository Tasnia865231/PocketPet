package com.example.pocketpet.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

/** The daily alarm (hour chosen in Settings): sends Mochi's day summary, then books tomorrow's. */
public class HungerAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final Context app = c.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Pet p = new PetRepository(app).load();
                if (!ForegroundTracker.visible) {
                    if (p.hunger < 40f) NotificationHelper.hungry(app, p);
                    else NotificationHelper.daily(app, p);
                }
                AlarmScheduler.schedule(app);
            } finally {
                pending.finish();
            }
        }).start();
    }
}
