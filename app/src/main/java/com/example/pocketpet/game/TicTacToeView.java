package com.example.pocketpet.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Tic Tac Toe on a cozy pastel board: you are X (mint), the computer is O (pink). */
public class TicTacToeView extends View implements GameBoard {
    private final Random rnd = new Random();
    private final TicTacToeGame game = new TicTacToeGame(rnd);
    private final PausableHandler timers = new PausableHandler();
    private SoundFx sfx;
    private GameEvents events;
    private int difficulty = TicTacToeGame.MEDIUM;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final long[] placedAt = new long[9];
    private float left, top, size, gx, gy, gsize, cell, d;

    private long startAt, winAt;
    private int[] winLine;
    private boolean userTurn = true, over = false;
    private int pressed = -1;

    private final Runnable cpuMove = this::doCpuMove;

    public TicTacToeView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override public void setEvents(GameEvents e) { events = e; }
    @Override public void setDifficulty(int level) { difficulty = level; }
    @Override public void setSound(SoundFx fx) { sfx = fx; }
    @Override public void setPaused(boolean paused) { timers.paused = paused; if (!paused) invalidate(); }

    /** Start (or restart) a round. You always go first. */
    @Override public void restart() {
        timers.clear();
        game.reset();
        for (int i = 0; i < 9; i++) placedAt[i] = 0;
        winLine = null; over = false; userTurn = true; pressed = -1;
        startAt = SystemClock.uptimeMillis();
        status("Your turn");
        invalidate();
    }

    private void status(String s) { if (events != null) events.onStatus(s); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        size = Math.min(w, h) - 8 * d;
        left = (w - size) / 2f;
        top = (h - size) / 2f;
        float pad = 14 * d;
        gx = left + pad; gy = top + pad; gsize = size - 2 * pad; cell = gsize / 3f;
    }

    @Override protected void onDetachedFromWindow() {
        timers.clear();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ input

    private int cellAt(float x, float y) {
        if (x < gx || y < gy || x > gx + gsize || y > gy + gsize) return -1;
        int c = (int) ((x - gx) / cell), r = (int) ((y - gy) / cell);
        return Math.max(0, Math.min(2, r)) * 3 + Math.max(0, Math.min(2, c));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (over || !userTurn) return true;
        int idx = cellAt(e.getX(), e.getY());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = (idx >= 0 && game.get(idx) == TicTacToeGame.EMPTY) ? idx : -1;
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (idx != pressed) { pressed = -1; invalidate(); }
                break;
            case MotionEvent.ACTION_UP:
                if (pressed >= 0 && idx == pressed) { int i = pressed; pressed = -1; playUser(i); }
                else { pressed = -1; invalidate(); }
                break;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1; invalidate();
                break;
        }
        return true;
    }

    private void playUser(int i) {
        if (!game.place(i, TicTacToeGame.USER)) return;
        placedAt[i] = SystemClock.uptimeMillis();
        if (sfx != null) sfx.play(SoundFx.PLACE_USER);
        userTurn = false;
        invalidate();
        if (checkEnd()) return;
        status("Computer is thinking...");
        timers.postDelayed(cpuMove, 650 + rnd.nextInt(350));
    }

    private void doCpuMove() {
        if (over) return;
        int mv = game.chooseCpuMove(difficulty);
        game.place(mv, TicTacToeGame.CPU);
        placedAt[mv] = SystemClock.uptimeMillis();
        if (sfx != null) sfx.play(SoundFx.PLACE_CPU);
        invalidate();
        if (checkEnd()) return;
        userTurn = true;
        status("Your turn");
    }

