package com.example.pocketpet.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.Random;

/**
 * The interactive bath mini-game, drawn entirely with Canvas (2D).
 *
 * It only draws the tub, shower, soap, foam, drops and towel. The room and the weather window
 * are drawn by PetView.drawRoom() before this scene, and are never touched here.
 *
 * Phases:
 *   SOAP    drag the soap over Mochi until he is covered in foam
 *   RINSE   tap the shower (head or knob) to turn the water on; the foam washes away
 *   TUB_OUT the tub and shower slide away, Mochi steps onto the floor covered in water drops
 *   DRY     drag the towel over Mochi to rub every drop off
 *   DONE    short sparkle, then finished() becomes true and PetView ends the bath
 *
 * No allocation happens in update()/draw*(): every Paint, Path and RectF is a field.
 */
public class BathScene {

    public enum Phase { SOAP, RINSE, TUB_OUT, DRY, DONE }

    // ---------------- tuning ----------------
    private static final float SOAP_REACH     = 0.45f;  // soap rub radius, in pet radii
    private static final float SOAP_GAIN      = 0.9f;   // foam added per pet-radius of rubbing
    private static final float FOAM_COVER     = 0.90f;  // fraction of cells that must be foamy
    private static final float TOWEL_REACH    = 0.50f;  // towel rub radius, in pet radii
    private static final float TOWEL_GAIN     = 1.3f;   // wetness removed per pet-radius of rubbing
    private static final float TUB_OUT_TIME   = 1.6f;   // seconds
    private static final float DONE_TIME      = 1.4f;   // seconds

    // ---------------- state ----------------
    private boolean active   = false;
    private boolean finished = false;
    private Phase   phase    = Phase.SOAP;
    private float   phaseT   = 0f;
    private float   time     = 0f;

    // tub / shower presentation
    private float tubOff    = 0f;   // downward slide, in px
    private float tubAlpha  = 1f;
    private float petBlend  = 0f;   // 0 = Mochi sits in the tub, 1 = standing on the floor

    // foam cells (pet-local coordinates, 1.0 = pet radius)
    private static final int MAX_CELLS = 60;
    private final float[] cellX    = new float[MAX_CELLS];
    private final float[] cellY    = new float[MAX_CELLS];
    private final float[] cellSize = new float[MAX_CELLS];
    private final float[] cellRate = new float[MAX_CELLS];
    private final float[] foam     = new float[MAX_CELLS];
    private int cells = 0;

    // water drops on Mochi after the rinse (pet-local coordinates)
    private static final int MAX_DROPS = 24;
    private final float[] dropX   = new float[MAX_DROPS];
    private final float[] dropY   = new float[MAX_DROPS];
    private final float[] dropSz  = new float[MAX_DROPS];
    private final float[] drop    = new float[MAX_DROPS];  // wetness 1..0
    private int drops = 0;
    private float dropShow = 0f;

    // soap
    private boolean holdingSoap = false;
    private float soapX, soapY, soapPrevX, soapPrevY;

    // towel
    private boolean holdingTowel = false;
    private float towelX, towelY, towelPrevX, towelPrevY, towelWiggle;

    // shower
    private boolean waterOn  = false;
    private float   waterAmt = 0f;   // eased 0..1

    // soap bubbles
    private static final int BUBBLES = 14;
    private final float[] bubX = new float[BUBBLES];
    private final float[] bubY = new float[BUBBLES];
    private final float[] bubR = new float[BUBBLES];
    private final float[] bubA = new float[BUBBLES];

    // hint text
    private float  flashT = 0f;
    private String flashText = "";

    // cached geometry (set by layout())
    private float lw, lh, lr;

    // ---------------- drawing tools ----------------
    private final Paint  p    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint  txt  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path   path = new Path();
    private final RectF  oval = new RectF();
    private final RectF  oval2 = new RectF();
    private final RectF  box  = new RectF();
    private final Random rnd  = new Random(21);

    private static final int OUTLINE   = Color.parseColor("#8B5E4B");
    private static final int TUB_BODY  = Color.parseColor("#CDEBE3");
    private static final int TUB_LIP   = Color.parseColor("#E6F5F0");
    private static final int TUB_INNER = Color.parseColor("#9CC9C6");
    private static final int WATER     = Color.parseColor("#BFE8EE");
    private static final int GOLD      = Color.parseColor("#D9A441");
    private static final int GOLD_LT   = Color.parseColor("#F2D58A");
    private static final int FOAM_EDGE = Color.parseColor("#B9D6E2");
    private static final int DROP_FILL = Color.parseColor("#9ED8F2");
    private static final int DROP_EDGE = Color.parseColor("#5FA9D2");

