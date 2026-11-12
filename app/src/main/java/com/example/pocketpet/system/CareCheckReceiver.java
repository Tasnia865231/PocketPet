package com.example.pocketpet.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires every ~15 minutes (AlarmManager) so Mochi can call you even when the app is closed. */
public class CareCheckReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final Context app = c.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try { PetWatcher.check(app); } finally { pending.finish(); }
        }).start();
    }
}
