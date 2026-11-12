package com.example.pocketpet.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Hide and Seek (shell-game style): Mochi hops behind one hiding spot, then the spots swap places
 * again and again. Keep your eyes on the right one and tap it. A wrong guess shuffles everything again.
 * Medium: 4 spots, one swap at a time. Hard: 6 identical spots, two swaps at once, much faster.
 * Measured in dp.
 */
public class HideSeekView extends View implements GameBoard {
    private static final int INTRO = 0, SHUFFLE = 1, GUESS = 2, REVEAL = 3, END = 4;

    private final Random rnd = new Random();
    private final PausableHandler timers = new PausableHandler();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final Scenery scenery = new Scenery();
    private GameEvents events;
    private SoundFx sfx;
    private boolean paused;
    /**
     * level 1..5. 1 = Easy, 2 = the old Medium, 5 = the old Hard, 3 and 4 step up in between.
     * (There is no separate Medium / Hard choice any more: GameActivity starts level 1 and moves up when you win.)
     */
    private int difficulty = 1;
    private float doubleChance;          // chance that a round swaps two pairs at once
    private float d, W, H;

    // layout and rules
    private int cols, rows, n, triesMax, tries, firstRounds, reshuffleRounds, pairsPerRound;
    private float swapDur;
    private float size;
    private final float[] sx = new float[6], sy = new float[6];       // slot centres
    private final float[] ox = new float[6], oy = new float[6];       // drawn positions
    private final int[] slotOf = new int[6];                          // object -> slot
    private final float[] lift = new float[6];
    private final boolean[] lifted = new boolean[6];
    private int mochiObj;

    // state
    private int state = INTRO;
    private float stateT, endT;
    private boolean won;
    private long last, startedAt;

    // current shuffle round
    private int roundsLeft;
    private float roundT;
    private boolean roundActive;
    private final int[] pairA = new int[3], pairB = new int[3];      // objects swapping
    private final int[] fromSlotA = new int[3], fromSlotB = new int[3];
    private int pairCount;
    private float gapT;

    private final Runnable afterWrong = this::doAfterWrong;
    private final Runnable finish = this::doFinish;

