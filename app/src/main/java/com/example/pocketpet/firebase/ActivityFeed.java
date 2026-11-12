package com.example.pocketpet.firebase;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REALTIME DATABASE: the live community activity feed. The ONLY place that touches the "activities" branch.
 *
 * Each moment is one small record:   activities/{autoId} = { uid, type, value, createdAt }
 *   type = "level" | "minigame",  value = the number that goes with it (level reached, or games played)
 *   minigame events also carry  coins = the player's coin total after the games (so the feed can show it)
 * Names are NOT stored here (they already live in Firestore / the status branch); the screen looks them up by uid.
 * Sentences like "Bunny reached Level 5!" are built on the phone from type + value.
 *
 * Writing: post...() methods, called from the game when something happens (see PetRepository.save and MainActivity).
 * Reading: watchRecent() keeps a live listener on the newest 20 events; every phone viewing the feed gets new ones by itself.
 */
public final class ActivityFeed {
    private ActivityFeed() {}

    private static final String TAG = "ActivityFeed";
    private static final String PATH = "activities";
    private static final int SHOWN = 20;

    private static Query recentQuery;
    private static ValueEventListener recentListener;

    // ---------------------------------------------------------------- writing

    public static void postLevelUp(int level)                     { post(ActivityEvent.LEVEL, level, -1); }
    /** games = how many mini-games were just finished, coinsAfter = the player's coins after the reward. */
    public static void postMiniGames(int games, int coinsAfter)   { post(ActivityEvent.MINIGAME, Math.max(1, games), Math.max(0, coinsAfter)); }

    private static void post(String type, long value, long coinsAfter) {
        String uid = FirebaseAuthManager.uid();
        // Only logged-in players whose phone game belongs to them (so nothing is posted while accounts are switching)
        if (uid == null || !PlayerProfileRepository.isLocalReady()) return;
        Map<String, Object> e = new HashMap<>();
        e.put("uid", uid);
        e.put("type", type);
        e.put("value", value);
        if (coinsAfter >= 0) e.put("coins", coinsAfter);
        e.put("createdAt", ServerValue.TIMESTAMP);        // Firebase's clock, not the phone's
        FirebaseConfig.rtdb().getReference(PATH).push().setValue(e)      // push() = a brand-new unique id for this event
                .addOnFailureListener(err -> Log.w(TAG, "Could not post activity (check the Realtime Database rules)", err));
    }

    // ---------------------------------------------------------------- reading

    /** Receives the newest events, newest first, every time one is added. Called on the main thread. */
    public interface Listener { void onChanged(@NonNull List<ActivityEvent> newestFirst); }

    public static void watchRecent(@NonNull final Listener listener) {
        stopWatching();
        recentQuery = FirebaseConfig.rtdb().getReference(PATH).orderByChild("createdAt").limitToLast(SHOWN);
        recentListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<ActivityEvent> list = new ArrayList<>();
                for (DataSnapshot c : snap.getChildren()) {
                    String uid = c.child("uid").getValue(String.class);
                    String type = c.child("type").getValue(String.class);
                    Long value = c.child("value").getValue(Long.class);
                    Long coins = c.child("coins").getValue(Long.class);
                    Long when = c.child("createdAt").getValue(Long.class);
                    if (uid == null || type == null || value == null) continue;
                    list.add(new ActivityEvent(c.getKey(), uid, type, value, coins == null ? -1L : coins,
                            when == null ? System.currentTimeMillis() : when));
                }
                Collections.sort(list, (a, b) -> Long.compare(b.createdAt, a.createdAt));
                listener.onChanged(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { Log.w(TAG, "Feed cancelled", e.toException()); }
        };
        recentQuery.addValueEventListener(recentListener);
    }

    public static void stopWatching() {
        if (recentQuery != null && recentListener != null) recentQuery.removeEventListener(recentListener);
        recentQuery = null;
        recentListener = null;
    }
}
