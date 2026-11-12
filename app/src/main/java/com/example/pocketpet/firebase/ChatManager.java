package com.example.pocketpet.firebase;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Live chat between the two players of ONE online match (Realtime Database: chats/{gameId}/{messageId}).
 * It lives next to the match, never inside games/{gameId}, and never touches the board, turns, rounds or coins.
 *
 *   messageId = a push key (sorted by time)
 *   uid       who sent it (the security rules force this to be the sender's own UID)
 *   text      1-60 characters
 *   createdAt the server's clock (the rules force this too)
 *
 * Only messages newer than the moment the screen opened are listened to (a limited query), so old messages are never
 * replayed and the whole history is never downloaded. Messages are never queued while offline.
 */
public final class ChatManager {
    private static final String TAG = "Chat";
    public static final int MAX_LEN = 60;
    public static final long COOLDOWN_MS = 1500;
    private static final int HISTORY_LIMIT = 10;      // never more than this many messages are requested

    public enum Result { SENT, EMPTY, COOLDOWN, OFFLINE }

    public interface Listener { void onMessage(@NonNull String uid, @NonNull String text); }

    private static long lastSendAt = -COOLDOWN_MS;

    private final String gid, me;
    private final Listener listener;
    private final Set<String> seen = new HashSet<>();
    private Query query;
    private ChildEventListener childListener;

    public ChatManager(@NonNull String gid, @NonNull String me, @NonNull Listener listener) {
        this.gid = gid; this.me = me; this.listener = listener;
    }

    private static DatabaseReference chatRef(String gid) {
        return FirebaseConfig.rtdb().getReference("chats").child(gid);
    }

    // ------------------------------------------------------------------ listening

    /** Start listening for messages sent from now on. Safe to call again after stop(). */
    public void start() {
        stop();
        seen.clear();
        double opened = MatchManager.serverNow();               // only messages newer than this moment
        query = chatRef(gid).orderByChild("createdAt").startAt(opened).limitToLast(HISTORY_LIMIT);
        childListener = new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot s, @Nullable String prev) { handle(s); }
            @Override public void onChildChanged(@NonNull DataSnapshot s, @Nullable String prev) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}       // the chat being cleaned up never disturbs the screen
            @Override public void onChildMoved(@NonNull DataSnapshot s, @Nullable String prev) {}
            @Override public void onCancelled(@NonNull DatabaseError e) { Log.w(TAG, "chat listener cancelled: " + e.getMessage()); }
        };
        query.addChildEventListener(childListener);
    }

    public void stop() {
        if (query != null && childListener != null) query.removeEventListener(childListener);
        query = null; childListener = null;
    }

    private void handle(DataSnapshot s) {
        String key = s.getKey();
        if (key == null || !seen.add(key)) return;              // each message floats only once
        String uid = s.child("uid").getValue(String.class);
        String text = clean(s.child("text").getValue(String.class));   // cleaned again: never trust what arrives
        if (uid == null || text.isEmpty()) return;
        listener.onMessage(uid, text);
    }

    // ------------------------------------------------------------------ sending

    /**
     * Sends one message. Returns what happened so the screen can react. Nothing is queued: offline = refused.
     * onFail runs if the database refuses the message (for example the match was cancelled).
     */
    public Result send(@Nullable String raw, boolean connected, @Nullable final Runnable onFail) {
        String text = clean(raw);
        if (text.isEmpty()) return Result.EMPTY;
        if (!connected) return Result.OFFLINE;
        long now = SystemClock.elapsedRealtime();
        if (now - lastSendAt < COOLDOWN_MS) return Result.COOLDOWN;
        lastSendAt = now;
        Map<String, Object> m = new HashMap<>();
        m.put("uid", me);
        m.put("text", text);
        m.put("createdAt", ServerValue.TIMESTAMP);
        chatRef(gid).push().setValue(m).addOnFailureListener(e -> {
            Log.w(TAG, "message refused", e);
            if (onFail != null) onFail.run();
        });
        return Result.SENT;
    }

    /** Removes this match's whole chat (used when a player leaves a finished or cancelled match). */
    public static void deleteChat(@NonNull String gid) {
        chatRef(gid).removeValue().addOnFailureListener(e -> Log.w(TAG, "could not clean up chat", e));
    }

    // ------------------------------------------------------------------ cleaning

    /**
     * Makes any text safe to send and show: line breaks and tabs become spaces, control characters and invisible
     * "tricky" characters (zero-width, text-direction switches) are removed, spaces are trimmed, and the result is cut
     * to 60 characters. Emoji (including joined ones) are kept. Returns "" when nothing is left.
     */
    @NonNull
    public static String clean(@Nullable String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastSpace = true;                                // also trims the start
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || Character.isSpaceChar(c)) {
                if (!lastSpace) { sb.append(' '); lastSpace = true; }
                continue;
            }
            if (Character.isISOControl(c) || isInvisible(c)) continue;
            sb.append(c);
            lastSpace = false;
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == ' ') end--;      // trims the end
        sb.setLength(end);
        if (sb.length() > MAX_LEN) {
            int cut = MAX_LEN;
            if (Character.isHighSurrogate(sb.charAt(cut - 1))) cut--;     // never split an emoji in half
            sb.setLength(cut);
            end = sb.length();
            while (end > 0 && sb.charAt(end - 1) == ' ') end--;
            sb.setLength(end);
        }
        return sb.toString();
    }

    private static boolean isInvisible(char c) {
        return c == '\u200B' || c == '\u200E' || c == '\u200F' || c == '\u2060' || c == '\uFEFF'
                || (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069');
    }
}
