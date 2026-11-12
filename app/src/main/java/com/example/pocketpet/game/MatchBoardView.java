package com.example.pocketpet.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * The shared Tic Tac Toe board of an online match. It only DRAWS what the database says (setCells) and reports taps;
 * it never decides anything itself. Same cozy look as the single-player board: X is mint, O is pink.
 */
public class MatchBoardView extends View {
    public interface OnCellTap { void onCell(int index); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final String[] cells = new String[9];
    private final long[] placedAt = new long[9];
    private float left, top, size, gx, gy, gsize, cell, d;
    private long startAt = SystemClock.uptimeMillis(), winAt;
    private int[] winLine;
    private boolean myTurn = false;
    private int pressed = -1;
    private OnCellTap listener;

    public MatchBoardView(Context c) { this(c, null); }

    public MatchBoardView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setOnCellTap(OnCellTap l) { listener = l; }

    /** Whether the player may tap right now (their turn, round running, connected). */
    public void setMyTurn(boolean v) { myTurn = v; if (!v) pressed = -1; invalidate(); }

    /** Shows the board from the database. Newly filled cells pop in; an empty board replays the tile pop-in. */
    public void setCells(String[] c) {
        long now = SystemClock.uptimeMillis();
        boolean wasEmpty = true, nowEmpty = true;
        for (int i = 0; i < 9; i++) { if (cells[i] != null) wasEmpty = false; if (c[i] != null) nowEmpty = false; }
        for (int i = 0; i < 9; i++) {
            if (cells[i] == null && c[i] != null) placedAt[i] = now;
            cells[i] = c[i];
        }
        if (nowEmpty && !wasEmpty) startAt = now;                // new round: tiles pop in again
        int[] line = MatchLogic.winningLine(cells);
        if (line != null && winLine == null) winAt = now + 350;   // let the last mark finish drawing first
        winLine = line;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        size = Math.min(w, h) - 8 * d;
        left = (w - size) / 2f;
        top = (h - size) / 2f;
        float pad = 14 * d;
        gx = left + pad; gy = top + pad; gsize = size - 2 * pad; cell = gsize / 3f;
    }

    private int cellAt(float x, float y) {
        if (x < gx || y < gy || x > gx + gsize || y > gy + gsize) return -1;
        int c = (int) ((x - gx) / cell), r = (int) ((y - gy) / cell);
        return Math.max(0, Math.min(2, r)) * 3 + Math.max(0, Math.min(2, c));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!myTurn) return true;
        int idx = cellAt(e.getX(), e.getY());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = (idx >= 0 && cells[idx] == null) ? idx : -1;
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (idx != pressed) { pressed = -1; invalidate(); }
                break;
            case MotionEvent.ACTION_UP:
                if (pressed >= 0 && idx == pressed) {
                    int i = pressed; pressed = -1; invalidate();
                    if (listener != null) listener.onCell(i);
                } else { pressed = -1; invalidate(); }
                break;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1; invalidate();
                break;
        }
        return true;
    }

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

        // nine pastel tiles
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

        // marks
        for (int i = 0; i < 9; i++) {
            if (cells[i] == null) continue;
            float t = Math.min(1f, (now - placedAt[i]) / 320f);
            if (t < 1f) animating = true;
            float cx = gx + (i % 3) * cell + cell / 2f, cy = gy + (i / 3) * cell + cell / 2f;
            if ("X".equals(cells[i])) drawX(c, cx, cy, t); else drawO(c, cx, cy, t);
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
