package com.example.pocketpet.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pet")
public class Pet {
    @PrimaryKey public int id = 1;
    public String name = "Mochi";
    public float hunger = 80f;        // 0 = starving, 100 = full
    public float energy = 80f;
    public float mood = 80f;
    public float cleanliness = 100f;  // 0 = dirty, 100 = clean
    public int coins = 50;
    public int xp = 0;
    public int level = 1;             // 1–10; replaces the old stage field
    public int stage = 0;             // derived: 0 for levels 1-2, 1 for 3-5, 2 for 6+
    public String hat = "";
    public long lastUpdated = System.currentTimeMillis();
    public boolean isSleeping = false;
    public long sleepStart = 0L;

    public Pet copy() {
        Pet p = new Pet();
        p.id = id; p.name = name;
        p.hunger = hunger; p.energy = energy; p.mood = mood; p.cleanliness = cleanliness;
        p.coins = coins; p.xp = xp; p.level = level; p.stage = stage;
        p.hat = hat; p.lastUpdated = lastUpdated;
        p.isSleeping = isSleeping; p.sleepStart = sleepStart;
        return p;
    }
}
