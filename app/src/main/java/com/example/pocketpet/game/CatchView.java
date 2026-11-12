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
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Catch the Treats: drag Mochi left and right to catch falling treats and dodge soot sprites.
 * Win by catching enough treats before the time runs out or you lose all hearts.
 * Everything is measured in dp (the canvas is scaled), so it looks the same on every phone.
 */
public class CatchView extends View implements GameBoard {
    private static final int MAX_ITEMS = 24, MAX_POPS = 10;
    private static final int READY = 0, PLAY = 1, END = 2;
    private static final float TIME_LIMIT = 45f;

    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final Scenery scenery = new Scenery();
    private GameEvents events;
    private SoundFx sfx;
    private boolean paused;
    private int lastCount = -1;
    private int difficulty = 1;
    private float d;
    private float W, H;

    // items
    private final float[] ix = new float[MAX_ITEMS], iy = new float[MAX_ITEMS], ivy = new float[MAX_ITEMS];
    private final int[] itype = new int[MAX_ITEMS];       // 0 strawberry, 1 cookie, 2 riceball, 3 soot
    private final boolean[] ialive = new boolean[MAX_ITEMS];
    // little pop rings
    private final float[] px = new float[MAX_POPS], py = new float[MAX_POPS], pt = new float[MAX_POPS];
    private final int[] pcol = new int[MAX_POPS];

    private int state = READY;
    private float stateT, spawnT, timeLeft, mx, targetX, faceT, shakeT, endT;
    private int score, target, hearts, face;
    private boolean won, finishedSent;
    private long last, startedAt;

    // tuning per difficulty
    private float speed, interval, badProb;

    public CatchView(Context c) {
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
    @Override public void setPaused(boolean p2) { paused = p2; last = SystemClock.uptimeMillis(); invalidate(); }

    @Override public void restart() {
        handler.removeCallbacksAndMessages(null);
        switch (difficulty) {
            case 0:  target = 15; speed = 150f; interval = 0.95f; badProb = 0.14f; break;
            case 1:  target = 25; speed = 195f; interval = 0.72f; badProb = 0.22f; break;
            default: target = 35; speed = 245f; interval = 0.55f; badProb = 0.28f; break;
        }
        for (int i = 0; i < MAX_ITEMS; i++) ialive[i] = false;
        for (int i = 0; i < MAX_POPS; i++) pt[i] = 1f;
        score = 0; hearts = 3; timeLeft = TIME_LIMIT; spawnT = 0.4f;
        state = READY; stateT = 0f; won = false; finishedSent = false; lastCount = -1;
        face = Sprites.NORMAL; faceT = 0f; shakeT = 0f;
        mx = W > 0 ? W / 2f : 150f; targetX = mx;
        last = SystemClock.uptimeMillis();
        status("Catch " + target + " treats!");
        invalidate();
    }

    private void status(String s) { if (events != null) events.onStatus(s); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w / d; H = h / d;
        scenery.resize(H);
        if (state == READY) { mx = W / 2f; targetX = mx; }
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            targetX = e.getX() / d;
        }
        return true;
    }

    // ------------------------------------------------------------------ update

    private float mochiR() { return 38f; }
    private float mochiY() { return H - 72f; }

    private void update(float dt) {
        stateT += dt;
        if (faceT > 0f) { faceT -= dt; if (faceT <= 0f && state != END) face = Sprites.NORMAL; }
        if (shakeT > 0f) shakeT -= dt;
        for (int i = 0; i < MAX_POPS; i++) if (pt[i] < 1f) pt[i] += dt * 2.2f;

        if (state != END) {
            float r = mochiR();
            float want = Math.max(r, Math.min(W - r, targetX));
            mx += (want - mx) * Math.min(1f, 14f * dt);
        }

        if (state == READY) {
            if (stateT > 1.2f) { state = PLAY; stateT = 0f; status("Treats 0 / " + target); }
            return;
        }

        if (state == PLAY) {
            timeLeft -= dt;
            spawnT -= dt;
            if (spawnT <= 0f) { spawn(); spawnT = interval * (0.8f + 0.4f * rnd.nextFloat()); }

            float r = mochiR(), my = mochiY();
            for (int i = 0; i < MAX_ITEMS; i++) {
                if (!ialive[i]) continue;
                iy[i] += ivy[i] * dt;
                if (iy[i] > H + 30f) { ialive[i] = false; continue; }
                float dx = Math.abs(ix[i] - mx);
                if (dx < r * 0.9f + 14f && iy[i] > my - r * 1.1f && iy[i] < my + r * 0.5f) {
                    ialive[i] = false;
                    if (itype[i] == 3) {
                        if (sfx != null) sfx.play(SoundFx.BAD);
                        hearts--; face = Sprites.SAD; faceT = 0.7f; shakeT = 0.35f;
                        pop(ix[i], iy[i], Color.parseColor("#6B5560"));
                    } else {
                        if (sfx != null) sfx.play(SoundFx.CATCH);
                        score++; face = Sprites.HAPPY; faceT = 0.4f;
                        if (events != null) events.onScore(score);
                        pop(ix[i], iy[i], Color.parseColor("#FFC857"));
                        status("Treats " + score + " / " + target);
                    }
                }
            }
            if (score >= target) endGame(true);
            else if (hearts <= 0 || timeLeft <= 0f) endGame(false);
            return;
        }

        // END: let things settle, then report
        endT += dt;
        if (!finishedSent && endT > 1.5f) {
            finishedSent = true;
            if (events != null) events.onGameOver(won ? GameEvents.RESULT_WIN : GameEvents.RESULT_LOSE);
        }
    }

