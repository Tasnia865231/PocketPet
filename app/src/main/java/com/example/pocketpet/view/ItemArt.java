package com.example.pocketpet.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * All shop / bag / pet artwork for foods, hats and eggshells, drawn with plain Canvas shapes in the
 * Cute Kawaii Storybook style (chunky warm outlines, pastel fills, white shine, rosy cheeks, tiny
 * sparkles). The same code draws the pictures in the shop and bag AND the items on Mochi himself,
 * so what you see before buying is exactly what you get.
 *
 * Coordinates: food() is centred on (x, y) with radius r. hat() and shell*() use "pet space":
 * the origin is Mochi's centre and r is his body radius (head top is at y = -1.2 r).
 */
public final class ItemArt {
    private ItemArt() {}

    public static final String SHELL_DEFAULT = "Spotted Shell";
    public static final String SHELL_STRAWBERRY = "Strawberry Shell";
    public static final String SHELL_STARRY = "Starry Night Shell";

    private static final int OUT = 0xFF8B5E4B;       // same warm brown outline Mochi uses
    private static final int INK = 0xFF5A3A30;
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final Path PB = new Path();
    private static final Path PC = new Path();
    private static final RectF R = new RectF();

    // ------------------------------------------------------------------ tiny helpers
    private static int mix(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        return Color.rgb((int) (ar + (Color.red(b) - ar) * t), (int) (ag + (Color.green(b) - ag) * t), (int) (ab + (Color.blue(b) - ab) * t));
    }

    private static int edge(int fill) { return mix(fill, OUT, 0.55f); }

