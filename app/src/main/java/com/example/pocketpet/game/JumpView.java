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

import java.util.ArrayList;
import java.util.Random;

/**
 * Mochi Jump: Mochi bounces up from cloud to cloud. Drag your finger left and right to steer.
 * Reach the rainbow at the top to win; fall off the bottom and you lose. Measured in dp.
 */
public class JumpView extends View implements GameBoard {
    private static final int READY = 0, PLAY = 1, END = 2;
    private static final float GRAV = 1150f, JUMP = 650f, PLAT_W = 62f, PLAT_H = 13f, R = 22f;

    private static class Plat {
        float x, y, w = PLAT_W, vx;
        int type;                 // 0 normal, 1 moving, 2 crumbling
        float breakT = -1f;       // >= 0 once it started to crumble
        boolean gone;
    }

    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final Scenery scenery = new Scenery();
    private final ArrayList<Plat> plats = new ArrayList<>();
    private GameEvents events;
    private SoundFx sfx;
    private boolean paused;
    private int difficulty = 1;
    private float d, W, H;

    private int state = READY;
    private float stateT, endT, x, y, vy, targetX, camTop, startY, genY, best, goalHeight, squash = 1f, squashT;
    private boolean won, finishedSent;
    private long last, startedAt;

    public JumpView(Context c) {
        super(c);
        text.setTypeface(com.example.pocketpet.view.BubbleFont.bold(c));
        d = c.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    @Override public void setEvents(GameEvents e) { events = e; }
    @Override public void setDifficulty(int level) { difficulty = level; }
    @Override public void setSound(SoundFx fx) { sfx = fx; }
    @Override public void setPaused(boolean p2) { paused = p2; last = SystemClock.uptimeMillis(); invalidate(); }

    @Override public void restart() {
        handler.removeCallbacksAndMessages(null);
        goalHeight = difficulty == 0 ? 2200f : difficulty == 1 ? 3200f : 4200f;
        plats.clear();
        state = READY; stateT = 0f; endT = 0f; won = false; finishedSent = false;
        best = 0f; squash = 1f; squashT = 0f;
        last = SystemClock.uptimeMillis();
        if (W > 0) setup();
        status("Reach " + (int) (goalHeight / 10f) + " m");
        invalidate();
    }

    private void setup() {
        camTop = 0f;
        startY = H - 70f;
        x = W / 2f; targetX = x;
        y = startY - R * 0.9f; vy = -JUMP;
        genY = startY;
        Plat base = new Plat();
        base.x = W / 2f - PLAT_W / 2f; base.y = startY; base.type = 0;
        plats.add(base);
        generate();
    }

    private void status(String s) { if (events != null) events.onStatus(s); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w / d; H = h / d;
        scenery.resize(H);
        if (plats.isEmpty() && state == READY) setup();
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) targetX = e.getX() / d;
        return true;
    }

    // ------------------------------------------------------------------ world

    private void generate() {
        float minGap = difficulty == 0 ? 62f : difficulty == 1 ? 72f : 86f;
        float maxGap = difficulty == 0 ? 105f : difficulty == 1 ? 120f : 138f;
        while (genY > camTop - 140f && startY - genY < goalHeight + 200f) {
            float progress = Math.min(1f, (startY - genY) / goalHeight);
            float gap = minGap + (maxGap - minGap) * (0.35f + 0.65f * progress) * rnd.nextFloat();
            genY -= gap;
            Plat pl = new Plat();
            pl.x = 6f + rnd.nextFloat() * (W - PLAT_W - 12f);
            pl.y = genY;
            float roll = rnd.nextFloat();
            if (difficulty >= 1 && startY - genY > 250f) {
                if (difficulty == 2 && roll < 0.25f) { pl.type = 1; pl.vx = (rnd.nextBoolean() ? 1 : -1) * (35f + rnd.nextFloat() * 30f); }
                else if (roll > 0.8f) pl.type = 2;
            }
            plats.add(pl);
        }
    }

