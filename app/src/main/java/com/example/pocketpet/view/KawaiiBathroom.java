package com.example.pocketpet.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.example.pocketpet.weather.WeatherType;

/**
 * Bath-time room: a pastel kawaii bathroom (bubble wallpaper, tiles, window with the live weather,
 * shelf with towels and a rubber duck, fluffy bath mat). BathScene draws the tub, shower, soap and
 * towel on top of it, so the tub area (centre / lower half) is kept free of furniture.
 */
final class KawaiiBathroom {
    private static final int WALL     = 0xFFCDEEE6;
    private static final int WALL_DK  = 0xFFB4DFD5;
    private static final int TILE     = 0xFFFFEEF3;
    private static final int GROUT    = 0xFFF2BFD0;
    private static final int FLOOR_A  = 0xFFFFE9F0;
    private static final int FLOOR_B  = 0xFFFFD3E1;
    private static final int OUT      = 0xFFB0707F;
    private static final int WHITE    = 0xFFFFFFFF;

    private final KawaiiWeather sky = new KawaiiWeather();
    private final Path tmp = new Path();
    private final Path clip = new Path();
    private final RectF rf = new RectF();
    private final RectF glass = new RectF();

    void draw(Canvas c, float w, float h, float time, WeatherType weather, float flash) {
        Paint p = KawaiiArt.P;
        p.setShader(null);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);

        float floorTop = h * 0.60f;
        float tileTop = h * 0.40f;
        float u = w / 360f;   // 1 "dp" at a 360-wide screen