    private static void fs(Canvas c, Path p, int fill, float sw) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(fill);
        c.drawPath(p, P);
        stroke(c, p, edge(fill), sw);
    }

    private static void fsOut(Canvas c, Path p, int fill, int out, float sw) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(fill);
        c.drawPath(p, P);
        stroke(c, p, out, sw);
    }

    private static void stroke(Canvas c, Path p, int col, float sw) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(sw);
        P.setStrokeJoin(Paint.Join.ROUND);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setColor(col);
        c.drawPath(p, P);
        P.setStyle(Paint.Style.FILL);
    }

    private static void circle(Canvas c, float x, float y, float r, int fill, float sw) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(fill);
        c.drawCircle(x, y, r, P);
        if (sw > 0f) {
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw);
            P.setColor(edge(fill));
            c.drawCircle(x, y, r, P);
            P.setStyle(Paint.Style.FILL);
        }
    }

    private static void oval(Canvas c, float l, float t, float r, float b, int fill, float sw) {
        R.set(l, t, r, b);
        P.setStyle(Paint.Style.FILL);
        P.setColor(fill);
        c.drawOval(R, P);
        if (sw > 0f) {
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw);
            P.setColor(edge(fill));
            c.drawOval(R, P);
            P.setStyle(Paint.Style.FILL);
        }
    }

    private static void rrect(Canvas c, float l, float t, float r, float b, float rad, int fill, float sw) {
        R.set(l, t, r, b);
        P.setStyle(Paint.Style.FILL);
        P.setColor(fill);
        c.drawRoundRect(R, rad, rad, P);
        if (sw > 0f) {
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(sw);
            P.setColor(edge(fill));
            c.drawRoundRect(R, rad, rad, P);
            P.setStyle(Paint.Style.FILL);
        }
    }

    private static void shine(Canvas c, float x, float y, float rx, float ry, int a) {
        R.set(x - rx, y - ry, x + rx, y + ry);
        P.setStyle(Paint.Style.FILL);
        P.setColor(Color.argb(a, 255, 255, 255));
        c.drawOval(R, P);
    }

    private static void line(Canvas c, float x1, float y1, float x2, float y2, float w, int col) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeWidth(w);
        P.setColor(col);
        c.drawLine(x1, y1, x2, y2, P);
        P.setStyle(Paint.Style.FILL);
    }

    private static void heart(Canvas c, float x, float y, float s, int col) {
        PA.reset();
        PA.moveTo(x, y + s * 0.85f);
        PA.cubicTo(x - s * 1.45f, y - s * 0.05f, x - s * 0.85f, y - s * 1.15f, x, y - s * 0.38f);
        PA.cubicTo(x + s * 0.85f, y - s * 1.15f, x + s * 1.45f, y - s * 0.05f, x, y + s * 0.85f);
        PA.close();
        P.setStyle(Paint.Style.FILL);
        P.setColor(col);
        c.drawPath(PA, P);
    }

    private static void star(Canvas c, float x, float y, float r, int fill, float sw) {
        PA.reset();
        for (int i = 0; i < 10; i++) {
            float rad = (i % 2 == 0) ? r : r * 0.5f;
            float a = (float) (-Math.PI / 2 + i * Math.PI / 5);
            float px = x + (float) Math.cos(a) * rad, py = y + (float) Math.sin(a) * rad;
            if (i == 0) PA.moveTo(px, py); else PA.lineTo(px, py);
        }
        PA.close();
        fs(c, PA, fill, sw);
    }

    /** Four-point twinkle. */
    public static void sparkle(Canvas c, float x, float y, float s, int col) {
        PA.reset();
        PA.moveTo(x, y - s);
        PA.quadTo(x + s * 0.12f, y - s * 0.12f, x + s, y);
        PA.quadTo(x + s * 0.12f, y + s * 0.12f, x, y + s);
        PA.quadTo(x - s * 0.12f, y + s * 0.12f, x, y + s);
        PA.quadTo(x - s * 0.12f, y + s * 0.12f, x - s, y);
        PA.quadTo(x - s * 0.12f, y - s * 0.12f, x, y - s);
        PA.close();
        P.setStyle(Paint.Style.FILL);
        P.setColor(col);
        c.drawPath(PA, P);
    }

    private static void face(Canvas c, float x, float y, float r) {
        circle(c, x - r * 0.24f, y, r * 0.075f, INK, 0f);
        circle(c, x + r * 0.24f, y, r * 0.075f, INK, 0f);
        shine(c, x - r * 0.26f, y - r * 0.03f, r * 0.028f, r * 0.028f, 235);
        shine(c, x + r * 0.22f, y - r * 0.03f, r * 0.028f, r * 0.028f, 235);
        shine(c, x - r * 0.44f, y + r * 0.13f, r * 0.11f, r * 0.07f, 0);
        P.setStyle(Paint.Style.FILL);
        P.setColor(Color.argb(120, 255, 120, 150));
        R.set(x - r * 0.52f, y + r * 0.07f, x - r * 0.3f, y + r * 0.2f);
        c.drawOval(R, P);
        R.set(x + r * 0.3f, y + r * 0.07f, x + r * 0.52f, y + r * 0.2f);
        c.drawOval(R, P);
        PA.reset();
        PA.moveTo(x - r * 0.1f, y + r * 0.1f);
        PA.quadTo(x - r * 0.05f, y + r * 0.2f, x, y + r * 0.1f);
        PA.quadTo(x + r * 0.05f, y + r * 0.2f, x + r * 0.1f, y + r * 0.1f);
        stroke(c, PA, INK, Math.max(1.2f, r * 0.045f));
    }

    // =================================================================== FOODS
    /** Draws a food item centred on (x, y) spanning roughly +-r. */
    public static void food(Canvas c, String name, float x, float y, float r, boolean withFace) {
        if (name == null) name = "";
        float sw = Math.max(1.6f, r * 0.075f);
        switch (name) {
            case "Berry": {
                PA.reset();
                PA.moveTo(x, y + r * 0.95f);
                PA.cubicTo(x - r * 1.2f, y + r * 0.45f, x - r * 1.05f, y - r * 0.55f, x - r * 0.4f, y - r * 0.6f);
                PA.cubicTo(x - r * 0.15f, y - r * 0.62f, x - r * 0.05f, y - r * 0.5f, x, y - r * 0.5f);
                PA.cubicTo(x + r * 0.05f, y - r * 0.5f, x + r * 0.15f, y - r * 0.62f, x + r * 0.4f, y - r * 0.6f);
                PA.cubicTo(x + r * 1.05f, y - r * 0.55f, x + r * 1.2f, y + r * 0.45f, x, y + r * 0.95f);
                PA.close();
                fs(c, PA, 0xFFF4607E, sw);
                for (int i = 0; i < 7; i++) {
                    float sx = x + (i % 3 - 1) * r * 0.42f + ((i / 3) % 2) * r * 0.2f - r * 0.1f;
                    float sy = y + (i / 3) * r * 0.36f - r * 0.12f;
                    oval(c, sx - r * 0.04f, sy - r * 0.07f, sx + r * 0.04f, sy + r * 0.07f, 0xFFFFE9A8, 0f);
                }
                leaf(c, x - r * 0.38f, y - r * 0.6f, r * 0.5f, -25f, 0xFF7FCB78);
                leaf(c, x + r * 0.38f, y - r * 0.6f, r * 0.5f, 25f, 0xFF7FCB78);
                leaf(c, x, y - r * 0.62f, r * 0.55f, 0f, 0xFF66BB63);
                shine(c, x - r * 0.5f, y - r * 0.2f, r * 0.12f, r * 0.2f, 150);
                if (withFace) face(c, x, y + r * 0.28f, r * 0.8f);
                break;
            }
            case "Rice Ball": {
                PA.reset();
                PA.moveTo(x, y - r * 0.95f);
                PA.cubicTo(x + r * 0.35f, y - r * 0.95f, x + r * 1.15f, y + r * 0.35f, x + r * 1.0f, y + r * 0.7f);
                PA.quadTo(x + r * 0.9f, y + r * 0.9f, x + r * 0.6f, y + r * 0.9f);
                PA.lineTo(x - r * 0.6f, y + r * 0.9f);
                PA.quadTo(x - r * 0.9f, y + r * 0.9f, x - r * 1.0f, y + r * 0.7f);
                PA.cubicTo(x - r * 1.15f, y + r * 0.35f, x - r * 0.35f, y - r * 0.95f, x, y - r * 0.95f);
                PA.close();
                fs(c, PA, 0xFFFFFBF0, sw);
                rrect(c, x - r * 0.52f, y + r * 0.35f, x + r * 0.52f, y + r * 0.9f, r * 0.12f, 0xFF4A4458, sw * 0.8f);
                shine(c, x - r * 0.32f, y - r * 0.4f, r * 0.12f, r * 0.2f, 190);
                if (withFace) face(c, x, y + r * 0.0f, r * 0.8f);
                break;
            }
            case "Apple": {
                PA.reset();
                PA.moveTo(x, y - r * 0.7f);
                PA.cubicTo(x + r * 0.5f, y - r * 0.95f, x + r * 1.2f, y - r * 0.4f, x + r * 1.0f, y + r * 0.35f);
                PA.cubicTo(x + r * 0.85f, y + r * 0.95f, x + r * 0.35f, y + r * 1.0f, x, y + r * 0.85f);
                PA.cubicTo(x - r * 0.35f, y + r * 1.0f, x - r * 0.85f, y + r * 0.95f, x - r * 1.0f, y + r * 0.35f);
                PA.cubicTo(x - r * 1.2f, y - r * 0.4f, x - r * 0.5f, y - r * 0.95f, x, y - r * 0.7f);
                PA.close();
                fs(c, PA, 0xFFF4585A, sw);
                line(c, x, y - r * 0.7f, x + r * 0.12f, y - r * 1.08f, sw * 1.3f, 0xFF8A5A3C);
                leaf(c, x + r * 0.14f, y - r * 0.95f, r * 0.6f, 30f, 0xFF7FCB78);
                shine(c, x - r * 0.55f, y - r * 0.2f, r * 0.14f, r * 0.26f, 160);
                if (withFace) face(c, x, y + r * 0.15f, r * 0.85f);
                break;
            }
            case "Cookie": {
                circle(c, x, y, r * 0.95f, 0xFFEDBB7C, sw);
                circle(c, x, y, r * 0.8f, 0xFFF3C98E, 0f);
                int[][] chips = {{-45, -35}, {30, -50}, {50, 10}, {-20, 40}, {-55, 15}, {15, 5}};
                for (int[] ch : chips) {
                    oval(c, x + r * ch[0] / 100f - r * 0.11f, y + r * ch[1] / 100f - r * 0.09f,
                            x + r * ch[0] / 100f + r * 0.11f, y + r * ch[1] / 100f + r * 0.09f, 0xFF8A5638, 0f);
                }
                shine(c, x - r * 0.5f, y - r * 0.5f, r * 0.16f, r * 0.1f, 140);
                if (withFace) face(c, x, y + r * 0.42f, r * 0.7f);
                break;
            }
            case "Milk": {
                rrect(c, x - r * 0.6f, y - r * 0.55f, x + r * 0.6f, y + r * 0.95f, r * 0.16f, 0xFFFFFFFF, sw);
                PA.reset();
                PA.moveTo(x - r * 0.6f, y - r * 0.55f);
                PA.lineTo(x - r * 0.38f, y - r * 1.0f);
                PA.lineTo(x + r * 0.38f, y - r * 1.0f);
                PA.lineTo(x + r * 0.6f, y - r * 0.55f);
                PA.close();
                fs(c, PA, 0xFFBFE3F6, sw);
                rrect(c, x - r * 0.4f, y - r * 0.12f, x + r * 0.4f, y + r * 0.62f, r * 0.12f, 0xFFD6EEFB, 0f);
                if (withFace) face(c, x, y + r * 0.12f, r * 0.62f); else heart(c, x, y + r * 0.26f, r * 0.2f, 0xFFF38FB0);
                shine(c, x - r * 0.42f, y - r * 0.1f, r * 0.06f, r * 0.3f, 180);
                break;
            }
            case "Carrot": {
                PA.reset();
                PA.moveTo(x - r * 0.1f, y - r * 0.75f);
                PA.cubicTo(x + r * 0.45f, y - r * 0.85f, x + r * 0.7f, y - r * 0.4f, x + r * 0.4f, y + r * 0.2f);
                PA.lineTo(x - r * 0.05f, y + r * 1.0f);
                PA.cubicTo(x - r * 0.25f, y + r * 0.65f, x - r * 0.85f, y - r * 0.15f, x - r * 0.65f, y - r * 0.5f);
                PA.cubicTo(x - r * 0.55f, y - r * 0.68f, x - r * 0.3f, y - r * 0.74f, x - r * 0.1f, y - r * 0.75f);
                PA.close();
                fs(c, PA, 0xFFFFA45C, sw);
                for (int i = 0; i < 3; i++) line(c, x - r * 0.45f + i * r * 0.18f, y - r * 0.3f + i * r * 0.28f, x - r * 0.2f + i * r * 0.18f, y - r * 0.2f + i * r * 0.28f, sw * 0.8f, 0xFFE08540);
                leaf(c, x - r * 0.18f, y - r * 0.78f, r * 0.7f, -30f, 0xFF7FCB78);
                leaf(c, x - r * 0.08f, y - r * 0.8f, r * 0.8f, 0f, 0xFF66BB63);
                leaf(c, x + r * 0.02f, y - r * 0.78f, r * 0.7f, 30f, 0xFF7FCB78);
                shine(c, x + r * 0.15f, y - r * 0.4f, r * 0.08f, r * 0.2f, 150);
                if (withFace) face(c, x - r * 0.1f, y - r * 0.2f, r * 0.7f);
                break;
            }
            case "Cake": {
                PA.reset();
                PA.moveTo(x - r * 0.95f, y + r * 0.8f);
                PA.lineTo(x - r * 0.95f, y - r * 0.1f);
                PA.lineTo(x + r * 0.95f, y - r * 0.1f);
                PA.lineTo(x + r * 0.95f, y + r * 0.8f);
                PA.close();
                fs(c, PA, 0xFFF7D9A0, sw);
                rrect(c, x - r * 0.95f, y + r * 0.22f, x + r * 0.95f, y + r * 0.36f, 0f, 0xFFFF8FA8, 0f);
                rrect(c, x - r * 1.0f, y - r * 0.38f, x + r * 1.0f, y - r * 0.02f, r * 0.17f, 0xFFFFFDF8, sw);
                for (int i = 0; i < 4; i++) circle(c, x - r * 0.66f + i * r * 0.44f, y + r * 0.0f, r * 0.11f, 0xFFFFFDF8, 0f);
                star(c, x, y - r * 0.62f, r * 0.24f, 0xFFFFD45E, sw * 0.7f);
                line(c, x - r * 0.5f, y - r * 0.46f, x - r * 0.5f, y - r * 0.8f, sw, 0xFFFF9EC4);
                circle(c, x - r * 0.5f, y - r * 0.88f, r * 0.09f, 0xFFFFE066, 0f);
                if (withFace) face(c, x, y + r * 0.5f, r * 0.7f);
                break;
            }
            case "Pudding": {
                PA.reset();
                PA.moveTo(x - r * 0.62f, y - r * 0.3f);
                PA.lineTo(x + r * 0.62f, y - r * 0.3f);
                PA.lineTo(x + r * 0.9f, y + r * 0.85f);
                PA.quadTo(x + r * 0.9f, y + r * 0.95f, x + r * 0.8f, y + r * 0.95f);
                PA.lineTo(x - r * 0.8f, y + r * 0.95f);
                PA.quadTo(x - r * 0.9f, y + r * 0.95f, x - r * 0.9f, y + r * 0.85f);
                PA.close();
                fs(c, PA, 0xFFFFE8A6, sw);
                PA.reset();
                PA.moveTo(x - r * 0.66f, y - r * 0.3f);
                PA.quadTo(x, y - r * 0.82f, x + r * 0.66f, y - r * 0.3f);
                PA.lineTo(x + r * 0.7f, y + r * 0.0f);
                PA.quadTo(x + r * 0.55f, y - r * 0.12f, x + r * 0.45f, y + r * 0.16f);
                PA.quadTo(x + r * 0.3f, y - r * 0.05f, x + r * 0.15f, y + r * 0.0f);
                PA.quadTo(x - r * 0.1f, y - r * 0.1f, x - r * 0.3f, y + r * 0.08f);
                PA.quadTo(x - r * 0.5f, y - r * 0.1f, x - r * 0.7f, y + r * 0.02f);
                PA.close();
                fs(c, PA, 0xFFC27A44, sw * 0.9f);
                circle(c, x, y - r * 0.72f, r * 0.17f, 0xFFF4546E, sw * 0.8f);
                shine(c, x - r * 0.5f, y + r * 0.3f, r * 0.07f, r * 0.26f, 150);
                if (withFace) face(c, x, y + r * 0.45f, r * 0.7f);
                break;
            }
            case "Ice Cream": {
                PA.reset();
                PA.moveTo(x - r * 0.55f, y + r * 0.0f);
                PA.lineTo(x + r * 0.55f, y + r * 0.0f);
                PA.lineTo(x, y + r * 1.05f);
                PA.close();
                fs(c, PA, 0xFFEFB878, sw);
                line(c, x - r * 0.3f, y + r * 0.1f, x + r * 0.15f, y + r * 0.7f, sw * 0.6f, 0xFFD4985A);
                line(c, x + r * 0.3f, y + r * 0.1f, x - r * 0.15f, y + r * 0.7f, sw * 0.6f, 0xFFD4985A);
                circle(c, x, y - r * 0.05f, r * 0.62f, 0xFFB9E8D2, sw);
                circle(c, x, y - r * 0.62f, r * 0.55f, 0xFFFFB3C8, sw);
                circle(c, x, y - r * 1.12f, r * 0.12f, 0xFFF4546E, sw * 0.7f);
                shine(c, x - r * 0.25f, y - r * 0.8f, r * 0.12f, r * 0.08f, 170);
                if (withFace) face(c, x, y - r * 0.55f, r * 0.62f);
                break;
            }
            case "Dumpling": {
                PA.reset();
                PA.moveTo(x - r * 1.0f, y + r * 0.55f);
                PA.cubicTo(x - r * 1.05f, y - r * 0.25f, x - r * 0.45f, y - r * 0.85f, x, y - r * 0.85f);
                PA.cubicTo(x + r * 0.45f, y - r * 0.85f, x + r * 1.05f, y - r * 0.25f, x + r * 1.0f, y + r * 0.55f);
                PA.quadTo(x + r * 0.95f, y + r * 0.85f, x + r * 0.6f, y + r * 0.85f);
                PA.lineTo(x - r * 0.6f, y + r * 0.85f);
                PA.quadTo(x - r * 0.95f, y + r * 0.85f, x - r * 1.0f, y + r * 0.55f);
                PA.close();
                fs(c, PA, 0xFFFFF3DE, sw);
                for (int i = 0; i < 5; i++) {
                    float px = x - r * 0.5f + i * r * 0.25f;
                    PB.reset();
                    PB.moveTo(px, y - r * 0.8f + Math.abs(i - 2) * r * 0.1f);
                    PB.quadTo(px + r * 0.05f, y - r * 0.5f, px + r * 0.1f, y - r * 0.3f + Math.abs(i - 2) * r * 0.05f);
                    stroke(c, PB, 0xFFE0C7A0, sw * 0.7f);
                }
                circle(c, x + r * 0.45f, y - r * 0.5f, r * 0.0f, 0, 0f);
                shine(c, x - r * 0.55f, y - r * 0.2f, r * 0.1f, r * 0.2f, 170);
                if (withFace) face(c, x, y + r * 0.3f, r * 0.8f);
                break;
            }
            case "Pancakes": {
                for (int i = 2; i >= 0; i--) {
                    float yy = y + r * 0.35f - i * r * 0.36f;
                    rrect(c, x - r * 0.95f, yy - r * 0.2f, x + r * 0.95f, yy + r * 0.2f, r * 0.2f, i == 0 ? 0xFFF2C47A : 0xFFF6CD88, sw);
                }
                PA.reset();
                float ty = y - r * 0.4f;
                PA.moveTo(x - r * 0.7f, ty);
                PA.quadTo(x - r * 0.2f, ty - r * 0.2f, x + r * 0.7f, ty);
                PA.lineTo(x + r * 0.7f, ty + r * 0.12f);
                PA.quadTo(x + r * 0.6f, ty + r * 0.4f, x + r * 0.5f, ty + r * 0.12f);
                PA.quadTo(x + r * 0.35f, ty + r * 0.3f, x + r * 0.15f, ty + r * 0.1f);
                PA.quadTo(x - r * 0.1f, ty + r * 0.45f, x - r * 0.3f, ty + r * 0.1f);
                PA.quadTo(x - r * 0.55f, ty + r * 0.3f, x - r * 0.7f, ty + r * 0.12f);
                PA.close();
                fs(c, PA, 0xFFC77A3C, sw * 0.8f);
                rrect(c, x - r * 0.2f, y - r * 0.72f, x + r * 0.22f, y - r * 0.44f, r * 0.06f, 0xFFFFE98A, sw * 0.8f);
                if (withFace) face(c, x, y + r * 0.45f, r * 0.7f);
                break;
            }
            case "Donut": {
                circle(c, x, y, r * 0.98f, 0xFFF2C27E, sw);
                PA.reset();
                PA.moveTo(x - r * 0.9f, y - r * 0.1f);
                PA.cubicTo(x - r * 0.95f, y - r * 0.85f, x + r * 0.95f, y - r * 0.85f, x + r * 0.9f, y - r * 0.1f);
                PA.quadTo(x + r * 0.95f, y + r * 0.2f, x + r * 0.7f, y + r * 0.1f);
                PA.quadTo(x + r * 0.55f, y + r * 0.35f, x + r * 0.35f, y + r * 0.15f);
                PA.quadTo(x + r * 0.1f, y + r * 0.35f, x - r * 0.1f, y + r * 0.1f);
                PA.quadTo(x - r * 0.4f, y + r * 0.3f, x - r * 0.55f, y + r * 0.1f);
                PA.quadTo(x - r * 0.9f, y + r * 0.25f, x - r * 0.9f, y - r * 0.1f);
                PA.close();
                fs(c, PA, 0xFFFF9EC4, sw);
                circle(c, x, y - r * 0.18f, r * 0.2f, 0xFFFFF3DE, sw * 0.9f);
                int[] sp = {0xFFFFE066, 0xFF8FD0F0, 0xFFFFFFFF, 0xFFB79CE8, 0xFF9AE6B4};
                float[][] pos = {{-0.55f, -0.5f}, {0.5f, -0.5f}, {-0.2f, -0.72f}, {0.25f, -0.7f}, {-0.7f, -0.2f}, {0.68f, -0.22f}, {0.0f, 0.1f}, {-0.35f, 0.02f}};
                for (int i = 0; i < pos.length; i++) {
                    float a = i * 0.9f;
                    line(c, x + r * pos[i][0], y + r * pos[i][1], x + r * pos[i][0] + (float) Math.cos(a) * r * 0.1f, y + r * pos[i][1] + (float) Math.sin(a) * r * 0.1f, sw * 0.9f, sp[i % sp.length]);
                }
                shine(c, x - r * 0.55f, y - r * 0.55f, r * 0.12f, r * 0.07f, 170);
                if (withFace) face(c, x, y + r * 0.58f, r * 0.55f);
                break;
            }
            case "Bento Box": {
                rrect(c, x - r * 1.0f, y - r * 0.72f, x + r * 1.0f, y + r * 0.78f, r * 0.22f, 0xFFF9BCCB, sw);
                rrect(c, x - r * 0.86f, y - r * 0.58f, x + r * 0.86f, y + r * 0.64f, r * 0.14f, 0xFFFFF3E8, sw * 0.6f);
                line(c, x + r * 0.0f, y - r * 0.56f, x + r * 0.0f, y + r * 0.62f, sw * 0.7f, 0xFFF0C8B0);
                // rice side
                oval(c, x - r * 0.78f, y - r * 0.42f, x - r * 0.08f, y + r * 0.3f, 0xFFFFFFFF, sw * 0.7f);
                circle(c, x - r * 0.43f, y - r * 0.06f, r * 0.1f, 0xFFF4546E, 0f);
                rrect(c, x - r * 0.78f, y + r * 0.32f, x - r * 0.08f, y + r * 0.58f, r * 0.08f, 0xFFFFE27A, sw * 0.7f);
                // other side: octopus sausage + broccoli
                circle(c, x + r * 0.28f, y - r * 0.2f, r * 0.2f, 0xFFF4585A, sw * 0.7f);
                for (int i = 0; i < 3; i++) line(c, x + r * 0.15f + i * r * 0.13f, y - r * 0.02f, x + r * 0.12f + i * r * 0.15f, y + r * 0.16f, sw, 0xFFF4585A);
                circle(c, x + r * 0.62f, y + r * 0.3f, r * 0.12f, 0xFF7FCB78, 0f);
                circle(c, x + r * 0.5f, y + r * 0.38f, r * 0.1f, 0xFF66BB63, 0f);
                circle(c, x + r * 0.72f, y + r * 0.38f, r * 0.1f, 0xFF66BB63, 0f);
                heart(c, x + r * 0.2f, y + r * 0.42f, r * 0.1f, 0xFFFF8FB0);
                if (withFace) face(c, x - r * 0.43f, y - r * 0.1f, r * 0.5f);
                break;
            }
            case "Rainbow Cake": {
                int[] rb = {0xFFFF8A8A, 0xFFFFB86B, 0xFFFFE27A, 0xFF9AE6A4, 0xFF8FD0F0};
                float top = y - r * 0.2f, lh = r * 0.2f;
                PA.reset();
                PA.addRoundRect(new RectF(x - r * 0.95f, top, x + r * 0.95f, top + lh * 5f), r * 0.1f, r * 0.1f, Path.Direction.CW);
                int sv = c.save();
                c.clipPath(PA);
                for (int i = 0; i < 5; i++) {
                    P.setStyle(Paint.Style.FILL);
                    P.setColor(rb[i]);
                    c.drawRect(x - r, top + i * lh, x + r, top + (i + 1) * lh + 1f, P);
                }
                c.restoreToCount(sv);
                stroke(c, PA, edge(0xFFFF9EB8), sw);
                rrect(c, x - r * 1.0f, top - r * 0.34f, x + r * 1.0f, top + r * 0.02f, r * 0.17f, 0xFFFFFDF8, sw);
                for (int i = 0; i < 4; i++) circle(c, x - r * 0.66f + i * r * 0.44f, top + r * 0.06f, r * 0.1f, 0xFFFFFDF8, 0f);
                star(c, x, top - r * 0.62f, r * 0.26f, 0xFFFFD45E, sw * 0.7f);
                heart(c, x - r * 0.55f, top - r * 0.18f, r * 0.1f, 0xFFFF8FB0);
                heart(c, x + r * 0.55f, top - r * 0.18f, r * 0.1f, 0xFF8FD0F0);
                sparkle(c, x + r * 0.9f, top - r * 0.7f, r * 0.14f, 0xFFFFE27A);
                if (withFace) face(c, x, y + r * 0.3f, r * 0.8f);
                break;
            }
            default: {
                circle(c, x, y, r * 0.9f, 0xFFFFC49A, sw);
                if (withFace) face(c, x, y + r * 0.1f, r * 0.8f);
            }
        }
    }

    private static void leaf(Canvas c, float x, float y, float len, float angleDeg, int fill) {
        c.save();
        c.translate(x, y);
        c.rotate(angleDeg);
        PB.reset();
        PB.moveTo(0f, 0f);
        PB.cubicTo(len * 0.35f, -len * 0.1f, len * 0.3f, -len * 0.75f, 0f, -len);
        PB.cubicTo(-len * 0.3f, -len * 0.75f, -len * 0.35f, -len * 0.1f, 0f, 0f);
        PB.close();
        P.setStyle(Paint.Style.FILL);
        P.setColor(fill);
        c.drawPath(PB, P);
        stroke(c, PB, edge(fill), Math.max(1.2f, len * 0.07f));
        c.restore();
    }

    // =================================================================== HATS (pet space)
    /** Vertical extent of a hat above the pet's centre, in units of r (negative = up). */
    public static float hatTop(String name) {
        if (name == null) return -1.3f;
        switch (name) {
            case "Straw Hat": return -1.55f;
            case "Leaf Hat": return -1.7f;
            case "Pink Bow": return -1.55f;
            case "Flower Crown": return -1.4f;
            case "Cat Ears": return -1.75f;
            case "Chef Hat": return -2.0f;
            case "Party Hat": return -2.05f;
            case "Wizard Hat": return -2.2f;
            case "Royal Crown": return -1.75f;
            default: return -1.3f;
        }
    }

    /** Draws the named hat in pet space (origin = Mochi's centre, head top at y = -1.2 r). */
    public static void hat(Canvas c, String name, float r) {
        if (name == null || name.isEmpty()) return;
        float sw = r * 0.045f;
        switch (name) {
            case "Straw Hat": {
                oval(c, -0.85f * r, -1.12f * r, 0.85f * r, -0.88f * r, 0xFFEBC76A, sw);
                PA.reset();
                R.set(-0.5f * r, -1.55f * r, 0.5f * r, -0.8f * r);
                PA.addArc(R, 180f, 180f);
                PA.close();
                fs(c, PA, 0xFFF2D585, sw);
                P.setStyle(Paint.Style.FILL);
                P.setColor(0xFFD9625B);
                c.drawRect(-0.5f * r, -1.02f * r, 0.5f * r, -0.92f * r, P);
                heart(c, 0.3f * r, -0.97f * r, 0.07f * r, 0xFFFFFFFF);
                line(c, -0.25f * r, -1.38f * r, -0.1f * r, -1.46f * r, r * 0.04f, 0x66FFFFFF);
                break;
            }
            case "Leaf Hat": {
                PA.reset();
                PA.moveTo(-0.1f * r, -1.12f * r);
                PA.cubicTo(0.1f * r, -1.7f * r, 0.8f * r, -1.7f * r, 0.9f * r, -1.45f * r);
                PA.cubicTo(0.7f * r, -1.2f * r, 0.3f * r, -1.1f * r, -0.1f * r, -1.12f * r);
                PA.close();
                fs(c, PA, 0xFF8CC78A, sw);
                PB.reset();
                PB.moveTo(-0.05f * r, -1.13f * r);
                PB.quadTo(0.4f * r, -1.35f * r, 0.78f * r, -1.46f * r);
                stroke(c, PB, 0xFF5FA75E, sw * 0.8f);
                break;
            }
            case "Pink Bow": {
                c.save();
                c.translate(0.55f * r, -1.08f * r);
                c.rotate(16f);
                for (int s = -1; s <= 1; s += 2) {
                    PA.reset();
                    PA.moveTo(0f, 0f);
                    PA.cubicTo(s * 0.25f * r, -0.5f * r, s * 0.8f * r, -0.42f * r, s * 0.74f * r, 0.02f * r);
                    PA.cubicTo(s * 0.7f * r, 0.4f * r, s * 0.25f * r, 0.32f * r, 0f, 0f);
                    PA.close();
                    fs(c, PA, 0xFFFF8FB5, sw);
                    circle(c, s * 0.4f * r, -0.08f * r, 0.05f * r, 0xFFFFFFFF, 0f);
                    circle(c, s * 0.52f * r, 0.12f * r, 0.04f * r, 0xFFFFFFFF, 0f);
                    shine(c, s * 0.3f * r, -0.25f * r, 0.1f * r, 0.04f * r, 150);
                }
                circle(c, 0f, 0f, 0.13f * r, 0xFFFF6FA0, sw);
                c.restore();
                break;
            }
            case "Flower Crown": {
                int[] pc = {0xFFFF9EC4, 0xFFFFE27A, 0xFFFFFFFF, 0xFFC9A8F0, 0xFFFFB085, 0xFFFF9EC4, 0xFFFFE27A};
                float[] xs = {-0.78f, -0.5f, -0.22f, 0.07f, 0.35f, 0.62f, 0.84f};
                for (int i = 0; i < xs.length; i++) {
                    float fx = xs[i] * r;
                    float t = xs[i];
                    float fy = (-0.05f - 1.12f * (float) Math.sqrt(Math.max(0f, 1f - t * t * 0.98f))) * r;
                    leaf(c, fx - 0.1f * r, fy + 0.04f * r, 0.26f * r, -70f, 0xFF7FCB78);
                    leaf(c, fx + 0.1f * r, fy + 0.04f * r, 0.26f * r, 70f, 0xFF66BB63);
                }
                for (int i = 0; i < xs.length; i++) {
                    float fx = xs[i] * r;
                    float t = xs[i];
                    float fy = (-0.05f - 1.12f * (float) Math.sqrt(Math.max(0f, 1f - t * t * 0.98f))) * r;
                    for (int k = 0; k < 5; k++) {
                        double a = k * Math.PI * 2 / 5 + i;
                        circle(c, fx + (float) Math.cos(a) * 0.085f * r, fy + (float) Math.sin(a) * 0.085f * r, 0.075f * r, pc[i], sw * 0.6f);
                    }
                    circle(c, fx, fy, 0.055f * r, i % 2 == 0 ? 0xFFFFE27A : 0xFFFF9EC4, 0f);
                }
                break;
            }
            case "Cat Ears": {
                for (int s = -1; s <= 1; s += 2) {
                    PA.reset();
                    PA.moveTo(s * 0.9f * r, -0.8f * r);
                    PA.quadTo(s * 0.96f * r, -1.5f * r, s * 0.62f * r, -1.74f * r);
                    PA.quadTo(s * 0.3f * r, -1.52f * r, s * 0.18f * r, -1.18f * r);
                    PA.close();
                    fs(c, PA, 0xFFC9B8F0, sw);
                    PB.reset();
                    PB.moveTo(s * 0.78f * r, -1.0f * r);
                    PB.quadTo(s * 0.82f * r, -1.4f * r, s * 0.62f * r, -1.54f * r);
                    PB.quadTo(s * 0.42f * r, -1.4f * r, s * 0.34f * r, -1.2f * r);
                    PB.close();
                    P.setStyle(Paint.Style.FILL);
                    P.setColor(0xFFFFB6CF);
                    c.drawPath(PB, P);
                }
                // headband
                PA.reset();
                PA.moveTo(-0.92f * r, -0.75f * r);
                PA.quadTo(0f, -1.45f * r, 0.92f * r, -0.75f * r);
                stroke(c, PA, 0xFFA894DC, r * 0.07f);
                heart(c, 0f, -1.22f * r, 0.08f * r, 0xFFFF8FB5);
                break;
            }
            case "Chef Hat": {
                float[][] puff = {{-0.42f, -1.55f, 0.34f}, {0.42f, -1.55f, 0.34f}, {0f, -1.75f, 0.4f}};
                for (float[] pf : puff) circle(c, pf[0] * r, pf[1] * r, pf[2] * r, 0xFFFFFFFF, sw);
                P.setStyle(Paint.Style.FILL);
                P.setColor(0xFFFFFFFF);
                c.drawRect(-0.55f * r, -1.5f * r, 0.55f * r, -1.2f * r, P);
                rrect(c, -0.62f * r, -1.22f * r, 0.62f * r, -0.96f * r, 0.1f * r, 0xFFFFFFFF, sw);
                line(c, -0.2f * r, -1.5f * r, -0.2f * r, -1.25f * r, sw * 0.7f, 0xFFD8E4F0);
                line(c, 0.2f * r, -1.5f * r, 0.2f * r, -1.25f * r, sw * 0.7f, 0xFFD8E4F0);
                heart(c, 0f, -1.1f * r, 0.07f * r, 0xFFFF9EC4);
                shine(c, -0.22f * r, -1.8f * r, 0.12f * r, 0.07f * r, 0);
                break;
            }
            case "Party Hat": {
                c.save();
                c.rotate(-8f, 0f, -1.1f * r);
                PA.reset();
                PA.moveTo(-0.5f * r, -1.02f * r);
                PA.lineTo(0.5f * r, -1.02f * r);
                PA.lineTo(0.04f * r, -2.0f * r);
                PA.close();
                P.setStyle(Paint.Style.FILL);
                P.setColor(0xFFFFD0E4);
                c.drawPath(PA, P);
                int sv = c.save();
                c.clipPath(PA);
                P.setColor(0xFFA8DCF5);
                for (int i = 0; i < 3; i++) {
                    PB.reset();
                    PB.moveTo(-0.7f * r, -1.0f * r - i * 0.35f * r);
                    PB.lineTo(0.7f * r, -1.25f * r - i * 0.35f * r);
                    PB.lineTo(0.7f * r, -1.12f * r - i * 0.35f * r);
                    PB.lineTo(-0.7f * r, -0.87f * r - i * 0.35f * r);
                    PB.close();
                    c.drawPath(PB, P);
                }
                c.restoreToCount(sv);
                stroke(c, PA, edge(0xFFFFD0E4), sw);
                circle(c, -0.18f * r, -1.38f * r, 0.05f * r, 0xFFFFE27A, 0f);
                circle(c, 0.2f * r, -1.25f * r, 0.05f * r, 0xFFFFE27A, 0f);
                circle(c, 0.04f * r, -1.7f * r, 0.04f * r, 0xFFFFE27A, 0f);
                circle(c, 0.04f * r, -2.02f * r, 0.13f * r, 0xFFFFE27A, sw);
                c.restore();
                break;
            }
            case "Wizard Hat": {
                oval(c, -0.98f * r, -1.24f * r, 0.98f * r, -0.9f * r, 0xFF8C78D4, sw);
                PA.reset();
                PA.moveTo(-0.6f * r, -1.12f * r);
                PA.cubicTo(-0.5f * r, -1.7f * r, 0.0f * r, -1.95f * r, 0.5f * r, -2.0f * r);
                PA.cubicTo(0.75f * r, -2.02f * r, 0.9f * r, -1.9f * r, 0.84f * r, -1.8f * r);
                PA.cubicTo(0.6f * r, -1.8f * r, 0.5f * r, -1.4f * r, 0.6f * r, -1.12f * r);
                PA.close();
                fs(c, PA, 0xFFA48CE8, sw);
                P.setStyle(Paint.Style.FILL);
                P.setColor(0xFFFFE27A);
                PB.reset();
                PB.moveTo(-0.6f * r, -1.12f * r);
                PB.lineTo(0.6f * r, -1.12f * r);
                PB.lineTo(0.58f * r, -1.28f * r);
                PB.lineTo(-0.57f * r, -1.28f * r);
                PB.close();
                c.drawPath(PB, P);
                star(c, -0.08f * r, -1.6f * r, 0.15f * r, 0xFFFFE27A, sw * 0.6f);
                circle(c, 0.3f * r, -1.82f * r, 0.05f * r, 0xFFFFFFFF, 0f);
                circle(c, -0.3f * r, -1.4f * r, 0.04f * r, 0xFFFFFFFF, 0f);
                sparkle(c, 0.5f * r, -1.55f * r, 0.1f * r, 0xFFFFFFFF);
                break;
            }
            case "Royal Crown": {
                c.save();
                c.rotate(6f, 0f, -1.1f * r);
                PA.reset();
                PA.moveTo(-0.6f * r, -0.98f * r);
                PA.lineTo(-0.66f * r, -1.52f * r);
                PA.lineTo(-0.32f * r, -1.3f * r);
                PA.lineTo(0f, -1.72f * r);
                PA.lineTo(0.32f * r, -1.3f * r);
                PA.lineTo(0.66f * r, -1.52f * r);
                PA.lineTo(0.6f * r, -0.98f * r);
                PA.close();
                fs(c, PA, 0xFFFFD04F, sw);
                rrect(c, -0.62f * r, -1.12f * r, 0.62f * r, -0.94f * r, 0.06f * r, 0xFFFFE27A, sw * 0.8f);
                circle(c, -0.66f * r, -1.56f * r, 0.08f * r, 0xFFFF8FB5, sw * 0.7f);
                circle(c, 0f, -1.76f * r, 0.09f * r, 0xFF8FD0F0, sw * 0.7f);
                circle(c, 0.66f * r, -1.56f * r, 0.08f * r, 0xFFFF8FB5, sw * 0.7f);
                circle(c, -0.3f * r, -1.03f * r, 0.055f * r, 0xFFF4546E, 0f);
                circle(c, 0f, -1.03f * r, 0.055f * r, 0xFF8FD0F0, 0f);
                circle(c, 0.3f * r, -1.03f * r, 0.055f * r, 0xFF9AE6B4, 0f);
                shine(c, -0.4f * r, -1.4f * r, 0.05f * r, 0.12f * r, 150);
                c.restore();
                break;
            }
            default:
                break;
        }
    }

    // =================================================================== EGGSHELLS (pet space)
    public static boolean isDefaultShell(String shell) {
        return shell == null || shell.isEmpty() || SHELL_DEFAULT.equals(shell);
    }

    /** Body colour of the egg for a shell. */
    public static int shellFill(String shell) {
        if (SHELL_STRAWBERRY.equals(shell)) return 0xFFFFC9D6;
        if (SHELL_STARRY.equals(shell)) return 0xFF7F85D0;
        return 0xFFFFFDF4;
    }

    /** Pattern drawn on the egg body (pet space), on top of the base colour. */
    public static void shellPattern(Canvas c, String shell, float r) {
        P.setStyle(Paint.Style.FILL);
        if (SHELL_STRAWBERRY.equals(shell)) {
            float[][] seeds = {{-0.6f, 0.3f}, {-0.3f, 0.5f}, {0f, 0.35f}, {0.32f, 0.52f}, {0.62f, 0.32f}, {-0.45f, 0.72f},
                    {-0.12f, 0.78f}, {0.2f, 0.75f}, {0.5f, 0.68f}, {-0.78f, 0.55f}, {0.8f, 0.55f}, {-0.2f, 0.12f}, {0.25f, 0.14f}};
            for (float[] s : seeds) oval(c, (s[0] - 0.045f) * r, (s[1] - 0.075f) * r, (s[0] + 0.045f) * r, (s[1] + 0.075f) * r, 0xFFFFF0A8, 0f);
                        leaf(c, -0.1f * r, 0.9f * r, 0.28f * r, -35f, 0xFF7FCB78);
            leaf(c, 0.1f * r, 0.9f * r, 0.28f * r, 35f, 0xFF7FCB78);
            leaf(c, 0f, 0.9f * r, 0.3f * r, 0f, 0xFF66BB63);
        } else if (SHELL_STARRY.equals(shell)) {
            star(c, -0.5f * r, 0.45f * r, 0.17f * r, 0xFFFFE27A, r * 0.02f);
            star(c, 0.25f * r, 0.7f * r, 0.13f * r, 0xFFFFE27A, r * 0.02f);
            // crescent moon
            P.setColor(0xFFFFF3C4);
            c.drawCircle(0.55f * r, 0.4f * r, 0.18f * r, P);
            P.setColor(shellFill(SHELL_STARRY));
            c.drawCircle(0.62f * r, 0.35f * r, 0.15f * r, P);
            sparkle(c, -0.15f * r, 0.35f * r, 0.08f * r, 0xFFFFFFFF);
            sparkle(c, 0.8f * r, 0.7f * r, 0.07f * r, 0xFFFFFFFF);
            circle(c, -0.8f * r, 0.7f * r, 0.03f * r, 0xFFFFFFFF, 0f);
            circle(c, 0.05f * r, 0.88f * r, 0.03f * r, 0xFFFFFFFF, 0f);
        } else {
            P.setColor(0xFFF27C7C);
            c.drawCircle(-0.55f * r, 0.4f * r, 0.13f * r, P);
            P.setColor(0xFFF7A07C);
            c.drawCircle(0.62f * r, 0.42f * r, 0.1f * r, P);
            PA.reset();
            PA.moveTo(-0.05f * r, 0.28f * r);
            PA.lineTo(0.3f * r, 0.62f * r);
            PA.lineTo(-0.2f * r, 0.66f * r);
            PA.close();
            P.setColor(0xFF8FD0F0);
            c.drawPath(PA, P);
        }
    }

    // =================================================================== ICON PICTURES (shop + bag)
    /** Mochi's cream dome (the "shell" on his head) in pet space. */
    private static void dome(Canvas c, float r, int fill, float sw) {
        PA.reset();
        PA.moveTo(-0.95f * r, -0.05f * r);
        PA.lineTo(-0.6f * r, -0.25f * r); PA.lineTo(-0.3f * r, -0.02f * r);
        PA.lineTo(0f, -0.25f * r);       PA.lineTo(0.3f * r, -0.02f * r);
        PA.lineTo(0.6f * r, -0.25f * r); PA.lineTo(0.95f * r, -0.05f * r);
        PA.cubicTo(1.15f * r, -0.9f * r, 0.5f * r, -1.25f * r, 0f, -1.2f * r);
        PA.cubicTo(-0.5f * r, -1.25f * r, -1.15f * r, -0.9f * r, -0.95f * r, -0.05f * r);
        PA.close();
        fs(c, PA, fill, sw);
    }

    private static void miniFace(Canvas c, float r) {
        circle(c, -0.34f * r, -0.55f * r, 0.06f * r, INK, 0f);
        circle(c, 0.34f * r, -0.55f * r, 0.06f * r, INK, 0f);
        P.setStyle(Paint.Style.FILL);
        P.setColor(Color.argb(120, 255, 120, 150));
        R.set(-0.66f * r, -0.42f * r, -0.42f * r, -0.3f * r);
        c.drawOval(R, P);
        R.set(0.42f * r, -0.42f * r, 0.66f * r, -0.3f * r);
        c.drawOval(R, P);
        PA.reset();
        PA.moveTo(-0.07f * r, -0.42f * r);
        PA.quadTo(0f, -0.33f * r, 0.07f * r, -0.42f * r);
        stroke(c, PA, INK, Math.max(1.2f, r * 0.04f));
    }

    /** A hat shown on a tiny Mochi head, centred in a box of size `size` at (cx, cy). */
    public static void hatIcon(Canvas c, String name, float cx, float cy, float size) {
        float r = size * 0.35f;
        float top = hatTop(name);
        c.save();
        c.translate(cx, cy - top * r * 0.5f + r * 0.12f);
        float sw = r * 0.045f;
        dome(c, r, 0xFFF9E7A0, sw);
        miniFace(c, r);
        hat(c, name, r);
        c.restore();
    }

    /** A shell shown as Mochi's egg body with the cream dome on top, centred at (cx, cy). */
    public static void shellIcon(Canvas c, String shell, float cx, float cy, float size) {
        float r = size * 0.36f;
        c.save();
        c.translate(cx, cy + r * 0.12f);
        float sw = r * 0.045f;
        // feet
        for (int s = -1; s <= 1; s += 2) oval(c, s * 0.45f * r - 0.17f * r, 0.8f * r, s * 0.45f * r + 0.17f * r, 1.0f * r, 0xFFF9E7A0, sw);
        PC.reset();
        R.set(-r, -0.85f * r, r, 0.95f * r);
        PC.addOval(R, Path.Direction.CW);
        fs(c, PC, shellFill(shell), sw);
        int sv = c.save();
        c.clipPath(PC);
        shellPattern(c, shell, r);
        c.restoreToCount(sv);
        stroke(c, PC, edge(shellFill(shell)), sw);
        dome(c, r, 0xFFF9E7A0, sw);
        miniFace(c, r);
        c.restore();
    }

    /** Picture for any shop / bag item. type: "food", "hat" or "shell". */
    public static void icon(Canvas c, String type, String name, float cx, float cy, float size) {
        if ("hat".equals(type)) hatIcon(c, name, cx, cy, size);
        else if ("shell".equals(type)) shellIcon(c, name, cx, cy, size);
        else food(c, name, cx, cy, size * 0.36f, true);
    }

    // =================================================================== MOCHI ICONS (notifications, widget)
    /**
     * Mochi's little portrait for notifications and the widget. kind: happy, sleep, awake, sad, hungry, level.
     * Drawn on a soft pastel plate, centred at (cx, cy) in a box of `size`.
     */
    public static void mochiIcon(Canvas c, String kind, float cx, float cy, float size) {
        int plate = 0xFFFFE3EE, plateEdge = 0xFFF5B8CF;
        if ("sleep".equals(kind)) { plate = 0xFFE4E9FA; plateEdge = 0xFFB9C4EE; }
        else if ("awake".equals(kind) || "level".equals(kind)) { plate = 0xFFFFF3C9; plateEdge = 0xFFF0D27A; }
        else if ("hungry".equals(kind)) { plate = 0xFFFFE9D6; plateEdge = 0xFFF5C49A; }
        P.setStyle(Paint.Style.FILL);
        P.setColor(plate);
        c.drawCircle(cx, cy, size * 0.48f, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(size * 0.03f);
        P.setColor(plateEdge);
        c.drawCircle(cx, cy, size * 0.46f, P);
        P.setStyle(Paint.Style.FILL);

        float r = size * 0.3f;
        c.save();
        c.translate(cx, cy + r * 0.28f);
        float sw = r * 0.05f;
        for (int s = -1; s <= 1; s += 2) oval(c, s * 0.45f * r - 0.17f * r, 0.8f * r, s * 0.45f * r + 0.17f * r, 1.0f * r, 0xFFF9E7A0, sw);
        PC.reset();
        R.set(-r, -0.85f * r, r, 0.95f * r);
        PC.addOval(R, Path.Direction.CW);
        fs(c, PC, 0xFFFFFDF4, sw);
        int sv = c.save();
        c.clipPath(PC);
        shellPattern(c, SHELL_DEFAULT, r);
        c.restoreToCount(sv);
        stroke(c, PC, edge(0xFFFFFDF4), sw);
        // hands always stay at his sides, exactly where Mochi's hands are in the game (never up at his head)
        for (int s = -1; s <= 1; s += 2) {
            oval(c, s * 0.98f * r - 0.16f * r, -0.2f * r, s * 0.98f * r + 0.16f * r, 0.2f * r, 0xFFF9E7A0, sw);
        }
        dome(c, r, 0xFFF9E7A0, sw);
        // faces
        float ex = 0.34f * r, ey = -0.55f * r;
        P.setStyle(Paint.Style.FILL);
        switch (kind == null ? "happy" : kind) {
            case "sleep": {
                for (int s = -1; s <= 1; s += 2) {
                    PA.reset();
                    PA.moveTo(s * ex - 0.11f * r, ey);
                    PA.quadTo(s * ex, ey + 0.1f * r, s * ex + 0.11f * r, ey);
                    stroke(c, PA, INK, r * 0.05f);
                }
                blushes(c, r);
                oval(c, -0.05f * r, -0.4f * r, 0.05f * r, -0.3f * r, INK, 0f);
                break;
            }
            case "sad": {
                for (int s = -1; s <= 1; s += 2) {
                    circle(c, s * ex, ey, 0.085f * r, INK, 0f);
                    shine(c, s * ex - 0.03f * r, ey - 0.03f * r, 0.03f * r, 0.03f * r, 240);
                    line(c, s * (ex + 0.12f * r), ey - 0.2f * r, s * (ex - 0.1f * r), ey - 0.13f * r, r * 0.04f, INK);
                }
                oval(c, ex + 0.1f * r, ey + 0.1f * r, ex + 0.18f * r, ey + 0.26f * r, 0xFFAEDDF5, 0f);   // tear
                PA.reset();
                PA.moveTo(-0.1f * r, -0.28f * r);
                PA.quadTo(0f, -0.38f * r, 0.1f * r, -0.28f * r);
                stroke(c, PA, INK, r * 0.045f);
                blushes(c, r);
                break;
            }
            case "hungry": {
                for (int s = -1; s <= 1; s += 2) {
                    circle(c, s * ex, ey, 0.085f * r, INK, 0f);
                    shine(c, s * ex - 0.03f * r, ey - 0.03f * r, 0.03f * r, 0.03f * r, 240);
                }
                oval(c, -0.1f * r, -0.4f * r, 0.1f * r, -0.15f * r, 0xFFF08A8A, sw * 0.6f);   // open mouth
                oval(c, 0.12f * r, -0.22f * r, 0.2f * r, -0.05f * r, 0xFFAEDDF5, 0f);          // drool
                blushes(c, r);
                break;
            }
            case "awake": {
                for (int s = -1; s <= 1; s += 2) {
                    circle(c, s * ex, ey, 0.1f * r, INK, 0f);
                    shine(c, s * ex - 0.035f * r, ey - 0.04f * r, 0.035f * r, 0.035f * r, 245);
                    shine(c, s * ex + 0.03f * r, ey + 0.03f * r, 0.02f * r, 0.02f * r, 220);
                }
                PA.reset();
                PA.moveTo(-0.12f * r, -0.36f * r);
                PA.quadTo(0f, -0.2f * r, 0.12f * r, -0.36f * r);
                stroke(c, PA, INK, r * 0.045f);
                blushes(c, r);
                sparkle(c, -1.05f * r, -1.35f * r, 0.18f * r, 0xFFFFD04F);
                sparkle(c, 1.05f * r, -1.25f * r, 0.14f * r, 0xFFFFD04F);
                break;
            }
            case "level": {
                for (int s = -1; s <= 1; s += 2) {
                    PA.reset();
                    PA.moveTo(s * ex - 0.11f * r, ey + 0.04f * r);
                    PA.quadTo(s * ex, ey - 0.12f * r, s * ex + 0.11f * r, ey + 0.04f * r);
                    stroke(c, PA, INK, r * 0.05f);
                }
                PA.reset();
                PA.moveTo(-0.14f * r, -0.38f * r);
                PA.quadTo(0f, -0.18f * r, 0.14f * r, -0.38f * r);
                stroke(c, PA, INK, r * 0.045f);
                blushes(c, r);
                star(c, 0f, -1.62f * r, 0.26f * r, 0xFFFFD04F, sw);
                break;
            }
            default: {
                for (int s = -1; s <= 1; s += 2) {
                    circle(c, s * ex, ey, 0.085f * r, INK, 0f);
                    shine(c, s * ex - 0.03f * r, ey - 0.03f * r, 0.03f * r, 0.03f * r, 240);
                }
                PA.reset();
                PA.moveTo(-0.1f * r, -0.36f * r);
                PA.quadTo(0f, -0.22f * r, 0.1f * r, -0.36f * r);
                stroke(c, PA, INK, r * 0.045f);
                blushes(c, r);
            }
        }
        c.restore();
        if ("sleep".equals(kind)) {
            P.setColor(0xFF8FA0E0);
            heart(c, cx + size * 0.30f, cy - size * 0.28f, size * 0.05f, 0xFF8FA0E0);
            sparkle(c, cx + size * 0.34f, cy - size * 0.34f, size * 0.06f, 0xFF8FA0E0);
        }
        if ("hungry".equals(kind)) heart(c, cx + size * 0.32f, cy - size * 0.3f, size * 0.05f, 0xFFF3779F);
    }

    private static void blushes(Canvas c, float r) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(Color.argb(130, 255, 120, 150));
        R.set(-0.66f * r, -0.42f * r, -0.42f * r, -0.28f * r);
        c.drawOval(R, P);
        R.set(0.42f * r, -0.42f * r, 0.66f * r, -0.28f * r);
        c.drawOval(R, P);
    }
}
