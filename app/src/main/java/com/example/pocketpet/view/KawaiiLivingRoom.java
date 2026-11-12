package com.example.pocketpet.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.example.pocketpet.weather.WeatherType;

/**
 * The default (awake) scene: a cosy pink living room in the Cute Kawaii Storybook style.
 *
 * Layout was measured from the reference picture, so all constants below are reference pixels
 * (origin of the room = 172,225). The room scales uniformly with the view width; furniture is
 * anchored to the floor (where Mochi stands) and the window group is anchored to the top, with the
 * window and curtains stretching to meet the sofa on taller screens.
 *
 * The view through the window is delegated to {@link KawaiiWeather} so it follows the weather.
 */
final class KawaiiLivingRoom {
    private static final float X0 = 172f, Y0 = 225f, RW = 1038f, RH = 1033f, ANCH = 1010f;

    // palette
    private static final int WALL     = 0xFFF9D6C6;
    private static final int WALL_HEART = 0x66F2A5A6;
    private static final int FLOOR    = 0xFFF8DDB0;
    private static final int FLOOR_LN = 0xFFEBC38C;
    private static final int WOOD     = 0xFFB9703F;
    private static final int WOOD_LT  = 0xFFD9955C;
    private static final int CURTAIN  = 0xFFF47FAA;
    private static final int SOFA     = 0xFFC9692F;
    private static final int SOFA_LT  = 0xFFDD8444;
    private static final int SOFA_DK  = 0xFFA94F26;
    private static final int TEAL     = 0xFF79D3BE;
    private static final int PINK     = 0xFFF38FA8;
    private static final int INK      = 0xFF5A3A33;

    private float s, ox, anchorY, w, h;
    private final RectF glass = new RectF();
    private final Path arch = new Path();
    private final Path tmp = new Path();
    private final Path scratch = new Path();
    private final Path panelPath = new Path();
    private final Path shadePath = new Path();
    private final RectF oval = new RectF();
    private final KawaiiWeather sky = new KawaiiWeather();

    // ---- coordinate mapping ----
    private float x(float px) { return ox + (px - X0) * s; }
    private float t(float py) { return (py - Y0) * s; }
    private float b(float py) { return anchorY + (py - ANCH) * s; }
    private float d(float v)  { return v * s; }

    void layout(float w, float h) {
        this.w = w; this.h = h;
        s = Math.min(w / RW, h / RH);
        ox = (w - RW * s) / 2f;
        anchorY = h * 0.76f;
    }

    /** Pet rests here (screen y of the pet centre). */
    float floorY(float h) { return h * 0.76f; }