    public HideSeekView(Context c) {
        super(c);
        text.setTypeface(com.example.pocketpet.view.BubbleFont.bold(c));
        d = c.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override public void setEvents(GameEvents e) { events = e; }
    @Override public void setDifficulty(int level) { difficulty = Math.max(1, Math.min(5, level)); }
    @Override public void setSound(SoundFx fx) { sfx = fx; }
    @Override public void setPaused(boolean pz) {
        paused = pz; timers.paused = pz; last = SystemClock.uptimeMillis(); invalidate();
    }

    @Override public void restart() {
        timers.clear();
        doubleChance = 0f;
        switch (difficulty) {
            case 1:   // Easy: 4 spots, slow single swaps, 3 tries
                cols = 2; rows = 2; n = 4; triesMax = 3; firstRounds = 5; reshuffleRounds = 3; swapDur = 0.85f; pairsPerRound = 1; break;
            case 2:   // Medium (as before)
                cols = 2; rows = 2; n = 4; triesMax = 2; firstRounds = 8; reshuffleRounds = 4; swapDur = 0.55f; pairsPerRound = 1; break;
            case 3:   // 6 spots (bushes and mushrooms), single swaps
                cols = 3; rows = 2; n = 6; triesMax = 2; firstRounds = 10; reshuffleRounds = 5; swapDur = 0.46f; pairsPerRound = 1; break;
            case 4:   // 6 identical spots, now and then two swaps at once
                cols = 3; rows = 2; n = 6; triesMax = 2; firstRounds = 11; reshuffleRounds = 6; swapDur = 0.40f; pairsPerRound = 2; doubleChance = 0.4f; break;
            default:  // 5 = Hard (as before): 6 identical spots, fast double swaps
                cols = 3; rows = 2; n = 6; triesMax = 2; firstRounds = 12; reshuffleRounds = 6; swapDur = 0.34f; pairsPerRound = 2; doubleChance = 1f; break;
        }
        tries = triesMax;
        for (int i = 0; i < 6; i++) { slotOf[i] = i; lift[i] = 0f; lifted[i] = false; }
        mochiObj = rnd.nextInt(n);
        state = INTRO; stateT = 0f; endT = 0f; won = false; roundActive = false; gapT = 0f;
        roundsLeft = firstRounds;
        last = SystemClock.uptimeMillis();
        layoutSpots();
        status("Mochi is hiding... watch closely!");
        invalidate();
    }

    private void status(String s) { if (events != null) events.onStatus(s); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w / d; H = h / d;
        scenery.resize(H);
        layoutSpots();
    }

    @Override protected void onDetachedFromWindow() {
        timers.clear();
        super.onDetachedFromWindow();
    }

    private void layoutSpots() {
        if (W <= 0 || cols == 0) return;
        float top = H * 0.26f, bottom = H - 16f;
        float cw = W / cols, ch = (bottom - top) / rows;
        size = Math.min(104f, Math.min(cw, ch) * 0.8f);
        for (int i = 0; i < n; i++) {
            sx[i] = cw * (i % cols + 0.5f);
            sy[i] = top + ch * (i / cols + 0.5f);
        }
    }

    // ------------------------------------------------------------------ rules

    private int objectAtSlot(int slot) {
        for (int o = 0; o < n; o++) if (slotOf[o] == slot) return o;
        return 0;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && state == GUESS && !paused) {
            float x = e.getX() / d, y = e.getY() / d;
            for (int s = 0; s < n; s++) {
                if (Math.abs(x - sx[s]) < size * 0.55f && Math.abs(y - sy[s]) < size * 0.55f) { pick(objectAtSlot(s)); break; }
            }
        }
        return true;
    }

    private void pick(int obj) {
        state = REVEAL;
        lifted[obj] = true;
        if (sfx != null) sfx.play(SoundFx.TAP);
        if (obj == mochiObj) {
            won = true; endT = 0f;
            if (sfx != null) sfx.play(SoundFx.FOUND);
            status("You found Mochi!");
            timers.postDelayed(finish, 1600);
        } else {
            tries--;
            if (sfx != null) sfx.play(SoundFx.WRONG);
            status(tries > 0 ? "Not there! " + tries + " try left" : "Out of tries!");
            timers.postDelayed(afterWrong, 1000);
        }
    }

    private void doAfterWrong() {
        for (int i = 0; i < n; i++) lifted[i] = false;
        if (tries <= 0) {
            lifted[mochiObj] = true;
            state = END; endT = 0f;
            status("Mochi was hiding here!");
            timers.postDelayed(finish, 1700);
            return;
        }
        roundsLeft = reshuffleRounds;
        state = SHUFFLE; gapT = 0.4f; roundActive = false;
        status("Mochi is moving again... watch!");
    }

    private void doFinish() {
        if (events != null) {
            events.onScore(won ? tries : 0);          // tries left = bonus score
            events.onGameOver(won ? GameEvents.RESULT_WIN : GameEvents.RESULT_LOSE);
        }
    }

    private void startRound() {
        // pick random disjoint pairs of objects to swap
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        for (int i = n - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = order[i]; order[i] = order[j]; order[j] = t; }
        int want = (pairsPerRound >= 2 && rnd.nextFloat() < doubleChance) ? 2 : 1;
        pairCount = Math.min(want, n / 2);
        for (int k = 0; k < pairCount; k++) {
            pairA[k] = order[2 * k]; pairB[k] = order[2 * k + 1];
            fromSlotA[k] = slotOf[pairA[k]]; fromSlotB[k] = slotOf[pairB[k]];
        }
        roundT = 0f; roundActive = true;
        if (sfx != null) sfx.play(SoundFx.SWAP);
    }