    public BathScene() {
        buildCells();
        buildDrops();
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
    }

    // ============================== public API ==============================

    public boolean isActive()   { return active; }
    public Phase   getPhase()   { return phase; }

    /** True once, after the last sparkle. PetView ends the bath when it reads true. */
    public boolean consumeFinished() {
        if (!finished) return false;
        finished = false;
        return true;
    }

    /** Overall progress 0..1 (used for partial credit when the player taps Done early). */
    public float progress() {
        switch (phase) {
            case SOAP:    return 0.35f * foamCoverage();
            case RINSE:   return 0.35f + 0.30f * (1f - avgFoam());
            case TUB_OUT: return 0.65f;
            case DRY:     return 0.65f + 0.35f * (1f - avgDrop());
            default:      return 1f;
        }
    }

    public void start(float w, float h) {
        active = true; finished = false;
        phase = Phase.SOAP; phaseT = 0f;
        tubOff = 0f; tubAlpha = 1f; petBlend = 0f;
        waterOn = false; waterAmt = 0f;
        holdingSoap = false; holdingTowel = false;
        dropShow = 0f; flashT = 0f;
        for (int i = 0; i < cells; i++) { foam[i] = 0f; }
        for (int i = 0; i < drops; i++) { drop[i] = 1f; }
        for (int i = 0; i < BUBBLES; i++) bubA[i] = 0f;
        soapX = w * 0.76f; soapY = h * 0.665f;
        towelX = w * 0.84f; towelY = h * 0.86f;
    }

    /** Leave the scene immediately (Done button). */
    public void stop() {
        active = false; finished = false;
        holdingSoap = false; holdingTowel = false;
        waterOn = false; waterAmt = 0f;
    }

    /** Where Mochi is drawn while the bath is on. */
    public float petX(float w) { return lerp(w * 0.44f, w * 0.5f, petBlend); }
    public float petY(float h) { return lerp(h * 0.675f, h * 0.76f, petBlend); }

    // ============================== update ==============================

    public void update(float dt, float w, float h, float r) {
        if (!active) return;
        layout(w, h, r);
        time += dt;
        phaseT += dt;
        if (flashT > 0f) flashT -= dt;

        // soap bubbles rise and fade
        for (int i = 0; i < BUBBLES; i++) {
            if (bubA[i] > 0f) { bubA[i] -= dt * 1.3f; bubY[i] -= dt * r * 0.55f; }
        }

        // soap flies back to its rest spot when let go
        if (!holdingSoap) {
            soapX += (w * 0.76f - soapX) * Math.min(1f, 9f * dt);
            soapY += (h * 0.665f - soapY) * Math.min(1f, 9f * dt);
        }
        if (!holdingTowel) {
            towelX += (w * 0.84f - towelX) * Math.min(1f, 9f * dt);
            towelY += (h * 0.86f - towelY) * Math.min(1f, 9f * dt);
        }
        towelWiggle = Math.max(0f, towelWiggle - dt * 6f);

        // shower water eases in and out
        float target = waterOn ? 1f : 0f;
        waterAmt += (target - waterAmt) * Math.min(1f, 8f * dt);

        switch (phase) {
            case SOAP:
                if (foamCoverage() >= FOAM_COVER) {
                    for (int i = 0; i < cells; i++) foam[i] = 1f;   // top up the last gaps
                    holdingSoap = false;
                    phase = Phase.RINSE; phaseT = 0f;
                }
                break;

            case RINSE:
                if (waterOn) {
                    float most = 0f;
                    for (int i = 0; i < cells; i++) {
                        foam[i] = Math.max(0f, foam[i] - dt * cellRate[i]);
                        if (foam[i] > most) most = foam[i];
                    }
                    if (most <= 0.02f) {          // every bit of foam is gone
                        for (int i = 0; i < cells; i++) foam[i] = 0f;
                        waterOn = false;
                        phase = Phase.TUB_OUT; phaseT = 0f;
                        for (int i = 0; i < drops; i++) drop[i] = 1f;
                    }
                }
                break;

            case TUB_OUT: {
                float t = Math.min(1f, phaseT / TUB_OUT_TIME);
                float e = t * t * (3f - 2f * t);
                petBlend = e;
                tubOff   = e * h * 0.42f;
                tubAlpha = 1f - e;
                dropShow = Math.max(0f, Math.min(1f, (e - 0.45f) * 2f));
                if (phaseT >= TUB_OUT_TIME) {
                    petBlend = 1f; tubAlpha = 0f; dropShow = 1f;
                    phase = Phase.DRY; phaseT = 0f;
                }
                break;
            }

            case DRY:
                if (maxDrop() <= 0.02f) {
                    for (int i = 0; i < drops; i++) drop[i] = 0f;
                    holdingTowel = false;
                    phase = Phase.DONE; phaseT = 0f;
                }
                break;

            case DONE:
                if (phaseT >= DONE_TIME) {
                    active = false;
                    finished = true;
                }
                break;
        }
    }

