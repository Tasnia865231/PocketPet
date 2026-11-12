package com.example.pocketpet.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.core.graphics.ColorUtils;

/**
 * Shared drawing kit for the "Cute Kawaii Storybook" look: chunky rounded shapes, soft pastel fills,
 * warm coloured outlines (never black), tiny white highlights, rosy cheeks, hearts and sparkles.
 *
 * Everything is drawn with Canvas primitives. All scratch objects are static and only used from the
 * UI thread, so nothing is allocated while drawing.
 */
final class KawaiiArt {
    private KawaiiArt() {}

    static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    static final Path PA = new Path();
    static final RectF R = new RectF();

    /** 0..1: pulls every colour drawn through this kit toward {@link #DIM_COL} (used for the dim bedroom). */
    static float dimAmt = 0f;
    static final int DIM_COL = 0xFF140B22;

    static int dm(int c) {
        if (dimAmt <= 0f) return c;
        float k = 1f - dimAmt;
        int r = (int) (((c >> 16) & 255) * k + ((DIM_COL >> 16) & 255) * dimAmt);
        int g = (int) (((c >> 8) & 255) * k + ((DIM_COL >> 8) & 255) * dimAmt);
        int b = (int) ((c & 255) * k + (DIM_COL & 255) * dimAmt);
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static final int COCOA = 0xFF8A5A4E;     // default warm outline
    static final int WHITE = 0xFFFFFFFF;

    /** Outline colour derived from a fill: the fill pulled toward warm cocoa brown. */
    static int edge(int fill, float amt) {
        return ColorUtils.blendARGB(fill, 0xFF6B3F3A, amt);
    }

    static int edge(int fill) { return edge(fill, 0.45f); }

    static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    // ---------------------------------------------------------------- basic outlined shapes
    static void fillStroke(Canvas c, Path path, int fill, int stroke, float sw) {
        P.setShader(null);
        P.setPathEffect(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(dm(fill));
        c.drawPath(path, P);
        if (sw > 0f) {
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw);
            P.setStrokeJoin(Paint.Join.ROUND);
            P.setStrokeCap(Paint.Cap.ROUND);
            P.setColor(dm(stroke));
            c.drawPath(path, P);
            P.setStyle(Paint.Style.FILL);
        }
    }

    static void rr(Canvas c, float l, float t, float r, float b, float rad, int fill, int stroke, float sw) {
        R.set(l, t, r, b);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(dm(fill));
        c.drawRoundRect(R, rad, rad, P);
        if (sw > 0f) {
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw);
            P.setColor(dm(stroke));
            c.drawRoundRect(R, rad, rad, P);
            P.setStyle(Paint.Style.FILL);
        }
    }

    static void rrFill(Canvas c, float l, float t, float r, float b, float rad, int fill) {
        rr(c, l, t, r, b, rad, fill, 0, 0f);
    }

