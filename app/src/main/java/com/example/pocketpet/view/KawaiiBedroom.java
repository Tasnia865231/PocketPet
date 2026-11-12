package com.example.pocketpet.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.example.pocketpet.weather.WeatherType;

/**
 * The sleep scene: Mochi's cosy, dim night bedroom in the Cute Kawaii Storybook style.
 *
 * Every position and colour below was measured from the reference picture (970 x 1024 reference
 * pixels). Furniture stands on the floor (anchored to where Mochi sleeps); the window frame and the
 * picture hang from the top, and the window stretches down to meet the bed on taller screens.
 *
 * The window shows the live weather through {@link KawaiiWeather}; clear night is exactly the reference.
 */
final class KawaiiBedroom {
    private static final float REF_W = 970f, REF_H = 1024f, PET_Y = 757f, BED_X = 562f;

    // ---- palette: already in the dim, lamp-lit tones of the reference ----
    private static final int FRAME     = 0xFF2B1A28;
    private static final int FRAME_LT  = 0xFF3F2838;
    private static final int OUT_PINK  = 0xFF542C4E;
    private static final int PINK_BODY = 0xFF6E3C61;
    private static final int PINK_LT   = 0xFF825676;
    private static final int PINK_DK   = 0xFF6F3A66;
    private static final int DOOR_L    = 0xFF74646F;
    private static final int DOOR_R    = 0xFF817179;
    private static final int TEAL      = 0xFF2F5A6C;
    private static final int TEAL_HI   = 0xFF5A8696;
    private static final int TEAL_DK   = 0xFF1E3C4C;
    private static final int GOLD      = 0xFF9A7432;
    private static final int GOLD_DK   = 0xFF5E431D;
    private static final int WOOD      = 0xFF4A2D2F;
    private static final int WOOD_DK   = 0xFF2B181C;
    private static final int INK       = 0xFF2A1A24;

    private float s, w, h, anchorY;
    private final Path tmp = new Path();
    private final Path tmp2 = new Path();
    private final RectF oval = new RectF();
    private final RectF glass = new RectF();
    private final KawaiiWeather sky = new KawaiiWeather();
    private LinearGradient wallGrad, pillowGrad, headGrad, floorGrad;
    private float gradW, gradH;
    private RadialGradient glow, vig, headGlow;
    private float glowKey, vigKey;

    // ---- mapping (reference pixels -> screen) ----
    private float x(float px) { return w - (REF_W - px) * s; }       // right-aligned: nothing spare beside the lamp
    private float t(float py) { return py * s; }                       // hangs from the top
    private float b(float py) { return anchorY + (py - PET_Y) * s; }   // stands on the floor
    private float d(float v)  { return v * s; }

    void layout(float w, float h) {
        this.w = w; this.h = h;
        s = Math.min(w / REF_W, h / REF_H);
        anchorY = h * 0.74f;
    }

    /** Screen x of the bed centre (Mochi sleeps here). */
    float bedCx(float w, float h) { layout(w, h); return x(BED_X); }

    /** Screen y of Mochi's centre while asleep. */
    float petY(float h) { return h * 0.74f; }

    // ================================================================= back layer
    void drawBack(Canvas c, float w, float h, float time, WeatherType weather, float flash) {
        layout(w, h);
        KawaiiArt.dimAmt = 0f;
        Paint p = KawaiiArt.P;
        p.setShader(null);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);

        float floorTop = Math.min(h, b(886f));

