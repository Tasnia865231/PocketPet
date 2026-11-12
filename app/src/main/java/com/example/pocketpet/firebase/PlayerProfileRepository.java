package com.example.pocketpet.firebase;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pocketpet.Bg;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.data.AppDatabase;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

/**
 * CLOUD FIRESTORE: the player's permanent profile. The ONLY place that reads or writes the "players" collection.
 *
 * One document per player, named after their Firebase UID:   players/{uid}
 *   uid          the player's Authentication id (same as the document name)
 *   displayName  the name other players see
 *   level        Mochi's level (1-10)
 *   totalCoins   the player's coin balance (goes up when earning, down when shopping); shown on the Friends cards
 *   createdAt    when the account was made (set by Firebase's clock, never by the phone)
 *   lastLoginAt  the last time the player opened the game (also Firebase's clock)
 *
 * The game itself still plays from the phone's own database (Room), which is fast and works offline.
 * This class keeps the cloud copy in step with it:
 *   - syncAfterLogin():  once per app start / login. Creates the profile the FIRST time, otherwise loads it.
 *   - pushProgress():    called by PetRepository.save() every time Mochi is saved; only writes when level/coins changed.
 *   - watchCoins():      live listener used by the Friends screen to show everyone's coins.
 */
public final class PlayerProfileRepository {
    private PlayerProfileRepository() {}

    private static final String TAG = "PlayerProfile";
    public static final String COLLECTION = "players";
    /** The security rules refuse a brand-new profile with more coins than this (see firebase-rules/firestore.rules). */
    public static final int MAX_START_COINS = 5000;

    // What the cloud is known to hold right now (so we only write real changes).
    private static final Object LOCK = new Object();
    private static String readyUid = null;       // coin/level pushes are allowed only after the login sync finished for THIS player
    private static String localUid = null;       // the phone's game belongs to THIS player (set right after any account switch wipe)
    private static int lastLevel = -1, lastCoins = -1;

    private static DocumentReference doc(String uid) {
        return FirebaseFirestore.getInstance().collection(COLLECTION).document(uid);
    }

    // ---------------------------------------------------------------- after login

    /**
     * Call right after a login (or when the app opens with a saved login).
     *  1. If a DIFFERENT player used this phone before, the phone's local game is wiped so accounts never mix.
     *  2. No profile in the cloud yet -> create it (from the phone's current level and coins).
     *  3. Profile exists -> update lastLoginAt and load its level + coins into the game. Nothing is reset.
     * "done" runs on the main thread at the end, even if the cloud could not be reached (the player just plays offline).
     */
    public static void syncAfterLogin(@NonNull Context ctx, @Nullable final Runnable done) {
        final Context app = ctx.getApplicationContext();
        final FirebaseUser user = FirebaseAuthManager.currentUser();
        if (user == null) { finish(done); return; }
        final String uid = user.getUid();
        final String name = FirebaseAuthManager.displayName();
        synchronized (LOCK) { readyUid = null; localUid = null; lastLevel = -1; lastCoins = -1; }

        Bg.IO.execute(() -> {
            Prefs prefs = new Prefs(app);
            String last = prefs.getLastUid();
            if (!last.isEmpty() && !last.equals(uid)) AppDatabase.get(app).clearAllTables();   // a different player: fresh game
            prefs.setLastUid(uid);
            synchronized (LOCK) { localUid = uid; }          // the phone's game now belongs to this player (feed posts are safe)
            final Pet local = new PetRepository(app).load();
            Bg.UI.post(() -> fetchOrCreate(app, uid, name, local, done));
        });
    }