    // ============================== touch ==============================

    /** Returns true when the scene consumed the event. */
    public boolean onTouch(MotionEvent ev, float w, float h, float r) {
        if (!active) return false;
        layout(w, h, r);
        float x = ev.getX();
        float y = ev.getY();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (phase == Phase.SOAP) {
                    if (dist(x, y, soapX, soapY) < w * 0.14f) {
                        holdingSoap = true;
                        soapX = x; soapY = y; soapPrevX = x; soapPrevY = y;
                    } else if (hitShower(x, y, w, h)) {
                        flash("Cover Mochi in foam first");
                    }
                } else if (phase == Phase.RINSE) {
                    if (hitShower(x, y, w, h)) waterOn = !waterOn;
                } else if (phase == Phase.DRY) {
                    if (dist(x, y, towelX, towelY) < w * 0.16f) {
                        holdingTowel = true;
                        towelX = x; towelY = y; towelPrevX = x; towelPrevY = y;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (holdingSoap && phase == Phase.SOAP) {
                    soapX = x; soapY = y;
                    float moved = dist(x, y, soapPrevX, soapPrevY);
                    soapPrevX = x; soapPrevY = y;
                    rubFoam(x, y, moved, r);
                } else if (holdingTowel && phase == Phase.DRY) {
                    towelX = x; towelY = y;
                    float moved = dist(x, y, towelPrevX, towelPrevY);
                    towelPrevX = x; towelPrevY = y;
                    towelWiggle = 1f;
                    rubDry(x, y, moved, r);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                holdingSoap = false;
                holdingTowel = false;
                break;
        }
        return true;
    }

    private void rubFoam(float x, float y, float moved, float r) {
        if (moved < 0.5f) return;
        float pcx = petX(lw), pcy = petY(lh);
        float reach = SOAP_REACH * r;
        float gain = moved / r * SOAP_GAIN;
        boolean over = false;
        for (int i = 0; i < cells; i++) {
            float dx = pcx + cellX[i] * r - x;
            float dy = pcy + cellY[i] * r - y;
            if (dx * dx + dy * dy < reach * reach) {
                foam[i] = Math.min(1f, foam[i] + gain);
                over = true;
            }
        }
        if (over) spawnBubble(x, y, r);
    }

    private void rubDry(float x, float y, float moved, float r) {
        if (moved < 0.5f) return;
        float pcx = petX(lw), pcy = petY(lh);
        float reach = TOWEL_REACH * r;
        float gain = moved / r * TOWEL_GAIN;
        for (int i = 0; i < drops; i++) {
            if (drop[i] <= 0f) continue;
            float dx = pcx + dropX[i] * r - x;
            float dy = pcy + dropY[i] * r - y;
            if (dx * dx + dy * dy < reach * reach) {
                drop[i] = Math.max(0f, drop[i] - gain);
            }
        }
    }

    private void spawnBubble(float x, float y, float r) {
        for (int i = 0; i < BUBBLES; i++) {
            if (bubA[i] <= 0f) {
                bubX[i] = x + (rnd.nextFloat() - 0.5f) * r * 0.5f;
                bubY[i] = y + (rnd.nextFloat() - 0.5f) * r * 0.3f;
                bubR[i] = r * (0.05f + rnd.nextFloat() * 0.07f);
                bubA[i] = 1f;
                return;
            }
        }
    }

    private void flash(String s) { flashText = s; flashT = 1.8f; }

    // ============================== geometry helpers ==============================

    private void layout(float w, float h, float r) { lw = w; lh = h; lr = r; }

    private float tubCx()  { return lw * 0.44f; }
    private float rimY()   { return lh * 0.68f; }
    private float rimRx()  { return lw * 0.37f; }
    private float rimRy()  { return lh * 0.05f; }
    private float poleX()  { return lw * 0.93f; }
    private float headX()  { return lw * 0.64f; }
    private float headY()  { return lh * 0.31f; }
    private float knobY()  { return lh * 0.52f; }

    private boolean hitShower(float x, float y, float w, float h) {
        return dist(x, y, poleX(), knobY()) < w * 0.10f
            || dist(x, y, headX(), headY()) < w * 0.12f;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    // ============================== data ==============================

    private void buildCells() {
        Random g = new Random(11);
        cells = 0;
        for (float y = -1.05f; y <= 0.3f && cells < MAX_CELLS; y += 0.27f) {
            for (float x = -0.9f; x <= 0.9f && cells < MAX_CELLS; x += 0.3f) {
                float ex = x / 0.95f, ey = (y + 0.4f) / 0.8f;
                if (ex * ex + ey * ey > 1.05f) continue;
                cellX[cells]    = x + (g.nextFloat() - 0.5f) * 0.12f;
                cellY[cells]    = y + (g.nextFloat() - 0.5f) * 0.12f;
                cellSize[cells] = 0.85f + g.nextFloat() * 0.35f;
                cellRate[cells] = 0.38f + g.nextFloat() * 0.25f;   // rinse speed
                cells++;
            }
        }
    }

    private void buildDrops() {
        Random g = new Random(5);
        drops = 0;
        while (drops < MAX_DROPS) {
            float x = (g.nextFloat() * 2f - 1f) * 0.9f;
            float y = -1.05f + g.nextFloat() * 1.85f;
            float ex = x / 0.95f, ey = (y + 0.1f) / 0.95f;
            if (ex * ex + ey * ey > 1f) continue;
            dropX[drops]  = x;
            dropY[drops]  = y;
            dropSz[drops] = 0.07f + g.nextFloat() * 0.05f;
            drop[drops]   = 1f;
            drops++;
        }
    }

    private float foamCoverage() {
        if (cells == 0) return 1f;
        int n = 0;
        for (int i = 0; i < cells; i++) if (foam[i] >= 0.7f) n++;
        return n / (float) cells;
    }
    private float avgFoam() {
        float s = 0f;
        for (int i = 0; i < cells; i++) s += foam[i];
        return cells == 0 ? 0f : s / cells;
    }
    private float avgDrop() {
        float s = 0f;
        for (int i = 0; i < drops; i++) s += drop[i];
        return drops == 0 ? 0f : s / drops;
    }
    private float maxDrop() {
        float m = 0f;
        for (int i = 0; i < drops; i++) if (drop[i] > m) m = drop[i];
        return m;
    }

    // ============================== drawing: layer helpers ==============================

    private void beginLayer(Canvas c, float alpha, float dy) {
        if (alpha < 0.999f) c.saveLayerAlpha(0f, 0f, lw, lh, (int) (alpha * 255f));
        else c.save();
        c.translate(0f, dy);
    }

    // ============================== drawing: behind Mochi ==============================

    /** Shower pipe, tub back (rim, inner wall, water). Called BEFORE the pet is drawn. */
    public void drawBehind(Canvas c, float w, float h, float r) {
        if (!active) return;
        layout(w, h, r);
        if (tubAlpha <= 0.01f) return;

        // ---- shower (fades with the tub, does not slide) ----
        beginLayer(c, tubAlpha, 0f);
        drawShower(c);
        c.restore();

        // ---- tub back ----
        beginLayer(c, tubAlpha, tubOff);
        float cx = tubCx(), ry = rimRy(), rx = rimRx(), ty = rimY();

        // outer rim
        oval.set(cx - rx, ty - ry, cx + rx, ty + ry);
        p.setStyle(Paint.Style.FILL);
        p.setColor(TUB_LIP);
        c.drawOval(oval, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.006f);
        p.setColor(OUTLINE);
        c.drawOval(oval, p);

        // inner wall
        oval2.set(cx - rx * 0.93f, ty - ry * 0.78f, cx + rx * 0.93f, ty + ry * 0.78f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(TUB_INNER);
        c.drawOval(oval2, p);

        // water surface (slightly lower than the inner wall edge)
        oval2.set(cx - rx * 0.90f, ty - ry * 0.62f, cx + rx * 0.90f, ty + ry * 0.74f);
        p.setColor(WATER);
        c.drawOval(oval2, p);
        // little ripple
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.004f);
        p.setColor(Color.argb(110, 255, 255, 255));
        float rip = 0.55f + 0.05f * (float) Math.sin(time * 2f);
        oval2.set(cx - rx * rip, ty - ry * 0.3f, cx + rx * rip, ty + ry * 0.5f);
        c.drawOval(oval2, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private void drawShower(Canvas c) {
        float px = poleX(), hx = headX(), hy = headY();
        float topY = lh * 0.27f;

        // pipe: dark outline pass then gold pass
        path.reset();
        path.moveTo(px, lh * 0.80f);
        path.lineTo(px, topY + lh * 0.05f);
        path.quadTo(px, topY, px - lw * 0.05f, topY);
        path.lineTo(hx + lw * 0.06f, topY);
        path.lineTo(hx + lw * 0.012f, hy - lh * 0.012f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(lw * 0.026f);
        p.setColor(OUTLINE);
        c.drawPath(path, p);
        p.setStrokeWidth(lw * 0.017f);
        p.setColor(GOLD);
        c.drawPath(path, p);
        p.setStrokeWidth(lw * 0.005f);
        p.setColor(GOLD_LT);
        c.drawPath(path, p);
        p.setStrokeCap(Paint.Cap.BUTT);

        // shower head: tilted disc facing Mochi
        c.save();
        c.rotate(30f, hx, hy);
        float hw = lw * 0.075f, hh = lh * 0.024f;
        oval.set(hx - hw, hy - hh, hx + hw, hy + hh);
        p.setStyle(Paint.Style.FILL);
        p.setColor(GOLD);
        c.drawOval(oval, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.006f);
        p.setColor(OUTLINE);
        c.drawOval(oval, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(GOLD_LT);
        for (int i = -2; i <= 2; i++) {
            c.drawCircle(hx + i * hw * 0.32f, hy + hh * 0.35f, lw * 0.004f, p);
        }
        c.restore();

        // tap knob on the pipe (pulses when it is time to rinse)
        float ky = knobY();
        boolean pulse = (phase == Phase.RINSE && !waterOn);
        if (pulse) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(lw * 0.008f);
            p.setColor(Color.argb((int) (140 + 90 * Math.sin(time * 6f)), 255, 255, 255));
            c.drawCircle(px, ky, lw * (0.052f + 0.01f * (float) Math.sin(time * 6f)), p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(GOLD);
        c.drawCircle(px, ky, lw * 0.03f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.006f);
        p.setColor(OUTLINE);
        c.drawCircle(px, ky, lw * 0.03f, p);
        // handle bar turns when the water is on
        c.save();
        c.rotate(waterOn ? 90f : 0f, px, ky);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(lw * 0.012f);
        p.setColor(GOLD_LT);
        c.drawLine(px - lw * 0.022f, ky, px + lw * 0.022f, ky, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    // ============================== drawing: on Mochi ==============================

    /** Surface suds, foam on Mochi and water drops. Called right AFTER the pet is drawn. */
    public void drawOnPet(Canvas c, float w, float h, float r) {
        if (!active) return;
        layout(w, h, r);
        float pcx = petX(w), pcy = petY(h);

        // ---- suds floating on the tub water ----
        if (tubAlpha > 0.01f) {
            float avg = avgFoam();
            int show = (int) Math.ceil(avg * 14f);
            if (show > 0) {
                beginLayer(c, tubAlpha, tubOff);
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 0; i < show; i++) {
                        float a = i * 2.399f;                        // golden-angle spread
                        float u = (float) Math.cos(a) * 0.82f;
                        float v = (float) Math.sin(a) * 0.55f;
                        float sx = tubCx() + u * rimRx() * 0.88f;
                        float sy = rimY() + v * rimRy() * 0.9f;
                        float rr = lw * 0.05f * (0.55f + 0.45f * avg)
                                 * (0.85f + 0.15f * (float) Math.sin(time * 1.6f + i));
                        blob(c, sx, sy, rr, pass);
                    }
                }
                c.restore();
            }
        }

        // ---- foam covering Mochi ----
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < cells; i++) {
                float f = foam[i];
                if (f <= 0.03f) continue;
                float rr = r * (0.12f + 0.20f * f) * cellSize[i];
                blob(c, pcx + cellX[i] * r, pcy + cellY[i] * r, rr, pass);
            }
        }
        // tiny shiny bubbles on top of the foam
        if (phase == Phase.SOAP || phase == Phase.RINSE) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(170, 200, 232, 245));
            for (int i = 0; i < cells; i += 3) {
                if (foam[i] < 0.6f) continue;
                float bx = pcx + cellX[i] * r + r * 0.10f * (float) Math.sin(time * 1.3f + i);
                float by = pcy + cellY[i] * r - r * 0.10f;
                c.drawCircle(bx, by, r * 0.035f, p);
            }
        }

        // ---- water drops clinging to Mochi ----
        if (dropShow > 0.01f && (phase == Phase.TUB_OUT || phase == Phase.DRY || phase == Phase.DONE)) {
            for (int i = 0; i < drops; i++) {
                float a = drop[i];
                if (a <= 0.01f) continue;
                float rr = r * dropSz[i] * (0.55f + 0.45f * a);
                float dx = pcx + dropX[i] * r;
                float dy = pcy + dropY[i] * r;
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb((int) (215 * a * dropShow), 0x9E, 0xD8, 0xF2));
                c.drawCircle(dx, dy, rr, p);
                // teardrop point on top
                path.reset();
                path.moveTo(dx - rr * 0.8f, dy - rr * 0.35f);
                path.lineTo(dx, dy - rr * 1.9f);
                path.lineTo(dx + rr * 0.8f, dy - rr * 0.35f);
                path.close();
                c.drawPath(path, p);
                p.setColor(Color.argb((int) (230 * a * dropShow), 255, 255, 255));
                c.drawCircle(dx - rr * 0.3f, dy - rr * 0.1f, rr * 0.28f, p);
            }
        }
    }

    /** One soft foam lump. pass 0 = outline, pass 1 = white fill + shade. */
    private void blob(Canvas c, float x, float y, float rr, int pass) {
        if (pass == 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, rr * 0.22f));
            p.setColor(FOAM_EDGE);
            c.drawCircle(x, y, rr, p);
            c.drawCircle(x + rr * 0.75f, y - rr * 0.35f, rr * 0.62f, p);
            c.drawCircle(x - rr * 0.7f, y - rr * 0.25f, rr * 0.55f, p);
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawCircle(x, y, rr, p);
            c.drawCircle(x + rr * 0.75f, y - rr * 0.35f, rr * 0.62f, p);
            c.drawCircle(x - rr * 0.7f, y - rr * 0.25f, rr * 0.55f, p);
            p.setColor(Color.argb(90, 190, 220, 235));
            c.drawCircle(x - rr * 0.2f, y + rr * 0.35f, rr * 0.45f, p);
        }
    }

    // ============================== drawing: in front of Mochi ==============================

    /** Tub front, water stream, soap, towel, bubbles, sparkles and the hint pill. */
    public void drawFront(Canvas c, float w, float h, float r) {
        if (!active) return;
        layout(w, h, r);
        float pcx = petX(w), pcy = petY(h);

        // ---- tub front (hides Mochi's lower half while he sits in it) ----
        if (tubAlpha > 0.01f) {
            beginLayer(c, tubAlpha, tubOff);
            drawTubFront(c);
            c.restore();
        }

        // ---- shower stream ----
        if (waterAmt > 0.02f && phase == Phase.RINSE) drawStream(c, pcx, pcy, r);

        // ---- soap on the tub edge ----
        if (phase == Phase.SOAP || phase == Phase.RINSE) {
            float a = (phase == Phase.RINSE) ? 1f - Math.min(1f, waterAmt * 0.0f) : 1f;
            drawSoap(c, soapX, soapY, r, a);
        }

        // ---- floating soap bubbles ----
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < BUBBLES; i++) {
            if (bubA[i] <= 0f) continue;
            p.setStrokeWidth(Math.max(2f, r * 0.03f));
            p.setColor(Color.argb((int) (Math.min(1f, bubA[i]) * 210), 160, 220, 255));
            c.drawCircle(bubX[i], bubY[i], bubR[i], p);
        }
        p.setStyle(Paint.Style.FILL);

        // ---- towel ----
        if (phase == Phase.DRY || phase == Phase.TUB_OUT) {
            float a = (phase == Phase.TUB_OUT) ? dropShow : 1f;
            drawTowel(c, r, a);
        }

        // ---- finish sparkles ----
        if (phase == Phase.DONE) {
            float a = 1f - phaseT / DONE_TIME;
            for (int i = 0; i < 8; i++) {
                float ang = time * 2.5f + i * 0.785f;
                float d = r * (0.9f + 0.7f * Math.min(1f, phaseT * 1.5f));
                star(c, pcx + (float) Math.cos(ang) * d, pcy + (float) Math.sin(ang) * d * 0.9f - r * 0.2f,
                        r * 0.16f, (int) (255 * Math.max(0f, a)));
            }
        }

        drawHint(c);
    }

