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
        float r = Math.min(getWidth(), getHeight()) * 0.42f;
        ItemArt.food(c, foodName, getWidth() * 0.5f, getHeight() * 0.5f, r, true);
    }
}