    private void update(float dt) {
        stateT += dt;
        if (squashT > 0f) { squashT -= dt; squash = 1f - 0.28f * (squashT / 0.18f); if (squashT <= 0f) squash = 1f; }

        for (Plat pl : plats) {
            if (pl.type == 1) {
                pl.x += pl.vx * dt;
                if (pl.x < 4f) { pl.x = 4f; pl.vx = -pl.vx; }
                if (pl.x + pl.w > W - 4f) { pl.x = W - 4f - pl.w; pl.vx = -pl.vx; }
            }
            if (pl.breakT >= 0f) {
                pl.breakT += dt;
                if (pl.breakT > 0.25f) pl.y += 260f * dt;
                if (pl.y > camTop + H + 60f) pl.gone = true;
            }
        }

        if (state == READY) {
            if (stateT > 0.9f) { state = PLAY; stateT = 0f; }
            else return;
        }

        if (state == PLAY) {
            float r = R;
            float want = Math.max(r * 0.6f, Math.min(W - r * 0.6f, targetX));
            float dx = want - x;
            float step = 560f * dt;
            x += Math.max(-step, Math.min(step, dx));

            float prevFeet = y + r * 0.9f;
            vy += GRAV * dt;
            y += vy * dt;
            float feet = y + r * 0.9f;

            if (vy > 0f) {
                for (Plat pl : plats) {
                    if (pl.gone || pl.breakT >= 0f) continue;
                    if (prevFeet <= pl.y + 3f && feet >= pl.y && x > pl.x - 6f && x < pl.x + pl.w + 6f) {
                        y = pl.y - r * 0.9f;
                        vy = -JUMP;
                        squashT = 0.18f; squash = 0.72f;
                        if (sfx != null) sfx.play(pl.type == 2 ? SoundFx.CRUMBLE : SoundFx.BOUNCE);
                        if (pl.type == 2) pl.breakT = 0f;
                        break;
                    }
                }
            }

            float top = camTop + H * 0.42f;
            if (y < top) camTop = y - H * 0.42f;
            best = Math.max(best, startY - (y + r * 0.9f));
            generate();
            for (int i = plats.size() - 1; i >= 0; i--) {
                Plat pl = plats.get(i);
                if (pl.gone || pl.y > camTop + H + 80f) plats.remove(i);
            }

            status((int) (best / 10f) + " m / " + (int) (goalHeight / 10f) + " m");
            if (events != null) events.onScore((int) (best / 10f));
            if (best >= goalHeight) endGame(true);
            else if (y - camTop > H + r * 2f) endGame(false);
            return;
        }

        endT += dt;
        if (won) { y -= 40f * dt; }
        if (!finishedSent && endT > 1.5f) {
            finishedSent = true;
            if (events != null) events.onGameOver(won ? GameEvents.RESULT_WIN : GameEvents.RESULT_LOSE);
        }
    }

