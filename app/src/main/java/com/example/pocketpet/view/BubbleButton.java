package com.example.pocketpet.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatButton;

import com.example.pocketpet.R;

/**
 * A normal Button (all click / enabled / setText behaviour is unchanged) whose label is drawn as
 * chunky "bubble" text: the fill colour with a darker outline around every letter.
 * Used by the kawaii HUD buttons (Feed, Play, Sleep, Bath, Shop, Bag, Stats, Share, Settings).
 */
public class BubbleButton extends AppCompatButton {
    private int outlineColor = 0xFF5B4636;
    private float outlineWidth;
    private boolean drawingOutline;

    public BubbleButton(Context c) { this(c, null); }

    public BubbleButton(Context c, AttributeSet a) { this(c, a, androidx.appcompat.R.attr.buttonStyle); }

    public BubbleButton(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        outlineWidth = 3f * c.getResources().getDisplayMetrics().density;
        if (a != null) {
            TypedArray t = c.obtainStyledAttributes(a, R.styleable.BubbleButton, defStyle, 0);
            outlineColor = t.getColor(R.styleable.BubbleButton_outlineColor, outlineColor);
            outlineWidth = t.getDimension(R.styleable.BubbleButton_outlineWidth, outlineWidth);
            t.recycle();
        }
    }

    /** Change the outline colour / width in code (e.g. for a selected vs unselected tab). */
    public void setOutline(int color, float widthPx) {
        outlineColor = color;
        outlineWidth = widthPx;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (outlineWidth <= 0f || getLayout() == null) {
            super.onDraw(canvas);
            return;
        }
        TextPaint tp = getPaint();
        ColorStateList fill = getTextColors();
        drawingOutline = true;                      // setTextColor() would otherwise re-invalidate us forever
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeJoin(Paint.Join.ROUND);
        tp.setStrokeWidth(outlineWidth);
        setTextColor(outlineColor);
        super.onDraw(canvas);                       // outline pass
        tp.setStyle(Paint.Style.FILL);
        tp.setStrokeWidth(0f);
        setTextColor(fill);
        super.onDraw(canvas);                       // fill pass
        drawingOutline = false;
    }

    @Override
    public void invalidate() {
        if (!drawingOutline) super.invalidate();
    }
}
