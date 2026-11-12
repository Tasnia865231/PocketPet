package com.example.pocketpet.firebase;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pocketpet.Bg;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REALTIME DATABASE: live online/offline status. The ONLY place that touches the "status" branch.
 *
 * Each logged-in player owns one tiny record:   status/{uid} = { state: "online"|"offline", lastChanged: <time>, displayName: "..." }
 *
 * How "online" and "offline" happen:
 *   1. Firebase keeps a magic value at ".info/connected": true while this phone is connected to the database.
 *   2. Every time it turns true we first tell the SERVER: "if this phone disconnects, write offline" (onDisconnect()).
 *      The server remembers it, so it works even when the phone dies or loses its internet and cannot send anything.
 *   3. Then we write "online".
 *   4. We also write "offline" ourselves when the app goes to the background or the player logs out.
 *
 * The record is written only while the app is on screen AND the player is logged in AND their profile finished loading.
 */
public final class PresenceManager {
    private PresenceManager() {}

    private static final String TAG = "Presence";
    private static final String STATUS = "status";
    private static final long BACKGROUND_DELAY_MS = 1500;   // ignores tiny gaps, e.g. while the screen rotates

    private static int startedActivities = 0;
    private static boolean allowed = false;     // true after login + profile sync, false after logout
    private static DatabaseReference connectedRef;
    private static ValueEventListener connectedListener;
    private static ValueEventListener statusListener;
    private static DatabaseReference statusRoot;

    private static final Runnable goBackground = () -> {
        stopWatchingConnection();
        writeOffline(null);
    };

    // ---------------------------------------------------------------- setup

    /** Call once from Application.onCreate(): follows whether any screen of the game is visible. */
    public static void install(@NonNull Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(@NonNull Activity a) {
                startedActivities++;
                Bg.UI.removeCallbacks(goBackground);
                if (startedActivities == 1) refresh();                  // app came to the front
            }
            @Override public void onActivityStopped(@NonNull Activity a) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (startedActivities == 0) Bg.UI.postDelayed(goBackground, BACKGROUND_DELAY_MS);   // app left the screen
            }
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityResumed(@NonNull Activity a) {}
            @Override public void onActivityPaused(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {}
        });
    }

    /** Call after login (and after the Firestore profile sync): the player may now appear online. */
    public static void enable() {
        allowed = true;
        refresh();
    }

    /**
     * Call BEFORE logging out: writes "offline" while the player is still signed in (the rules need that),
     * then runs "then". Never waits more than 2 seconds, so a bad connection cannot trap the player.
     */
    public static void disableThen(@NonNull final Runnable then) {
        allowed = false;
        stopWatchingConnection();
        final AtomicBoolean fired = new AtomicBoolean(false);
        final Runnable once = () -> { if (fired.compareAndSet(false, true)) then.run(); };
        Bg.UI.postDelayed(once, 2000);
        writeOffline(once);
    }

    // ---------------------------------------------------------------- going online / offline

    private static void refresh() {
        if (startedActivities > 0 && allowed && FirebaseAuthManager.isLoggedIn()) startWatchingConnection();
    }

    private static DatabaseReference myStatus() {
        String uid = FirebaseAuthManager.uid();
        return uid == null ? null : FirebaseConfig.rtdb().getReference(STATUS).child(uid);
    }

    private static Map<String, Object> record(String state) {
        Map<String, Object> m = new HashMap<>();
        m.put("state", state);
        m.put("lastChanged", ServerValue.TIMESTAMP);          // the server writes its own clock = trustworthy
        m.put("displayName", FirebaseAuthManager.displayName());
        return m;
    }

    private static void startWatchingConnection() {
        if (connectedListener != null) return;                 // already watching
        final DatabaseReference me = myStatus();
        if (me == null) return;
        connectedRef = FirebaseConfig.rtdb().getReference(".info/connected");
        connectedListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!Boolean.TRUE.equals(snap.getValue(Boolean.class))) return;      // not connected right now
                // Step 1: server, please write "offline" if this phone disconnects. Step 2: only then write "online".
                me.onDisconnect().setValue(record("offline")).addOnCompleteListener(t -> {
                    if (t.isSuccessful()) me.setValue(record("online"));
                    else Log.w(TAG, "Could not register onDisconnect (check the Realtime Database rules)", t.getException());
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { Log.w(TAG, "Connection watch cancelled", e.toException()); }
        };
        connectedRef.addValueEventListener(connectedListener);
    }

    private static void stopWatchingConnection() {
        if (connectedRef != null && connectedListener != null) connectedRef.removeEventListener(connectedListener);
        connectedRef = null;
        connectedListener = null;
    }

    private static void writeOffline(@Nullable final Runnable done) {
        DatabaseReference me = myStatus();
        if (me == null) { if (done != null) done.run(); return; }
        me.setValue(record("offline")).addOnCompleteListener(t -> { if (done != null) done.run(); });
    }

    // ---------------------------------------------------------------- reading everybody's status

    /** Receives the full, sorted list every time anyone's status changes. Called on the main thread. */
    public interface Listener { void onChanged(@NonNull List<PlayerStatus> players); }

    /** Start a live listener on the whole status branch (call stopWatchingPlayers() when the screen closes). */
    public static void watchPlayers(@NonNull final Listener listener) {
        stopWatchingPlayers();
        statusRoot = FirebaseConfig.rtdb().getReference(STATUS);
        statusListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<PlayerStatus> list = new ArrayList<>();
                for (DataSnapshot c : snap.getChildren()) {
                    String name = c.child("displayName").getValue(String.class);
                    String state = c.child("state").getValue(String.class);
                    Long when = c.child("lastChanged").getValue(Long.class);
                    if (name == null || state == null) continue;
                    list.add(new PlayerStatus(c.getKey(), name, "online".equals(state), when == null ? 0L : when));
                }
                // online players first, then the most recently seen, then by name
                Collections.sort(list, (a, b) -> {
                    if (a.online != b.online) return a.online ? -1 : 1;
                    if (a.lastChanged != b.lastChanged) return a.lastChanged > b.lastChanged ? -1 : 1;
                    return a.displayName.compareToIgnoreCase(b.displayName);
                });
                listener.onChanged(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { Log.w(TAG, "Player list cancelled", e.toException()); }
        };
        statusRoot.addValueEventListener(statusListener);
    }

    public static void stopWatchingPlayers() {
        if (statusRoot != null && statusListener != null) statusRoot.removeEventListener(statusListener);
        statusRoot = null;
        statusListener = null;
    }
}