    private void endGame(boolean win) {
        state = END; won = win; endT = 0f;
        status(win ? "You reached the rainbow!" : "Mochi fell down!");
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        if (W <= 0) return;
        if (plats.isEmpty()) setup();
        long now = SystemClock.uptimeMillis();
        float dt = paused ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        update(dt);
        if (startedAt == 0) startedAt = now;
        float t = (now - startedAt) / 1000f;

        c.save();
        c.scale(d, d);
        clip.reset();
        rect.set(2f, 2f, W - 2f, H - 2f);
        clip.addRoundRect(rect, 22f, 22f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        scenery.drawSky(c, W, H, t, camTop * 0.25f);

        // ground at the start
        float gy = startY + 6f - camTop;
        if (gy < H + 40f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(GameTheme.GRASS);
            rect.set(-10f, gy + 10f, W + 10f, gy + 120f);
            c.drawRoundRect(rect, 30f, 30f, p);
        }

        // rainbow goal
        float goalY = startY - goalHeight - camTop;
        if (goalY > -60f && goalY < H + 60f) drawRainbow(c, goalY);

        // clouds you bounce on
        for (Plat pl : plats) drawPlat(c, pl);

        // Mochi
        int face = vy < 0f ? Sprites.HAPPY : Sprites.OPEN;
        if (state == END) face = won ? Sprites.HAPPY : Sprites.SAD;
        float sy = y - camTop;
        Sprites.mochi(c, p, x, sy + (1f - squash) * R * 0.9f, R, face, squash);
        c.restore();

        // window border + HUD
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(GameTheme.TAN);
        rect.set(2f, 2f, W - 2f, H - 2f);
        c.drawRoundRect(rect, 22f, 22f, p);
        drawHud(c);

        if (state == READY) {
            text.setTextSize(30f);
            text.setColor(GameTheme.OUTLINE);
            c.drawText("Drag to steer!", W / 2f + 1.5f, H * 0.42f + 1.5f, text);
            text.setColor(Color.WHITE);
            c.drawText("Drag to steer!", W / 2f, H * 0.42f, text);
        }
        c.restore();
        postInvalidateOnAnimation();
    }

    private void drawRainbow(Canvas c, float gy) {
        int[] cols = {Color.parseColor("#FFB3BF"), Color.parseColor("#FFD3A8"), Color.parseColor("#FFF0A6"),
                      Color.parseColor("#B9EBC5"), Color.parseColor("#B5DDF8"), Color.parseColor("#D4C7F2")};
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < cols.length; i++) {
            p.setColor(cols[i]);
            rect.set(-10f, gy + i * 7f, W + 10f, gy + i * 7f + 8f);
            c.drawRect(rect, p);
        }
        Sprites.cloud(c, p, 6f, gy - 12f, 70f, 18f, Color.WHITE, Color.parseColor("#E4D3C0"));
        Sprites.cloud(c, p, W - 80f, gy - 8f, 64f, 16f, Color.WHITE, Color.parseColor("#E4D3C0"));
        text.setTextSize(16f);
        text.setColor(GameTheme.BROWN);
        c.drawText("Rainbow!", W / 2f, gy - 6f, text);
    }

    private void drawPlat(Canvas c, Plat pl) {
        if (pl.gone) return;
        float sy = pl.y - camTop;
        if (sy < -30f || sy > H + 30f) return;
        int fill = Color.WHITE, edge = Color.parseColor("#E8D8C6");
        if (pl.type == 1) { fill = Color.parseColor("#E8E0FB"); edge = Color.parseColor("#CFC3EE"); }
        else if (pl.type == 2) { fill = Color.parseColor("#F9E5C9"); edge = Color.parseColor("#E5C9A2"); }
        float shake = (pl.breakT >= 0f && pl.breakT < 0.25f) ? (float) Math.sin(pl.breakT * 90f) * 1.5f : 0f;
        Sprites.cloud(c, p, pl.x + shake, sy - 4f, pl.w, PLAT_H, fill, edge);
        if (pl.type == 2) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f);
            p.setColor(Color.parseColor("#D0AE84"));
            c.drawLine(pl.x + pl.w * 0.4f, sy - 2f, pl.x + pl.w * 0.48f, sy + 7f, p);
            c.drawLine(pl.x + pl.w * 0.62f, sy - 3f, pl.x + pl.w * 0.55f, sy + 6f, p);
        }
    }

    private void drawHud(Canvas c) {
        float bx = W * 0.5f - 62f, by = 14f, bw = 124f, bh = 14f;
        float frac = Math.min(1f, best / goalHeight);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(170, 255, 248, 234));
        rect.set(bx, by, bx + bw, by + bh);
        c.drawRoundRect(rect, 7f, 7f, p);
        p.setColor(GameTheme.CPU);
        rect.set(bx, by, bx + Math.max(10f, bw * frac), by + bh);
        c.drawRoundRect(rect, 7f, 7f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        p.setColor(GameTheme.TAN);
        rect.set(bx, by, bx + bw, by + bh);
        c.drawRoundRect(rect, 7f, 7f, p);
        text.setTextSize(13f);
        text.setColor(GameTheme.BROWN);
        c.drawText((int) (best / 10f) + " m", bx + bw + 28f, by + 12f, text);
    }
}
