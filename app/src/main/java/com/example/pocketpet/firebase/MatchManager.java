package com.example.pocketpet.firebase;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pocketpet.Bg;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.game.MatchLogic;
import com.example.pocketpet.ui.KawaiiDialog;
import com.example.pocketpet.ui.KawaiiToast;
import com.example.pocketpet.ui.MultiplayerGameActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ONLINE TIC-TAC-TOE: invitations + the shared match. The ONLY place that touches these Realtime Database branches:
 *
 *   invites/{toUid}/{fromUid} = { fromName, toName, state: pending | accepted | rejected, createdAt, gameId? }
 *        one invitation per pair, so duplicates are impossible. Expires 30 s after createdAt.
 *   playing/{uid}             = gameId of the match this player is in (blocks new invitations; lets a player rejoin)
 *   games/{gameId}            = the match (see MatchState): board, turn, round, scores, phase
 *   claims/{gameId}/{uid}     = true once the winner collected the 50 coins (can be written once, only by the winner)
 *
 * Firestore keeps the permanent record: matches/{gameId} (who played, final score, winner) and the coin balance
 * (the winner's phone adds 50 coins to the normal game coins, which the existing code saves to players/{uid}).
 *
 * The rules in firebase-rules/database.rules.json check every move, win, score and claim, so a changed app
 * cannot cheat the board. (Awarding the coins itself is done by the winner's phone: see claimReward.)
 */
public final class MatchManager {
    private MatchManager() {}

    private static final String TAG = "Match";
    public static final int WIN_COINS = 50;
    private static final long INVITE_MS = 30_000;
    private static final long STALE_MATCH_MS = 30 * 60_000L;

    // ------------------------------------------------------------------ state
    private static Activity top;                 // the screen the player is looking at (for dialogs)
    private static int started = 0;
    private static boolean allowed = false;      // true after login, false after logout
    private static long serverOffset = 0;        // server clock minus phone clock

    private static DatabaseReference offsetRef, invitesRef;
    private static ValueEventListener offsetListener, invitesListener;

    private static final List<Invite> lastInvites = new ArrayList<>();
    private static final Set<String> handled = new HashSet<>();
    private static Dialog incomingDialog;
    private static String incomingKey;
    private static Runnable incomingExpiry;

    private static String pendingTo, pendingName;              // the invitation I sent and wait for
    private static DatabaseReference sentRef;
    private static ValueEventListener sentListener;
    private static Runnable sentTimeout;

    private static String currentGid;            // the match this player is in (null = none)
    private static String screenGid;             // the match whose screen is open right now
    private static String pendingOpen;           // open this match as soon as a screen is visible
    private static String pendingNoticeTitle, pendingNoticeBody;
    private static boolean resumeChecking = false;

    private static DatabaseReference gameRef;
    private static ValueEventListener gameListener;
    private static String watchedGid;
    private static int watchRetries = 0;

    private static Runnable uiListener;
    private static final Set<String> claimsInFlight = new HashSet<>();

    private static final Runnable stopLater = MatchManager::stopListening;

    private static final class Invite {
        String fromUid, fromName, state, gameId;
        long createdAt;
    }

    public interface StateListener { void onState(@Nullable MatchState state); }
    public interface RewardCallback { void onClaimed(int newBalance); void onAlreadyClaimed(); }

    // ------------------------------------------------------------------ setup

    /** Call once from Application.onCreate(): follows which screen is visible. */
    public static void install(@NonNull Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(@NonNull Activity a) {
                started++; top = a;
                Bg.UI.removeCallbacks(stopLater);
                refresh();
            }
            @Override public void onActivityResumed(@NonNull Activity a) {
                top = a;
                showPendingNotice();
                if (pendingOpen != null) { String g = pendingOpen; pendingOpen = null; openGame(g); }
                evaluateIncoming();
            }
            @Override public void onActivityStopped(@NonNull Activity a) {
                started = Math.max(0, started - 1);
                dismissIncoming();                      // the dialog belongs to the screen that is leaving
                if (top == a) top = null;
                if (started == 0) Bg.UI.postDelayed(stopLater, 1500);
            }
            @Override public void onActivityDestroyed(@NonNull Activity a) { if (top == a) top = null; }
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityPaused(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
        });
    }

    /** Call after login (next to PresenceManager.enable()). */
    public static void enable() { allowed = true; refresh(); }

    /** Call BEFORE logging out. */
    public static void disable() {
        allowed = false;
        stopListening();
        stopWatchingGame();
        cleanupSent();
        lastInvites.clear(); handled.clear();
        currentGid = null; screenGid = null; pendingOpen = null;
    }

    private static void refresh() {
        if (started > 0 && allowed && FirebaseAuthManager.isLoggedIn()) startListening();
    }

    /** The Community list redraws itself when this is set (so "Invite" becomes "Waiting..."). */
    public static void setUiListener(@Nullable Runnable r) { uiListener = r; }
    private static void notifyUi() { Runnable r = uiListener; if (r != null) Bg.UI.post(r); }

    public static boolean isPendingTo(String uid) { return pendingTo != null && pendingTo.equals(uid); }
    public static boolean hasPendingInvite() { return pendingTo != null; }
    public static boolean inMatch() { return currentGid != null; }

    /** The server's clock (milliseconds), so both phones agree on timers even if their own clocks differ. */
    public static long serverNow() { return System.currentTimeMillis() + serverOffset; }

    private static DatabaseReference root() { return FirebaseConfig.rtdb().getReference(); }
    private static DatabaseReference invite(String to, String from) { return root().child("invites").child(to).child(from); }
    private static DatabaseReference playing(String uid) { return root().child("playing").child(uid); }
    private static DatabaseReference game(String gid) { return root().child("games").child(gid); }
    private static DatabaseReference claim(String gid, String uid) { return root().child("claims").child(gid).child(uid); }

    private static void once(DatabaseReference ref, final java.util.function.Consumer<DataSnapshot> done) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) { done.accept(s); }
            @Override public void onCancelled(@NonNull DatabaseError e) { Log.w(TAG, "read cancelled", e.toException()); done.accept(null); }
        });
    }

    // ------------------------------------------------------------------ listening for invitations

    private static void startListening() {
        final String me = FirebaseAuthManager.uid();
        if (me == null || invitesListener != null) return;

        offsetRef = FirebaseConfig.rtdb().getReference(".info/serverTimeOffset");
        offsetListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) { Double v = s.getValue(Double.class); if (v != null) serverOffset = v.longValue(); }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        offsetRef.addValueEventListener(offsetListener);

        invitesRef = root().child("invites").child(me);
        invitesListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                lastInvites.clear();
                for (DataSnapshot c : snap.getChildren()) {
                    Invite i = new Invite();
                    i.fromUid = c.getKey();
                    i.fromName = c.child("fromName").getValue(String.class);
                    i.state = c.child("state").getValue(String.class);
                    i.gameId = c.child("gameId").getValue(String.class);
                    Long t = c.child("createdAt").getValue(Long.class);
                    i.createdAt = t == null ? 0L : t;
                    if (i.fromUid != null && i.fromName != null && i.state != null) lastInvites.add(i);
                }
                updateIncoming();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { Log.w(TAG, "Invitations cancelled", e.toException()); }
        };
        invitesRef.addValueEventListener(invitesListener);
        resumeIfAny();                       // a match may still be running (app was closed) -> rejoin it
    }

    private static void stopListening() {
        if (invitesRef != null && invitesListener != null) invitesRef.removeEventListener(invitesListener);
        if (offsetRef != null && offsetListener != null) offsetRef.removeEventListener(offsetListener);
        invitesRef = null; invitesListener = null; offsetRef = null; offsetListener = null;
        dismissIncoming();
    }

    // ------------------------------------------------------------------ receiving an invitation

    private static boolean fresh(Invite i) {
        return i.createdAt > 0 && serverNow() - i.createdAt < INVITE_MS;
    }

    private static String keyOf(Invite i) { return i.fromUid + "|" + i.createdAt; }

    private static void updateIncoming() {
        if (incomingKey != null) {                          // the shown invitation was cancelled or expired?
            boolean still = false;
            for (Invite i : lastInvites) if (keyOf(i).equals(incomingKey) && "pending".equals(i.state) && fresh(i)) still = true;
            if (!still) dismissIncoming();
        }
        evaluateIncoming();
    }

    private static void evaluateIncoming() {
        if (top == null || incomingDialog != null || currentGid != null || !allowed) return;
        for (Invite i : lastInvites) {
            if ("pending".equals(i.state) && fresh(i) && !handled.contains(keyOf(i))) { showIncoming(i); return; }
        }
    }

    private static void showIncoming(final Invite inv) {
        final String key = keyOf(inv);
        incomingKey = key;
        // "Tasnia2 wants to play Tic-Tac-Toe with you!"  [ACCEPT] [REJECT]   (same storybook dialog as the rest of the game)
        incomingDialog = KawaiiDialog.show(top, 0, "Game invite!",
                inv.fromName + " wants to play Tic-Tac-Toe with you!",
                "ACCEPT", () -> { handled.add(key); clearIncomingState(); accept(inv); },
                "REJECT", () -> { handled.add(key); clearIncomingState(); reject(inv); });
        if (incomingDialog == null) { incomingKey = null; return; }
        long left = Math.max(500, INVITE_MS - (serverNow() - inv.createdAt));
        incomingExpiry = () -> {
            if (key.equals(incomingKey)) {
                handled.add(key);
                dismissIncoming();
                if (top != null) KawaiiToast.show(top, "The invitation from " + inv.fromName + " expired");
            }
        };
        Bg.UI.postDelayed(incomingExpiry, left);
    }

    private static void clearIncomingState() {
        if (incomingExpiry != null) Bg.UI.removeCallbacks(incomingExpiry);
        incomingExpiry = null; incomingDialog = null; incomingKey = null;
    }

    private static void dismissIncoming() {
        Dialog d = incomingDialog;
        clearIncomingState();
        if (d != null) { try { d.dismiss(); } catch (Exception ignored) { } }
    }

    private static void reject(Invite inv) {
        String me = FirebaseAuthManager.uid();
        if (me == null) return;
        Map<String, Object> up = new HashMap<>();
        up.put("state", "rejected");
        invite(me, inv.fromUid).updateChildren(up)
                .addOnFailureListener(e -> Log.w(TAG, "Could not reject (maybe it expired)", e));
    }

    /** Accept: mark the invitation accepted, create the match (I am O), then open the game screen. */
    private static void accept(final Invite inv) {
        final String me = FirebaseAuthManager.uid();
        if (me == null) return;
        final DatabaseReference gref = root().child("games").push();
        final String gid = gref.getKey();
        if (gid == null) return;
        Map<String, Object> up = new HashMap<>();
        up.put("state", "accepted");
        up.put("gameId", gid);
        invite(me, inv.fromUid).updateChildren(up).addOnSuccessListener(v1 -> {
            Map<String, Object> g = new HashMap<>();
            g.put("x", inv.fromUid);                      // Player 1 = the one who invited = X, plays first
            g.put("o", me);                               // Player 2 = the one who accepted = O
            g.put("xName", inv.fromName);
            g.put("oName", FirebaseAuthManager.displayName());
            g.put("phase", "playing");
            g.put("round", 1);
            g.put("turn", "X");
            g.put("sx", 0);
            g.put("so", 0);
            g.put("createdAt", ServerValue.TIMESTAMP);
            gref.setValue(g).addOnSuccessListener(v2 -> {
                playing(me).setValue(gid);
                invite(me, inv.fromUid).removeValue();    // the invitation did its job
                openGame(gid);
            }).addOnFailureListener(e -> {
                Log.w(TAG, "Could not create the match", e);
                notice("Oops", "The match could not start. Please try again.");
            });
        }).addOnFailureListener(e -> {
            Log.w(TAG, "Could not accept", e);
            notice("Too late", "That invitation has expired.");
        });
    }

    // ------------------------------------------------------------------ sending an invitation

    /** The Invite button. Rules block: offline players, busy players, yourself, and a second live invitation to the same player. */
    public static void sendInvite(final Context ctx, final String toUid, final String toName) {
        final String me = FirebaseAuthManager.uid();
        if (me == null || toUid == null || toUid.equals(me)) return;
        if (currentGid != null) { toast(ctx, "You are already in a match"); return; }
        if (pendingTo != null) { toast(ctx, "Wait for the answer to your invite first"); return; }
        pendingTo = toUid; pendingName = toName;
        notifyUi();
        Map<String, Object> m = new HashMap<>();
        m.put("fromName", FirebaseAuthManager.displayName());
        m.put("toName", toName);
        m.put("state", "pending");
        m.put("createdAt", ServerValue.TIMESTAMP);
        final DatabaseReference ref = invite(toUid, me);
        ref.setValue(m).addOnSuccessListener(v -> {
            toast(ctx, "Invite sent to " + toName + "!");
            watchSent(ref, toName);
        }).addOnFailureListener(e -> {
            Log.w(TAG, "Invite refused", e);
            cleanupSent();
            toast(ctx, toName + " is busy or offline right now");
        });
    }

    private static void watchSent(final DatabaseReference ref, final String toName) {
        cleanupSentListeners();
        sentRef = ref;
        sentListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                String state = s.child("state").getValue(String.class);
                if ("accepted".equals(state)) {
                    String gid = s.child("gameId").getValue(String.class);
                    if (gid == null) return;
                    cleanupSent();
                    String me = FirebaseAuthManager.uid();
                    if (me != null) playing(me).setValue(gid);
                    openGame(gid);                    // the screen waits a moment until the match exists
                } else if ("rejected".equals(state)) {
                    cleanupSent();
                    ref.removeValue();
                    // "Tasnia1 doesn't want to play with you right now."
                    notice("Maybe next time", toName + " doesn't want to play with you right now.");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { Log.w(TAG, "Sent invite watch cancelled", e.toException()); }
        };
        ref.addValueEventListener(sentListener);
        sentTimeout = () -> {
            if (pendingTo == null) return;
            ref.removeValue();
            cleanupSent();
            notice("No answer", toName + " didn't answer the invitation.");
        };
        Bg.UI.postDelayed(sentTimeout, INVITE_MS + 1500);
    }

    private static void cleanupSentListeners() {
        if (sentRef != null && sentListener != null) sentRef.removeEventListener(sentListener);
        if (sentTimeout != null) Bg.UI.removeCallbacks(sentTimeout);
        sentRef = null; sentListener = null; sentTimeout = null;
    }

    private static void cleanupSent() {
        cleanupSentListeners();
        pendingTo = null; pendingName = null;
        notifyUi();
    }

    // ------------------------------------------------------------------ opening / resuming a match

    private static void openGame(String gid) {
        if (gid == null) return;
        currentGid = gid;
        notifyUi();
        if (gid.equals(screenGid)) return;
        Activity a = top;
        if (a == null) { pendingOpen = gid; return; }
        a.startActivity(new Intent(a, MultiplayerGameActivity.class).putExtra(MultiplayerGameActivity.EXTRA_GID, gid));
    }

    /** Called by the match screen: it is now open for this match. */
    public static void setScreen(String gid) { screenGid = gid; currentGid = gid; notifyUi(); }

    /** Called by the match screen when the player leaves it for good (match over or left). Frees the player for new matches. */
    public static void leaveScreen() {
        String me = FirebaseAuthManager.uid();
        screenGid = null; currentGid = null; pendingOpen = null;
        stopWatchingGame();
        if (me != null) playing(me).removeValue();
        notifyUi();
    }

    /**
     * If this player is still inside an unfinished match (the app was closed, or the phone lost its connection),
     * open it again with the board exactly as it is. Also collects a win that was not collected yet.
     * Called when the Mochi screen appears and when the app starts listening.
     */
    public static void resumeIfAny() {
        final String me = FirebaseAuthManager.uid();
        if (!allowed || me == null || currentGid != null || resumeChecking) return;
        resumeChecking = true;
        once(playing(me), ps -> {
            final String gid = ps == null ? null : ps.getValue(String.class);
            if (gid == null) { resumeChecking = false; return; }
            once(game(gid), gs -> {
                resumeChecking = false;
                if (gs == null) return;                                  // could not read: try again next time
                MatchState s = MatchState.from(gs);
                if (s == null || s.symbolOf(me) == null || "cancelled".equals(s.phase)) { playing(me).removeValue(); return; }
                if ("finished".equals(s.phase)) {
                    if (!s.wonBy(me)) { playing(me).removeValue(); return; }
                    once(claim(gid, me), cs -> {
                        if (cs == null || cs.exists()) playing(me).removeValue();   // already collected
                        else openGame(gid);                                          // the screen collects the reward
                    });
                    return;
                }
                if (serverNow() - s.createdAt > STALE_MATCH_MS) { cancelMatch(s); playing(me).removeValue(); return; }
                openGame(gid);
            });
        });
    }

    // ------------------------------------------------------------------ the shared match

    /** Starts a live listener on one match. A match that is not created yet is retried for a few seconds. */
    public static void watchGame(final String gid, @NonNull final StateListener listener) {
        stopWatchingGame();
        watchedGid = gid; watchRetries = 0;
        attachGame(gid, listener);
    }

    private static void attachGame(final String gid, final StateListener listener) {
        gameRef = game(gid);
        gameListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) { watchRetries = 0; listener.onState(MatchState.from(s)); }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                // Reading a match that does not exist yet is refused by the rules: the invitee is still creating it. Try again.
                if (gid.equals(watchedGid) && watchRetries++ < 20) {
                    Bg.UI.postDelayed(() -> { if (gid.equals(watchedGid)) attachGame(gid, listener); }, 800);
                } else if (gid.equals(watchedGid)) {
                    listener.onState(null);
                }
            }
        };
        gameRef.addValueEventListener(gameListener);
    }

    public static void stopWatchingGame() {
        if (gameRef != null && gameListener != null) gameRef.removeEventListener(gameListener);
        gameRef = null; gameListener = null; watchedGid = null;
    }

    /** A tap on the board. Every field written here is checked again by the security rules. */
    public static void playMove(MatchState s, int cell) {
        String me = FirebaseAuthManager.uid();
        String sym = s.symbolOf(me);
        if (me == null || sym == null || cell < 0 || cell > 8) return;
        if (!"playing".equals(s.phase) || !sym.equals(s.turn) || s.cells[cell] != null) return;
        String[] c = s.cells.clone();
        c[cell] = sym;
        Map<String, Object> up = new HashMap<>();
        up.put("board/" + cell, sym);
        String out = MatchLogic.outcome(c);
        if (out == null) {
            up.put("turn", "X".equals(sym) ? "O" : "X");
        } else {                                              // this move ended the round
            int sx = s.sx + ("X".equals(out) ? 1 : 0);
            int so = s.so + ("O".equals(out) ? 1 : 0);
            up.put("r" + s.round, out);
            up.put("sx", sx);
            up.put("so", so);
            up.put("endedAt", ServerValue.TIMESTAMP);
            if (s.round < 3) {
                up.put("phase", "roundEnd");
            } else {
                up.put("phase", "finished");
                up.put("winner", sx > so ? "X" : so > sx ? "O" : "D");
            }
        }
        game(s.id).updateChildren(up).addOnFailureListener(e -> Log.w(TAG, "Move refused", e));
    }

    /** After a round result was shown for a moment, either phone may start the next round (the first one wins, the other is ignored). */
    public static void advanceRound(MatchState s) {
        if (!"roundEnd".equals(s.phase) || s.round >= 3) return;
        Map<String, Object> up = new HashMap<>();
        up.put("phase", "playing");
        up.put("round", s.round + 1);
        up.put("turn", "X");
        up.put("board", null);
        game(s.id).updateChildren(up).addOnFailureListener(e -> Log.d(TAG, "Next round already started (or too early)"));
    }

    /** Leave the match or give up waiting. Nobody wins and nobody gets coins. */
    public static void cancelMatch(MatchState s) {
        String me = FirebaseAuthManager.uid();
        if (me == null || s.isOver()) return;
        Map<String, Object> up = new HashMap<>();
        up.put("phase", "cancelled");
        up.put("cancelledBy", me);
        game(s.id).updateChildren(up).addOnFailureListener(e -> Log.w(TAG, "Could not cancel", e));
    }

    // ------------------------------------------------------------------ reward + record

    /**
     * The verified winner collects the 50 coins. claims/{gameId}/{uid} can be written ONCE and only by the winner
     * (the rules check the real final score), so pressing twice, reopening the app or a second phone cannot pay twice.
     * Only after that write succeeded the coins are added to the normal game coins (saved to Firestore by PetRepository).
     */
    public static void claimReward(final Context ctx, final MatchState s, @NonNull final RewardCallback cb) {
        final String me = FirebaseAuthManager.uid();
        if (me == null || !s.wonBy(me) || !claimsInFlight.add(s.id)) return;
        final Context app = ctx.getApplicationContext();
        claim(s.id, me).setValue(true).addOnSuccessListener(v -> Bg.IO.execute(() -> {
            Pet p = new PetRepository(app).edit(pet -> { pet.coins += WIN_COINS; return pet; });
            final int balance = p.coins;
            Bg.UI.post(() -> { claimsInFlight.remove(s.id); cb.onClaimed(balance); });
        })).addOnFailureListener(e -> {
            Log.w(TAG, "Reward not paid (already collected?)", e);
            claimsInFlight.remove(s.id);
            cb.onAlreadyClaimed();
        });
    }

    /** Firestore: the permanent record of a finished match (one document per match; the rules allow creating it once). */
    public static void writeMatchRecord(MatchState s) {
        String me = FirebaseAuthManager.uid();
        if (me == null || !"finished".equals(s.phase) || s.symbolOf(me) == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("gameId", s.id);
        m.put("xUid", s.x);
        m.put("oUid", s.o);
        m.put("xName", s.xName);
        m.put("oName", s.oName);
        m.put("sx", s.sx);
        m.put("so", s.so);
        m.put("winnerUid", "X".equals(s.winner) ? s.x : "O".equals(s.winner) ? s.o : "");
        m.put("playedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("matches").document(s.id).set(m)
                .addOnFailureListener(e -> Log.d(TAG, "Match record exists already (the other phone saved it)"));
    }

    // ------------------------------------------------------------------ small helpers

    private static void toast(Context ctx, String text) {
        Context c = (ctx instanceof Activity) ? ctx : top;
        if (c != null) KawaiiToast.show(c, text);
    }

    /** A short message in the storybook dialog; waits for a visible screen if the app is in the background. */
    private static void notice(String title, String body) {
        if (top == null) { pendingNoticeTitle = title; pendingNoticeBody = body; return; }
        KawaiiDialog.show(top, 0, title, body, "OK", null, null, null);
    }

    private static void showPendingNotice() {
        if (pendingNoticeTitle != null && top != null) {
            String t = pendingNoticeTitle, b = pendingNoticeBody;
            pendingNoticeTitle = null; pendingNoticeBody = null;
            KawaiiDialog.show(top, 0, t, b, "OK", null, null, null);
        }
    }
}
