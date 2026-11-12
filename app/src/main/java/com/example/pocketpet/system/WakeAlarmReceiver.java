package com.example.pocketpet.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Set when Mochi falls asleep for the moment his energy will be full: sends "Mochi is awake!". */
public class WakeAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final Context app = c.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try { PetWatcher.check(app); } finally { pending.finish(); }
        }).start();
    }
}
