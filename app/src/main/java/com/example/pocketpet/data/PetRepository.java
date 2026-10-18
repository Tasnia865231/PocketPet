package com.example.pocketpet.data;

import android.content.Context;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * All methods are blocking: call them from Bg.IO (or a receiver/provider thread).
 * Hunger/energy/mood are NOT ticked by a service. They are calculated from lastUpdated
 * every time the pet is loaded, so the pet "lives" even when the app is closed.
 */
public class PetRepository {
    private static final Object LOCK = new Object();
    public static final float HUNGER_PER_HOUR = 5f;
    public static final float ENERGY_PER_HOUR = 3f;
    public static final float MOOD_PER_HOUR = 2f;

    private final AppDatabase db;

    public PetRepository(Context ctx) {
        db = AppDatabase.get(ctx);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void seed() {
        if (db.itemDao().count() == 0) {
            db.itemDao().insertAll(Arrays.asList(
                    new Item(1, "Berry", "food", 5, 15),
                    new Item(2, "Rice Ball", "food", 10, 30),
                    new Item(3, "Straw Hat", "hat", 30, 0),
                    new Item(4, "Leaf Hat", "hat", 40, 0)));
        }
    }

    public Pet decay(Pet p, long now) {
        float h = Math.max(0f, (now - p.lastUpdated) / 3_600_000f);
        Pet d = p.copy();
        d.hunger = clamp(p.hunger - h * HUNGER_PER_HOUR, 0f, 100f);
        d.energy = clamp(p.energy - h * ENERGY_PER_HOUR, 0f, 100f);
        float moodRate = d.hunger < 20f ? MOOD_PER_HOUR * 2f : MOOD_PER_HOUR;
        d.mood = clamp(p.mood - h * moodRate, 0f, 100f);
        d.lastUpdated = now;
        return d;
    }

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

    // ---- actions ----
    public Pet feed(Pet p, int amount) {
        Pet n = p.copy();
        n.hunger = Math.min(100f, p.hunger + amount);
        n.mood = Math.min(100f, p.mood + 3f);
        return grow(n, 5);
    }

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

    public Pet play(Pet p) {
        if (p.energy < 10f) return p;
        Pet n = p.copy();
        n.mood = Math.min(100f, p.mood + 15f);
        n.energy = p.energy - 10f;
        n.hunger = Math.max(0f, p.hunger - 5f);
        n.coins = p.coins + 5;
        return grow(n, 10);
    }

    public Pet clean(Pet p) {
        Pet n = p.copy();
        n.mood = Math.min(100f, p.mood + 8f);
        return n;
    }

    public void tick(String mode) {
        edit(p -> {
            Pet n = p.copy();
            if ("SLEEP".equals(mode)) {
                n.energy = Math.min(100f, n.energy + 1.5f);
            } else {
                n.mood = Math.min(100f, n.mood + 1f);
                n.energy = Math.max(0f, n.energy - 0.5f);
                n.xp += 1;
            }
            return n;
        });
    }

    private Pet grow(Pet p, int xp) {
        p.xp += xp;
        int stage = p.xp >= 300 ? 2 : (p.xp >= 100 ? 1 : 0);
        if (stage > p.stage) {
            db.logDao().add(new GrowthLog("Evolved to stage " + (stage + 1) + "!"));
            p.stage = stage;
        }
        return p;
    }

    // ---- shop / inventory / history ----
    public List<Item> items() {
        synchronized (LOCK) { seed(); }
        return db.itemDao().all();
    }

    public List<Item> ownedItems() { return db.itemDao().owned(); }

    public List<GrowthLog> logs() { return db.logDao().all(); }

    private Item find(int id) {
        for (Item i : db.itemDao().all()) if (i.id == id) return i;
        return null;
    }

    public boolean buy(Item item) {
        synchronized (LOCK) {
            Pet p = load();
            if (p.coins < item.price) return false;
            Item fresh = find(item.id);
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
            Item fresh = find(item.id);
            if (fresh == null) return "Item not found";
            if ("food".equals(fresh.type)) {
                if (fresh.owned <= 0) return "None left";
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