    void draw(Canvas c, float w, float h, float time, WeatherType weather, float flash) {
        layout(w, h);
        Paint p = KawaiiArt.P;
        p.setShader(null);
        p.setPathEffect(null);

        // ---- wall + wallpaper hearts ----
        float floorTop = Math.min(h, b(885f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(WALL);
        c.drawRect(0f, 0f, w, floorTop + 1f, p);
        KawaiiArt.heartField(c, 0f, 0f, w, floorTop, d(62f), d(8f), WALL_HEART, 0f);

        // ---- floor ----
        p.setColor(FLOOR);
        c.drawRect(0f, floorTop, w, h, p);
        p.setColor(0xFFEFC995);
        c.drawRect(0f, floorTop, w, floorTop + d(10f), p);          // skirting shadow
        p.setColor(Color.argb(120, 235, 190, 140));
        float plank = d(150f);
        for (float yy = floorTop + d(10f); yy < h; yy += d(58f)) {
            c.drawRect(0f, yy, w, yy + Math.max(1.5f, d(3f)), p);
        }
        int k = 0;
        for (float yy = floorTop + d(10f); yy < h; yy += d(58f), k++) {
            float off = (k % 2 == 0) ? 0f : plank * 0.5f;
            for (float xx = off; xx < w; xx += plank) {
                c.drawRect(xx, yy, xx + Math.max(1.5f, d(3f)), yy + d(58f), p);
            }
        }

        // ---- wall dressing ----
        drawShelves(c, time);
        drawHangingPlants(c, time);
        drawWindow(c, time, weather, flash);
        drawCurtains(c, time);

        // ---- floor furniture ----
        drawRug(c);
        drawTv(c);
        drawTableAndLamp(c);
        drawSofa(c, time);
    }

    // ============================================================ window
    private void drawWindow(Canvas c, float time, WeatherType weather, float flash) {
        float wl = x(305f), wr = x(845f), top = t(292f);
        float spring = t(500f);
        float sillTop = b(683f), sillBot = b(706f);
        float rx = (wr - wl) / 2f, cx = (wl + wr) / 2f;
        float ry = spring - top;
        float fw = d(17f);

        // outer frame (arched)
        archPath(arch, wl, wr, top, spring, sillTop);
        KawaiiArt.fillStroke(c, arch, WOOD, 0xFF8A4F2E, d(6f));

        // glass
        archPath(tmp, wl + fw, wr - fw, top + fw, spring, sillTop - d(2f));
        glass.set(wl + fw, top + fw, wr - fw, sillTop - d(2f));
        int sv = c.save();
        c.clipPath(tmp);
        sky.draw(c, glass, weather, time, flash, 0f);
        c.restoreToCount(sv);

        // inner frame line
        KawaiiArt.P.setStyle(Paint.Style.STROKE);
        KawaiiArt.P.setStrokeWidth(d(5f));
        KawaiiArt.P.setColor(0xFF8A4F2E);
        c.drawPath(tmp, KawaiiArt.P);
        KawaiiArt.P.setStyle(Paint.Style.FILL);

        // cross mullions
        float mh = d(15f);
        KawaiiArt.rr(c, cx - mh * 0.5f, top + fw, cx + mh * 0.5f, sillTop, mh * 0.3f, WOOD, 0xFF8A4F2E, d(4f));
        float barY = t(470f);
        KawaiiArt.rr(c, wl + fw, barY - mh * 0.5f, wr - fw, barY + mh * 0.5f, mh * 0.3f, WOOD, 0xFF8A4F2E, d(4f));
        KawaiiArt.shine(c, cx - d(2f), top + fw + d(40f), d(2f), d(22f), 110);

        // sill
        KawaiiArt.rr(c, x(288f), sillTop, x(872f), sillBot, d(9f), WOOD_LT, 0xFF8A4F2E, d(5f));
        KawaiiArt.shine(c, x(430f), sillTop + d(6f), d(70f), d(2.5f), 120);

        // potted bush + pink chick on the sill
        pottedBush(c, x(356f), sillTop);
        pinkChick(c, x(440f), sillTop, time);
    }

    private void archPath(Path pa, float l, float r, float top, float spring, float bottom) {
        pa.reset();
        pa.moveTo(l, bottom);
        pa.lineTo(l, spring);
        oval.set(l, top, r, top + (spring - top) * 2f);
        pa.arcTo(oval, 180f, 180f, false);
        pa.lineTo(r, bottom);
        pa.close();
    }

    private void pottedBush(Canvas c, float cx, float sillTop) {
        float pw = d(23f), ph = d(36f);
        tmp.reset();
        tmp.moveTo(cx - pw, sillTop - ph);
        tmp.lineTo(cx + pw, sillTop - ph);
        tmp.lineTo(cx + pw * 0.78f, sillTop);
        tmp.lineTo(cx - pw * 0.78f, sillTop);
        tmp.close();
        int green = 0xFF5CBF6B;
        KawaiiArt.circ(c, cx, sillTop - ph - d(24f), d(32f), green, KawaiiArt.edge(green, 0.4f), d(4f));
        KawaiiArt.circ(c, cx - d(22f), sillTop - ph - d(10f), d(22f), 0xFF72CD7A, KawaiiArt.edge(green, 0.4f), d(4f));
        KawaiiArt.circ(c, cx + d(22f), sillTop - ph - d(10f), d(22f), 0xFF72CD7A, KawaiiArt.edge(green, 0.4f), d(4f));
        KawaiiArt.circ(c, cx, sillTop - ph - d(24f), d(30f), green, 0, 0f);
        KawaiiArt.shine(c, cx - d(10f), sillTop - ph - d(36f), d(9f), d(6f), 130);
        KawaiiArt.fillStroke(c, tmp, 0xFFE59A6B, 0xFF9A5A3A, d(4f));
        KawaiiArt.rr(c, cx - pw - d(3f), sillTop - ph - d(4f), cx + pw + d(3f), sillTop - ph + d(8f), d(5f), 0xFFF0AB7C, 0xFF9A5A3A, d(4f));
    }

    private void pinkChick(Canvas c, float cx, float sillTop, float time) {
        float bob = (float) Math.sin(time * 2.2f) * d(1.6f);
        float r = d(40f);
        float cy = sillTop - r * 0.92f + bob;
        int body = 0xFFF8A9A4;
        KawaiiArt.oval(c, cx - r, cy - r * 0.9f, cx + r, cy + r * 0.95f, body, 0xFFBF6460, d(4f));
        // wing + tail tuft
        KawaiiArt.oval(c, cx + r * 0.2f, cy - r * 0.05f, cx + r * 0.95f, cy + r * 0.62f, 0xFFF38F8B, 0xFFBF6460, d(3f));
        KawaiiArt.fillStroke(c, tuft(cx, cy - r * 0.9f, r * 0.28f), 0xFFF8A9A4, 0xFFBF6460, d(3f));
        // eyes + beak
        KawaiiArt.circ(c, cx - r * 0.38f, cy - r * 0.12f, r * 0.1f, INK, 0, 0f);
        KawaiiArt.circ(c, cx + r * 0.12f, cy - r * 0.12f, r * 0.1f, INK, 0, 0f);
        KawaiiArt.shine(c, cx - r * 0.41f, cy - r * 0.16f, r * 0.04f, r * 0.04f, 230);
        KawaiiArt.shine(c, cx + r * 0.09f, cy - r * 0.16f, r * 0.04f, r * 0.04f, 230);
        tmp.reset();
        tmp.moveTo(cx - r * 0.2f, cy + r * 0.03f);
        tmp.lineTo(cx - r * 0.05f, cy + r * 0.03f);
        tmp.lineTo(cx - r * 0.125f, cy + r * 0.2f);
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, 0xFFFFB347, 0xFFD27D1F, d(2f));
        KawaiiArt.blush(c, cx - r * 0.62f, cy + r * 0.2f, r * 0.17f, Color.argb(130, 255, 90, 120));
        KawaiiArt.blush(c, cx + r * 0.34f, cy + r * 0.2f, r * 0.17f, Color.argb(130, 255, 90, 120));
        KawaiiArt.shine(c, cx - r * 0.45f, cy - r * 0.55f, r * 0.2f, r * 0.12f, 140);
        // feet
        KawaiiArt.line(c, cx - r * 0.3f, cy + r * 0.92f, cx - r * 0.3f, sillTop, d(4f), 0xFFFFB347);
        KawaiiArt.line(c, cx + r * 0.1f, cy + r * 0.92f, cx + r * 0.1f, sillTop, d(4f), 0xFFFFB347);
    }

    private Path tuft(float x, float y, float r) {
        Path q = scratch;
        q.reset();
        q.moveTo(x - r, y + r * 0.6f);
        q.quadTo(x - r * 0.6f, y - r * 0.9f, x, y - r * 1.2f);
        q.quadTo(x + r * 0.5f, y - r * 0.6f, x + r, y + r * 0.6f);
        q.close();
        return q;
    }

    // ============================================================ curtains
    private float cy(float py) {
        float top = t(275f), bot = b(735f);
        return top + (py - 275f) / (735f - 275f) * (bot - top);
    }

    private void drawCurtains(Canvas c, float time) {
        // rod
        float rodY = t(262f);
        KawaiiArt.rr(c, x(205f), rodY - d(9f), x(930f), rodY + d(9f), d(9f), 0xFFC8773F, 0xFF8A4F2E, d(4f));
        KawaiiArt.shine(c, x(560f), rodY - d(4f), d(150f), d(2.5f), 110);
        KawaiiArt.circ(c, x(204f), rodY, d(21f), 0xFFD58A4D, 0xFF8A4F2E, d(4f));
        KawaiiArt.circ(c, x(931f), rodY, d(21f), 0xFFD58A4D, 0xFF8A4F2E, d(4f));
        KawaiiArt.shine(c, x(198f), rodY - d(7f), d(6f), d(4f), 140);
        KawaiiArt.shine(c, x(925f), rodY - d(7f), d(6f), d(4f), 140);

        // left panel
        curtain(c, 232f, 395f, 318f, 335f, 1, time);
        // right panel
        curtain(c, 737f, 905f, 820f, 806f, -1, time);

        // curtain rings (drawn over the rod)
        for (int i = 0; i < 5; i++) {
            ring(c, x(245f + i * 36f), rodY);
            ring(c, x(752f + i * 36f), rodY);
        }
    }

    private void ring(Canvas c, float xx, float yy) {
        KawaiiArt.P.setStyle(Paint.Style.STROKE);
        KawaiiArt.P.setStrokeWidth(d(4f));
        KawaiiArt.P.setColor(0xFF8A4F2E);
        oval.set(xx - d(7f), yy - d(11f), xx + d(7f), yy + d(13f));
        c.drawOval(oval, KawaiiArt.P);
        KawaiiArt.P.setStyle(Paint.Style.FILL);
    }

    /**
     * One curtain panel. outerX = wall side, innerX = window side at the top, tieInner = window side at the
     * tie-back, flareInner = window side at the hem. dir = +1 for the left panel, -1 for the right.
     */
    private void curtain(Canvas c, float outerX, float innerX, float tieInner, float flareInner, int dir, float time) {
        float top = cy(275f), tie = cy(600f), bot = cy(735f);
        float ox1 = x(outerX), ix = x(innerX), ti = x(tieInner), fi = x(flareInner);
        float sway = (float) Math.sin(time * 0.8f) * d(1.5f) * dir;
        tmp.reset();
        tmp.moveTo(ox1, top);
        tmp.lineTo(ix, top);
        tmp.cubicTo(ix + d(2f) * dir, top + (tie - top) * 0.45f, ti + d(14f) * dir, tie - d(60f), ti, tie);
        tmp.cubicTo(ti - d(6f) * dir, tie + (bot - tie) * 0.35f, fi + sway, bot - d(40f), fi, bot);
        tmp.lineTo(ox1, bot);
        tmp.close();
        Path panel = panelPath;
        panel.reset();
        panel.addPath(tmp);
        KawaiiArt.fillStroke(c, panel, CURTAIN, 0xFFC4507F, d(6f));

        int sv = c.save();
        c.clipPath(panel);
        // soft folds
        float wid = Math.abs(innerX - outerX);
        for (int i = 1; i <= 3; i++) {
            float fx = x(outerX + dir * wid * (i / 4f));
            KawaiiArt.P.setStyle(Paint.Style.FILL);
            KawaiiArt.P.setColor(Color.argb(46, 190, 50, 110));
            c.drawRect(Math.min(fx, fx + d(14f) * dir), top, Math.max(fx, fx + d(14f) * dir), bot, KawaiiArt.P);
            KawaiiArt.P.setColor(Color.argb(55, 255, 255, 255));
            c.drawRect(Math.min(fx - d(10f) * dir, fx - d(4f) * dir), top, Math.max(fx - d(10f) * dir, fx - d(4f) * dir), bot, KawaiiArt.P);
        }
        // white hearts
        float cell = d(46f);
        float hsz = d(7.5f);
        int row = 0;
        for (float yy = top + cell * 0.4f; yy < bot; yy += cell * 0.82f, row++) {
            for (float xx = Math.min(ox1, fi) + cell * ((row % 2 == 0) ? 0.35f : 0.8f); xx < Math.max(ix, ox1) + cell; xx += cell) {
                KawaiiArt.heart(c, xx, yy, hsz, 0xCCFFFFFF);
            }
        }
        c.restoreToCount(sv);

        // dust bunnies stuck on the curtain
        if (dir == 1) {
            KawaiiArt.dustBunny(c, x(255f), cy(358f), d(17f), time);
            KawaiiArt.dustBunny(c, x(305f), cy(325f), d(17f), time + 1f);
            KawaiiArt.dustBunny(c, x(335f), cy(445f), d(17f), time + 2f);
            KawaiiArt.dustBunny(c, x(250f), cy(497f), d(17f), time + 3f);
        } else {
            KawaiiArt.dustBunny(c, x(822f), cy(325f), d(17f), time + 1.5f);
            KawaiiArt.dustBunny(c, x(872f), cy(360f), d(17f), time + 2.5f);
            KawaiiArt.dustBunny(c, x(795f), cy(443f), d(17f), time + 0.5f);
            KawaiiArt.dustBunny(c, x(878f), cy(497f), d(17f), time + 3.5f);
        }

        // tie-back sash
        float sx0 = Math.min(ox1, ti) - d(2f), sx1 = Math.max(ox1, ti) + d(2f);
        KawaiiArt.rr(c, sx0, tie - d(15f), sx1, tie + d(15f), d(12f), 0xFFFF9EC1, 0xFFC4507F, d(4f));
        KawaiiArt.heart(c, (sx0 + sx1) / 2f, tie, d(9f), 0xFFFFFFFF);
        KawaiiArt.shine(c, (sx0 + sx1) / 2f - d(18f), tie - d(8f), d(12f), d(3f), 140);
    }

    // ============================================================ wall shelves + plants
    private void drawShelves(Canvas c, float time) {
        float l = x(925f), r = x(1163f);
        float y1 = t(448f), y2 = t(558f);
        // bracket shadows
        KawaiiArt.rr(c, l, y1, r, y1 + d(19f), d(7f), WOOD_LT, 0xFF8A4F2E, d(4f));
        KawaiiArt.rr(c, l, y2, r, y2 + d(19f), d(7f), WOOD_LT, 0xFF8A4F2E, d(4f));

        // books on top shelf
        int[] bc = {0xFF5CB8B2, 0xFFEE7890, 0xFF78AEDC, 0xFFF3C25A};
        float[] bh = {84f, 76f, 66f, 72f};
        float bx = 938f;
        for (int i = 0; i < 4; i++) {
            float bw = 23f;
            if (i == 3) {
                c.save();
                c.rotate(18f, x(bx + bw), y1);
                KawaiiArt.rr(c, x(bx + bw), y1 - d(bh[i]), x(bx + bw * 2f), y1, d(4f), bc[i], KawaiiArt.edge(bc[i], 0.4f), d(3.5f));
                c.restore();
            } else {
                KawaiiArt.rr(c, x(bx), y1 - d(bh[i]), x(bx + bw), y1, d(4f), bc[i], KawaiiArt.edge(bc[i], 0.4f), d(3.5f));
                KawaiiArt.line(c, x(bx + 4f), y1 - d(bh[i]) + d(14f), x(bx + bw - 4f), y1 - d(bh[i]) + d(14f), d(2f), 0x99FFFFFF);
            }
            bx += bw + 2f;
        }
        // little blue plush spirit
        blobPlush(c, x(1068f), y1 - d(26f), d(24f), 0xFF9ED2EA, time);

        // top shelf: tiny blimp floating above the lower shelf
        float bxm = x(1078f), bym = t(482f) + (float) Math.sin(time * 1.3f) * d(3f);
        KawaiiArt.oval(c, bxm - d(30f), bym - d(14f), bxm + d(30f), bym + d(14f), 0xFFFFE3C9, 0xFFC98A5E, d(3.5f));
        KawaiiArt.rr(c, bxm - d(7f), bym + d(11f), bxm + d(7f), bym + d(20f), d(3f), 0xFFE9788F, 0xFFA84E64, d(2.5f));
        KawaiiArt.line(c, bxm - d(14f), bym - d(11f), bxm - d(10f), bym + d(11f), d(3f), 0xFFE9788F);
        KawaiiArt.line(c, bxm + d(6f), bym - d(13f), bxm + d(10f), bym + d(12f), d(3f), 0xFFE9788F);
        KawaiiArt.fillStroke(c, finTri(bxm + d(30f), bym), 0xFFE9788F, 0xFFA84E64, d(2.5f));

        // lower shelf: black cat, mint plush, orange tabby loaf
        cat(c, x(988f), y2, d(1f));
        blobPlush(c, x(944f), y2 - d(19f), d(17f), 0xFF9BDDC2, time + 1f);
        tabbyLoaf(c, x(1105f), y2, time);

        // hanging decorations under the lower shelf
        float sy = y2 + d(19f);
        hang(c, x(968f), sy, t(655f), 0, time);
        hang(c, x(1027f), sy, t(625f), 1, time);
        hang(c, x(1075f), sy, t(612f), 0, time + 1f);
        hang(c, x(1122f), sy, t(662f), 1, time + 2f);
    }

    private Path finTri(float x, float y) {
        Path q = scratch;
        q.reset();
        q.moveTo(x - d(4f), y - d(8f));
        q.lineTo(x + d(13f), y - d(14f));
        q.lineTo(x + d(13f), y + d(14f));
        q.lineTo(x - d(4f), y + d(8f));
        q.close();
        return q;
    }

    private void hang(Canvas c, float xx, float from, float toY, int type, float time) {
        float sway = (float) Math.sin(time * 1.1f + xx) * d(2f);
        KawaiiArt.line(c, xx, from, xx + sway, toY, d(2f), 0xFF9A6A55);
        if (type == 0) KawaiiArt.star(c, xx + sway, toY + d(10f), d(16f), 0xFFFFD45E, 0xFFE0A23A, d(2f));
        else KawaiiArt.dustBunny(c, xx + sway, toY + d(8f), d(17f), time);
    }

    private void blobPlush(Canvas c, float cx, float cy, float r, int col, float time) {
        int edge = KawaiiArt.edge(col, 0.4f);
        KawaiiArt.oval(c, cx - r, cy - r * 0.9f, cx + r, cy + r, col, edge, d(3.5f));
        KawaiiArt.fillStroke(c, ear(cx - r * 0.55f, cy - r * 0.85f, r * 0.32f, -1), col, edge, d(3f));
        KawaiiArt.fillStroke(c, ear(cx + r * 0.55f, cy - r * 0.85f, r * 0.32f, 1), col, edge, d(3f));
        KawaiiArt.plushFace(c, cx, cy - r * 0.08f, r * 0.62f, INK);
        KawaiiArt.shine(c, cx - r * 0.45f, cy - r * 0.55f, r * 0.2f, r * 0.12f, 150);
    }

    private Path ear(float x, float y, float r, int dir) {
        Path q = scratch;
        q.reset();
        q.moveTo(x - r, y + r * 0.7f);
        q.quadTo(x - r * 0.2f * dir, y - r * 1.5f, x + r, y + r * 0.7f);
        q.close();
        return q;
    }

    private void cat(Canvas c, float cx, float shelfY, float k) {
        int body = 0xFF433B4D, edge = 0xFF2A2433;
        float r = d(26f);
        // tail
        tmp.reset();
        tmp.moveTo(cx + r * 0.8f, shelfY - r * 0.2f);
        tmp.quadTo(cx + r * 2.0f, shelfY - r * 0.2f, cx + r * 1.7f, shelfY - r * 1.6f);
        KawaiiArt.strokePath(c, tmp, d(8f), edge);
        KawaiiArt.strokePath(c, tmp, d(4f), body);
        // body
        KawaiiArt.oval(c, cx - r * 1.1f, shelfY - r * 2.25f, cx + r * 1.1f, shelfY, body, edge, d(3.5f));
        // head
        float hy = shelfY - r * 2.55f;
        KawaiiArt.fillStroke(c, ear(cx - r * 0.7f, hy - r * 0.55f, r * 0.45f, -1), body, edge, d(3f));
        KawaiiArt.fillStroke(c, ear(cx + r * 0.7f, hy - r * 0.55f, r * 0.45f, 1), body, edge, d(3f));
        KawaiiArt.oval(c, cx - r * 1.05f, hy - r * 0.85f, cx + r * 1.05f, hy + r * 0.8f, body, edge, d(3.5f));
        // big sparkly eyes
        KawaiiArt.oval(c, cx - r * 0.62f, hy - r * 0.2f, cx - r * 0.2f, hy + r * 0.34f, 0xFFFFE27A, edge, d(2f));
        KawaiiArt.oval(c, cx + r * 0.2f, hy - r * 0.2f, cx + r * 0.62f, hy + r * 0.34f, 0xFFFFE27A, edge, d(2f));
        KawaiiArt.circ(c, cx - r * 0.41f, hy + r * 0.07f, r * 0.1f, 0xFF2A2433, 0, 0f);
        KawaiiArt.circ(c, cx + r * 0.41f, hy + r * 0.07f, r * 0.1f, 0xFF2A2433, 0, 0f);
        KawaiiArt.shine(c, cx - r * 0.45f, hy - r * 0.02f, r * 0.05f, r * 0.05f, 240);
        KawaiiArt.shine(c, cx + r * 0.37f, hy - r * 0.02f, r * 0.05f, r * 0.05f, 240);
        KawaiiArt.blush(c, cx - r * 0.78f, hy + r * 0.5f, r * 0.2f, Color.argb(110, 255, 120, 160));
        KawaiiArt.blush(c, cx + r * 0.78f, hy + r * 0.5f, r * 0.2f, Color.argb(110, 255, 120, 160));
        KawaiiArt.shine(c, cx - r * 0.5f, hy - r * 0.6f, r * 0.22f, r * 0.1f, 90);
    }

    private void tabbyLoaf(Canvas c, float cx, float shelfY, float time) {
        int body = 0xFFF5A95C, edge = 0xFFB86A2E;
        float w2 = d(52f), hh = d(36f);
        KawaiiArt.oval(c, cx - w2, shelfY - hh * 1.9f, cx + w2, shelfY, body, edge, d(3.5f));
        // stripes
        for (int i = -2; i <= 2; i++) {
            KawaiiArt.line(c, cx + i * d(18f) + d(8f), shelfY - hh * 1.8f, cx + i * d(18f) + d(4f), shelfY - hh * 1.35f, d(4f), 0xFFE0873C);
        }
        // head
        float hx = cx - w2 * 0.6f, hy = shelfY - hh * 1.25f;
        KawaiiArt.fillStroke(c, ear(hx - d(14f), hy - d(16f), d(11f), -1), body, edge, d(3f));
        KawaiiArt.fillStroke(c, ear(hx + d(14f), hy - d(16f), d(11f), 1), body, edge, d(3f));
        KawaiiArt.oval(c, hx - d(24f), hy - d(18f), hx + d(24f), hy + d(17f), body, edge, d(3.5f));
        KawaiiArt.plushFace(c, hx, hy, d(17f), INK);
        // little paws
        KawaiiArt.oval(c, cx - w2 * 0.85f, shelfY - d(8f), cx - w2 * 0.45f, shelfY + d(2f), 0xFFFFF0DC, edge, d(2.5f));
        KawaiiArt.oval(c, cx - w2 * 0.35f, shelfY - d(8f), cx + w2 * 0.05f, shelfY + d(2f), 0xFFFFF0DC, edge, d(2.5f));
    }

    private void drawHangingPlants(Canvas c, float time) {
        hangingPot(c, x(980f), t(232f), t(288f), 0, time);
        hangingPot(c, x(1095f), t(232f), t(318f), 1, time + 1.2f);
    }

    private void hangingPot(Canvas c, float cx, float hookY, float potTop, int type, float time) {
        float sway = (float) Math.sin(time * 0.9f + cx) * d(2.5f);
        float pw = d(30f), ph = d(36f);
        KawaiiArt.line(c, cx, hookY, cx - pw * 0.8f + sway * 0.2f, potTop, d(2.5f), 0xFF9A6A55);
        KawaiiArt.line(c, cx, hookY, cx + pw * 0.8f + sway * 0.2f, potTop, d(2.5f), 0xFF9A6A55);
        c.save();
        c.translate(sway * 0.4f, 0f);
        int leaf = 0xFF6CC27B, leafDk = 0xFF3F8D55;
        if (type == 0) {
            // trailing vine with pink flowers
            for (int i = 0; i < 6; i++) {
                float lx = cx - pw * 0.8f + i * pw * 0.32f;
                float len = d(34f + (i % 3) * 18f);
                tmp.reset();
                tmp.moveTo(lx, potTop + ph * 0.5f);
                tmp.quadTo(lx + d(6f) * ((i % 2 == 0) ? 1 : -1), potTop + ph + len * 0.5f, lx, potTop + ph + len);
                KawaiiArt.strokePath(c, tmp, d(3.5f), leafDk);
                KawaiiArt.circ(c, lx, potTop + ph + len, d(7f), leaf, leafDk, d(2.5f));
            }
            for (int i = 0; i < 5; i++) {
                float fx = cx - pw * 0.7f + i * pw * 0.35f;
                float fy = potTop - d(8f) - (i % 2) * d(10f);
                KawaiiArt.circ(c, fx, fy, d(11f), 0xFFF6709C, 0xFFC03D6E, d(2.5f));
                KawaiiArt.circ(c, fx, fy, d(4f), 0xFFFFE27A, 0, 0f);
            }
            KawaiiArt.circ(c, cx, potTop - d(6f), d(18f), leaf, leafDk, d(3f));
        } else {
            // big heart-leaf plant
            for (int i = 0; i < 7; i++) {
                float a = -2.4f + i * 0.55f;
                float lx = cx + (float) Math.cos(a) * pw * 1.4f;
                float ly = potTop - d(4f) + (float) Math.sin(a) * pw * 0.9f + d(24f);
                KawaiiArt.heartOutlined(c, lx, ly, d(14f), (i % 2 == 0) ? 0xFF5FBE74 : 0xFF7BCF86, leafDk, d(2.5f));
            }
            for (int i = 0; i < 4; i++) {
                float lx = cx - pw * 0.7f + i * pw * 0.48f;
                float ly = potTop + ph + d(18f + 14f * (i % 2)) + d(8f);
                KawaiiArt.line(c, lx, potTop + ph, lx, ly - d(8f), d(3f), leafDk);
                KawaiiArt.heartOutlined(c, lx, ly, d(12f), (i % 2 == 0) ? 0xFF6BC77E : 0xFF4FB06A, leafDk, d(2.5f));
            }
        }
        // pot
        tmp.reset();
        tmp.moveTo(cx - pw, potTop);
        tmp.lineTo(cx + pw, potTop);
        tmp.quadTo(cx + pw, potTop + ph, cx + pw * 0.55f, potTop + ph);
        tmp.lineTo(cx - pw * 0.55f, potTop + ph);
        tmp.quadTo(cx - pw, potTop + ph, cx - pw, potTop);
        tmp.close();
        int pot = (type == 0) ? 0xFFFFC7A8 : 0xFFFFE3A6;
        KawaiiArt.fillStroke(c, tmp, pot, KawaiiArt.edge(pot, 0.5f), d(4f));
        KawaiiArt.rr(c, cx - pw - d(3f), potTop - d(5f), cx + pw + d(3f), potTop + d(7f), d(5f), KawaiiArt.edge(pot, 0.12f), KawaiiArt.edge(pot, 0.5f), d(3.5f));
        KawaiiArt.heart(c, cx, potTop + ph * 0.55f, d(8f), 0xFFF6709C);
        KawaiiArt.shine(c, cx - pw * 0.5f, potTop + ph * 0.3f, d(4f), d(9f), 150);
        c.restore();
    }

    // ============================================================ rug
    private void drawRug(Canvas c) {
        float cx = x(690f), cy = b(1150f), rx = d(420f), ry = d(160f);
        int[] ring = {0xFFEC6A66, 0xFFFFEBCB, 0xFFEC6A66, 0xFFFFEBCB, 0xFFF1827C};
        float[] sc = {1f, 0.86f, 0.70f, 0.55f, 0.38f};
        // soft shadow
        KawaiiArt.oval(c, cx - rx - d(8f), cy - ry + d(8f), cx + rx + d(8f), cy + ry + d(10f), Color.argb(36, 140, 80, 60), 0, 0f);
        for (int i = 0; i < ring.length; i++) {
            KawaiiArt.oval(c, cx - rx * sc[i], cy - ry * sc[i], cx + rx * sc[i], cy + ry * sc[i], ring[i], 0xFFB85A52, d(3f));
        }
        // stitched dashes on the cream rings
        KawaiiArt.P.setStyle(Paint.Style.STROKE);
        KawaiiArt.P.setStrokeCap(Paint.Cap.ROUND);
        KawaiiArt.P.setStrokeWidth(d(3f));
        KawaiiArt.P.setColor(0x88E9A59A);
        for (int i = 0; i < 34; i++) {
            double a = i * Math.PI * 2 / 34.0;
            float k = 0.93f;
            float px = cx + (float) Math.cos(a) * rx * k, py = cy + (float) Math.sin(a) * ry * k;
            c.drawPoint(px, py, KawaiiArt.P);
        }
        KawaiiArt.P.setStyle(Paint.Style.FILL);
        KawaiiArt.P.setStrokeCap(Paint.Cap.BUTT);
        KawaiiArt.heart(c, cx - rx * 0.62f, cy - ry * 0.05f, d(10f), 0xFFFFC2CF);
        KawaiiArt.heart(c, cx + rx * 0.62f, cy - ry * 0.05f, d(10f), 0xFFFFC2CF);
    }

    // ============================================================ TV
    private void drawTv(Canvas c) {
        // stand
        float stTop = b(842f);
        KawaiiArt.rr(c, x(890f), b(878f), x(1190f), b(978f), d(12f), TEAL, 0xFF3F9A8A, d(5f));
        // open shelf interior
        KawaiiArt.rr(c, x(910f), b(892f), x(1170f), b(962f), d(8f), 0xFF58B8A4, 0xFF3F9A8A, d(3f));
        // game console + books
        KawaiiArt.rr(c, x(940f), b(912f), x(1075f), b(945f), d(6f), 0xFF8E8AA8, 0xFF5F5B7C, d(3f));
        KawaiiArt.rr(c, x(955f), b(922f), x(1020f), b(928f), d(3f), 0xFF5F5B7C, 0, 0f);
        KawaiiArt.circ(c, x(1055f), b(928f), d(5f), 0xFFFF9EC1, 0, 0f);
        int[] bk = {0xFFF38FA8, 0xFFFFD45E, 0xFF7CC7F0, 0xFFB79CE8, 0xFFF3A56B};
        for (int i = 0; i < bk.length; i++) {
            KawaiiArt.rr(c, x(1090f + i * 15f), b(906f), x(1102f + i * 15f), b(962f), d(3f), bk[i], KawaiiArt.edge(bk[i], 0.4f), d(2.5f));
        }
        // pink top plank + legs
        KawaiiArt.rr(c, x(876f), stTop, x(1204f), b(882f), d(11f), PINK, 0xFFC25773, d(5f));
        KawaiiArt.shine(c, x(980f), stTop + d(8f), d(60f), d(3f), 130);
        legAngled(c, x(912f), b(978f), -1);
        legAngled(c, x(1172f), b(978f), 1);

        // TV body
        KawaiiArt.rr(c, x(925f), b(706f), x(1181f), b(852f), d(24f), TEAL, 0xFF3F9A8A, d(6f));
        KawaiiArt.shine(c, x(960f), b(719f), d(32f), d(4f), 140);
        // screen
        KawaiiArt.rr(c, x(947f), b(728f), x(1085f), b(836f), d(14f), 0xFF3A4D54, 0xFF2A3A40, d(5f));
        KawaiiArt.shine(c, x(990f), b(748f), d(26f), d(5f), 70);
        KawaiiArt.shine(c, x(967f), b(780f), d(6f), d(16f), 40);
        // right panel
        KawaiiArt.rr(c, x(1098f), b(728f), x(1166f), b(836f), d(10f), 0xFF9BE3D2, 0xFF3F9A8A, d(4f));
        KawaiiArt.circ(c, x(1132f), b(752f), d(15f), PINK, 0xFFC25773, d(3.5f));
        KawaiiArt.circ(c, x(1132f), b(790f), d(15f), PINK, 0xFFC25773, d(3.5f));
        KawaiiArt.line(c, x(1132f), b(742f), x(1132f), b(752f), d(3f), 0xFFC25773);
        KawaiiArt.line(c, x(1140f), b(792f), x(1132f), b(790f), d(3f), 0xFFC25773);
        for (int i = 0; i < 3; i++) {
            KawaiiArt.line(c, x(1112f), b(812f + i * 8f), x(1152f), b(812f + i * 8f), d(3f), 0xFF3F9A8A);
        }
        // tiny feet
        KawaiiArt.rr(c, x(960f), b(850f), x(995f), b(862f), d(5f), PINK, 0xFFC25773, d(3f));
        KawaiiArt.rr(c, x(1110f), b(850f), x(1145f), b(862f), d(5f), PINK, 0xFFC25773, d(3f));
    }

    private void legAngled(Canvas c, float x1, float y1, int dir) {
        tmp.reset();
        tmp.moveTo(x1 - d(9f), y1);
        tmp.lineTo(x1 + d(9f), y1);
        tmp.lineTo(x1 + d(9f) + d(14f) * dir, y1 + d(34f));
        tmp.lineTo(x1 - d(9f) + d(14f) * dir, y1 + d(34f));
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, PINK, 0xFFC25773, d(4f));
    }

