package com.example.pocketpet.data;

import android.content.Context;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * All methods are blocking: call them from Bg.IO (or a receiver/provider thread).
 *
 * Stat decay rules (computed from timestamps, not a service):
 *   Awake:  hunger -5/h, energy -3/h, cleanliness -2.5/h, mood -2/h (or -3/h when dirty or hungry)
 *   Asleep: energy +12/h, hunger -2.5/h (half speed), cleanliness -1.25/h (half speed), mood unchanged
 *
 * XP / levels:  XP to next level = 50 * currentLevel; levels 1-10.
 * Stage: 0 for levels 1-2, 1 for levels 3-5, 2 for levels 6+.
 */
public class PetRepository {
    private static final Object LOCK = new Object();

    // Awake decay rates (per hour)
    public static final float HUNGER_PER_HOUR      = 5f;
    public static final float ENERGY_PER_HOUR      = 3f;
    public static final float MOOD_PER_HOUR        = 2f;
    public static final float CLEAN_PER_HOUR       = 2.5f;

    // Sleep rates (per hour)
    public static final float SLEEP_ENERGY_REFILL  = 12f;
    public static final float SLEEP_HUNGER_DRAIN   = 2.5f;
    public static final float SLEEP_CLEAN_DRAIN    = 1.25f;

    private final AppDatabase db;

