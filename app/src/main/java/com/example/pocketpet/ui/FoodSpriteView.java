package com.example.pocketpet.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Small custom View that draws a Canvas food sprite matching PetView's drawFoodByName style.
 * Set the food name via setFoodName(). No bitmaps needed.
 */
public class FoodSpriteView extends View {
    private String foodName = "";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path  = new Path();
    private final RectF rect  = new RectF();

    public FoodSpriteView(Context c) { this(c, null); }
    public FoodSpriteView(Context c, AttributeSet a) { super(c, a); }

    public void setFoodName(String name) {
        foodName = name == null ? "" : name;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float r  = Math.min(getWidth(), getHeight()) * 0.38f;
        paint.setStyle(Paint.Style.FILL);

        switch (foodName) {
            case "Berry":
                paint.setColor(Color.parseColor("#CC3366"));
                c.drawCircle(cx, cy, r, paint);
                paint.setColor(Color.parseColor("#44AA44"));
                path.reset();
                path.moveTo(cx, cy - r);
                path.lineTo(cx - r * 0.25f, cy - r * 1.3f);
                path.lineTo(cx + r * 0.1f, cy - r * 1.1f);
                path.lineTo(cx + r * 0.3f, cy - r * 1.4f);
                path.lineTo(cx + r * 0.1f, cy - r * 1.0f);
                path.close();
                c.drawPath(path, paint);
                paint.setColor(Color.argb(80, 255, 255, 255));
                c.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r * 0.25f, paint);
                break;
            case "Rice Ball":
                paint.setColor(Color.parseColor("#F8F4E8"));
                path.reset();
                path.moveTo(cx - r, cy + r * 0.3f);
                path.cubicTo(cx - r * 1.1f, cy - r, cx - r * 0.3f, cy - r * 1.3f, cx, cy - r * 1.3f);
                path.cubicTo(cx + r * 0.3f, cy - r * 1.3f, cx + r * 1.1f, cy - r, cx + r, cy + r * 0.3f);
                path.lineTo(cx - r, cy + r * 0.3f);
                path.close();
                c.drawPath(path, paint);
                paint.setColor(Color.parseColor("#333333"));
                rect.set(cx - r * 0.9f, cy, cx + r * 0.9f, cy + r * 0.55f);
                c.drawRoundRect(rect, r * 0.1f, r * 0.1f, paint);
                break;
            case "Apple":
                paint.setColor(Color.parseColor("#E83030"));
                c.drawCircle(cx, cy + r * 0.1f, r, paint);
                paint.setColor(Color.parseColor("#C82020"));
                c.drawCircle(cx, cy - r * 0.65f, r * 0.3f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * 0.12f);
                paint.setColor(Color.parseColor("#885533"));
                c.drawLine(cx, cy - r, cx + r * 0.2f, cy - r * 1.35f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#44AA22"));
                path.reset();
                path.moveTo(cx + r * 0.2f, cy - r * 1.35f);
                path.cubicTo(cx + r * 0.6f, cy - r * 1.5f, cx + r * 0.7f, cy - r * 1.1f, cx + r * 0.35f, cy - r * 1.15f);
                path.close();
                c.drawPath(path, paint);
                paint.setColor(Color.argb(80, 255, 255, 255));
                c.drawCircle(cx - r * 0.3f, cy - r * 0.1f, r * 0.2f, paint);
                break;
            case "Cake":
                paint.setColor(Color.parseColor("#F2C97A"));
                rect.set(cx - r * 0.9f, cy, cx + r * 0.9f, cy + r * 0.8f);
                c.drawRoundRect(rect, r * 0.1f, r * 0.1f, paint);
                paint.setColor(Color.parseColor("#FFFDF5"));
                rect.set(cx - r * 0.9f, cy - r * 0.25f, cx + r * 0.9f, cy + r * 0.1f);
                c.drawRoundRect(rect, r * 0.1f, r * 0.1f, paint);
                paint.setColor(Color.parseColor("#FF88AA"));
                c.drawRect(cx - r * 0.08f, cy - r * 0.8f, cx + r * 0.08f, cy - r * 0.2f, paint);
                paint.setColor(Color.parseColor("#FFEE44"));
                c.drawCircle(cx, cy - r * 0.85f, r * 0.1f, paint);
                break;
            case "Milk":
                // Glass
                paint.setColor(Color.argb(200, 240, 240, 255));
                rect.set(cx - r * 0.55f, cy - r, cx + r * 0.55f, cy + r);
                c.drawRoundRect(rect, r * 0.12f, r * 0.12f, paint);
                // Liquid
                paint.setColor(Color.argb(230, 255, 255, 255));
                rect.set(cx - r * 0.47f, cy - r * 0.55f, cx + r * 0.47f, cy + r * 0.88f);
                c.drawRect(rect, paint);
                // Outline
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * 0.08f);
                paint.setColor(Color.argb(180, 180, 180, 220));
                rect.set(cx - r * 0.55f, cy - r, cx + r * 0.55f, cy + r);
                c.drawRoundRect(rect, r * 0.12f, r * 0.12f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            default:
                paint.setColor(Color.parseColor("#FF9966"));
                c.drawCircle(cx, cy, r, paint);
                break;
        }
    }
}