    // ============================================================ side table + lamp
    private void drawTableAndLamp(Canvas c) {
        // floor shadow
        KawaiiArt.oval(c, x(770f), b(905f), x(905f), b(930f), Color.argb(40, 140, 80, 60), 0, 0f);
        // table legs + top
        KawaiiArt.line(c, x(815f), b(795f), x(803f), b(915f), d(10f), 0xFF8F5A38);
        KawaiiArt.line(c, x(865f), b(795f), x(880f), b(915f), d(10f), 0xFF8F5A38);
        KawaiiArt.line(c, x(815f), b(795f), x(803f), b(915f), d(5f), WOOD);
        KawaiiArt.line(c, x(865f), b(795f), x(880f), b(915f), d(5f), WOOD);
        KawaiiArt.oval(c, x(778f), b(762f), x(902f), b(800f), WOOD_LT, 0xFF8A4F2E, d(5f));
        KawaiiArt.shine(c, x(820f), b(773f), d(18f), d(3f), 140);

        // lamp base (pink ball) + stem
        float lx = x(812f);
        KawaiiArt.rr(c, lx - d(5f), b(740f), lx + d(5f), b(768f), d(3f), 0xFFE0749C, 0, 0f);
        KawaiiArt.oval(c, lx - d(25f), b(746f), lx + d(25f), b(784f), 0xFFF3779F, 0xFFB94A74, d(4f));
        KawaiiArt.shine(c, lx - d(9f), b(757f), d(7f), d(4f), 170);
        // shade
        tmp.reset();
        tmp.moveTo(lx - d(52f), b(742f));
        tmp.lineTo(lx + d(52f), b(742f));
        tmp.lineTo(lx + d(34f), b(662f));
        tmp.lineTo(lx - d(34f), b(662f));
        tmp.close();
        Path shade = shadePath;
        shade.reset();
        shade.addPath(tmp);
        KawaiiArt.fillStroke(c, shade, 0xFFFFC3D8, 0xFFC25773, d(5f));
        int sv = c.save();
        c.clipPath(shade);
        KawaiiArt.heartField(c, lx - d(55f), b(660f), lx + d(55f), b(745f), d(26f), d(6f), 0xFFF3779F, 0f);
        c.restoreToCount(sv);
        KawaiiArt.oval(c, lx - d(36f), b(656f), lx + d(36f), b(670f), 0xFFFFE6EE, 0xFFC25773, d(3.5f));
        KawaiiArt.shine(c, lx - d(22f), b(690f), d(4f), d(14f), 120);
    }

