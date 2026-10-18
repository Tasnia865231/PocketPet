package com.example.pocketpet.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.pocketpet.Bg;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

/** Declared in the manifest: fired by AlarmManager, shows the "hungry" notification. */
public class HungerAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult pending = goAsync();
        final Context app = c.getApplicationContext();
        Bg.IO.execute(() -> {
            try {
                Pet p = new PetRepository(app).load();
                if (p.hunger < 40f) NotificationHelper.hungry(app, p.name);
                AlarmScheduler.schedule(app);
            } finally {
                pending.finish();
            }
        });
    }
}