    private void drawTubFront(Canvas c) {
        float cx = tubCx(), ry = rimRy(), rx = rimRx(), ty = rimY();
        float L = cx - rx, R = cx + rx;
        float bottom = lh * 0.88f;

        // body below the front rim
        oval.set(L, ty - ry, R, ty + ry);
        path.reset();
        path.moveTo(L, ty);
        path.arcTo(oval, 180f, -180f, false);              // lower half, left -> right
        path.cubicTo(R, ty + lh * 0.10f, R - lw * 0.05f, bottom - lh * 0.02f, R - lw * 0.10f, bottom);
        path.lineTo(L + lw * 0.10f, bottom);
        path.cubicTo(L + lw * 0.05f, bottom - lh * 0.02f, L, ty + lh * 0.10f, L, ty);
        path.close();

        // claw feet (drawn first so the body overlaps them)
        p.setStyle(Paint.Style.FILL);
        p.setColor(TUB_LIP);
        for (int s = 0; s < 2; s++) {
            float fx = (s == 0) ? L + lw * 0.16f : R - lw * 0.16f;
            box.set(fx - lw * 0.035f, bottom - lh * 0.012f, fx + lw * 0.035f, bottom + lh * 0.04f);
            c.drawRoundRect(box, lw * 0.02f, lw * 0.02f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(lw * 0.006f);
            p.setColor(OUTLINE);
            c.drawRoundRect(box, lw * 0.02f, lw * 0.02f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(TUB_LIP);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(TUB_BODY);
        c.drawPath(path, p);
        // soft highlight stripe
        p.setColor(Color.argb(90, 255, 255, 255));
        box.set(L + lw * 0.08f, ty + ry + lh * 0.025f, L + lw * 0.30f, ty + ry + lh * 0.045f);
        c.drawRoundRect(box, lh * 0.01f, lh * 0.01f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.006f);
        p.setColor(OUTLINE);
        c.drawPath(path, p);

        // front lip: crescent between outer and inner rim, drawn over Mochi
        oval.set(L, ty - ry, R, ty + ry);
        oval2.set(cx - rx * 0.93f, ty - ry * 0.78f, cx + rx * 0.93f, ty + ry * 0.78f);
        path.reset();
        path.moveTo(R, ty);
        path.arcTo(oval, 0f, 180f, false);                 // outer lower half, right -> left
        path.lineTo(cx - rx * 0.93f, ty);
        path.arcTo(oval2, 180f, -180f, false);             // inner lower half, left -> right
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(TUB_LIP);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.005f);
        p.setColor(OUTLINE);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawStream(Canvas c, float pcx, float pcy, float r) {
        float hx = headX(), hy = headY();
        // origin: lower-left side of the tilted shower disc
        float ox = hx - lw * 0.02f, oy = hy + lh * 0.02f;
        float ex = pcx, ey = pcy - r * 0.55f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(2f, lw * 0.007f));
        int n = 16;
        for (int i = 0; i < n; i++) {
            float ph = (time * 1.7f + i * 0.37f) % 1f;
            float spread = ((i % 8) - 3.5f) / 3.5f;              // -1..1 across the disc
            float sx = ox + spread * lw * 0.05f;
            float tx = ex + spread * r * 0.8f;
            float f = (float) Math.pow(ph, 1.25f);
            float x = lerp(sx, tx, f);
            float y = lerp(oy, ey + (i % 3) * r * 0.2f, f);
            float tailX = lerp(sx, tx, Math.max(0f, f - 0.08f));
            float tailY = lerp(oy, ey + (i % 3) * r * 0.2f, Math.max(0f, f - 0.08f));
            p.setColor(Color.argb((int) (190 * waterAmt), 0x8F, 0xD0, 0xF0));
            c.drawLine(tailX, tailY, x, y, p);
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        // splashes bouncing off Mochi
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int) (170 * waterAmt), 0xB8, 0xE4, 0xF6));
        for (int i = 0; i < 6; i++) {
            float ph = (time * 2.2f + i * 0.31f) % 1f;
            float side = (i % 2 == 0) ? -1f : 1f;
            float sx = pcx + side * r * (0.4f + 0.7f * ph) + (i - 3) * r * 0.08f;
            float sy = pcy - r * 0.55f - (float) Math.sin(ph * Math.PI) * r * 0.45f + ph * r * 0.5f;
            c.drawCircle(sx, sy, r * 0.045f * (1f - ph * 0.5f), p);
        }
    }

