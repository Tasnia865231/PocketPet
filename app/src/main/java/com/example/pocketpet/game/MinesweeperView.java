package com.example.pocketpet.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.List;
import java.util.Random;

/**
 * Minesweeper duel board. You and the computer take turns opening cells (mint = yours, pink = computer's).
 * Whoever opens a mine loses. Long-press a hidden cell to put a flag on it.
 */
public class MinesweeperView extends View implements GameBoard {
    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());   // long-press detection only
    private final PausableHandler timers = new PausableHandler();
    private SoundFx sfx;
    private GameEvents events;
    private int difficulty = 0;
    private MinesweeperGame game;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private float left, top, cell, bw, bh, d;

    private long[] revealAt, mineShowAt;
    private boolean[] flag;
    private boolean userTurn = true, over = false, showMines = false;
    private int pressed = -1, cpuTarget = -1, boomCell = -1;
    private long cpuTargetAt, boomAt, animUntil;
    private boolean longPressed = false;

    private final Runnable longPress = this::onLongPress;
    private final Runnable cpuThink = this::doCpuThink;
    private final Runnable cpuReveal = this::doCpuReveal;

    public MinesweeperView(Context c) {
        super(c);
        text.setTypeface(com.example.pocketpet.view.BubbleFont.bold(c));
        d = c.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override public void setEvents(GameEvents e) { events = e; }
    @Override public void setDifficulty(int level) { difficulty = level; }
    @Override public void setSound(SoundFx fx) { sfx = fx; }
    @Override public void setPaused(boolean paused) { timers.paused = paused; if (!paused) invalidate(); }

    @Override public void restart() {
        handler.removeCallbacksAndMessages(null);
        timers.clear();
        game = MinesweeperGame.forDifficulty(difficulty, rnd);
        int n = game.size();
        revealAt = new long[n]; mineShowAt = new long[n]; flag = new boolean[n];
        userTurn = true; over = false; showMines = false;
        pressed = -1; cpuTarget = -1; boomCell = -1; longPressed = false;
        layoutBoard();
        status(userStatus());
        invalidate();
    }

    /** Score = safe cells the player opened (the computer's cells do not count). */
    private void reportScore() {
        if (events == null) return;
        int mine = 0;
        for (int i = 0; i < game.size(); i++) if (game.isRevealed(i) && game.ownerOf(i) == MinesweeperGame.USER && !game.isMine(i)) mine++;
        events.onScore(mine);
    }

    private String userStatus() {
        return "Your turn  -  " + game.mineCount + " mines hidden";
    }

    private void status(String s) { if (events != null) events.onStatus(s); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { layoutBoard(); }

    private void layoutBoard() {
        if (game == null || getWidth() == 0) return;
        float pad = 12 * d;
        cell = Math.min((getWidth() - 2 * pad) / game.cols, (getHeight() - 2 * pad) / game.rows);
        bw = cell * game.cols; bh = cell * game.rows;
        left = (getWidth() - bw) / 2f; top = (getHeight() - bh) / 2f;
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        timers.clear();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ input

    private int cellAt(float x, float y) {
        if (game == null || x < left || y < top || x >= left + bw || y >= top + bh) return -1;
        return (int) ((y - top) / cell) * game.cols + (int) ((x - left) / cell);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (game == null || over || !userTurn) return true;
        int idx = cellAt(e.getX(), e.getY());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = (idx >= 0 && !game.isRevealed(idx)) ? idx : -1;
                longPressed = false;
                if (pressed >= 0) handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (idx != pressed && pressed >= 0) {
                    handler.removeCallbacks(longPress);
                    pressed = -1; invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (pressed >= 0 && idx == pressed && !longPressed) {
                    int i = pressed; pressed = -1;
                    if (!flag[i]) openCell(i, MinesweeperGame.USER);
                    else invalidate();
                } else { pressed = -1; invalidate(); }
                break;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                pressed = -1; invalidate();
                break;
        }
        return true;
    }

    private void onLongPress() {
        if (pressed < 0 || game.isRevealed(pressed) || over) return;
        flag[pressed] = !flag[pressed];
        if (sfx != null) sfx.play(SoundFx.FLAG);
        longPressed = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
    }

    // ------------------------------------------------------------------ turns

    private void openCell(int idx, int who) {
        int res = game.reveal(idx, who);
        long now = SystemClock.uptimeMillis();
        List<Integer> opened = game.lastOpened();
        for (int k = 0; k < opened.size(); k++) {
            int cellIdx = opened.get(k);
            revealAt[cellIdx] = now + Math.min(k * 28L, 500L);
            flag[cellIdx] = false;
        }
        animUntil = now + 1200;

        if (sfx != null) sfx.play(res == MinesweeperGame.MINE ? SoundFx.BOOM : SoundFx.REVEAL);
        reportScore();
        if (res == MinesweeperGame.MINE) {
            over = true;
            boomCell = idx; boomAt = now;
            showMines = true;
            for (int i = 0; i < game.size(); i++) {
                if (game.isMine(i) && !game.isRevealed(i)) mineShowAt[i] = now + 350 + rnd.nextInt(600);
            }
            final int result;
            if (who == MinesweeperGame.USER) { result = GameEvents.RESULT_LOSE; status("Boom! You opened a mine"); }
            else                             { result = GameEvents.RESULT_WIN;  status("The computer opened a mine!"); }
            animUntil = now + 2200;
            timers.postDelayed(() -> { if (events != null) events.onGameOver(result); }, 1900);
        } else if (game.safeCellsLeft() == 0) {
            over = true;
            final int result = (who == MinesweeperGame.USER) ? GameEvents.RESULT_WIN : GameEvents.RESULT_LOSE;
            status("Board cleared!");
            timers.postDelayed(() -> { if (events != null) events.onGameOver(result); }, 1200);
        } else if (who == MinesweeperGame.USER) {
            userTurn = false;
            status("Computer is thinking...");
            timers.postDelayed(cpuThink, 650);
        } else {
            userTurn = true;
            status(userStatus());
        }
        invalidate();
    }

    private void doCpuThink() {
        if (over) return;
        cpuTarget = game.chooseCpuMove(difficulty);
        cpuTargetAt = SystemClock.uptimeMillis();
        animUntil = cpuTargetAt + 800;
        invalidate();
        timers.postDelayed(cpuReveal, 600);
    }

    private void doCpuReveal() {
        if (over || cpuTarget < 0) return;
        int t = cpuTarget;
        cpuTarget = -1;
        openCell(t, MinesweeperGame.CPU);
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        if (game == null || cell <= 0) return;
        long now = SystemClock.uptimeMillis();

        // paper board with soft shadow
        float m = 9 * d;
        rect.set(left - m, top - m + 5 * d, left + bw + m, top + bh + m + 5 * d);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(60, 107, 74, 58));
        c.drawRoundRect(rect, 22 * d, 22 * d, p);
        rect.set(left - m, top - m, left + bw + m, top + bh + m);
        p.setColor(GameTheme.CREAM);
        c.drawRoundRect(rect, 22 * d, 22 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(GameTheme.TAN);
        c.drawRoundRect(rect, 22 * d, 22 * d, p);

        for (int i = 0; i < game.size(); i++) drawTile(c, i, now);

        // explosion: a soft pastel poof
        if (boomCell >= 0) {
            float e = (now - boomAt) / 800f;
            if (e < 1f) {
                float cx = left + (boomCell % game.cols) * cell + cell / 2f;
                float cy = top + (boomCell / game.cols) * cell + cell / 2f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb((int) (150 * (1f - e)), 255, 236, 170));
                c.drawCircle(cx, cy, cell * (0.3f + 1.0f * e), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(5 * d * (1f - e) + d);
                p.setColor(Color.argb((int) (230 * (1f - e)), 255, 164, 92));
                c.drawCircle(cx, cy, cell * (0.4f + 1.5f * e), p);
            }
        }

        if (now < animUntil || pressed >= 0) postInvalidateOnAnimation();
    }

    private void drawTile(Canvas c, int i, long now) {
        int r = i / game.cols, col = i % game.cols;
        float x0 = left + col * cell, y0 = top + r * cell;
        float gap = 1.8f * d;
        float cx = x0 + cell / 2f, cy = y0 + cell / 2f;
        float rad = cell * 0.24f;

        boolean opened = game.isRevealed(i);
        float t = opened ? Math.min(1f, (now - revealAt[i]) / 260f) : 0f;
        boolean mineShown = showMines && !opened && game.isMine(i) && now >= mineShowAt[i];

        if (opened && t > 0f) {
            float s = 0.55f + 0.45f * t;
            c.save();
            c.scale(s, s, cx, cy);
            int bg = game.ownerOf(i) == MinesweeperGame.CPU ? GameTheme.OPEN_CPU : GameTheme.OPEN_USER;
            if (game.isMine(i)) bg = GameTheme.OPEN_BOOM;
            p.setStyle(Paint.Style.FILL);
            p.setColor(bg);
            rect.set(x0 + gap, y0 + gap, x0 + cell - gap, y0 + cell - gap);
            c.drawRoundRect(rect, rad, rad, p);
            if (game.isMine(i)) {
                drawMine(c, cx, cy, cell * 0.26f, now);
            } else if (game.adjacent(i) > 0) {
                text.setColor(GameTheme.numberColor(game.adjacent(i)));
                text.setTextSize(cell * 0.52f);
                c.drawText(String.valueOf(game.adjacent(i)), cx, cy - (text.ascent() + text.descent()) / 2f, text);
            }
            c.restore();
            return;
        }

        if (mineShown) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(GameTheme.CREAM_2);
            rect.set(x0 + gap, y0 + gap, x0 + cell - gap, y0 + cell - gap);
            c.drawRoundRect(rect, rad, rad, p);
            drawMine(c, cx, cy, cell * 0.24f, now);
            return;
        }

        // hidden lilac cushion tile
        boolean down = (pressed == i);
        float lift = down ? 0f : 3f * d;
        float push = down ? 1.5f * d : 0f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(GameTheme.TILE_SHADE);
        rect.set(x0 + gap, y0 + gap + lift * 0.6f, x0 + cell - gap, y0 + cell - gap + (down ? 0f : 1f));
        c.drawRoundRect(rect, rad, rad, p);
        p.setColor(GameTheme.TILE_BASE);
        rect.set(x0 + gap, y0 + gap + push, x0 + cell - gap, y0 + cell - gap - lift + push);
        c.drawRoundRect(rect, rad, rad, p);
        p.setColor(GameTheme.TILE_TOP);
        rect.set(x0 + gap + 2.5f * d, y0 + gap + 2.5f * d + push, x0 + cell - gap - 2.5f * d, y0 + cell * 0.44f + push);
        c.drawRoundRect(rect, rad * 0.8f, rad * 0.8f, p);

        if (flag[i]) drawFlag(c, cx, cy - lift * 0.5f);

        if (cpuTarget == i) {
            float pulse = 0.5f + 0.5f * (float) Math.sin((now - cpuTargetAt) / 70f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.5f * d);
            p.setColor(Color.argb((int) (150 + 100 * pulse), 255, 143, 163));
            rect.set(x0 + gap, y0 + gap, x0 + cell - gap, y0 + cell - gap);
            c.drawRoundRect(rect, rad, rad, p);
        }
    }

    /** Mines are little soot sprites. */
    private void drawMine(Canvas c, float cx, float cy, float r, long now) {
        Sprites.soot(c, p, cx, cy, r, now / 1000f);
    }

    private void drawFlag(Canvas c, float cx, float cy) {
        float s = cell * 0.3f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(2f, cell * 0.07f));
        p.setColor(GameTheme.OUTLINE);
        c.drawLine(cx - s * 0.3f, cy - s, cx - s * 0.3f, cy + s, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#FF8FA8"));
        path.reset();
        path.moveTo(cx - s * 0.3f, cy - s);
        path.lineTo(cx + s * 0.95f, cy - s * 0.4f);
        path.lineTo(cx - s * 0.3f, cy + s * 0.15f);
        path.close();
        c.drawPath(path, p);
    }
}