        // ---- upper wall with soap-bubble wallpaper ----
        p.setColor(WALL);
        c.drawRect(0f, 0f, w, floorTop, p);
        for (int i = 0; i < 26; i++) {
            float bx = ((i * 0.6180339f) % 1f) * w;
            float by = ((i * 0.3819660f + 0.13f) % 1f) * tileTop;
            float br = (6f + (i % 4) * 4f) * u;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * u);
            p.setColor(0x88FFFFFF);
            c.drawCircle(bx, by, br, p);
            p.setStyle(Paint.Style.FILL);
            KawaiiArt.shine(c, bx - br * 0.35f, by - br * 0.35f, br * 0.22f, br * 0.14f, 150);
        }

        // ---- tiled lower wall ----
        p.setColor(TILE);
        c.drawRect(0f, tileTop, w, floorTop, p);
        p.setColor(GROUT);
        float tile = 36f * u;
        for (float yy = tileTop; yy < floorTop; yy += tile) c.drawRect(0f, yy, w, yy + 2f * u, p);
        int row = 0;
        for (float yy = tileTop; yy < floorTop; yy += tile, row++) {
            for (float xx = (row % 2 == 0) ? 0f : tile * 0.5f; xx < w; xx += tile) c.drawRect(xx, yy, xx + 2f * u, Math.min(yy + tile, floorTop), p);
        }
        // scalloped tile-cap trim
        p.setColor(WALL_DK);
        c.drawRect(0f, tileTop - 6f * u, w, tileTop + 2f * u, p);
        for (float xx = 0f; xx < w + 14f * u; xx += 14f * u) {
            KawaiiArt.circ(c, xx, tileTop + 2f * u, 7f * u, WALL_DK, 0, 0f);
        }
        // little hearts on a few tiles
        for (int i = 0; i < 5; i++) {
            KawaiiArt.heart(c, (0.12f + i * 0.2f) * w + ((i % 2) * 0.5f) * tile, tileTop + tile * (1.5f + (i % 3) * 1.8f), 6f * u, 0x88F3779F);
        }

        // ---- floor: pastel checkerboard ----
        float fs = 40f * u;
        int rr2 = 0;
        for (float yy = floorTop; yy < h; yy += fs * 0.6f, rr2++) {
            int cc = 0;
            for (float xx = -fs; xx < w + fs; xx += fs, cc++) {
                p.setColor(((rr2 + cc) % 2 == 0) ? FLOOR_A : FLOOR_B);
                c.drawRect(xx + ((rr2 % 2) * 0f), yy, xx + fs, yy + fs * 0.6f, p);
            }
        }
        p.setColor(0xFFE9A9BF);
        c.drawRect(0f, floorTop, w, floorTop + 5f * u, p);       // skirting
        p.setColor(WHITE);
        c.drawRect(0f, floorTop - 2f * u, w, floorTop, p);

        // ---- window (live weather) ----
        float wl = w * 0.09f, wr = w * 0.50f, wt = h * 0.065f, wb = h * 0.335f;
        float fw = 9f * u;
        KawaiiArt.rr(c, wl, wt, wr, wb, 22f * u, WHITE, OUT, 5f * u);
        glass.set(wl + fw, wt + fw, wr - fw, wb - fw);
        clip.reset();
        clip.addRoundRect(glass, 14f * u, 14f * u, Path.Direction.CW);
        int sv = c.save();
        c.clipPath(clip);
        sky.draw(c, glass, weather, time, flash, 0f);
        c.restoreToCount(sv);
        KawaiiArt.P.setStyle(Paint.Style.STROKE);
        KawaiiArt.P.setStrokeWidth(4f * u);
        KawaiiArt.P.setColor(OUT);
        c.drawRoundRect(glass, 14f * u, 14f * u, KawaiiArt.P);
        KawaiiArt.P.setStyle(Paint.Style.FILL);
        float mx = (wl + wr) / 2f, my = (wt + wb) / 2f;
        KawaiiArt.rr(c, mx - 4f * u, glass.top, mx + 4f * u, glass.bottom, 3f * u, WHITE, OUT, 2.5f * u);
        KawaiiArt.rr(c, glass.left, my - 4f * u, glass.right, my + 4f * u, 3f * u, WHITE, OUT, 2.5f * u);
        // frosted sparkle on the glass
        KawaiiArt.sparkle(c, glass.left + 22f * u, glass.top + 22f * u, 7f * u, 0xCCFFFFFF);
        // sill + valance
        KawaiiArt.rr(c, wl - 8f * u, wb - 2f * u, wr + 8f * u, wb + 12f * u, 6f * u, 0xFFFFF7F0, OUT, 4f * u);
        valance(c, wl - 6f * u, wr + 6f * u, wt - 6f * u, u);
        // plant + duck on the sill
        cactus(c, wl + 38f * u, wb - 2f * u, u);
        duck(c, wr - 36f * u, wb - 2f * u, u, time);

        // ---- shelf with towels + bottles (left of the tub, above the tiles) ----
        float sl = w * 0.05f, sr = w * 0.41f, sy = h * 0.45f;
        KawaiiArt.rr(c, sl, sy, sr, sy + 10f * u, 5f * u, 0xFFFFD27F, 0xFFB98532, 3.5f * u);
        // rolled towels
        int[] tc = {0xFFFFB8D0, 0xFFA9DDF5, 0xFFFFE08A};
        for (int i = 0; i < 3; i++) {
            float tx = sl + 18f * u + i * 26f * u;
            KawaiiArt.rr(c, tx - 12f * u, sy - 24f * u, tx + 12f * u, sy, 10f * u, tc[i], KawaiiArt.edge(tc[i], 0.45f), 3f * u);
            KawaiiArt.oval(c, tx - 8f * u, sy - 22f * u, tx + 8f * u, sy - 12f * u, KawaiiArt.edge(tc[i], 0.12f), KawaiiArt.edge(tc[i], 0.45f), 2f * u);
        }
        // bottles
        bottle(c, sr - 36f * u, sy, 0xFFBFA8F0, u);
        bottle(c, sr - 12f * u, sy, 0xFF9ADBC4, u);

        // ---- fluffy bath mat where Mochi steps out ----
        float mcx = w * 0.5f, mcy = h * 0.865f;
        KawaiiArt.oval(c, mcx - w * 0.30f, mcy - h * 0.05f, mcx + w * 0.30f, mcy + h * 0.06f, 0xFFFFC2D6, 0xFFD97C9C, 4f * u);
        KawaiiArt.oval(c, mcx - w * 0.25f, mcy - h * 0.035f, mcx + w * 0.25f, mcy + h * 0.045f, 0xFFFFD6E3, 0, 0f);
        KawaiiArt.heart(c, mcx - w * 0.18f, mcy, 6f * u, 0xFFF3779F);
        KawaiiArt.heart(c, mcx + w * 0.18f, mcy, 6f * u, 0xFFF3779F);
    }

    private void valance(Canvas c, float l, float r, float top, float u) {
        KawaiiArt.rr(c, l, top, r, top + 14f * u, 6f * u, 0xFFFF9EC1, OUT, 4f * u);
        float n = 7f;
        float sw = (r - l) / n;
        for (int i = 0; i < n; i++) {
            float x0 = l + i * sw;
            tmp.reset();
            tmp.moveTo(x0, top + 10f * u);
            tmp.quadTo(x0 + sw * 0.5f, top + 34f * u, x0 + sw, top + 10f * u);
            tmp.close();
            KawaiiArt.fillStroke(c, tmp, 0xFFFFB8D0, OUT, 3f * u);
            KawaiiArt.heart(c, x0 + sw * 0.5f, top + 17f * u, 3.5f * u, WHITE);
        }
    }

    private void cactus(Canvas c, float cx, float base, float u) {
        int g = 0xFF6CC27B, ge = 0xFF3F8D55;
        KawaiiArt.rr(c, cx - 7f * u, base - 40f * u, cx + 7f * u, base - 8f * u, 7f * u, g, ge, 3f * u);
        KawaiiArt.rr(c, cx - 19f * u, base - 30f * u, cx - 9f * u, base - 22f * u, 4f * u, g, ge, 2.5f * u);
        KawaiiArt.rr(c, cx + 9f * u, base - 34f * u, cx + 19f * u, base - 26f * u, 4f * u, g, ge, 2.5f * u);
        KawaiiArt.circ(c, cx, base - 44f * u, 4.5f * u, 0xFFFF9EC1, 0xFFC4507F, 2f * u);
        tmp.reset();
        tmp.moveTo(cx - 15f * u, base - 14f * u);
        tmp.lineTo(cx + 15f * u, base - 14f * u);
        tmp.lineTo(cx + 11f * u, base);
        tmp.lineTo(cx - 11f * u, base);
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, 0xFFFFC7A8, 0xFFB86F50, 3f * u);
        KawaiiArt.circ(c, cx - 3f * u, base - 33f * u, 1.3f * u, 0xFF2B3A2B, 0, 0f);
        KawaiiArt.circ(c, cx + 3f * u, base - 33f * u, 1.3f * u, 0xFF2B3A2B, 0, 0f);
    }

    private void duck(Canvas c, float cx, float base, float u, float time) {
        float bob = (float) Math.sin(time * 2f) * u;
        KawaiiArt.oval(c, cx - 20f * u, base - 24f * u + bob, cx + 22f * u, base + bob, 0xFFFFE066, 0xFFC99A1E, 3.5f * u);
        KawaiiArt.circ(c, cx - 8f * u, base - 34f * u + bob, 12f * u, 0xFFFFE066, 0xFFC99A1E, 3.5f * u);
        tmp.reset();
        tmp.moveTo(cx - 19f * u, base - 33f * u + bob);
        tmp.quadTo(cx - 29f * u, base - 31f * u + bob, cx - 19f * u, base - 27f * u + bob);
        tmp.close();
        KawaiiArt.fillStroke(c, tmp, 0xFFFF9A4A, 0xFFB8601F, 2.5f * u);
        KawaiiArt.circ(c, cx - 10f * u, base - 37f * u + bob, 1.8f * u, 0xFF3A2624, 0, 0f);
        KawaiiArt.blush(c, cx - 4f * u, base - 30f * u + bob, 3.5f * u, 0x99FF7A9A);
        KawaiiArt.oval(c, cx + 2f * u, base - 20f * u + bob, cx + 16f * u, base - 8f * u + bob, 0xFFFFD233, 0xFFC99A1E, 2.5f * u);
        KawaiiArt.shine(c, cx - 14f * u, base - 40f * u + bob, 3f * u, 2f * u, 190);
    }

    private void bottle(Canvas c, float cx, float base, int col, float u) {
        int e = KawaiiArt.edge(col, 0.45f);
        KawaiiArt.rr(c, cx - 10f * u, base - 30f * u, cx + 10f * u, base, 8f * u, col, e, 3f * u);
        KawaiiArt.rr(c, cx - 5f * u, base - 40f * u, cx + 5f * u, base - 29f * u, 3f * u, 0xFFFFFFFF, e, 2.5f * u);
        KawaiiArt.heart(c, cx, base - 14f * u, 4f * u, 0xFFFFFFFF);
        KawaiiArt.shine(c, cx - 5f * u, base - 22f * u, 2f * u, 6f * u, 150);
    }
}