    private void drawSoap(Canvas c, float x, float y, float r, float alpha) {
        boolean atRest = !holdingSoap && dist(x, y, lw * 0.76f, lh * 0.665f) < lw * 0.03f;
        if (atRest) {
            // little dish on the tub edge
            oval.set(x - lw * 0.075f, y + lh * 0.005f, x + lw * 0.075f, y + lh * 0.03f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#A9D6D2"));
            c.drawOval(oval, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(lw * 0.005f);
            p.setColor(OUTLINE);
            c.drawOval(oval, p);
        }
        float sw = lw * 0.085f, sh = lh * 0.032f;
        c.save();
        c.rotate(holdingSoap ? -12f : 0f, x, y);
        box.set(x - sw, y - sh, x + sw, y + sh);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#F4D9A0"));
        c.drawRoundRect(box, sh * 0.8f, sh * 0.8f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.006f);
        p.setColor(OUTLINE);
        c.drawRoundRect(box, sh * 0.8f, sh * 0.8f, p);
        // shine and little wordmark dots
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(150, 255, 255, 255));
        c.drawCircle(x - sw * 0.5f, y - sh * 0.35f, sh * 0.28f, p);
        p.setColor(Color.argb(160, 139, 94, 75));
        c.drawCircle(x - sw * 0.05f, y + sh * 0.05f, sh * 0.12f, p);
        c.drawCircle(x + sw * 0.2f, y + sh * 0.05f, sh * 0.12f, p);
        c.drawCircle(x + sw * 0.45f, y + sh * 0.05f, sh * 0.12f, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private void drawTowel(Canvas c, float r, float alpha) {
        float x = towelX, y = towelY;
        float a = Math.max(0f, Math.min(1f, alpha));
        float grow = holdingTowel ? 1f : 0f;
        float tw = lw * (0.115f + 0.05f * grow);
        float th = lh * (0.036f + 0.03f * grow);
        c.save();
        c.rotate((float) Math.sin(time * 18f) * 8f * towelWiggle, x, y);
        box.set(x - tw, y - th, x + tw, y + th);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int) (255 * a), 0xF2, 0xEA, 0xD6));
        c.drawRoundRect(box, th * 0.45f, th * 0.45f, p);
        // green stripes + leaf
        p.setColor(Color.argb((int) (255 * a), 0x8F, 0xBF, 0x8A));
        box.set(x - tw, y - th * 0.55f, x + tw, y - th * 0.25f);
        c.drawRect(box, p);
        box.set(x - tw, y + th * 0.25f, x + tw, y + th * 0.55f);
        c.drawRect(box, p);
        p.setColor(Color.argb((int) (255 * a), 0x6F, 0xA8, 0x6B));
        oval.set(x - th * 0.5f, y - th * 0.18f, x + th * 0.5f, y + th * 0.18f);
        c.drawOval(oval, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.006f);
        p.setColor(Color.argb((int) (255 * a), 0x8B, 0x5E, 0x4B));
        box.set(x - tw, y - th, x + tw, y + th);
        c.drawRoundRect(box, th * 0.45f, th * 0.45f, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private void star(Canvas c, float x, float y, float s, int alpha) {
        path.reset();
        path.moveTo(x, y - s);
        path.quadTo(x, y, x + s, y);
        path.quadTo(x, y, x, y + s);
        path.quadTo(x, y, x - s, y);
        path.quadTo(x, y, x, y - s);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(Math.max(0, Math.min(255, alpha)), 0xF7, 0xC9, 0x48));
        c.drawPath(path, p);
    }

    private void drawHint(Canvas c) {
        String s;
        if (flashT > 0f) {
            s = flashText;
        } else {
            switch (phase) {
                case SOAP:    s = "Rub the soap on Mochi"; break;
                case RINSE:   s = waterOn ? "Rinsing..." : "Tap the shower to rinse"; break;
                case DRY:     s = "Rub the towel on Mochi"; break;
                case DONE:    s = "Squeaky clean!"; break;
                default:      return;
            }
        }
        float ts = lh * 0.032f;
        txt.setTextSize(ts);
        float tw = txt.measureText(s);
        float padX = ts * 0.9f, top = lh * 0.012f;
        box.set(lw * 0.5f - tw * 0.5f - padX, top, lw * 0.5f + tw * 0.5f + padX, top + ts * 1.9f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(215, 0xF3, 0xE6, 0xCF));
        c.drawRoundRect(box, ts, ts, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 0.004f);
        p.setColor(Color.argb(200, 0x8B, 0x5E, 0x4B));
        c.drawRoundRect(box, ts, ts, p);
        p.setStyle(Paint.Style.FILL);
        txt.setColor(Color.parseColor("#6B4A3A"));
        c.drawText(s, lw * 0.5f, top + ts * 1.32f, txt);
    }
}