        // ---- wall: dark plum, a little warmer toward the lamp ----
        if (wallGrad == null || gradW != w || gradH != h) {
            wallGrad = new LinearGradient(0f, 0f, w, 0f, new int[]{0xFF2A1E2E, 0xFF33263A, 0xFF433340}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            floorGrad = new LinearGradient(0f, 0f, w, 0f, new int[]{0xFF281A27, 0xFF322326, 0xFF3A2B2B}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            gradW = w; gradH = h;
        }
        p.setColor(0xFF000000);
        p.setShader(wallGrad);
        c.drawRect(0f, 0f, w, floorTop + 1f, p);
        p.setShader(null);
        // fine dotted wallpaper
        p.setColor(0x16FFFFFF);
        float cell = d(30f);
        int row = 0;
        for (float yy = cell * 0.5f; yy < floorTop; yy += cell, row++) {
            for (float xx = ((row % 2 == 0) ? 0f : cell * 0.5f) + cell * 0.5f; xx < w; xx += cell) {
                c.drawCircle(xx, yy, Math.max(0.9f, d(1.3f)), p);
            }
        }

        // ---- floor: dark boards with brick-style joints ----
        p.setColor(0xFF000000);
        p.setShader(floorGrad);
        c.drawRect(0f, floorTop, w, h, p);
        p.setShader(null);
        p.setColor(0xFF3A2834);
        c.drawRect(0f, floorTop, w, floorTop + d(10f), p);               // skirting
        p.setColor(0x33000000);
        int k = 0;
        for (float yy = floorTop + d(10f); yy < h; yy += d(46f), k++) {
            c.drawRect(0f, yy, w, yy + Math.max(1.5f, d(2.5f)), p);
            float off = (k % 2 == 0) ? 0f : d(90f);
            for (float xx = off; xx < w; xx += d(180f)) c.drawRect(xx, yy, xx + Math.max(1.5f, d(2.5f)), yy + d(46f), p);
        }

        drawFrameAndHangers(c, time);
        drawWindow(c, time, weather, flash);
        drawWardrobe(c, time);
        drawLeftPlant(c, time);
        drawRug(c);
        drawNightstandAndLamp(c, time);
        drawBedBack(c);

        // ---- soft vignette: dark at the far-left / top, warm and bright by the lamp ----
        float vx = x(880f), vy = b(690f), vr = Math.max(w, h) * 1.30f;
        float key = vx + vy * 3f + vr * 7f;
        if (vig == null || Math.abs(vigKey - key) > 1f) {
            vig = new RadialGradient(vx, vy, vr, new int[]{0x00100818, 0x00100818, 0x26100818, 0x6A100818}, new float[]{0f, 0.40f, 0.74f, 1f}, Shader.TileMode.CLAMP);
            vigKey = key;
        }
        p.setColor(0xFF000000);
        p.setShader(vig);
        c.drawRect(0f, 0f, w, h, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
    }

    // ================================================================= window (live weather)
    private void drawWindow(Canvas c, float time, WeatherType weather, float flash) {
        float l = x(290f), r = x(840f), top = t(18f);
        float sillTop = b(668f), sillBot = b(706f);
        KawaiiArt.rr(c, l, top, r, sillTop + d(4f), d(7f), FRAME, 0xFF1E1020, d(3f));
        float gl = x(328f), gr = x(806f), gt = t(46f), gb = sillTop;
        glass.set(gl, gt, gr, gb);
        int sv = c.save();
        c.clipRect(glass);
        sky.draw(c, glass, weather, time, flash, weather == WeatherType.CLEAR_NIGHT ? 0f : 0.34f);
        c.restoreToCount(sv);

        // mullions
        float mx = x(561f), my = gt + (gb - gt) * 0.39f;
        KawaiiArt.rr(c, mx - d(8.5f), gt, mx + d(8.5f), gb, d(2f), FRAME, 0xFF1E1020, d(2f));
        KawaiiArt.rr(c, gl, my - d(7f), gr, my + d(7f), d(2f), FRAME, 0xFF1E1020, d(2f));
        KawaiiArt.shine(c, mx - d(3f), gt + d(60f), d(1.6f), d(40f), 26);
        // sill
        KawaiiArt.rr(c, x(305f), sillTop, x(822f), sillBot, d(8f), 0xFF47303A, 0xFF1E1020, d(3f));
        KawaiiArt.shine(c, x(470f), sillTop + d(8f), d(130f), d(2.2f), 40);
    }

    // ================================================================= picture + hanging charms
    private void drawFrameAndHangers(Canvas c, float time) {
        float l = x(870f), r = x(966f), top = t(65f), bot = t(176f);
        float cx = (l + r) / 2f;
        KawaiiArt.line(c, cx, t(36f), cx, top, d(2f), 0xFF6A5258);
        KawaiiArt.circ(c, cx, t(34f), d(5f), 0xFFA8842F, 0xFF5E431D, d(2f));
        KawaiiArt.rr(c, l, top, r, bot, d(7f), 0xFF3A2234, 0xFF1E1020, d(3f));
        KawaiiArt.rr(c, l + d(10f), top + d(10f), r - d(10f), bot - d(10f), d(3f), 0xFF4F3646, 0xFF2C1826, d(2f));
        KawaiiArt.heartOutlined(c, cx, (top + bot) / 2f - d(1f), d(22f), 0xFFB04569, 0xFF6C2640, d(3f));
        KawaiiArt.shine(c, cx - d(9f), (top + bot) / 2f - d(11f), d(5f), d(3f), 90);
        float sway = (float) Math.sin(time * 1.1f) * d(1.5f);
        KawaiiArt.line(c, cx, bot, cx + sway, t(200f), d(1.6f), 0xFF6A5258);
        KawaiiArt.star(c, cx + sway, t(219f), d(19f), 0xFFB8923B, 0xFF7A5A1E, d(1.6f));
        KawaiiArt.line(c, cx + sway, t(240f), cx - d(4f) + sway, t(262f), d(1.6f), 0xFF6A5258);
        KawaiiArt.dustBunny(c, cx - d(5f) + sway, t(283f), d(17f), time, 0xFF564E62);
    }

    // ================================================================= wardrobe
    private void drawWardrobe(Canvas c, float time) {
        float l = x(20f), r = x(312f);
        // feet
        KawaiiArt.rr(c, x(98f), b(896f), x(136f), b(922f), d(9f), PINK_BODY, OUT_PINK, d(3f));
        KawaiiArt.rr(c, x(262f), b(896f), x(300f), b(922f), d(9f), PINK_BODY, OUT_PINK, d(3f));
        // body
        KawaiiArt.rr(c, l, b(466f), r, b(904f), d(12f), PINK_BODY, OUT_PINK, d(4f));
        // scalloped crest: four domes on a flat base
        tmp.reset();
        tmp.moveTo(l, b(474f));
        tmp.lineTo(l, b(448f));
        tmp.quadTo(l - d(2f), b(404f), x(46f), b(408f));
        tmp.quadTo(x(72f), b(412f), x(70f), b(448f));
        tmp.lineTo(x(160f), b(448f));
        tmp.quadTo(x(160f), b(420f), x(183f), b(420f));
        tmp.quadTo(x(208f), b(420f), x(208f), b(448f));
        tmp.lineTo(x(216f), b(448f));
        tmp.quadTo(x(216f), b(420f), x(240f), b(420f));
        tmp.quadTo(x(264f), b(420f), x(264f), b(448f));
        tmp.lineTo(x(268f), b(448f));
        tmp.quadTo(x(266f), b(408f), x(290f), b(406f));
        tmp.quadTo(r + d(4f), b(404f), r, b(446f));
        tmp.lineTo(r, b(474f));
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, PINK_LT, OUT_PINK, d(4f));
        KawaiiArt.shine(c, x(46f), b(428f), d(12f), d(4f), 40);

        // side panel with stickers
        KawaiiArt.rr(c, x(26f), b(478f), x(104f), b(888f), d(10f), 0xFF5E3258, OUT_PINK, d(3f));
        KawaiiArt.rr(c, x(30f), b(512f), x(73f), b(590f), d(5f), 0xFF70496C, OUT_PINK, d(2.5f));
        KawaiiArt.heartOutlined(c, x(51f), b(551f), d(11f), 0xFFC4508A, 0xFF7C2E5C, d(2f));
        KawaiiArt.rr(c, x(31f), b(613f), x(76f), b(692f), d(5f), 0xFF8A7E78, 0xFF5E4640, d(2.5f));
        KawaiiArt.circ(c, x(53f), b(656f), d(14f), 0xFFA89443, 0xFF6A5420, d(2f));
        KawaiiArt.circ(c, x(48f), b(654f), d(1.7f), INK, 0, 0f);
        KawaiiArt.circ(c, x(58f), b(654f), d(1.7f), INK, 0, 0f);
        KawaiiArt.star(c, x(62f), b(628f), d(8f), 0xFFA8842F, 0xFF6E5520, d(1.2f));

        // doors
        KawaiiArt.rr(c, x(109f), b(473f), x(206f), b(786f), d(8f), DOOR_L, 0xFF5E4458, d(3f));
        KawaiiArt.rr(c, x(212f), b(473f), x(304f), b(786f), d(8f), DOOR_R, 0xFF5E4458, d(3f));
        // heart windows
        KawaiiArt.heartOutlined(c, x(157f), b(546f), d(31f), TEAL, TEAL_DK, d(3f));
        KawaiiArt.heartOutlined(c, x(256f), b(546f), d(31f), TEAL, TEAL_DK, d(3f));
        KawaiiArt.shine(c, x(143f), b(536f), d(8f), d(5f), 80);
        KawaiiArt.shine(c, x(242f), b(536f), d(8f), d(5f), 80);
        // knobs
        KawaiiArt.circ(c, x(185f), b(627f), d(13f), GOLD, GOLD_DK, d(3f));
        KawaiiArt.circ(c, x(232f), b(627f), d(13f), GOLD, GOLD_DK, d(3f));
        KawaiiArt.shine(c, x(181f), b(622f), d(4f), d(3f), 130);
        KawaiiArt.shine(c, x(228f), b(622f), d(4f), d(3f), 130);
        // drawer + flower
        KawaiiArt.rr(c, x(111f), b(792f), x(298f), b(873f), d(10f), TEAL, TEAL_DK, d(3.5f));
        flower(c, x(207f), b(832f), d(21f), 0xFFA8643A, 0xFFA89A55);

        drawWardrobeTop(c, time);
    }

    private void flower(Canvas c, float x, float y, float r, int petal, int center) {
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5 - 1.2;
            KawaiiArt.circ(c, x + (float) Math.cos(a) * r * 0.55f, y + (float) Math.sin(a) * r * 0.55f, r * 0.46f, petal, KawaiiArt.edge(petal, 0.4f), d(1.8f));
        }
        KawaiiArt.circ(c, x, y, r * 0.34f, center, KawaiiArt.edge(center, 0.4f), d(1.8f));
    }