    private static void fetchOrCreate(final Context app, final String uid, final String name,
                                      final Pet local, @Nullable final Runnable done) {
        doc(uid).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Could not reach Firestore; playing with the phone's saved data", task.getException());
                finish(done);
                return;
            }
            DocumentSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                Long cloudLevel = snap.getLong("level");
                Long cloudCoins = snap.getLong("totalCoins");
                if (snap.contains("totalEarnedCoins")) {
                    // A retired test field from an earlier version: remove it (the security rules no longer allow it)
                    doc(uid).update("lastLoginAt", FieldValue.serverTimestamp(), "totalEarnedCoins", FieldValue.delete());
                } else {
                    doc(uid).update("lastLoginAt", FieldValue.serverTimestamp());
                }
                if (cloudLevel == null || cloudCoins == null) { markReady(uid, local.level, local.coins); finish(done); return; }
                applyCloudToLocal(app, uid, cloudLevel.intValue(), cloudCoins.intValue(), done);
            } else {
                createProfile(uid, name, local, done);
            }
        });
    }

    private static void createProfile(final String uid, String name, Pet local, @Nullable final Runnable done) {
        final int level = Math.max(1, Math.min(10, local.level));
        final int coins = Math.max(0, Math.min(MAX_START_COINS, local.coins));
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("displayName", name);
        data.put("level", level);
        data.put("totalCoins", coins);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("lastLoginAt", FieldValue.serverTimestamp());
        doc(uid).set(data).addOnCompleteListener(t -> {
            if (t.isSuccessful()) markReady(uid, level, coins);
            else Log.w(TAG, "Could not create the profile (check the Firestore rules)", t.getException());
            finish(done);
        });
    }

    /** Cloud wins: the saved level and coins replace the phone's. (Mochi's hunger, hats etc. stay local.) */
    private static void applyCloudToLocal(final Context app, final String uid, final int level, final int coins,
                                          @Nullable final Runnable done) {
        Bg.IO.execute(() -> {
            new PetRepository(app).restoreProgress(level, coins);
            markReady(uid, level, coins);
            Bg.UI.post(() -> finish(done));
        });
    }

    private static void markReady(String uid, int level, int coins) {
        synchronized (LOCK) { readyUid = uid; lastLevel = level; lastCoins = coins; }
    }

    /**
     * True once the phone's game belongs to the logged-in player (right after login, before the cloud answers).
     * Used by the community feed so events are not lost when the network is slow or offline.
     */
    public static boolean isLocalReady() {
        String uid = FirebaseAuthManager.uid();
        synchronized (LOCK) { return uid != null && uid.equals(localUid); }
    }

    /** True once the login sync finished for the player who is logged in right now. */
    public static boolean isReady() {
        String uid = FirebaseAuthManager.uid();
        synchronized (LOCK) { return uid != null && uid.equals(readyUid); }
    }

    private static void finish(@Nullable Runnable done) { if (done != null) done.run(); }

    // ---------------------------------------------------------------- while playing

    /**
     * Saves the current level and coin balance to the cloud. Safe to call as often as you like: it does nothing
     * unless someone is logged in, the login sync has finished, and the numbers actually changed.
     */
    public static void pushProgress(final int level, final int coins) {
        final String uid = FirebaseAuthManager.uid();
        if (uid == null) return;
        synchronized (LOCK) {
            if (!uid.equals(readyUid)) return;
            if (level == lastLevel && coins == lastCoins) return;
            lastLevel = level; lastCoins = coins;
        }
        Map<String, Object> changes = new HashMap<>();
        changes.put("level", level);
        changes.put("totalCoins", coins);
        doc(uid).update(changes).addOnFailureListener(e -> {
            Log.w(TAG, "Progress was not saved to the cloud (rules or network)", e);
            synchronized (LOCK) { lastLevel = -1; lastCoins = -1; }      // try again at the next save
        });
    }

    // ---------------------------------------------------------------- reading everybody's coins

    /** Receives "coins by player UID" every time anyone's profile changes. Called on the main thread. */
    public interface CoinsListener { void onChanged(@NonNull Map<String, Long> coinsByUid); }

    /**
     * Live listener on the players collection (Firestore pushes only what changed). Each document is named after
     * the player's UID, so the map key IS the UID. Remove it with registration.remove() when the screen closes.
     */
    public static ListenerRegistration watchCoins(@NonNull final CoinsListener listener) {
        return FirebaseFirestore.getInstance().collection(COLLECTION).addSnapshotListener((snap, error) -> {
            if (error != null || snap == null) { Log.w(TAG, "Coin list cancelled", error); return; }
            Map<String, Long> coins = new HashMap<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Long c = d.getLong("totalCoins");
                if (c != null) coins.put(d.getId(), c);
            }
            listener.onChanged(coins);
        });
    }
}
