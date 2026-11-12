package com.example.pocketpet.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

/** Declared in the manifest: after a reboot, books the alarms again and resumes a sleep in progress. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        final Context app = c.getApplicationContext();
        AlarmScheduler.schedule(app);
        AlarmScheduler.scheduleCareChecks(app);
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Pet p = new PetRepository(app).load();
                if (p.isSleeping) ContextCompat.startForegroundService(app, new Intent(app, PetSessionService.class).putExtra("mode", "SLEEP"));
            } finally {
                pending.finish();
            }
        }).start();
    }
}
