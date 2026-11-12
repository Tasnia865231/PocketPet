package com.example.pocketpet.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turn-based Minesweeper duel. The player and the computer take turns opening cells on the same
 * board. Whoever opens a mine loses. If every safe cell gets opened, whoever opened the last one wins.
 * The first cell opened is always safe. Pure Java, no Android classes.
 */
public class MinesweeperGame {
    public static final int SAFE = 0, MINE = 1;
    public static final int NOBODY = 0, USER = 1, CPU = 2;

    public final int rows, cols, mineCount;
    private final boolean[] mine;
    private final boolean[] revealed;
    private final int[] adj;
    private final int[] owner;
    private final Random rnd;
    private boolean placed = false;
    private int revealedCount = 0;
    private final ArrayList<Integer> lastOpened = new ArrayList<>();

    public MinesweeperGame(int rows, int cols, int mines, Random rnd) {
        this.rows = rows; this.cols = cols; this.mineCount = mines; this.rnd = rnd;
        int n = rows * cols;
        mine = new boolean[n]; revealed = new boolean[n]; adj = new int[n]; owner = new int[n];
    }

    /** 0 = easy, 1 = medium, 2 = hard. */
    public static MinesweeperGame forDifficulty(int difficulty, Random rnd) {
        switch (difficulty) {
            case 0:  return new MinesweeperGame(6, 6, 5, rnd);
            case 1:  return new MinesweeperGame(8, 8, 10, rnd);
            default: return new MinesweeperGame(9, 9, 15, rnd);
        }
    }

    public int size()                  { return rows * cols; }
    public boolean isRevealed(int i)   { return revealed[i]; }
    public boolean isMine(int i)       { return mine[i]; }
    public int adjacent(int i)         { return adj[i]; }
    public int ownerOf(int i)          { return owner[i]; }
    public int safeCellsLeft()         { return rows * cols - mineCount - revealedCount; }
    /** Cells opened by the last reveal() call, in the order they opened (for animation). */
    public List<Integer> lastOpened()  { return lastOpened; }

    public int hiddenCount() { return rows * cols - revealedCount; }

    // ------------------------------------------------------------------ moves

    public int reveal(int idx, int who) {
        lastOpened.clear();
        if (revealed[idx]) return SAFE;
        if (!placed) placeMines(idx);
        if (mine[idx]) {
            revealed[idx] = true; owner[idx] = who;
            lastOpened.add(idx);
            return MINE;
        }
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(idx);
        revealed[idx] = true;
        while (!q.isEmpty()) {
            int cur = q.poll();
            owner[cur] = who;
            revealedCount++;
            lastOpened.add(cur);
            if (adj[cur] == 0) {
                for (int nb : neighbors(cur)) {
                    if (!revealed[nb] && !mine[nb]) { revealed[nb] = true; q.add(nb); }
                }
            }
        }
        return SAFE;
    }

    private void placeMines(int first) {
        // keep the first cell and its neighbours clear when the board is big enough
        boolean[] banned = new boolean[rows * cols];
        banned[first] = true;
        int free = rows * cols - 1;
        for (int nb : neighbors(first)) {
            if (free - 1 >= mineCount) { banned[nb] = true; free--; }
        }
        int put = 0;
        while (put < mineCount) {
            int i = rnd.nextInt(rows * cols);
            if (banned[i] || mine[i]) continue;
            mine[i] = true; put++;
        }
        for (int i = 0; i < rows * cols; i++) {
            int c = 0;
            for (int nb : neighbors(i)) if (mine[nb]) c++;
            adj[i] = c;
        }
        placed = true;
    }

    public int[] neighbors(int i) {
        int r = i / cols, c = i % cols;
        int[] tmp = new int[8];
        int n = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int rr = r + dr, cc = c + dc;
                if (rr < 0 || rr >= rows || cc < 0 || cc >= cols) continue;
                tmp[n++] = rr * cols + cc;
            }
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    // ------------------------------------------------------------------ computer
    // The computer only uses what is visible on the board (it never peeks at the mines).

    public int chooseCpuMove(int difficulty) {
        int n = rows * cols;
        ArrayList<Integer> hidden = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!revealed[i]) hidden.add(i);

        if (difficulty == 0) return hidden.get(rnd.nextInt(hidden.size()));   // easy: pure guess

        int[] st = new int[n];                 // 0 unknown, 1 deduced safe, 2 deduced mine
        deduce(st, difficulty >= 2);

