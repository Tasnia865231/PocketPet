package com.example.pocketpet.game;

/**
 * Pure Tic Tac Toe rules for the ONLINE match. Cells hold null (empty), "X" or "O".
 * The same rules are also written in the Realtime Database security rules (firebase-rules/database.rules.json),
 * so a modified app cannot invent a win.
 */
public final class MatchLogic {
    private MatchLogic() {}

    /** The three winning cells, or null. */
    public static int[] winningLine(String[] c) {
        for (int[] l : TicTacToeGame.LINES) {
            String a = c[l[0]];
            if (a != null && a.equals(c[l[1]]) && a.equals(c[l[2]])) return l;
        }
        return null;
    }

    public static boolean isFull(String[] c) {
        for (String s : c) if (s == null) return false;
        return true;
    }

    /** "X" or "O" when someone has three in a row, "D" when the board is full without a winner, otherwise null (round goes on). */
    public static String outcome(String[] c) {
        int[] l = winningLine(c);
        if (l != null) return c[l[0]];
        return isFull(c) ? "D" : null;
    }
}
