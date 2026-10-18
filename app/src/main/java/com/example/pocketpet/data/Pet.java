package com.example.pocketpet.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pet")
public class Pet {
    @PrimaryKey public int id = 1;
    public String name = "Mochi";
    public float hunger = 80f;   // 0 = starving, 100 = full
    public float energy = 80f;
    public float mood = 80f;
    public int coins = 50;
    public int xp = 0;
    public int stage = 0;
    public String hat = "";
    public long lastUpdated = System.currentTimeMillis();

    public Pet copy() {
        Pet p = new Pet();
        p.id = id; p.name = name; p.hunger = hunger; p.energy = energy; p.mood = mood;
        p.coins = coins; p.xp = xp; p.stage = stage; p.hat = hat; p.lastUpdated = lastUpdated;
        return p;
    }
}
