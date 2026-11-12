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
 *   Asleep: energy +200/h (0 -> 100 takes 30 min), hunger -2.5/h (half speed), cleanliness -1.25/h (half speed), mood unchanged
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
    /** 200 per hour: sleeping from 0 energy to full takes (at least) 30 minutes. */
    public static final float SLEEP_ENERGY_REFILL  = 200f;
    public static final float SLEEP_HUNGER_DRAIN   = 2.5f;
    public static final float SLEEP_CLEAN_DRAIN    = 1.25f;

    private final AppDatabase db;
    private final Context appCtx;

    public PetRepository(Context ctx) {
        db = AppDatabase.get(ctx);
        appCtx = ctx.getApplicationContext();
    }

    /** Tells every ContentObserver (main screen, Shop, Bag) and the widget that Mochi's data changed. */
    private void notifyPet()   { appCtx.getContentResolver().notifyChange(com.example.pocketpet.system.PetProvider.PET, null); }
    private void notifyItems() { appCtx.getContentResolver().notifyChange(com.example.pocketpet.system.PetProvider.ITEMS, null); }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- shop catalogue ----
    // name, type ("food" | "hat" | "shell"), price, hunger restored (food only), unlock level.
    // Ids never change once shipped (the bag stores counts by id). Foods can be bought again and again and are
    // used up when Mochi eats them; hats and eggshells are bought ONCE and then stay in the bag forever.
    private static List<Item> catalog() {
        return Arrays.asList(
                // ---- foods ----
                new Item(1,  "Berry",        "food",   5, 15, 1),
                new Item(2,  "Rice Ball",    "food",  10, 30, 1),
                new Item(3,  "Apple",        "food",  15, 20, 2),
                new Item(8,  "Cookie",       "food",  12, 18, 2),
                new Item(4,  "Milk",         "food",  20, 25, 3),
                new Item(9,  "Carrot",       "food",  18, 22, 3),
                new Item(5,  "Cake",         "food",  30, 40, 4),
                new Item(10, "Pudding",      "food",  25, 28, 4),
                new Item(11, "Ice Cream",    "food",  28, 32, 5),
                new Item(12, "Dumpling",     "food",  35, 38, 6),
                new Item(13, "Pancakes",     "food",  40, 45, 7),
                new Item(14, "Donut",        "food",  32, 35, 8),
                new Item(15, "Bento Box",    "food",  55, 55, 9),
                new Item(16, "Rainbow Cake", "food",  80, 70, 10),
                // ---- hats ----
                new Item(17, "Pink Bow",     "hat",   25,  0, 1),
                new Item(6,  "Straw Hat",    "hat",   30,  0, 2),
                new Item(18, "Flower Crown", "hat",   45,  0, 3),
                new Item(19, "Cat Ears",     "hat",   55,  0, 4),
                new Item(7,  "Leaf Hat",     "hat",   40,  0, 5),
                new Item(20, "Chef Hat",     "hat",   60,  0, 6),
                new Item(21, "Party Hat",    "hat",   70,  0, 7),
                new Item(22, "Wizard Hat",   "hat",   90,  0, 8),
                new Item(23, "Royal Crown",  "hat",  150,  0, 10),
                // ---- eggshells ----
                new Item(24, "Strawberry Shell",  "shell",  90, 0, 4),
                new Item(25, "Starry Night Shell","shell", 160, 0, 8));
    }

    /** Adds any catalogue item the database does not have yet and refreshes prices/levels, keeping owned counts. */
    private void seed() {
        List<Item> want = catalog();
        List<Item> have = db.itemDao().all();
        if (have.size() == want.size()) {
            boolean same = true;
            for (Item w : want) {
                boolean found = false;
                for (Item h : have) if (h.id == w.id && h.name.equals(w.name) && h.type.equals(w.type)
                        && h.price == w.price && h.minLevel == w.minLevel && h.effect == w.effect) { found = true; break; }
                if (!found) { same = false; break; }
            }
            if (same) return;
        }
        for (Item w : want) {
            Item old = null;
            for (Item h : have) if (h.id == w.id) { old = h; break; }
            if (old != null) w.owned = old.owned;       // never lose what the player already owns
        }
        db.itemDao().insertAll(want);
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
            // A level-up reward earned while Mochi slept is an earned coin too (keeps the lifetime total honest)
            if (d.coins > p.coins) d.totalEarned = Math.max(d.totalEarned, p.totalEarned) + (d.coins - p.coins);
            db.petDao().save(d);
            if (d.level > p.level) com.example.pocketpet.firebase.ActivityFeed.postLevelUp(d.level);   // community feed
            return d;
        }
    }

    public void save(Pet p) {
        final Pet c = p.copy();
        int levelReached = 0;
        synchronized (LOCK) {
            Pet prev = db.petDao().get();
            // Phone-only earned-coins counter (kept for saved-game compatibility; not shown anywhere).
            // ANY coin increase counts; spending never lowers it. This one place covers every earning source.
            if (prev != null) {
                c.totalEarned = Math.max(c.totalEarned, prev.totalEarned);
                if (c.coins > prev.coins) c.totalEarned += c.coins - prev.coins;
            }
            c.totalEarned = Math.max(c.totalEarned, c.coins);      // can never be below the balance
            // Community feed moment: a new level
            if (prev != null && c.level > prev.level) levelReached = c.level;
            c.lastUpdated = System.currentTimeMillis();
            db.petDao().save(c);
        }
        notifyPet();
        // Firestore: keep the cloud copy of level + coins up to date (does nothing unless changed)
        com.example.pocketpet.firebase.PlayerProfileRepository.pushProgress(c.level, c.coins);
        // Realtime Database: tell the community (does nothing unless someone is logged in)
        if (levelReached > 0) com.example.pocketpet.firebase.ActivityFeed.postLevelUp(levelReached);
    }

    /**
     * Used right after login: puts the saved level and coins from the cloud into the game.
     * (totalEarned is a phone-only counter, no longer shown anywhere; it is kept so saved games stay compatible.)
     */
    public void restoreProgress(int level, int coins) {
        synchronized (LOCK) {
            Pet p = db.petDao().get();
            if (p == null) p = new Pet();
            if (p.level != level) { p.level = level; p.xp = 0; }
            p.stage = stageForLevel(level);
            p.coins = coins;
            p.totalEarned = Math.max(p.totalEarned, coins);
            p.lastUpdated = System.currentTimeMillis();
            db.petDao().save(p);
        }
        notifyPet();
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
            notifyItems();
            Pet fed = feed(p, fresh.effect);
            save(fed);
            return "OK:" + fresh.name;
        }
    }

    /** Coins earned in the mini-games (shown on the game screen) are added here. */
    public Pet addCoins(Pet p, int amount) {
        Pet n = p.copy();
        n.coins = p.coins + Math.max(0, amount);
        return n;
    }

    public Pet play(Pet p) {
        if (p.energy < 10f) return p;
        Pet n = p.copy();
        n.mood        = Math.min(100f, p.mood + 15f);
        n.energy      = p.energy - 10f;
        n.hunger      = Math.max(0f, p.hunger - 5f);
        n.cleanliness = Math.max(0f, p.cleanliness - 5f);  // play makes pet dirty
        return addXp(n, 10);     // coins are paid separately from what the player actually earned in the game
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

    /** Waking Mochi while his sleep bar (energy) is below this means he did not have a nice sleep. */
    public static final float POOR_SLEEP_BELOW = 40f;

    public Pet wake(Pet p, boolean early) {
        Pet n = p.copy();
        if (early && p.energy < POOR_SLEEP_BELOW) {
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
                // Energy while asleep is time-based (see decay(): SLEEP_ENERGY_REFILL per hour), so the
                // old extra +1.5 per second bonus is gone: it filled the bar in about a minute.
            } else {
                n.mood   = Math.min(100f, n.mood + 1f);
                n.energy = Math.max(0f, n.energy - 0.5f);
                n = addXp(n, 1);
            }
            return n;
        });
    }

    /** Used by the content provider: set how many of an item are owned. */
    public void setOwned(int itemId, int owned) {
        synchronized (LOCK) {
            Item it = findItem(itemId);
            if (it == null) return;
            it.owned = owned;
            db.itemDao().update(it);
        }
        notifyItems();
    }

    public void addLog(String text) { db.logDao().add(new GrowthLog(text)); }

    public int clearLogs() { return db.logDao().clear(); }

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

    /** Result codes for tryBuy. */
    public static final String BUY_OK = "OK", BUY_COINS = "COINS", BUY_LOCKED = "LOCKED", BUY_OWNED = "OWNED", BUY_MISSING = "MISSING";

    public String tryBuy(Item item) {
        synchronized (LOCK) {
            Pet p = load();
            Item fresh = findItem(item.id);
            if (fresh == null) return BUY_MISSING;
            if (p.level < fresh.minLevel) return BUY_LOCKED;
            boolean oneTime = !"food".equals(fresh.type);
            if (oneTime && fresh.owned >= 1) return BUY_OWNED;      // hats and eggshells: buy once, keep forever
            if (p.coins < fresh.price) return BUY_COINS;
            fresh.owned += 1;
            db.itemDao().update(fresh);
            notifyItems();
            p.coins -= fresh.price;
            save(p);
            return BUY_OK;
        }
    }

    public boolean buy(Item item) {
        return BUY_OK.equals(tryBuy(item));
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
            notifyItems();
                save(feed(p, fresh.effect));
                return "Yum!";
            }
            if ("shell".equals(fresh.type)) {
                boolean on = !fresh.name.equals(p.shell);
                p.shell = on ? fresh.name : "";
                save(p);
                return on ? "Wearing " + fresh.name : "Back to the spotted shell";
            }
            boolean on = !fresh.name.equals(p.hat);
            p.hat = on ? fresh.name : "";
            save(p);
            return on ? "Wearing " + fresh.name : "Hat removed";
        }
    }
}
