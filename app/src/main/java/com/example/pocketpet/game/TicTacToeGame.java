package com.example.pocketpet.game;

import java.util.ArrayList;
import java.util.Random;

/** Pure Tic Tac Toe rules + computer player. No Android classes, so it is easy to test. */
public class TicTacToeGame {
    public static final int EMPTY = 0, USER = 1, CPU = 2;
    public static final int EASY = 0, MEDIUM = 1, HARD = 2;

    public static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}};

    private final int[] cells = new int[9];
    private final Random rnd;

    public TicTacToeGame(Random rnd) { this.rnd = rnd; }

    public void reset() { for (int i = 0; i < 9; i++) cells[i] = EMPTY; }
    public int get(int i) { return cells[i]; }

    public boolean place(int i, int who) {
        if (i < 0 || i > 8 || cells[i] != EMPTY) return false;
        cells[i] = who;
        return true;
    }

    /** The three winning cells, or null. */
    public int[] winningLine() {
        for (int[] l : LINES) {
            int a = cells[l[0]];
            if (a != EMPTY && a == cells[l[1]] && a == cells[l[2]]) return l;
        }
        return null;
    }

    public int winner() {
        int[] l = winningLine();
        return l == null ? EMPTY : cells[l[0]];
    }

    public boolean isFull() {
        for (int c : cells) if (c == EMPTY) return false;
        return true;
    }

    public boolean isOver() { return winner() != EMPTY || isFull(); }

    // ------------------------------------------------------------------ computer

    public int chooseCpuMove(int difficulty) {
        switch (difficulty) {
            case EASY: {
                if (rnd.nextFloat() < 0.35f) {
                    int w = lineMove(CPU);
                    if (w >= 0) return w;
                }
                return randomEmpty();
            }
            case MEDIUM: {
                if (rnd.nextFloat() < 0.20f) return randomEmpty();   // sometimes slips
                int m = lineMove(CPU);
                if (m >= 0) return m;
                m = lineMove(USER);
                if (m >= 0) return m;
                if (cells[4] == EMPTY) return 4;
                int[] corners = {0, 2, 6, 8};
                ArrayList<Integer> free = new ArrayList<>();
                for (int c : corners) if (cells[c] == EMPTY) free.add(c);
                if (!free.isEmpty()) return free.get(rnd.nextInt(free.size()));
                return randomEmpty();
            }
            default:
                return bestMove();
        }
    }

    private int randomEmpty() {
        ArrayList<Integer> free = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (cells[i] == EMPTY) free.add(i);
        return free.get(rnd.nextInt(free.size()));
    }

    /** A cell that completes a line for "who", or -1. */
    private int lineMove(int who) {
        for (int[] l : LINES) {
            int mine = 0, empty = -1;
            for (int i : l) {
                if (cells[i] == who) mine++;
                else if (cells[i] == EMPTY) empty = i;
            }
            if (mine == 2 && empty >= 0) return empty;
        }
        return -1;
    }

    /** Perfect play (minimax). Picks randomly among equally good moves. */
    private int bestMove() {
        int bestScore = -1000;
        ArrayList<Integer> best = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (cells[i] != EMPTY) continue;
            cells[i] = CPU;
            int s = minimax(false, 1);
            cells[i] = EMPTY;
            if (s > bestScore) { bestScore = s; best.clear(); best.add(i); }
            else if (s == bestScore) best.add(i);
        }
        return best.get(rnd.nextInt(best.size()));
    }

    private int minimax(boolean cpuTurn, int depth) {
        int w = winner();
        if (w == CPU)  return 10 - depth;
        if (w == USER) return depth - 10;
        if (isFull())  return 0;
        int best = cpuTurn ? -1000 : 1000;
        for (int i = 0; i < 9; i++) {
            if (cells[i] != EMPTY) continue;
            cells[i] = cpuTurn ? CPU : USER;
            int s = minimax(!cpuTurn, depth + 1);
            cells[i] = EMPTY;
            best = cpuTurn ? Math.max(best, s) : Math.min(best, s);
        }
        return best;
    }
}
