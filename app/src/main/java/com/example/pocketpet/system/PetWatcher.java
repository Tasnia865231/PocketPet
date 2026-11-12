package com.example.pocketpet.system;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * "Is Mochi OK?" Compares Mochi now with what we saw last time, and reacts:
 *   woke up           -> custom broadcast PET_WOKE + "Mochi is awake" notification
 *   mood very low     -> PET_CRITICAL(mood) + "Mochi wants to play" notification
 *   hunger below 40%  -> PET_CRITICAL(hunger) + "Mochi is hungry" notification (repeats hourly until he is fed)
 *   energy at 0       -> PET_CRITICAL(energy) + "Mochi needs to sleep" notification
 *   level up          -> LEVEL_UP + notification listing what unlocked
 * Called by the bound service, the 15-minute care alarm, the wake alarm and after boot.
 * While the app is on screen the broadcasts still go out but notifications are skipped (Mochi talks instead).
 */
public final class PetWatcher {
    private PetWatcher() {}

    public static final float MOOD_LOW = 15f, HUNGER_LOW = 40f, ENERGY_ZERO = 0.5f;
    private static final long MOOD_EVERY = 45 * 60_000L, HUNGER_EVERY = 60 * 60_000L, TIRED_EVERY = 90 * 60_000L;

    public static synchronized void check(Context ctx) {
        Context app = ctx.getApplicationContext();
        PetRepository repo = new PetRepository(app);
        Pet p = repo.load();                                   // applies the time-based changes (incl. waking up when energy is full)
        SharedPreferences sp = app.getSharedPreferences("pocketpet_watch", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        long now = System.currentTimeMillis();
        boolean quiet = ForegroundTracker.visible;

        // ---- woke up ----
        if (sp.getBoolean("sleeping", false) && !p.isSleeping) {
            PetEvents.send(app, PetEvents.ACTION_PET_WOKE);
            if (!quiet) NotificationHelper.awake(app, p);
            NotificationHelper.cancel(app, NotificationHelper.ID_SESSION);
        }

        // ---- level up ----
        int last = sp.getInt("level", 0);
        if (last > 0 && p.level > last) {
            List<String> unlocked = new ArrayList<>();
            for (Item it : repo.items()) if (it.minLevel > last && it.minLevel <= p.level) unlocked.add(it.name);
            Bundle b = new Bundle();
            b.putInt(PetEvents.EXTRA_LEVEL, p.level);
            PetEvents.send(app, PetEvents.ACTION_LEVEL_UP, b);
            if (!quiet) NotificationHelper.levelUp(app, p.level, unlocked);
        }

        // ---- needs (only while awake) ----
        if (!p.isSleeping) {
            if (p.mood <= MOOD_LOW) {
                critical(app, "mood");
                if (!quiet && due(sp, "moodAt", now, MOOD_EVERY)) { NotificationHelper.wantsPlay(app, p); ed.putLong("moodAt", now); }
            } else if (p.mood > 35f) ed.remove("moodAt");

            if (p.hunger < HUNGER_LOW) {
                critical(app, "hunger");
                if (!quiet && due(sp, "hungerAt", now, HUNGER_EVERY)) { NotificationHelper.hungry(app, p); ed.putLong("hungerAt", now); }
            } else ed.remove("hungerAt");          // fed again: the next time it drops below 40% Mochi calls right away

            if (p.energy <= ENERGY_ZERO) {
                critical(app, "energy");
                if (!quiet && due(sp, "tiredAt", now, TIRED_EVERY)) { NotificationHelper.needsSleep(app, p); ed.putLong("tiredAt", now); }
            } else if (p.energy > 25f) ed.remove("tiredAt");
        }

        ed.putBoolean("sleeping", p.isSleeping).putInt("level", p.level).apply();
        PetWidget.updateAll(app);
    }

    private static boolean due(SharedPreferences sp, String key, long now, long every) {
        long at = sp.getLong(key, 0L);
        return at == 0L || now - at > every;
    }

    private static void critical(Context app, String kind) {
        Bundle b = new Bundle();
        b.putString(PetEvents.EXTRA_KIND, kind);
        PetEvents.send(app, PetEvents.ACTION_PET_CRITICAL, b);
    }
}