    static void oval(Canvas c, float l, float t, float r, float b, int fill, int stroke, float sw) {
        R.set(l, t, r, b);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(dm(fill));
        c.drawOval(R, P);
        if (sw > 0f) {
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw);
            P.setColor(dm(stroke));
            c.drawOval(R, P);
            P.setStyle(Paint.Style.FILL);
        }
    }

    static void circ(Canvas c, float x, float y, float r, int fill, int stroke, float sw) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(dm(fill));
        c.drawCircle(x, y, r, P);
        if (sw > 0f) {
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw);
            P.setColor(dm(stroke));
            c.drawCircle(x, y, r, P);
            P.setStyle(Paint.Style.FILL);
        }
    }

    static void line(Canvas c, float x1, float y1, float x2, float y2, float sw, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeWidth(sw);
        P.setColor(dm(color));
        c.drawLine(x1, y1, x2, y2, P);
        P.setStyle(Paint.Style.FILL);
        P.setStrokeCap(Paint.Cap.BUTT);
    }

    static void strokePath(Canvas c, Path path, float sw, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
        P.setStrokeWidth(sw);
        P.setColor(dm(color));
        c.drawPath(path, P);
        P.setStyle(Paint.Style.FILL);
        P.setStrokeCap(Paint.Cap.BUTT);
    }

    /** Small white shine blob used on almost every glossy kawaii surface. */
    static void shine(Canvas c, float x, float y, float rx, float ry, int a) {
        R.set(x - rx, y - ry, x + rx, y + ry);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(Color.argb(a, 255, 255, 255));
        c.drawOval(R, P);
    }

    // ---------------------------------------------------------------- icons
    static void heartPath(Path p, float x, float y, float s) {
        p.reset();
        p.moveTo(x, y + s * 0.85f);
        p.cubicTo(x - s * 1.45f, y - s * 0.05f, x - s * 0.85f, y - s * 1.15f, x, y - s * 0.38f);
        p.cubicTo(x + s * 0.85f, y - s * 1.15f, x + s * 1.45f, y - s * 0.05f, x, y + s * 0.85f);
        p.close();
    }

    static void heart(Canvas c, float x, float y, float s, int fill) {
        heartPath(PA, x, y, s);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(dm(fill));
        c.drawPath(PA, P);
    }

    static void heartOutlined(Canvas c, float x, float y, float s, int fill, int stroke, float sw) {
        heartPath(PA, x, y, s);
        fillStroke(c, PA, fill, stroke, sw);
    }

    /** Chubby five-point star with rounded tips. */
    static void star(Canvas c, float x, float y, float r, int fill, int stroke, float sw) {
        PA.reset();
        for (int i = 0; i < 10; i++) {
            float rad = (i % 2 == 0) ? r : r * 0.52f;
            float a = (float) (-Math.PI / 2 + i * Math.PI / 5);
            float px = x + (float) Math.cos(a) * rad, py = y + (float) Math.sin(a) * rad;
            if (i == 0) PA.moveTo(px, py); else PA.lineTo(px, py);
        }
        PA.close();
        fillStroke(c, PA, fill, sw > 0f ? stroke : fill, Math.max(sw, r * 0.28f));
    }

    /** Four-point twinkle. */
    static void sparkle(Canvas c, float x, float y, float s, int color) {
        PA.reset();
        PA.moveTo(x, y - s);
        PA.quadTo(x + s * 0.12f, y - s * 0.12f, x + s, y);
        PA.quadTo(x + s * 0.12f, y + s * 0.12f, x, y + s);
        PA.quadTo(x - s * 0.12f, y + s * 0.12f, x - s, y);
        PA.quadTo(x - s * 0.12f, y - s * 0.12f, x, y - s);
        PA.close();
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(dm(color));
        c.drawPath(PA, P);
    }

    /** Puffy cloud made of circles on a flat base. (x, y) is the centre, w the full width. */
    static void cloud(Canvas c, float x, float y, float w, int fill) {
        float h = w * 0.42f;
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(fill);
        R.set(x - w * 0.5f, y - h * 0.05f, x + w * 0.5f, y + h * 0.5f);
        c.drawRoundRect(R, h * 0.5f, h * 0.5f, P);
        c.drawCircle(x - w * 0.18f, y - h * 0.12f, h * 0.50f, P);
        c.drawCircle(x + w * 0.10f, y - h * 0.28f, h * 0.62f, P);
        c.drawCircle(x + w * 0.31f, y - h * 0.02f, h * 0.40f, P);
    }

    /** Rosy cheek. */
    static void blush(Canvas c, float x, float y, float rx, int color) {
        R.set(x - rx, y - rx * 0.6f, x + rx, y + rx * 0.6f);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawOval(R, P);
    }

    /** Dot eyes + tiny smile + blush: the face every plush in the room shares. s = head radius. */
    static void plushFace(Canvas c, float x, float y, float s, int ink) {
        circ(c, x - s * 0.42f, y, s * 0.11f, ink, 0, 0f);
        circ(c, x + s * 0.42f, y, s * 0.11f, ink, 0, 0f);
        shine(c, x - s * 0.45f, y - s * 0.04f, s * 0.04f, s * 0.04f, 230);
        shine(c, x + s * 0.39f, y - s * 0.04f, s * 0.04f, s * 0.04f, 230);
        blush(c, x - s * 0.7f, y + s * 0.2f, s * 0.2f, Color.argb(120, 255, 130, 150));
        blush(c, x + s * 0.7f, y + s * 0.2f, s * 0.2f, Color.argb(120, 255, 130, 150));
        PA.reset();
        PA.moveTo(x - s * 0.14f, y + s * 0.14f);
        PA.quadTo(x - s * 0.07f, y + s * 0.3f, x, y + s * 0.14f);
        PA.quadTo(x + s * 0.07f, y + s * 0.3f, x + s * 0.14f, y + s * 0.14f);
        strokePath(c, PA, Math.max(1.5f, s * 0.07f), ink);
    }

    /** Fuzzy little dust-bunny: soft black puff with two big white eyes. */
    static void dustBunny(Canvas c, float x, float y, float r, float t) {
        dustBunny(c, x, y, r, t, 0xFF3A3340);
    }

    static void dustBunny(Canvas c, float x, float y, float r, float t, int body) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(body);
        c.drawCircle(x, y, r * 0.86f, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeWidth(Math.max(1.2f, r * 0.13f));
        for (int i = 0; i < 12; i++) {
            float a = i * 0.5236f + (float) Math.sin(t * 2f + i) * 0.05f;
            float a1 = r * 0.74f, a2 = r * (1.04f + 0.05f * (i % 2));
            c.drawLine(x + (float) Math.cos(a) * a1, y + (float) Math.sin(a) * a1,
                    x + (float) Math.cos(a) * a2, y + (float) Math.sin(a) * a2, P);
        }
        P.setStyle(Paint.Style.FILL);
        P.setStrokeCap(Paint.Cap.BUTT);
        circ(c, x - r * 0.33f, y - r * 0.05f, r * 0.27f, WHITE, 0, 0f);
        circ(c, x + r * 0.33f, y - r * 0.05f, r * 0.27f, WHITE, 0, 0f);
        circ(c, x - r * 0.30f, y - r * 0.03f, r * 0.11f, 0xFF2A2430, 0, 0f);
        circ(c, x + r * 0.36f, y - r * 0.03f, r * 0.11f, 0xFF2A2430, 0, 0f);
    }

    /** Fine pastel heart pattern used on wallpaper, curtains and lamp shades. */
    static void heartField(Canvas c, float l, float t, float r, float b, float cell, float hs, int color, float rot) {
        int row = 0;
        for (float y = t + cell * 0.5f; y < b; y += cell, row++) {
            float off = (row % 2 == 0) ? 0f : cell * 0.5f;
            for (float x = l + cell * 0.5f + off; x < r; x += cell) {
                heart(c, x, y, hs, color);
            }
        }
    }
}