    private void endRound() {
        for (int k = 0; k < pairCount; k++) {
            slotOf[pairA[k]] = fromSlotB[k];
            slotOf[pairB[k]] = fromSlotA[k];
        }
        roundActive = false;
        roundsLeft--;
        gapT = 0.05f;
    }

    // ------------------------------------------------------------------ update + draw

    private void update(float dt) {
        stateT += dt;
        if (state == END || (state == REVEAL && won)) endT += dt;
        for (int i = 0; i < n; i++) {
            float goal = lifted[i] ? 1f : 0f;
            lift[i] += Math.max(-dt * 3f, Math.min(dt * 3f, goal - lift[i]));
        }
        if (state == INTRO && stateT >= 2.0f) {
            state = SHUFFLE; gapT = 0.1f;
            status("Keep your eyes on Mochi's spot!");
        }
        if (state == SHUFFLE) {
            if (roundActive) {
                roundT += dt / swapDur;
                if (roundT >= 1f) endRound();
            } else if (roundsLeft <= 0) {
                state = GUESS;
                status("Where is Mochi? " + tries + (tries == 1 ? " try left" : " tries left"));
            } else {
                gapT -= dt;
                if (gapT <= 0f) startRound();
            }
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (W <= 0 || cols == 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = paused ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        if (startedAt == 0) startedAt = now;
        float t = (now - startedAt) / 1000f;
        update(dt);

        c.save();
        c.scale(d, d);
        clip.reset();
        rect.set(2f, 2f, W - 2f, H - 2f);
        clip.addRoundRect(rect, 22f, 22f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        scenery.drawSky(c, W, H, t, 0f);
        scenery.drawHills(c, W, H, H * 0.2f);

        // patches of earth under each slot
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(55, 107, 74, 58));
        for (int s = 0; s < n; s++) {
            rect.set(sx[s] - size * 0.5f, sy[s] + size * 0.28f, sx[s] + size * 0.5f, sy[s] + size * 0.5f);
            c.drawOval(rect, p);
        }

        // object positions (slots, or halfway through a swap)
        for (int o = 0; o < n; o++) { ox[o] = sx[slotOf[o]]; oy[o] = sy[slotOf[o]]; }
        if (roundActive) {
            float k = Math.max(0f, Math.min(1f, roundT));
            float e = k * k * (3f - 2f * k);
            float arc = (float) Math.sin(Math.PI * k) * size * 0.42f;
            for (int q = 0; q < pairCount; q++) {
                float ax = sx[fromSlotA[q]], ay = sy[fromSlotA[q]], bx = sx[fromSlotB[q]], by = sy[fromSlotB[q]];
                float vx = bx - ax, vy = by - ay;
                float len = Math.max(1f, (float) Math.sqrt(vx * vx + vy * vy));
                float nx = -vy / len, ny = vx / len;
                ox[pairA[q]] = ax + vx * e + nx * arc;  oy[pairA[q]] = ay + vy * e + ny * arc;
                ox[pairB[q]] = bx - vx * e - nx * arc;  oy[pairB[q]] = by - vy * e - ny * arc;
            }
        }

        // Mochi hopping in
        if (state == INTRO) {
            float k = Math.max(0f, Math.min(1f, (stateT - 0.4f) / 1.1f));
            float sx0 = W / 2f, sy0 = H * 0.12f;
            float tx = ox[mochiObj], ty = oy[mochiObj] + size * 0.05f;
            float mx = sx0 + (tx - sx0) * k;
            float my = sy0 + (ty - sy0) * k - (float) Math.sin(Math.PI * k) * 46f;
            Sprites.mochi(c, p, mx, my, size * 0.3f, k < 1f ? Sprites.OPEN : Sprites.NORMAL, 1f);
        }

        // objects (draw the lifted one last so it is on top)
        for (int pass = 0; pass < 2; pass++) {
            for (int o = 0; o < n; o++) {
                boolean top = lift[o] > 0.02f;
                if ((pass == 0) == top) continue;
                if (o == mochiObj && lift[o] > 0.05f) {
                    float jump = (won || state == END) ? (float) Math.abs(Math.sin(endT * 6f)) * size * 0.12f : 0f;
                    Sprites.mochi(c, p, ox[o], oy[o] + size * 0.04f - jump, size * 0.3f, won ? Sprites.HAPPY : Sprites.NORMAL, 1f);
                }
                int type = difficulty >= 4 ? 0 : (o % 2);     // levels 4 and 5: identical-looking spots
                drawSpot(c, type, ox[o], oy[o] - lift[o] * size * 0.5f);
            }
        }
        c.restore();

        // border + tries
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(GameTheme.TAN);
        rect.set(2f, 2f, W - 2f, H - 2f);
        c.drawRoundRect(rect, 22f, 22f, p);
        for (int i = 0; i < triesMax; i++) Sprites.heart(c, p, 24f + i * 24f, 24f, 8f, i < tries);
        c.restore();
        postInvalidateOnAnimation();
    }

    /** type 0 = bush, 1 = mushroom. cx,cy = centre of the spot. */
    private void drawSpot(Canvas c, int type, float cx, float cy) {
        float s = size;
        if (type == 0) {
            for (int pass = 0; pass < 2; pass++) {
                float e = pass == 0 ? 2f : 0f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(pass == 0 ? Color.parseColor("#6DB07F") : Color.parseColor("#88CE98"));
                c.drawCircle(cx - s * 0.27f, cy + s * 0.12f, s * 0.26f + e, p);
                c.drawCircle(cx + s * 0.27f, cy + s * 0.12f, s * 0.26f + e, p);
                c.drawCircle(cx, cy - s * 0.06f, s * 0.32f + e, p);
                rect.set(cx - s * 0.52f - e, cy + s * 0.1f, cx + s * 0.52f + e, cy + s * 0.4f + e);
                c.drawRoundRect(rect, s * 0.15f, s * 0.15f, p);
            }
            p.setColor(Color.parseColor("#ABE2B6"));
            c.drawCircle(cx - s * 0.1f, cy - s * 0.18f, s * 0.1f, p);
            p.setColor(Color.parseColor("#FF9EB5"));
            c.drawCircle(cx + s * 0.2f, cy - s * 0.02f, s * 0.04f, p);
            c.drawCircle(cx - s * 0.3f, cy + s * 0.18f, s * 0.04f, p);
            c.drawCircle(cx + s * 0.05f, cy + s * 0.22f, s * 0.04f, p);
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#FFF1DA"));
            rect.set(cx - s * 0.16f, cy - s * 0.02f, cx + s * 0.16f, cy + s * 0.4f);
            c.drawRoundRect(rect, s * 0.1f, s * 0.1f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(Color.parseColor("#D9BFA0"));
            c.drawRoundRect(rect, s * 0.1f, s * 0.1f, p);
            for (int pass = 0; pass < 2; pass++) {
                float e = pass == 0 ? 2f : 0f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(pass == 0 ? Color.parseColor("#E0708A") : Color.parseColor("#FF92A8"));
                rect.set(cx - s * 0.5f - e, cy - s * 0.42f - e, cx + s * 0.5f + e, cy + s * 0.14f + e);
                c.drawArc(rect, 180f, 180f, true, p);
                rect.set(cx - s * 0.5f - e, cy - s * 0.04f, cx + s * 0.5f + e, cy + s * 0.14f + e);
                c.drawRoundRect(rect, s * 0.08f, s * 0.08f, p);
            }
            p.setColor(Color.WHITE);
            c.drawCircle(cx - s * 0.25f, cy - s * 0.12f, s * 0.07f, p);
            c.drawCircle(cx + s * 0.05f, cy - s * 0.24f, s * 0.08f, p);
            c.drawCircle(cx + s * 0.3f, cy - s * 0.08f, s * 0.06f, p);
        }
    }
}