    // ============================================================ sofa + cushions
    private void drawSofa(Canvas c, float time) {
        // soft floor shadow
        KawaiiArt.oval(c, x(180f), b(1072f), x(880f), b(1120f), Color.argb(44, 140, 80, 60), 0, 0f);
        // legs
        int leg = 0xFF7A4A30;
        KawaiiArt.rr(c, x(205f), b(1060f), x(245f), b(1104f), d(10f), leg, 0xFF5A3322, d(3.5f));
        KawaiiArt.rr(c, x(350f), b(1060f), x(390f), b(1104f), d(10f), leg, 0xFF5A3322, d(3.5f));
        KawaiiArt.rr(c, x(760f), b(1000f), x(800f), b(1040f), d(10f), leg, 0xFF5A3322, d(3.5f));

        // back
        KawaiiArt.rr(c, x(205f), b(688f), x(770f), b(935f), d(70f), SOFA, SOFA_DK, d(6f));
        KawaiiArt.shine(c, x(330f), b(710f), d(70f), d(5f), 90);
        // right arm
        KawaiiArt.rr(c, x(700f), b(800f), x(868f), b(1015f), d(60f), 0xFFB95E2F, SOFA_DK, d(6f));
        KawaiiArt.shine(c, x(785f), b(822f), d(36f), d(4f), 80);
        // seat cushion
        KawaiiArt.rr(c, x(235f), b(868f), x(790f), b(1008f), d(46f), SOFA_LT, SOFA_DK, d(6f));
        KawaiiArt.line(c, x(300f), b(990f), x(740f), b(990f), d(3f), 0x40803E1B);
        KawaiiArt.shine(c, x(420f), b(886f), d(110f), d(5f), 80);
        // left arm (front)
        KawaiiArt.rr(c, x(172f), b(842f), x(412f), b(1072f), d(70f), 0xFFB95E2F, SOFA_DK, d(6f));
        KawaiiArt.rr(c, x(172f), b(842f), x(412f), b(930f), d(44f), 0xFFD27B3E, SOFA_DK, d(5f));
        KawaiiArt.shine(c, x(255f), b(862f), d(48f), d(5f), 100);
        // buttons / stitches
        KawaiiArt.circ(c, x(292f), b(978f), d(4f), SOFA_DK, 0, 0f);
        KawaiiArt.circ(c, x(292f), b(1030f), d(4f), SOFA_DK, 0, 0f);

        // cushions on the seat
        c.save();
        c.rotate(-10f, x(330f), b(835f));
        pillowBear(c, x(330f), b(832f));
        c.restore();
        flameCushion(c, x(447f), b(852f), time);
        girlCushion(c, x(517f), b(818f));
        dollSitting(c, x(612f), b(842f));
        c.save();
        c.rotate(7f, x(706f), b(806f));
        KawaiiArt.rr(c, x(646f), b(752f), x(766f), b(860f), d(34f), 0xFFFFFFFF, 0xFFC9C0CE, d(5f));
        KawaiiArt.shine(c, x(676f), b(776f), d(14f), d(7f), 220);
        KawaiiArt.rr(c, x(646f), b(820f), x(766f), b(860f), d(30f), 0x22B9B2D8, 0, 0f);
        c.restore();
    }