        ArrayList<Integer> safe = new ArrayList<>();
        for (int i : hidden) if (st[i] == 1) safe.add(i);
        if (!safe.isEmpty()) return safe.get(rnd.nextInt(safe.size()));

        if (difficulty == 1) {                 // medium: random guess, but avoids known mines
            ArrayList<Integer> ok = new ArrayList<>();
            for (int i : hidden) if (st[i] != 2) ok.add(i);
            if (ok.isEmpty()) ok = hidden;
            return ok.get(rnd.nextInt(ok.size()));
        }

        // hard: guess the cell least likely to be a mine
        double[] prob = new double[n];
        for (int i = 0; i < n; i++) prob[i] = -1;
        int knownMines = 0, unknownCells = 0;
        for (int i : hidden) { if (st[i] == 2) knownMines++; else if (st[i] == 0) unknownCells++; }
        for (int i = 0; i < n; i++) {
            if (!revealed[i] || adj[i] == 0) continue;
            int km = 0; ArrayList<Integer> unk = new ArrayList<>();
            for (int nb : neighbors(i)) {
                if (revealed[nb]) continue;
                if (st[nb] == 2) km++; else if (st[nb] == 0) unk.add(nb);
            }
            if (unk.isEmpty()) continue;
            double p = (adj[i] - km) / (double) unk.size();
            for (int u : unk) prob[u] = Math.max(prob[u], p);
        }
        double density = unknownCells > 0 ? Math.max(0, mineCount - knownMines) / (double) unknownCells : 1.0;
        double best = 2.0;
        ArrayList<Integer> picks = new ArrayList<>();
        for (int i : hidden) {
            if (st[i] != 0) continue;
            double p = prob[i] < 0 ? density : prob[i];
            if (p < best - 1e-9) { best = p; picks.clear(); picks.add(i); }
            else if (Math.abs(p - best) <= 1e-9) picks.add(i);
        }
        if (picks.isEmpty()) return hidden.get(rnd.nextInt(hidden.size()));
        return picks.get(rnd.nextInt(picks.size()));
    }

    private void deduce(int[] st, boolean useSubsetRule) {
        int n = rows * cols;
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 60) {
            changed = false;
            for (int i = 0; i < n; i++) {
                if (!revealed[i] || adj[i] == 0) continue;
                int km = 0; ArrayList<Integer> unk = new ArrayList<>();
                for (int nb : neighbors(i)) {
                    if (revealed[nb]) continue;
                    if (st[nb] == 2) km++; else if (st[nb] == 0) unk.add(nb);
                }
                if (unk.isEmpty()) continue;
                int need = adj[i] - km;
                if (need == 0) { for (int u : unk) st[u] = 1; changed = true; }
                else if (need == unk.size()) { for (int u : unk) st[u] = 2; changed = true; }
            }
            if (!changed && useSubsetRule) changed = subsetRule(st);
        }
    }

    /** If A's unknown neighbours are a subset of B's, the difference cells follow from the number gap. */
    private boolean subsetRule(int[] st) {
        int n = rows * cols;
        ArrayList<ArrayList<Integer>> sets = new ArrayList<>();
        ArrayList<Integer> needs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!revealed[i] || adj[i] == 0) continue;
            int km = 0; ArrayList<Integer> unk = new ArrayList<>();
            for (int nb : neighbors(i)) {
                if (revealed[nb]) continue;
                if (st[nb] == 2) km++; else if (st[nb] == 0) unk.add(nb);
            }
            if (unk.isEmpty()) continue;
            sets.add(unk); needs.add(adj[i] - km);
        }
        boolean changed = false;
        for (int a = 0; a < sets.size(); a++) {
            for (int b = 0; b < sets.size(); b++) {
                if (a == b) continue;
                ArrayList<Integer> sa = sets.get(a), sb = sets.get(b);
                if (sa.size() >= sb.size() || !sb.containsAll(sa)) continue;
                ArrayList<Integer> diff = new ArrayList<>(sb);
                diff.removeAll(sa);
                int dn = needs.get(b) - needs.get(a);
                if (dn == 0) {
                    for (int u : diff) if (st[u] == 0) { st[u] = 1; changed = true; }
                } else if (dn == diff.size()) {
                    for (int u : diff) if (st[u] == 0) { st[u] = 2; changed = true; }
                }
            }
        }
        return changed;
    }
}