    private void drawWardrobeTop(Canvas c, float time) {
        // bow
        float bx = x(205f), by = b(452f);
        for (int side = -1; side <= 1; side += 2) {
            tmp.reset();
            tmp.moveTo(bx, by);
            tmp.cubicTo(bx + side * d(18f), by - d(20f), bx + side * d(62f), by - d(18f), bx + side * d(66f), by - d(2f));
            tmp.cubicTo(bx + side * d(68f), by + d(16f), bx + side * d(24f), by + d(22f), bx, by);
            tmp.close();
            KawaiiArt.fillStroke(c, tmp, 0xFF7C3A6C, 0xFF52244A, d(3.5f));
            KawaiiArt.oval(c, bx + side * d(26f) - d(14f), by - d(10f), bx + side * d(26f) + d(14f), by - d(3f), 0xFF9E6890, 0, 0f);
        }
        KawaiiArt.circ(c, bx, by, d(15f), 0xFF8A4478, 0xFF52244A, d(3.5f));
        KawaiiArt.shine(c, bx - d(4f), by - d(5f), d(4f), d(2.5f), 90);
        // star above the bow
        KawaiiArt.star(c, x(203f), b(410f), d(17f), 0xFFB8923B, 0xFF7A5A1E, d(1.8f));

        // pink bear (head peeking over the crest)
        float qx = x(216f), qy = b(357f), qr = d(38f);
        int fur = 0xFF804068, furE = 0xFF50244A;
        KawaiiArt.circ(c, qx - qr * 0.68f, qy - qr * 0.74f, qr * 0.38f, fur, furE, d(3f));
        KawaiiArt.circ(c, qx + qr * 0.68f, qy - qr * 0.74f, qr * 0.38f, fur, furE, d(3f));
        KawaiiArt.circ(c, qx - qr * 0.68f, qy - qr * 0.74f, qr * 0.2f, 0xFF9A5684, 0, 0f);
        KawaiiArt.circ(c, qx + qr * 0.68f, qy - qr * 0.74f, qr * 0.2f, 0xFF9A5684, 0, 0f);
        KawaiiArt.circ(c, qx, qy, qr, fur, furE, d(3.5f));
        KawaiiArt.oval(c, qx - qr * 0.62f, qy + qr * 0.1f, qr * 0.62f + qx, qy + qr * 0.92f, 0xFF8E6584, 0, 0f);
        KawaiiArt.heart(c, qx, qy + qr * 0.5f, d(6.5f), 0xFFC4508A);
        // sleepy smiling eyes
        for (int sd = -1; sd <= 1; sd += 2) {
            tmp.reset();
            tmp.moveTo(qx + sd * qr * 0.46f - d(5f), qy - qr * 0.12f);
            tmp.quadTo(qx + sd * qr * 0.46f, qy - qr * 0.28f, qx + sd * qr * 0.46f + d(5f), qy - qr * 0.12f);
            KawaiiArt.strokePath(c, tmp, d(2.2f), INK);
        }
        KawaiiArt.oval(c, qx - d(4f), qy + qr * 0.08f, qx + d(4f), qy + qr * 0.22f, INK, 0, 0f);
        KawaiiArt.blush(c, qx - qr * 0.7f, qy + qr * 0.1f, d(7f), 0x66FF8CB4);
        KawaiiArt.blush(c, qx + qr * 0.7f, qy + qr * 0.1f, d(7f), 0x66FF8CB4);

        // trailing plant pot
        int leaf = 0xFF2C5C54, leafE = 0xFF193A38;
        float[][] hearts = {{75f, 346f}, {93f, 338f}, {110f, 346f}, {127f, 338f}, {148f, 348f}, {165f, 372f}, {174f, 394f}, {47f, 374f}, {34f, 397f}};
        // stems first
        tmp.reset();
        tmp.moveTo(x(72f), b(380f));
        tmp.quadTo(x(40f), b(380f), x(35f), b(397f));
        KawaiiArt.strokePath(c, tmp, d(3f), leafE);
        tmp.reset();
        tmp.moveTo(x(136f), b(380f));
        tmp.quadTo(x(172f), b(374f), x(174f), b(394f));
        KawaiiArt.strokePath(c, tmp, d(3f), leafE);
        for (int i = 0; i < hearts.length; i++) {
            KawaiiArt.heartOutlined(c, x(hearts[i][0]), b(hearts[i][1]), d(11.5f), leaf, leafE, d(2f));
        }
        tmp.reset();
        tmp.moveTo(x(64f), b(388f));
        tmp.lineTo(x(138f), b(388f));
        tmp.lineTo(x(128f), b(446f));
        tmp.lineTo(x(75f), b(446f));
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, 0xFF5C3331, 0xFF3A1C1C, d(3.5f));
        KawaiiArt.rr(c, x(56f), b(370f), x(147f), b(389f), d(5f), 0xFF6C3D38, 0xFF3A1C1C, d(3.5f));
        KawaiiArt.shine(c, x(78f), b(378f), d(14f), d(2.4f), 50);
        KawaiiArt.heart(c, x(101f), b(411f), d(8f), 0xFFC8517E);
    }

    // ================================================================= plant with a face + rug
    private void drawLeftPlant(Canvas c, float time) {
        int leaf = 0xFF21404A, leafE = 0xFF3A6B6C;
        float[][] lf = {{9f, 742f, 15f}, {35f, 722f, 15f}, {59f, 719f, 15f}, {85f, 717f, 15f}, {111f, 744f, 15f}};
        for (int i = 0; i < lf.length; i++) {
            float sway = (float) Math.sin(time * 1.1f + i) * d(1.4f);
            float tipX = lf[i][0], tipY = lf[i][1], hw = lf[i][2];
            float baseX = 58f + (tipX - 58f) * 0.72f;
            tmp.reset();
            tmp.moveTo(x(baseX), b(828f));
            tmp.cubicTo(x(tipX - hw * 1.5f), b(tipY + 52f), x(tipX - hw * 0.8f), b(tipY + 14f), x(tipX) + sway, b(tipY));
            tmp.cubicTo(x(tipX + hw * 0.8f), b(tipY + 14f), x(tipX + hw * 1.5f), b(tipY + 52f), x(baseX), b(828f));
            tmp.close();
            KawaiiArt.fillStroke(c, tmp, leaf, leafE, d(2.8f));
            tmp.reset();
            tmp.moveTo(x(baseX), b(824f));
            tmp.quadTo(x((baseX + tipX) / 2f), b((828f + tipY) / 2f), x(tipX) + sway, b(tipY + 6f));
            KawaiiArt.strokePath(c, tmp, d(1.4f), 0x553A6B6C);
        }
        // pot
        tmp.reset();
        tmp.moveTo(x(8f), b(835f));
        tmp.lineTo(x(104f), b(835f));
        tmp.lineTo(x(94f), b(922f));
        tmp.lineTo(x(18f), b(922f));
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, 0xFF4F2539, 0xFF2E1523, d(3.5f));
        KawaiiArt.rr(c, x(2f), b(814f), x(108f), b(836f), d(6f), 0xFF5C2D43, 0xFF2E1523, d(3.5f));
        KawaiiArt.circ(c, x(40f), b(868f), d(3f), 0xFFD8C8D8, 0, 0f);
        KawaiiArt.circ(c, x(67f), b(868f), d(3f), 0xFFD8C8D8, 0, 0f);
        tmp.reset();
        tmp.moveTo(x(46f), b(878f));
        tmp.quadTo(x(54f), b(889f), x(62f), b(878f));
        KawaiiArt.strokePath(c, tmp, d(2.6f), 0xFFD8C8D8);
        KawaiiArt.blush(c, x(28f), b(879f), d(7f), 0x77C85A86);
        KawaiiArt.blush(c, x(79f), b(879f), d(7f), 0x77C85A86);
    }

    private void drawRug(Canvas c) {
        // small round pink rug, well clear of the wardrobe feet
        KawaiiArt.oval(c, x(107f), b(946f), x(318f), b(1004f), 0xFF4A2748, 0xFF361A35, d(3f));
        KawaiiArt.oval(c, x(120f), b(953f), x(305f), b(997f), 0xFF58305A, 0, 0f);
        flower(c, x(212f), b(975f), d(21f), 0xFF935A38, 0xFF978A50);
    }

    // ================================================================= nightstand + lamp
    private void drawNightstandAndLamp(Canvas c, float time) {
        Paint p = KawaiiArt.P;
        // warm light pooling on the wall and floor around the lamp
        float lx = x(872f), ly = b(668f), gr = d(300f);
        float key = lx + ly * 3f + gr * 7f;
        if (glow == null || Math.abs(glowKey - key) > 1f) {
            glow = new RadialGradient(lx, ly, gr, new int[]{0x8CFFC070, 0x52FFA850, 0x1CFF9440, 0x00FF9440}, new float[]{0f, 0.24f, 0.60f, 1f}, Shader.TileMode.CLAMP);
            glowKey = key;
        }
        p.setColor(0xFF000000);
        p.setShader(glow);
        c.drawRect(lx - gr, ly - gr, lx + gr, ly + gr, p);
        p.setShader(null);

        // legs + body
        KawaiiArt.rr(c, x(798f), b(906f), x(827f), b(927f), d(7f), 0xFF3A2024, WOOD_DK, d(3f));
        KawaiiArt.rr(c, x(925f), b(906f), x(954f), b(927f), d(7f), 0xFF3A2024, WOOD_DK, d(3f));
        KawaiiArt.rr(c, x(790f), b(748f), x(960f), b(912f), d(8f), 0xFF442A2E, WOOD_DK, d(3.5f));
        KawaiiArt.rr(c, x(784f), b(724f), x(966f), b(754f), d(9f), 0xFF7E5444, 0xFF3A2024, d(3.5f));
        KawaiiArt.shine(c, x(860f), b(731f), d(60f), d(2.6f), 70);
        // drawers
        KawaiiArt.rr(c, x(802f), b(765f), x(948f), b(827f), d(7f), 0xFF4C3033, WOOD_DK, d(3f));
        KawaiiArt.rr(c, x(802f), b(835f), x(948f), b(896f), d(7f), 0xFF4C3033, WOOD_DK, d(3f));
        KawaiiArt.heartOutlined(c, x(873f), b(797f), d(11f), 0xFFA04468, 0xFF5E1E3A, d(2f));
        flower(c, x(873f), b(863f), d(12f), 0xFF8A9CD8, 0xFFB69A55);

        // lamp (the light source: bright)
        float bx = x(870f);
        KawaiiArt.rr(c, bx - d(7f), b(684f), bx + d(7f), b(700f), d(3f), 0xFFC96C62, 0, 0f);
        KawaiiArt.oval(c, bx - d(28f), b(690f), bx + d(28f), b(731f), 0xFFE08A7C, 0xFFA85850, d(3.5f));
        KawaiiArt.shine(c, bx - d(9f), b(702f), d(7f), d(4f), 150);
        tmp.reset();
        tmp.moveTo(x(808f), b(698f));
        tmp.lineTo(x(934f), b(698f));
        tmp.lineTo(x(903f), b(594f));
        tmp.lineTo(x(839f), b(594f));
        tmp.close();
        tmp2.reset();
        tmp2.addPath(tmp);
        KawaiiArt.fillStroke(c, tmp2, 0xFFF4AAA0, 0xFFFFC6B0, d(3.5f));
        int sv = c.save();
        c.clipPath(tmp2);
        KawaiiArt.heartField(c, x(806f), b(592f), x(936f), b(700f), d(27f), d(6.5f), 0xFFFFE9E2, 0f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x40FFF0C0);
        c.drawRect(x(806f), b(592f), x(936f), b(640f), p);
        c.restoreToCount(sv);
        KawaiiArt.oval(c, x(837f), b(588f), x(905f), b(601f), 0xFFFFE6C6, 0xFFE8A090, d(2.5f));
        KawaiiArt.oval(c, x(806f), b(692f), x(936f), b(706f), 0x00000000, 0xFFFFC6B0, d(3f));
    }

    // ================================================================= bed (behind Mochi)
    private void drawBedBack(Canvas c) {
        Paint p = KawaiiArt.P;
        // headboard: dark wood, warmer where the lamp lights it
        float hl = x(408f), hr = x(773f), ht = b(640f), hb = b(800f);
        if (headGrad == null || Math.abs(glowKey - 1f) > 1e9) {
            headGrad = new LinearGradient(hl, 0f, hr, 0f, new int[]{0xFF4A2D31, 0xFF58362F, 0xFFA2683F}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            pillowGrad = new LinearGradient(x(435f), 0f, x(742f), 0f, new int[]{0xFF3C4684, 0xFF565FA2, 0xFF9488A8}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }
        KawaiiArt.rr(c, hl, ht, hr, hb, d(24f), 0xFF4A2D31, 0xFF2B181C, d(4f));
        // inner panel, lit from the right
        R(hl + d(10f), ht + d(10f), hr - d(10f), hb);
        p.setColor(0xFF000000);
        p.setShader(headGrad);
        c.drawRoundRect(KawaiiArt.R, d(16f), d(16f), p);
        p.setShader(null);
        KawaiiArt.shine(c, x(480f), ht + d(8f), d(60f), d(2.2f), 60);
        // pillow
        R(x(435f), b(667f), x(742f), b(766f));
        p.setColor(0xFF000000);
        p.setShader(pillowGrad);
        c.drawRoundRect(KawaiiArt.R, d(28f), d(28f), p);
        p.setShader(null);
        KawaiiArt.P.setStyle(Paint.Style.STROKE);
        KawaiiArt.P.setStrokeWidth(d(3f));
        KawaiiArt.P.setColor(0xFF3C4382);
        c.drawRoundRect(KawaiiArt.R, d(28f), d(28f), KawaiiArt.P);
        KawaiiArt.P.setStyle(Paint.Style.FILL);
    }

    private static void R(float l, float t, float r, float b) { KawaiiArt.R.set(l, t, r, b); }

    // ================================================================= front layer: blanket, legs, footboard
    void drawFront(Canvas c, float w, float h, float time, float petCx, float petCy, float r) {
        layout(w, h);
        Paint p = KawaiiArt.P;
        float lampLight = 1f;

        // Mochi's head catches the warm lamp light from the right
        {
            float gx = petCx + r * 0.95f, gy = petCy - r * 0.55f;
            RadialGradient hg = new RadialGradient(gx, gy, r * 1.5f, new int[]{0x92FFC878, 0x00FFC878}, null, Shader.TileMode.CLAMP);
            int sv0 = c.save();
            tmp.reset();
            tmp.addCircle(petCx, petCy - r * 0.15f, r * 1.02f, Path.Direction.CW);
            c.clipPath(tmp);
            c.clipRect(petCx - r * 1.1f, petCy - r * 1.4f, petCx + r * 1.1f, petCy - r * 0.18f);
            p.setColor(0xFF000000);
            p.setShader(hg);
            c.drawRect(gx - r * 1.5f, gy - r * 1.5f, gx + r * 1.5f, gy + r * 1.5f, p);
            p.setShader(null);
            c.restoreToCount(sv0);
        }

        float topY = petCy - r * 0.20f;                 // blanket edge under Mochi's chin (y ~ 737 in the reference)
        float bl = x(342f), br = x(786f), bb = b(902f);

        // ---- blanket silhouette (a rounded dome, widest at the hem) ----
        tmp2.reset();
        tmp2.moveTo(bl + d(4f), bb);
        tmp2.cubicTo(bl - d(4f), b(850f), bl + d(4f), b(800f), x(388f), b(775f));
        tmp2.cubicTo(x(414f), b(760f), x(436f), b(754f), petCx - r * 1.05f, petCy - r * 0.02f);
        tmp2.cubicTo(petCx - r * 0.72f, topY + r * 0.02f, petCx - r * 0.32f, topY - r * 0.01f, petCx, topY - r * 0.02f);
        tmp2.cubicTo(petCx + r * 0.32f, topY - r * 0.01f, petCx + r * 0.72f, topY + r * 0.02f, petCx + r * 1.05f, petCy - r * 0.02f);
        tmp2.cubicTo(x(690f), b(754f), x(712f), b(760f), x(738f), b(775f));
        tmp2.cubicTo(x(776f), b(800f), br + d(4f), b(850f), br - d(4f), bb);
        tmp2.close();
        KawaiiArt.fillStroke(c, tmp2, 0xFF7480A6, 0xFF485490, d(4f));

        int sv = c.save();
        c.clipPath(tmp2);
        // folded-over cuff: a bluer band above a long, gentle arch
        tmp.reset();
        tmp.moveTo(bl - d(10f), b(822f));
        tmp.cubicTo(x(420f), b(768f), x(520f), b(752f), x(562f), b(774f));
        tmp.cubicTo(x(610f), b(752f), x(700f), b(768f), br + d(10f), b(822f));
        tmp.lineTo(br + d(10f), b(700f));
        tmp.lineTo(bl - d(10f), b(700f));
        tmp.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0x16566AC8);
        c.drawPath(tmp, p);
        // warm lamp light on the right side, cool shade on the left
        p.setColor(0xFF000000);
        p.setShader(new LinearGradient(bl, 0f, br, 0f, new int[]{0x10303C8C, 0x00000000, 0x22FFC48C, 0x5AFFC48C}, new float[]{0f, 0.25f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(bl, b(740f), br, bb, p);
        p.setShader(null);
        // wavy quilting lines
        float top0 = b(775f);
        for (int i = 0; i < 12; i++) {
            float lx = x(367f + i * 38.7f);
            tmp.reset();
            tmp.moveTo(lx, top0);
            float segs = 5f;
            for (int k = 1; k <= 5; k++) {
                float yy = top0 + (bb - top0) * (k / segs);
                float off = ((k % 2 == 0) ? -1f : 1f) * d(4.5f);
                tmp.quadTo(lx + off * 2.2f, top0 + (bb - top0) * ((k - 0.5f) / segs), lx, yy);
            }
            KawaiiArt.strokePath(c, tmp, Math.max(1.6f, d(2f)), 0xCC5663A0);
        }
        c.restoreToCount(sv);

        // arch fold line + cuff edge
        tmp.reset();
        tmp.moveTo(x(354f), b(818f));
        tmp.cubicTo(x(420f), b(770f), x(520f), b(758f), x(562f), b(778f));
        tmp.cubicTo(x(610f), b(758f), x(710f), b(770f), x(774f), b(820f));
        KawaiiArt.strokePath(c, tmp, Math.max(2.4f, d(3.6f)), 0xFF404B86);
        tmp.reset();
        tmp.moveTo(x(372f), b(790f));
        tmp.cubicTo(x(400f), b(772f), x(430f), b(764f), x(452f), b(760f));
        KawaiiArt.strokePath(c, tmp, Math.max(1.6f, d(2.2f)), 0xAA4B5792);
        tmp.reset();
        tmp.moveTo(x(676f), b(760f));
        tmp.cubicTo(x(700f), b(764f), x(730f), b(772f), x(752f), b(790f));
        KawaiiArt.strokePath(c, tmp, Math.max(1.6f, d(2.2f)), 0xAA4B5792);

        // Mochi's legs: one long wave with two humps, like the reference
        tmp.reset();
        tmp.moveTo(x(460f), b(861f));
        tmp.cubicTo(x(463f), b(836f), x(480f), b(826f), x(496f), b(828f));
        tmp.cubicTo(x(520f), b(832f), x(535f), b(852f), x(565f), b(870f));
        tmp.cubicTo(x(590f), b(866f), x(606f), b(838f), x(634f), b(830f));
        tmp.cubicTo(x(656f), b(824f), x(678f), b(838f), x(672f), b(862f));
        KawaiiArt.strokePath(c, tmp, d(9f), 0xFF46547E);
        KawaiiArt.strokePath(c, tmp, d(5.5f), 0xFF55648F);

        // hem + cosy blue band against the footboard
        KawaiiArt.rr(c, bl, bb - d(2f), br, bb + d(10f), d(4f), 0xFF4D5DA8, 0xFF3A4688, d(2f));
        KawaiiArt.shine(c, x(560f), bb - d(6f), d(210f), d(1.8f), 60);

        // tiny slivers of cream sheet peeking out beside Mochi's hands
        for (int sd = -1; sd <= 1; sd += 2) {
            float ax = petCx + sd * r * 0.78f, ay = petCy - r * 0.10f;
            tmp.reset();
            tmp.moveTo(ax, ay - r * 0.10f);
            tmp.lineTo(ax + sd * r * 0.14f, ay + r * 0.03f);
            tmp.lineTo(ax - sd * r * 0.06f, ay + r * 0.05f);
            tmp.close();
            KawaiiArt.fillStroke(c, tmp, sd < 0 ? 0xFFB8B2C6 : 0xFFD2C8CC, 0xFF8A86A8, d(1.4f));
        }

        // ---- footboard: chunky wood with a sunken panel ----
        KawaiiArt.rr(c, x(335f), b(914f), x(788f), b(988f), d(16f), 0xFF5A3A33, 0xFF2E1B1D, d(4.5f));
        KawaiiArt.rr(c, x(342f), b(919f), x(781f), b(930f), d(5f), 0xFF70493F, 0, 0f);
        KawaiiArt.shine(c, x(400f), b(925f), d(60f), d(2.4f), 110);
        KawaiiArt.rr(c, x(366f), b(937f), x(758f), b(978f), d(8f), 0xFF2B1C24, 0xFF1C1119, d(3f));
        KawaiiArt.rr(c, x(372f), b(942f), x(752f), b(950f), d(3f), 0xFF5A4A58, 0, 0f);
    }
}
