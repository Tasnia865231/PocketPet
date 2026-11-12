package com.example.pocketpet.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.example.pocketpet.view.ItemArt;

/**
 * The picture of a shop / bag item: a soft pastel plate with sparkles and the item drawn on top in the
 * Cute Kawaii Storybook style (see ItemArt). Locked items are veiled and show a little padlock.
 */
public class ItemIconView extends View {
    private String type = "food", name = "";
    private boolean locked;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public ItemIconView(Context c) { this(c, null); }
    public ItemIconView(Context c, AttributeSet a) { super(c, a); }

    public void setItem(String type, String name, boolean locked) {
        this.type = type == null ? "food" : type;
        this.name = name == null ? "" : name;
        this.locked = locked;
        invalidate();
    }

    private int plateColor() {
        if ("hat".equals(type)) return 0xFFE6F2FB;
        if ("shell".equals(type)) return 0xFFEFE6FA;
        return 0xFFFFE9F0;
    }

    private int plateEdge() {
        if ("hat".equals(type)) return 0xFFC3DDF0;
        if ("shell".equals(type)) return 0xFFD9C9F0;
        return 0xFFF7C9D8;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, s = Math.min(w, h);
        // plate
        p.setStyle(Paint.Style.FILL);
        p.setColor(plateColor());
        c.drawCircle(cx, cy, s * 0.46f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.025f);
        p.setColor(plateEdge());
        c.drawCircle(cx, cy, s * 0.46f, p);
        p.setStyle(Paint.Style.FILL);
        // tiny sparkles + hearts around the plate
        ItemArt.sparkle(c, cx + s * 0.40f, cy - s * 0.36f, s * 0.07f, 0xCCFFFFFF);
        ItemArt.sparkle(c, cx - s * 0.42f, cy + s * 0.30f, s * 0.05f, 0xCCFFFFFF);
        p.setColor(Color.argb(150, 255, 150, 180));
        heart(c, cx - s * 0.40f, cy - s * 0.34f, s * 0.045f);

        ItemArt.icon(c, type, name, cx, cy, s * 0.86f);

        if (locked) {
            p.setColor(Color.argb(150, 246, 235, 212));
            c.drawCircle(cx, cy, s * 0.46f, p);
            // padlock
            float lw = s * 0.26f, lh = s * 0.2f, ly = cy + s * 0.02f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.045f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFF9A8470);
            rect.set(cx - lw * 0.33f, ly - lh * 1.05f, cx + lw * 0.33f, ly + lh * 0.2f);
            c.drawArc(rect, 180f, 180f, false, p);
            c.drawLine(cx - lw * 0.33f, ly - lh * 0.45f, cx - lw * 0.33f, ly, p);
            c.drawLine(cx + lw * 0.33f, ly - lh * 0.45f, cx + lw * 0.33f, ly, p);
            p.setStyle(Paint.Style.FILL);
            rect.set(cx - lw * 0.5f, ly - lh * 0.1f, cx + lw * 0.5f, ly + lh * 0.9f);
            p.setColor(0xFFB9A58E);
            c.drawRoundRect(rect, lw * 0.18f, lw * 0.18f, p);
            p.setColor(0xFF7E6A57);
            c.drawCircle(cx, ly + lh * 0.38f, lw * 0.09f, p);
        }
    }

    private void heart(Canvas c, float x, float y, float s) {
        path.reset();
        path.moveTo(x, y + s * 0.85f);
        path.cubicTo(x - s * 1.45f, y - s * 0.05f, x - s * 0.85f, y - s * 1.15f, x, y - s * 0.38f);
        path.cubicTo(x + s * 0.85f, y - s * 1.15f, x + s * 1.45f, y - s * 0.05f, x, y + s * 0.85f);
        path.close();
        c.drawPath(path, p);
    }
}
