package com.example.pocketpet.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Pastel storybook sky + drifting clouds + rolling hills used inside the solo game boards. */
final class Scenery {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hill = new Path();
    private LinearGradient sky;
    private static final float[] CX = {0.10f, 0.45f, 0.78f, 0.28f, 0.92f};
    private static final float[] CY = {0.12f, 0.24f, 0.08f, 0.46f, 0.38f};
    private static final float[] CS = {1.0f, 0.75f, 0.9f, 0.6f, 0.7f};
    private static final float[] CV = {6f, 4f, 7f, 3f, 5f};

    void resize(float h) {
        sky = new LinearGradient(0f, 0f, 0f, h,
                new int[]{Color.parseColor("#B9E0F6"), Color.parseColor("#E1EEF8"), Color.parseColor("#FFE9D6")},
                null, Shader.TileMode.CLAMP);
    }

    /** t = seconds, scroll = extra vertical parallax (jump game). */
    void drawSky(Canvas c, float w, float h, float t, float scroll) {
        if (sky == null) resize(h);
        p.setStyle(Paint.Style.FILL);
        p.setShader(sky);
        c.drawRect(0f, 0f, w, h, p);
        p.setShader(null);
        for (int i = 0; i < CX.length; i++) {
            float cw = 60f * CS[i];
            float x = (CX[i] * w + t * CV[i]) % (w + cw * 2f) - cw;
            float y = CY[i] * h + scroll;
            if (scroll != 0f) y = ((y % (h + 60f)) + (h + 60f)) % (h + 60f) - 30f;
            Sprites.cloud(c, p, x, y, cw, 14f * CS[i], Color.argb(235, 255, 255, 255), Color.argb(90, 255, 255, 255));
        }
    }

    /** Two rolling hills along the bottom, the nearer one starting at groundTop. */
    void drawHills(Canvas c, float w, float h, float groundTop) {
        p.setStyle(Paint.Style.FILL);
        float gh = h - groundTop;
        p.setColor(Color.parseColor("#C4E7CB"));
        hill.reset();
        hill.moveTo(0f, groundTop + gh * 0.1f);
        hill.quadTo(w * 0.25f, groundTop - gh * 0.55f, w * 0.55f, groundTop + gh * 0.05f);
        hill.quadTo(w * 0.8f, groundTop + gh * 0.4f, w, groundTop - gh * 0.2f);
        hill.lineTo(w, h);
        hill.lineTo(0f, h);
        hill.close();
        c.drawPath(hill, p);
        p.setColor(GameTheme.GRASS);
        hill.reset();
        hill.moveTo(0f, groundTop + gh * 0.45f);
        hill.quadTo(w * 0.3f, groundTop + gh * 0.05f, w * 0.6f, groundTop + gh * 0.4f);
        hill.quadTo(w * 0.85f, groundTop + gh * 0.65f, w, groundTop + gh * 0.3f);
        hill.lineTo(w, h);
        hill.lineTo(0f, h);
        hill.close();
        c.drawPath(hill, p);
    }
}
