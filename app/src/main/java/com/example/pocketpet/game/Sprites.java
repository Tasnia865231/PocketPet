package com.example.pocketpet.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Little hand-drawn-looking characters and props, all drawn with Canvas (no image files). */
final class Sprites {
    static final int NORMAL = 0, HAPPY = 1, OPEN = 2, SAD = 3;

    private static final RectF R = new RectF();
    private static final Path P = new Path();
    private static final int BODY    = Color.parseColor("#F9DD80");
    private static final int LEAF    = Color.parseColor("#8CCB8E");
    private static final int OUTLINE = Color.parseColor("#8B5E4B");
    private static final int DARK    = Color.parseColor("#4A3328");

    private Sprites() {}

    /** Mochi. cx,cy = centre of the body, r = radius, squash < 1 makes him short and wide. */
    static void mochi(Canvas c, Paint p, float cx, float cy, float r, int face, float squash) {
        float rx = r * (1f + (1f - squash) * 0.4f);
        float ry = r * 0.92f * squash;
        float sw = Math.max(1.5f, r * 0.07f);

        // leaf on top
        c.save();
        c.translate(cx + rx * 0.1f, cy - ry * 0.98f);
        c.rotate(-28f);
        R.set(-r * 0.08f, -r * 0.46f, r * 0.50f, r * 0.04f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(LEAF);
        c.drawOval(R, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw * 0.8f);
        p.setColor(OUTLINE);
        c.drawOval(R, p);
        c.restore();

        // body
        R.set(cx - rx, cy - ry, cx + rx, cy + ry);
        p.setStyle(Paint.Style.FILL);
        p.setColor(BODY);
        c.drawOval(R, p);
        p.setColor(Color.argb(120, 255, 255, 255));
        R.set(cx - rx * 0.66f, cy - ry * 0.72f, cx - rx * 0.2f, cy - ry * 0.32f);
        c.drawOval(R, p);
        R.set(cx - rx, cy - ry, cx + rx, cy + ry);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(OUTLINE);
        c.drawOval(R, p);

        // eyes
        float ey = cy - ry * 0.05f, ex = rx * 0.4f;
        if (face == HAPPY) {
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(sw * 1.2f);
            p.setColor(DARK);
            for (int s = -1; s <= 1; s += 2) {
                R.set(cx + s * ex - r * 0.11f, ey - r * 0.09f, cx + s * ex + r * 0.11f, ey + r * 0.13f);
                c.drawArc(R, 180f, 180f, false, p);
            }
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(DARK);
            for (int s = -1; s <= 1; s += 2) {
                c.drawCircle(cx + s * ex, ey, r * 0.095f, p);
            }
            p.setColor(Color.WHITE);
            for (int s = -1; s <= 1; s += 2) {
                c.drawCircle(cx + s * ex - r * 0.03f, ey - r * 0.035f, r * 0.032f, p);
            }
        }

        // blush
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(170, 255, 150, 170));
        for (int s = -1; s <= 1; s += 2) {
            R.set(cx + s * rx * 0.66f - r * 0.12f, cy + ry * 0.2f - r * 0.07f,
                  cx + s * rx * 0.66f + r * 0.12f, cy + ry * 0.2f + r * 0.07f);
            c.drawOval(R, p);
        }

        // mouth
        float my = cy + ry * 0.22f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw * 0.9f);
        p.setColor(DARK);
        if (face == OPEN) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#7A3F3F"));
            R.set(cx - r * 0.1f, my - r * 0.02f, cx + r * 0.1f, my + r * 0.2f);
            c.drawOval(R, p);
        } else if (face == SAD) {
            p.setStyle(Paint.Style.STROKE);
            R.set(cx - r * 0.11f, my + r * 0.04f, cx + r * 0.11f, my + r * 0.22f);
            c.drawArc(R, 200f, 140f, false, p);
        } else {
            p.setStyle(Paint.Style.STROKE);
            float w = face == HAPPY ? 0.17f : 0.1f;
            R.set(cx - r * w, my - r * 0.1f, cx + r * w, my + r * (face == HAPPY ? 0.14f : 0.08f));
            c.drawArc(R, 15f, 150f, false, p);
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
    }

    /** A fuzzy soot sprite. t animates the fuzz. */
    static void soot(Canvas c, Paint p, float cx, float cy, float r, float t) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(r * 0.2f);
        p.setColor(Color.parseColor("#4D3F47"));
        for (int k = 0; k < 10; k++) {
            double a = k * Math.PI / 5.0 + 0.3;
            float len = r * (1.3f + 0.12f * (float) Math.sin(t * 7f + k * 1.7f));
            c.drawLine(cx + (float) Math.cos(a) * r * 0.7f, cy + (float) Math.sin(a) * r * 0.7f,
                       cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len, p);
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, r, p);
        p.setColor(Color.WHITE);
        c.drawCircle(cx - r * 0.38f, cy - r * 0.1f, r * 0.27f, p);
        c.drawCircle(cx + r * 0.38f, cy - r * 0.1f, r * 0.27f, p);
        p.setColor(Color.parseColor("#2B2226"));
        c.drawCircle(cx - r * 0.34f, cy - r * 0.08f, r * 0.11f, p);
        c.drawCircle(cx + r * 0.42f, cy - r * 0.08f, r * 0.11f, p);
    }

    static void strawberry(Canvas c, Paint p, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#FF7B8E"));
        R.set(cx - r * 0.85f, cy - r * 0.7f, cx + r * 0.85f, cy + r);
        c.drawOval(R, p);
        p.setColor(Color.parseColor("#FFD870"));
        c.drawCircle(cx - r * 0.35f, cy - r * 0.05f, r * 0.08f, p);
        c.drawCircle(cx + r * 0.3f, cy - r * 0.15f, r * 0.08f, p);
        c.drawCircle(cx, cy + r * 0.3f, r * 0.08f, p);
        c.drawCircle(cx - r * 0.3f, cy + r * 0.5f, r * 0.07f, p);
        c.drawCircle(cx + r * 0.35f, cy + r * 0.45f, r * 0.07f, p);
        p.setColor(Color.parseColor("#7FC98A"));
        for (int k = -1; k <= 1; k++) {
            R.set(cx + k * r * 0.4f - r * 0.2f, cy - r * 0.95f, cx + k * r * 0.4f + r * 0.2f, cy - r * 0.45f);
            c.drawOval(R, p);
        }
    }

    static void cookie(Canvas c, Paint p, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#EDC088"));
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, r * 0.12f));
        p.setColor(Color.parseColor("#C99560"));
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#8A5A40"));
        c.drawCircle(cx - r * 0.35f, cy - r * 0.3f, r * 0.14f, p);
        c.drawCircle(cx + r * 0.3f, cy - r * 0.1f, r * 0.14f, p);
        c.drawCircle(cx - r * 0.05f, cy + r * 0.4f, r * 0.14f, p);
    }

    static void riceball(Canvas c, Paint p, float cx, float cy, float r) {
        P.reset();
        P.moveTo(cx, cy - r * 0.9f);
        P.quadTo(cx + r * 0.2f, cy - r * 0.8f, cx + r * 0.95f, cy + r * 0.7f);
        P.quadTo(cx, cy + r * 0.95f, cx - r * 0.95f, cy + r * 0.7f);
        P.quadTo(cx - r * 0.2f, cy - r * 0.8f, cx, cy - r * 0.9f);
        P.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#FFFDF6"));
        c.drawPath(P, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, r * 0.1f));
        p.setColor(Color.parseColor("#CDB9A0"));
        c.drawPath(P, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#5C6E60"));
        R.set(cx - r * 0.42f, cy + r * 0.15f, cx + r * 0.42f, cy + r * 0.8f);
        c.drawRoundRect(R, r * 0.1f, r * 0.1f, p);
    }

    static void heart(Canvas c, Paint p, float cx, float cy, float r, boolean filled) {
        P.reset();
        P.moveTo(cx, cy + r * 0.9f);
        P.cubicTo(cx - r * 1.5f, cy - r * 0.1f, cx - r * 0.7f, cy - r * 1.1f, cx, cy - r * 0.35f);
        P.cubicTo(cx + r * 0.7f, cy - r * 1.1f, cx + r * 1.5f, cy - r * 0.1f, cx, cy + r * 0.9f);
        P.close();
        if (filled) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#FF8FA8"));
            c.drawPath(P, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.2f, r * 0.18f));
        p.setColor(filled ? Color.parseColor("#D0607C") : Color.parseColor("#D8BFA8"));
        c.drawPath(P, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** A soft puffy cloud: x,y = top-left, w x h = size of the flat part. */
    static void cloud(Canvas c, Paint p, float x, float y, float w, float h, int fill, int edge) {
        for (int pass = 0; pass < 2; pass++) {
            float e = pass == 0 ? Math.max(1.2f, h * 0.1f) : 0f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(pass == 0 ? edge : fill);
            R.set(x - e, y + h * 0.15f - e, x + w + e, y + h + e);
            c.drawRoundRect(R, h * 0.5f + e, h * 0.5f + e, p);
            c.drawCircle(x + w * 0.30f, y + h * 0.15f, h * 0.7f + e, p);
            c.drawCircle(x + w * 0.56f, y, h * 0.9f + e, p);
            c.drawCircle(x + w * 0.80f, y + h * 0.2f, h * 0.62f + e, p);
        }
    }
}
