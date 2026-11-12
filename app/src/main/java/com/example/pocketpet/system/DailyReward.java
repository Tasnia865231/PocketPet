package com.example.pocketpet.system;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.pocketpet.data.PetRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A small gift for opening the game on a new day: coins and a happier Mochi. Returns the coins given (0 if already claimed). */
public final class DailyReward {
    private DailyReward() {}
    public static final int COINS = 10;

    public static int claimIfNeeded(Context c) {
        Context app = c.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences("pocketpet_watch", Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (today.equals(sp.getString("rewardDay", ""))) return 0;
        sp.edit().putString("rewardDay", today).apply();
        new PetRepository(app).edit(p -> { p.coins += COINS; p.mood = Math.min(100f, p.mood + 10f); return p; });
        return COINS;
    }
}