    private void pillowBear(Canvas c, float cx, float cy) {
        float pw = d(76f), ph = d(54f);
        int base = 0xFFB4CDEB;
        KawaiiArt.rr(c, cx - pw, cy - ph, cx + pw, cy + ph, d(30f), base, 0xFF6F8CB8, d(5f));
        // round bear
        int fur = 0xFF8FA9D2, edge = 0xFF5F7CAA;
        KawaiiArt.fillStroke(c, ear(cx - pw * 0.42f, cy - ph * 0.62f, d(15f), -1), fur, edge, d(3.5f));
        KawaiiArt.fillStroke(c, ear(cx + pw * 0.42f, cy - ph * 0.62f, d(15f), 1), fur, edge, d(3.5f));
        KawaiiArt.oval(c, cx - pw * 0.62f, cy - ph * 0.82f, cx + pw * 0.62f, cy + ph * 0.8f, fur, edge, d(4f));
        KawaiiArt.oval(c, cx - pw * 0.38f, cy - ph * 0.02f, cx + pw * 0.38f, cy + ph * 0.7f, 0xFFF3F6FC, 0, 0f);
        KawaiiArt.circ(c, cx - pw * 0.26f, cy - ph * 0.28f, d(5f), INK, 0, 0f);
        KawaiiArt.circ(c, cx + pw * 0.26f, cy - ph * 0.28f, d(5f), INK, 0, 0f);
        KawaiiArt.shine(c, cx - pw * 0.28f, cy - ph * 0.32f, d(1.8f), d(1.8f), 240);
        KawaiiArt.shine(c, cx + pw * 0.24f, cy - ph * 0.32f, d(1.8f), d(1.8f), 240);
        KawaiiArt.oval(c, cx - d(6f), cy - ph * 0.14f, cx + d(6f), cy - ph * 0.04f, INK, 0, 0f);
        KawaiiArt.blush(c, cx - pw * 0.46f, cy - ph * 0.05f, d(8f), Color.argb(120, 255, 120, 160));
        KawaiiArt.blush(c, cx + pw * 0.46f, cy - ph * 0.05f, d(8f), Color.argb(120, 255, 120, 160));
        KawaiiArt.heart(c, cx, cy + ph * 0.38f, d(8f), 0xFFFFB6CC);
    }