    public PetRepository(Context ctx) {
        db = AppDatabase.get(ctx);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- seed ----
    private void seed() {
        if (db.itemDao().count() == 0) {
            db.itemDao().insertAll(Arrays.asList(
                    new Item(1, "Berry",    "food", 5,  15, 1),
                    new Item(2, "Rice Ball","food", 10, 30, 1),
                    new Item(3, "Apple",    "food", 15, 20, 2),
                    new Item(4, "Milk",     "food", 20, 25, 3),
                    new Item(5, "Cake",     "food", 30, 40, 4),
                    new Item(6, "Straw Hat","hat",  30,  0, 2),
                    new Item(7, "Leaf Hat", "hat",  40,  0, 5)));
        }
    }

    // ---- decay ----
    public Pet decay(Pet p, long now) {
        float h = Math.max(0f, (now - p.lastUpdated) / 3_600_000f);
        Pet d = p.copy();
        if (p.isSleeping) {
            // Sleeping: energy refills, hunger and cleanliness drain at half speed, mood stays
            d.energy       = clamp(p.energy + h * SLEEP_ENERGY_REFILL, 0f, 100f);
            d.hunger       = clamp(p.hunger - h * SLEEP_HUNGER_DRAIN, 0f, 100f);
            d.cleanliness  = clamp(p.cleanliness - h * SLEEP_CLEAN_DRAIN, 0f, 100f);
            d.mood         = p.mood;
            // Auto-wake at full energy
            if (d.energy >= 100f) {
                d.energy = 100f;
                d.isSleeping = false;
                d.sleepStart = 0L;
                d.xp = p.xp;
                d = addXp(d, 6);    // sleep completion XP
            }
        } else {
            d.hunger      = clamp(p.hunger - h * HUNGER_PER_HOUR, 0f, 100f);
            d.energy      = clamp(p.energy - h * ENERGY_PER_HOUR, 0f, 100f);
            // Mood decays faster when dirty (<30) or hungry (<20)
            float moodRate = MOOD_PER_HOUR;
            if (d.hunger < 20f) moodRate *= 2f;
            if (p.cleanliness < 30f) moodRate *= 1.5f;
            d.mood        = clamp(p.mood - h * moodRate, 0f, 100f);
            d.cleanliness = clamp(p.cleanliness - h * CLEAN_PER_HOUR, 0f, 100f);
        }
        d.lastUpdated = now;
        return d;
    }

    // ---- load / save / edit ----
    public Pet load() {
        synchronized (LOCK) {
            seed();
            Pet p = db.petDao().get();
            if (p == null) p = new Pet();
            Pet d = decay(p, System.currentTimeMillis());
            db.petDao().save(d);
            return d;
        }
    }

    public void save(Pet p) {
        synchronized (LOCK) {
            Pet c = p.copy();
            c.lastUpdated = System.currentTimeMillis();
            db.petDao().save(c);
        }
    }

    /** Atomic load -> change -> save. */
    public Pet edit(UnaryOperator<Pet> f) {
        synchronized (LOCK) {
            Pet r = f.apply(load());
            save(r);
            return r;
        }
    }

    // ---- XP / level / stage ----
    private int stageForLevel(int level) {
        if (level >= 6) return 2;
        if (level >= 3) return 1;
        return 0;
    }

    /**
     * Add XP, handle level-ups (coins, log, stage evolution), return the updated Pet.
     * Caller is responsible for saving the returned pet.
     */
    public Pet addXp(Pet p, int amount) {
        p.xp += amount;
        int loopGuard = 0;
        while (loopGuard++ < 10) {
            int xpNeeded = 50 * p.level;
            if (p.xp >= xpNeeded && p.level < 10) {
                p.xp -= xpNeeded;
                p.level++;
                p.coins += 10;  // level-up reward
                int newStage = stageForLevel(p.level);
                if (newStage > p.stage) {
                    db.logDao().add(new GrowthLog("Evolved to stage " + (newStage + 1) + "!"));
                    p.stage = newStage;
                }
                db.logDao().add(new GrowthLog("Reached level " + p.level + "!"));
            } else {
                break;
            }
        }
        p.stage = stageForLevel(p.level);
        return p;
    }

    // ---- actions ----
    /** Feed with a specific hunger amount; returns the updated (unsaved) pet. */
    public Pet feed(Pet p, int amount) {
        Pet n = p.copy();
        n.hunger = Math.min(100f, p.hunger + amount);
        n.mood   = Math.min(100f, p.mood + 3f);
        return addXp(n, 5);
    }

    /** Feed the first owned food item from the bag (legacy bag button support). */
    public Pet feedFromBag(Pet p) {
        for (Item it : db.itemDao().owned()) {
            if ("food".equals(it.type)) {
                it.owned -= 1;
                db.itemDao().update(it);
                return feed(p, it.effect);
            }
        }
        return feed(p, 8);
    }

    /** Feed a specific item from the bag; returns result message. */
    public String feedItem(int itemId) {
        synchronized (LOCK) {
            Pet p = load();
            if (p.isSleeping) return "Shh, Mochi is sleeping";
            if (p.hunger >= 95f) return "FULL";
            Item fresh = findItem(itemId);
            if (fresh == null) return "Item not found";
            if (fresh.owned <= 0) return "None left";
            fresh.owned -= 1;
            db.itemDao().update(fresh);
            Pet fed = feed(p, fresh.effect);
            save(fed);
            return "OK:" + fresh.name;
        }
    }

    public Pet play(Pet p) {
        if (p.energy < 10f) return p;
        Pet n = p.copy();
        n.mood        = Math.min(100f, p.mood + 15f);
        n.energy      = p.energy - 10f;
        n.hunger      = Math.max(0f, p.hunger - 5f);
        n.cleanliness = Math.max(0f, p.cleanliness - 5f);  // play makes pet dirty
        n.coins       = p.coins + 5;
        return addXp(n, 10);
    }

    /** Bath completed: cleanliness + mood + XP. */
    public Pet bath(Pet p, float cleanGained) {
        Pet n = p.copy();
        n.cleanliness = Math.min(100f, p.cleanliness + cleanGained);
        if (cleanGained >= 90f) {
            // Full bath
            n.cleanliness = 100f;
            n.mood = Math.min(100f, p.mood + 8f);
            return addXp(n, 8);
        } else {
            // Early exit — partial clean only, no mood or XP bonus
            return n;
        }
    }

    /** Legacy clean (kept for any remaining callers). */
    public Pet clean(Pet p) {
        return bath(p, 100f);
    }

    // ---- sleep ----
    public Pet startSleep(Pet p) {
        Pet n = p.copy();
        n.isSleeping = true;
        n.sleepStart = System.currentTimeMillis();
        return n;
    }

    public Pet wake(Pet p, boolean early) {
        Pet n = p.copy();
        if (early && p.energy < 50f) {
            n.mood = Math.max(0f, p.mood - 5f);
        }
        boolean wasAsleep = p.isSleeping;
        n.isSleeping = false;
        n.sleepStart = 0L;
        // Award sleep XP only if the pet actually slept (not auto-wake, which already awarded)
        if (wasAsleep) {
            n = addXp(n, 6);
        }
        return n;
    }

    // ---- service tick ----
    public void tick(String mode) {
        edit(p -> {
            Pet n = p.copy();
            if ("SLEEP".equals(mode)) {
                n.energy = Math.min(100f, n.energy + 1.5f);
            } else {
                n.mood   = Math.min(100f, n.mood + 1f);
                n.energy = Math.max(0f, n.energy - 0.5f);
                n = addXp(n, 1);
            }
            return n;
        });
    }

    // ---- shop / inventory / history ----
    public List<Item> items() {
        synchronized (LOCK) { seed(); }
        return db.itemDao().all();
    }

    public List<Item> ownedItems() { return db.itemDao().owned(); }

    public List<GrowthLog> logs() { return db.logDao().all(); }

    private Item findItem(int id) {
        for (Item i : db.itemDao().all()) if (i.id == id) return i;
        return null;
    }

    public boolean buy(Item item) {
        synchronized (LOCK) {
            Pet p = load();
            if (p.coins < item.price) return false;
            if (p.level < item.minLevel) return false;
            Item fresh = findItem(item.id);
            if (fresh == null) return false;
            fresh.owned += 1;
            db.itemDao().update(fresh);
            p.coins -= item.price;
            save(p);
            return true;
        }
    }

    public String use(Item item) {
        synchronized (LOCK) {
            Pet p = load();
            if (p.isSleeping && "food".equals(item.type)) return "Shh, Mochi is sleeping";
            Item fresh = findItem(item.id);
            if (fresh == null) return "Item not found";
            if ("food".equals(fresh.type)) {
                if (fresh.owned <= 0) return "None left";
                if (p.hunger >= 95f) return "Mochi is already full";
                fresh.owned -= 1;
                db.itemDao().update(fresh);
                save(feed(p, fresh.effect));
                return "Yum!";
            }
            boolean on = !fresh.name.equals(p.hat);
            p.hat = on ? fresh.name : "";
            save(p);
            return on ? "Wearing " + fresh.name : "Hat removed";
        }
    }
}
