package com.example.pocketpet.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.example.pocketpet.Bg;
import com.example.pocketpet.data.PetRepository;

/**
 * Registered IN CODE from MainActivity (these system broadcasts cannot be declared in the manifest):
 *   BATTERY_LOW   Mochi gets a bit sleepy (energy drops)
 *   DATE_CHANGED  a new day: claim the daily login reward
 * Each one ends by telling the UI through PetEvents.ACTION_PET_CHANGED.
 */
public class SystemEventsReceiver extends BroadcastReceiver {
    public interface Callback { void onSystemEvent(String action, int coins); }

    private final Callback callback;

    public SystemEventsReceiver(Callback callback) { this.callback = callback; }

    public static IntentFilter filter() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_LOW);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        return f;
    }

    @Override
    public void onReceive(Context c, Intent i) {
        final Context app = c.getApplicationContext();
        final String action = i.getAction();
        if (action == null) return;
        Bg.IO.execute(() -> {
            int coins = 0;
            if (Intent.ACTION_BATTERY_LOW.equals(action)) {
                new PetRepository(app).edit(p -> { p.energy = Math.max(0f, p.energy - 15f); return p; });
            } else if (Intent.ACTION_DATE_CHANGED.equals(action)) {
                coins = DailyReward.claimIfNeeded(app);
            }
            final int got = coins;
            PetEvents.send(app, PetEvents.ACTION_PET_CHANGED);
            if (callback != null) Bg.UI.post(() -> callback.onSystemEvent(action, got));
        });
    }
}