    private void flameCushion(Canvas c, float cx, float cy, float time) {
        float k = d(1f);
        float sw = (float) Math.sin(time * 4f) * d(2f);
        tmp.reset();
        tmp.moveTo(cx, cy + 52f * k);
        tmp.cubicTo(cx - 62f * k, cy + 48f * k, cx - 56f * k, cy - 4f * k, cx - 26f * k, cy - 34f * k + sw);
        tmp.cubicTo(cx - 24f * k, cy - 14f * k, cx - 14f * k, cy - 24f * k, cx - 8f * k, cy - 54f * k + sw);
        tmp.cubicTo(cx + 10f * k, cy - 36f * k, cx + 14f * k, cy - 18f * k, cx + 20f * k, cy - 30f * k);
        tmp.cubicTo(cx + 62f * k, cy - 4f * k, cx + 62f * k, cy + 48f * k, cx, cy + 52f * k);
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, 0xFFFF7F3F, 0xFFC4471F, d(5f));
        KawaiiArt.oval(c, cx - 30f * k, cy - 2f * k, cx + 30f * k, cy + 44f * k, 0xFFFFC85F, 0, 0f);
        KawaiiArt.circ(c, cx - 14f * k, cy + 16f * k, 7f * k, 0xFFFFFFFF, INK, d(2.5f));
        KawaiiArt.circ(c, cx + 14f * k, cy + 16f * k, 7f * k, 0xFFFFFFFF, INK, d(2.5f));
        KawaiiArt.circ(c, cx - 13f * k, cy + 17f * k, 3f * k, INK, 0, 0f);
        KawaiiArt.circ(c, cx + 15f * k, cy + 17f * k, 3f * k, INK, 0, 0f);
        tmp.reset();
        tmp.moveTo(cx - 9f * k, cy + 30f * k);
        tmp.quadTo(cx, cy + 38f * k, cx + 9f * k, cy + 30f * k);
        KawaiiArt.strokePath(c, tmp, d(3f), INK);
        KawaiiArt.blush(c, cx - 26f * k, cy + 28f * k, 7f * k, Color.argb(130, 255, 90, 110));
        KawaiiArt.blush(c, cx + 26f * k, cy + 28f * k, 7f * k, Color.argb(130, 255, 90, 110));
    }

    private void girlCushion(Canvas c, float cx, float cy) {
        float pw = d(68f), ph = d(52f);
        KawaiiArt.rr(c, cx - pw, cy - ph, cx + pw, cy + ph, d(22f), 0xFFE9C092, 0xFFA87A4D, d(5f));
        float k = d(1f);
        // hair (back), face, fringe
        KawaiiArt.oval(c, cx - 42f * k, cy - 44f * k, cx + 42f * k, cy + 40f * k, 0xFF8B5A3C, 0xFF5E3A24, d(3.5f));
        KawaiiArt.oval(c, cx - 33f * k, cy - 30f * k, cx + 33f * k, cy + 38f * k, 0xFFFFDDBC, 0xFFD39B73, d(3f));
        tmp.reset();
        tmp.moveTo(cx - 38f * k, cy - 6f * k);
        tmp.quadTo(cx - 34f * k, cy - 44f * k, cx, cy - 40f * k);
        tmp.quadTo(cx + 34f * k, cy - 44f * k, cx + 38f * k, cy - 6f * k);
        tmp.quadTo(cx + 18f * k, cy - 26f * k, cx, cy - 18f * k);
        tmp.quadTo(cx - 18f * k, cy - 26f * k, cx - 38f * k, cy - 6f * k);
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, 0xFF8B5A3C, 0xFF5E3A24, d(3f));
        KawaiiArt.circ(c, cx - 14f * k, cy + 6f * k, 4.5f * k, INK, 0, 0f);
        KawaiiArt.circ(c, cx + 14f * k, cy + 6f * k, 4.5f * k, INK, 0, 0f);
        KawaiiArt.shine(c, cx - 15.5f * k, cy + 4.5f * k, 1.8f * k, 1.8f * k, 240);
        KawaiiArt.shine(c, cx + 12.5f * k, cy + 4.5f * k, 1.8f * k, 1.8f * k, 240);
        KawaiiArt.blush(c, cx - 22f * k, cy + 18f * k, 8f * k, Color.argb(130, 255, 110, 130));
        KawaiiArt.blush(c, cx + 22f * k, cy + 18f * k, 8f * k, Color.argb(130, 255, 110, 130));
        tmp.reset();
        tmp.moveTo(cx - 6f * k, cy + 20f * k);
        tmp.quadTo(cx, cy + 27f * k, cx + 6f * k, cy + 20f * k);
        KawaiiArt.strokePath(c, tmp, d(2.5f), 0xFFB5504F);
    }

    private void dollSitting(Canvas c, float cx, float cy) {
        float k = d(1f);
        int hair = 0xFFEC4A74, hairEdge = 0xFFA82D50, skin = 0xFFFFE0C2, dress = 0xFFFFC94A;
        // dress / body
        KawaiiArt.rr(c, cx - 32f * k, cy - 10f * k, cx + 32f * k, cy + 44f * k, 22f * k, dress, 0xFFC99220, d(4f));
        KawaiiArt.rr(c, cx - 36f * k, cy + 30f * k, cx + 36f * k, cy + 46f * k, 8f * k, 0xFFFFE08A, 0xFFC99220, d(3.5f));
        // arms + feet
        KawaiiArt.oval(c, cx - 46f * k, cy - 2f * k, cx - 26f * k, cy + 24f * k, skin, 0xFFC99070, d(3f));
        KawaiiArt.oval(c, cx + 26f * k, cy - 2f * k, cx + 46f * k, cy + 24f * k, skin, 0xFFC99070, d(3f));
        KawaiiArt.oval(c, cx - 28f * k, cy + 42f * k, cx - 4f * k, cy + 58f * k, 0xFFFF9EC1, 0xFFC25773, d(3f));
        KawaiiArt.oval(c, cx + 4f * k, cy + 42f * k, cx + 28f * k, cy + 58f * k, 0xFFFF9EC1, 0xFFC25773, d(3f));
        // buns
        KawaiiArt.circ(c, cx - 38f * k, cy - 46f * k, 17f * k, hair, hairEdge, d(3.5f));
        KawaiiArt.circ(c, cx + 38f * k, cy - 46f * k, 17f * k, hair, hairEdge, d(3.5f));
        KawaiiArt.heart(c, cx - 38f * k, cy - 62f * k, 6f * k, 0xFFFFFFFF);
        KawaiiArt.heart(c, cx + 38f * k, cy - 62f * k, 6f * k, 0xFFFFFFFF);
        // head
        KawaiiArt.circ(c, cx, cy - 36f * k, 33f * k, skin, 0xFFC99070, d(3.5f));
        tmp.reset();
        tmp.moveTo(cx - 33f * k, cy - 40f * k);
        tmp.quadTo(cx - 28f * k, cy - 74f * k, cx, cy - 70f * k);
        tmp.quadTo(cx + 28f * k, cy - 74f * k, cx + 33f * k, cy - 40f * k);
        tmp.quadTo(cx + 16f * k, cy - 54f * k, cx, cy - 48f * k);
        tmp.quadTo(cx - 16f * k, cy - 54f * k, cx - 33f * k, cy - 40f * k);
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, hair, hairEdge, d(3f));
        KawaiiArt.circ(c, cx - 12f * k, cy - 32f * k, 4f * k, INK, 0, 0f);
        KawaiiArt.circ(c, cx + 12f * k, cy - 32f * k, 4f * k, INK, 0, 0f);
        KawaiiArt.shine(c, cx - 13.4f * k, cy - 33.6f * k, 1.6f * k, 1.6f * k, 240);
        KawaiiArt.shine(c, cx + 10.6f * k, cy - 33.6f * k, 1.6f * k, 1.6f * k, 240);
        KawaiiArt.blush(c, cx - 22f * k, cy - 22f * k, 7f * k, Color.argb(140, 255, 100, 130));
        KawaiiArt.blush(c, cx + 22f * k, cy - 22f * k, 7f * k, Color.argb(140, 255, 100, 130));
        tmp.reset();
        tmp.moveTo(cx - 5f * k, cy - 20f * k);
        tmp.quadTo(cx, cy - 14f * k, cx + 5f * k, cy - 20f * k);
        KawaiiArt.strokePath(c, tmp, d(2.5f), 0xFFB5504F);
    }
}
