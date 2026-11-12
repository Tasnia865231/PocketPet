package com.example.pocketpet.system;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;

/** Reads Mochi THROUGH the content provider, exactly like another app or a widget would. */
public final class PetClient {
    private PetClient() {}

    public static final class Snapshot {
        public String name = "Mochi";
        public float hunger = 100, energy = 100, mood = 100, cleanliness = 100;
        public int coins, level = 1;
        public boolean sleeping;
    }

    public static Snapshot read(Context c) {
        Snapshot s = new Snapshot();
        ContentResolver cr = c.getContentResolver();
        try (Cursor cur = cr.query(PetProvider.PET, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                s.name = cur.getString(cur.getColumnIndexOrThrow("name"));
                s.hunger = cur.getFloat(cur.getColumnIndexOrThrow("hunger"));
                s.energy = cur.getFloat(cur.getColumnIndexOrThrow("energy"));
                s.mood = cur.getFloat(cur.getColumnIndexOrThrow("mood"));
                s.cleanliness = cur.getFloat(cur.getColumnIndexOrThrow("cleanliness"));
                s.coins = cur.getInt(cur.getColumnIndexOrThrow("coins"));
                s.level = cur.getInt(cur.getColumnIndexOrThrow("level"));
                s.sleeping = cur.getInt(cur.getColumnIndexOrThrow("isSleeping")) == 1;
            }
        } catch (Exception ignored) { }
        return s;
    }
}
