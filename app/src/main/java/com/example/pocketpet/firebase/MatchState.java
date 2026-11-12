package com.example.pocketpet.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;

/**
 * One snapshot of an online match (Realtime Database: games/{gameId}). Read-only; only MatchManager writes.
 *   x / o          UIDs of Player 1 (X, the inviter) and Player 2 (O, the invitee)
 *   phase          "playing" | "roundEnd" | "finished" | "cancelled"
 *   round, turn    round 1-3, and whose turn it is ("X" or "O")
 *   cells          the shared 3x3 board (null = empty)
 *   results        winner of rounds 1-3: "X", "O" or "D" (draw), null = not played yet
 *   sx, so         rounds won by X and by O
 *   winner         only when finished: "X", "O" or "D" (match draw)
 */
public final class MatchState {
    public String id, x, o, xName, oName, phase = "playing", turn = "X", winner, cancelledBy;
    public int round = 1, sx = 0, so = 0;
    public long endedAt = 0, createdAt = 0;
    public final String[] cells = new String[9];
    public final String[] results = new String[3];

    @Nullable
    static MatchState from(@NonNull DataSnapshot s) {
        if (!s.exists()) return null;
        MatchState m = new MatchState();
        m.id = s.getKey();
        m.x = s.child("x").getValue(String.class);
        m.o = s.child("o").getValue(String.class);
        m.xName = str(s, "xName", "Player 1");
        m.oName = str(s, "oName", "Player 2");
        m.phase = str(s, "phase", "playing");
        m.turn = str(s, "turn", "X");
        m.winner = s.child("winner").getValue(String.class);
        m.cancelledBy = s.child("cancelledBy").getValue(String.class);
        m.round = num(s, "round", 1);
        m.sx = num(s, "sx", 0);
        m.so = num(s, "so", 0);
        m.endedAt = lng(s, "endedAt");
        m.createdAt = lng(s, "createdAt");
        for (int i = 0; i < 9; i++) m.cells[i] = s.child("board").child(String.valueOf(i)).getValue(String.class);
        for (int i = 0; i < 3; i++) m.results[i] = s.child("r" + (i + 1)).getValue(String.class);
        if (m.x == null || m.o == null) return null;
        return m;
    }

    private static String str(DataSnapshot s, String k, String def) { String v = s.child(k).getValue(String.class); return v == null ? def : v; }
    private static int num(DataSnapshot s, String k, int def) { Long v = s.child(k).getValue(Long.class); return v == null ? def : v.intValue(); }
    private static long lng(DataSnapshot s, String k) { Long v = s.child(k).getValue(Long.class); return v == null ? 0L : v; }

    /** "X", "O", or null if this uid is not in the match. */
    @Nullable public String symbolOf(String uid) { return uid == null ? null : uid.equals(x) ? "X" : uid.equals(o) ? "O" : null; }
    public String opponentUid(String uid) { return uid != null && uid.equals(x) ? o : x; }
    public String nameOf(String symbol) { return "X".equals(symbol) ? xName : oName; }
    public boolean isOver() { return "finished".equals(phase) || "cancelled".equals(phase); }

    /** True when this player is the verified overall winner (never for a draw). */
    public boolean wonBy(String uid) {
        String sym = symbolOf(uid);
        return "finished".equals(phase) && sym != null && sym.equals(winner);
    }
}