    private void endGame(boolean win) {
        state = END; won = win; endT = 0f;
        face = win ? Sprites.HAPPY : Sprites.SAD; faceT = 0f;
        if (win) status("You caught them all!");
        else status(hearts <= 0 ? "Oh no, the soot sprites got Mochi!" : "Time is up!");
    }

    private void spawn() {
        for (int i = 0; i < MAX_ITEMS; i++) {
            if (ialive[i]) continue;
            ialive[i] = true;
            ix[i] = 26f + rnd.nextFloat() * (W - 52f);
            iy[i] = -26f;
            ivy[i] = speed * (0.85f + 0.3f * rnd.nextFloat());
            itype[i] = rnd.nextFloat() < badProb ? 3 : rnd.nextInt(3);
            return;
        }
    }

    private void pop(float x, float y, int color) {
        for (int i = 0; i < MAX_POPS; i++) {
            if (pt[i] >= 1f) { px[i] = x; py[i] = y; pt[i] = 0f; pcol[i] = color; return; }
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        if (W <= 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = paused ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        update(dt);
        if (startedAt == 0) startedAt = now;
        float t = (now - startedAt) / 1000f;

        c.save();
        c.scale(d, d);

        // rounded window
        clip.reset();
        rect.set(2f, 2f, W - 2f, H - 2f);
        clip.addRoundRect(rect, 22f, 22f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        scenery.drawSky(c, W, H, t, 0f);
        scenery.drawHills(c, W, H, H - 78f);

        // items
        for (int i = 0; i < MAX_ITEMS; i++) {
            if (!ialive[i]) continue;
            switch (itype[i]) {
                case 0: Sprites.strawberry(c, p, ix[i], iy[i], 17f); break;
                case 1: Sprites.cookie(c, p, ix[i], iy[i], 17f); break;
                case 2: Sprites.riceball(c, p, ix[i], iy[i], 17f); break;
                default: Sprites.soot(c, p, ix[i], iy[i], 16f, t + i); break;
            }
        }

        // pops
        for (int i = 0; i < MAX_POPS; i++) {
            if (pt[i] >= 1f) continue;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f * (1f - pt[i]) + 0.5f);
            int a = (int) (230 * (1f - pt[i]));
            p.setColor(Color.argb(a, Color.red(pcol[i]), Color.green(pcol[i]), Color.blue(pcol[i])));
            c.drawCircle(px[i], py[i], 8f + 26f * pt[i], p);
        }

        // Mochi
        float r = mochiR();
        float bob = state == END && won ? (float) Math.abs(Math.sin(endT * 7f)) * 10f : 0f;
        float shake = shakeT > 0f ? (float) Math.sin(shakeT * 60f) * 3f : 0f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(60, 107, 74, 58));
        rect.set(mx - r * 0.9f, mochiY() + r * 0.8f, mx + r * 0.9f, mochiY() + r * 1.05f);
        c.drawOval(rect, p);
        Sprites.mochi(c, p, mx + shake, mochiY() - bob, r, face, 1f);
        c.restore();

        // window border
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(GameTheme.TAN);
        c.drawRoundRect(rectBorder(), 22f, 22f, p);

        drawHud(c);

        if (state == READY) {
            int n = 3 - (int) (stateT / 0.4f);
            if (n != lastCount) { lastCount = n; if (sfx != null) sfx.play(SoundFx.TAP); }
            String s = n > 0 ? String.valueOf(n) : "Go!";
            banner(c, s);
        }
        c.restore();
        postInvalidateOnAnimation();
    }

    private RectF rectBorder() { rect.set(2f, 2f, W - 2f, H - 2f); return rect; }

    private void banner(Canvas c, String s) {
        text.setTextSize(54f);
        text.setColor(GameTheme.OUTLINE);
        c.drawText(s, W / 2f + 2f, H * 0.42f + 2f, text);
        text.setColor(Color.WHITE);
        c.drawText(s, W / 2f, H * 0.42f, text);
    }

    private void drawHud(Canvas c) {
        // hearts
        for (int i = 0; i < 3; i++) Sprites.heart(c, p, 22f + i * 24f, 22f, 8f, i < hearts);
        // time
        int secs = Math.max(0, (int) Math.ceil(timeLeft));
        text.setTextSize(15f);
        text.setColor(GameTheme.BROWN);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(secs + "s", W - 16f, 28f, text);
        text.setTextAlign(Paint.Align.CENTER);
        // progress bar
        float bx = W * 0.5f - 52f, by = 14f, bw = 104f, bh = 14f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(170, 255, 248, 234));
        rect.set(bx, by, bx + bw, by + bh);
        c.drawRoundRect(rect, 7f, 7f, p);
        p.setColor(GameTheme.USER);
        rect.set(bx, by, bx + Math.max(10f, bw * Math.min(1f, score / (float) target)), by + bh);
        c.drawRoundRect(rect, 7f, 7f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        p.setColor(GameTheme.TAN);
        rect.set(bx, by, bx + bw, by + bh);
        c.drawRoundRect(rect, 7f, 7f, p);
    }
}