    private boolean checkEnd() {
        if (!game.isOver()) return false;
        over = true;
        winLine = game.winningLine();
        winAt = SystemClock.uptimeMillis() + 350;      // let the last mark finish drawing first
        final int result;
        int w = game.winner();
        if (w == TicTacToeGame.USER)      { result = GameEvents.RESULT_WIN;  status("You got three in a row!"); }
        else if (w == TicTacToeGame.CPU)  { result = GameEvents.RESULT_LOSE; status("The computer got three in a row"); }
        else                              { result = GameEvents.RESULT_DRAW; status("It's a draw"); }
        timers.postDelayed(() -> { if (events != null) events.onGameOver(result); }, 1500);
        invalidate();
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean animating = false;

        // soft shadow + paper board
        rect.set(left, top + 5 * d, left + size, top + size + 5 * d);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(60, 107, 74, 58));
        c.drawRoundRect(rect, 26 * d, 26 * d, p);
        rect.set(left, top, left + size, top + size);
        p.setColor(GameTheme.CREAM);
        c.drawRoundRect(rect, 26 * d, 26 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(GameTheme.TAN);
        c.drawRoundRect(rect, 26 * d, 26 * d, p);

        // nine pastel tiles that pop in one after another
        float gap = 5 * d;
        for (int i = 0; i < 9; i++) {
            float tp = Math.max(0f, Math.min(1f, (now - startAt - i * 45L) / 280f));
            if (tp < 1f) animating = true;
            if (tp <= 0f) continue;
            float s = 0.6f + 0.4f * tp;
            float x0 = gx + (i % 3) * cell, y0 = gy + (i / 3) * cell;
            float cx = x0 + cell / 2f, cy = y0 + cell / 2f;
            c.save();
            c.scale(s, s, cx, cy);
            p.setStyle(Paint.Style.FILL);
            p.setColor(GameTheme.SHADOW);
            rect.set(x0 + gap, y0 + gap + 3 * d, x0 + cell - gap, y0 + cell - gap + 3 * d);
            c.drawRoundRect(rect, 16 * d, 16 * d, p);
            p.setColor(((i % 2) == 0) ? GameTheme.CREAM_2 : GameTheme.PINK_2);
            rect.set(x0 + gap, y0 + gap, x0 + cell - gap, y0 + cell - gap);
            c.drawRoundRect(rect, 16 * d, 16 * d, p);
            if (pressed == i) {
                p.setColor(Color.argb(110, 255, 214, 120));
                c.drawRoundRect(rect, 16 * d, 16 * d, p);
            }
            c.restore();
        }

        // marks (sticker style: brown outline + pastel colour)
        for (int i = 0; i < 9; i++) {
            int v = game.get(i);
            if (v == TicTacToeGame.EMPTY) continue;
            float t = Math.min(1f, (now - placedAt[i]) / 320f);
            if (t < 1f) animating = true;
            float cx = gx + (i % 3) * cell + cell / 2f, cy = gy + (i / 3) * cell + cell / 2f;
            if (v == TicTacToeGame.USER) drawX(c, cx, cy, t); else drawO(c, cx, cy, t);
        }

        // winning line
        if (winLine != null && now >= winAt) {
            float t = Math.min(1f, (now - winAt) / 500f);
            if (t < 1f) animating = true;
            float x1 = gx + (winLine[0] % 3) * cell + cell / 2f, y1 = gy + (winLine[0] / 3) * cell + cell / 2f;
            float x2 = gx + (winLine[2] % 3) * cell + cell / 2f, y2 = gy + (winLine[2] / 3) * cell + cell / 2f;
            float ex = x1 + (x2 - x1) * t, ey = y1 + (y2 - y1) * t;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(15 * d);
            p.setColor(GameTheme.OUTLINE);
            c.drawLine(x1, y1, ex, ey, p);
            p.setStrokeWidth(9 * d);
            p.setColor(GameTheme.GOLD);
            c.drawLine(x1, y1, ex, ey, p);
        } else if (winLine != null) {
            animating = true;
        }

        if (animating) postInvalidateOnAnimation();
    }

    private void drawX(Canvas c, float cx, float cy, float t) {
        float m = cell * 0.24f;
        float t1 = Math.min(1f, t * 2f), t2 = Math.max(0f, t * 2f - 1f);
        p.setStyle(Paint.Style.STROKE);
        for (int pass = 0; pass < 2; pass++) {
            p.setStrokeWidth(pass == 0 ? 13 * d : 8 * d);
            p.setColor(pass == 0 ? GameTheme.OUTLINE : GameTheme.USER);
            c.drawLine(cx - m, cy - m, cx - m + 2 * m * t1, cy - m + 2 * m * t1, p);
            if (t2 > 0f) c.drawLine(cx + m, cy - m, cx + m - 2 * m * t2, cy - m + 2 * m * t2, p);
        }
    }

    private void drawO(Canvas c, float cx, float cy, float t) {
        float m = cell * 0.25f;
        rect.set(cx - m, cy - m, cx + m, cy + m);
        p.setStyle(Paint.Style.STROKE);
        for (int pass = 0; pass < 2; pass++) {
            p.setStrokeWidth(pass == 0 ? 13 * d : 8 * d);
            p.setColor(pass == 0 ? GameTheme.OUTLINE : GameTheme.CPU);
            c.drawArc(rect, -90f, 360f * t, false, p);
        }
    }
}
