package com.example.pocketpet.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Full-screen storybook background: pastel sky, sun, drifting clouds, hills, flowers and floating petals. */
public class GhibliSkyView extends View {
    private static final int PETALS = 14, FLOWERS = 22;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path far = new Path(), near = new Path();
    private final RectF r = new RectF();
    private final float[] pfx = new float[PETALS], pfy = new float[PETALS], pph = new float[PETALS], psp = new float[PETALS];
    private final float[] ffx = new float[FLOWERS], ffy = new float[FLOWERS];
    private final int[] fcol = new int[FLOWERS];
    private LinearGradient sky;
    private final float[] cx = {0.08f, 0.42f, 0.75f, 0.25f};
    private final float[] cy = {0.14f, 0.27f, 0.09f, 0.40f};
    private final float[] cs = {1.1f, 0.8f, 0.95f, 0.65f};
    private final float[] cv = {7f, 4f, 6f, 3f};

    public GhibliSkyView(Context c) { this(c, null); }

    public GhibliSkyView(Context c, AttributeSet a) {
        super(c, a);
        Random g = new Random(3);
        for (int i = 0; i < PETALS; i++) {
            pfx[i] = g.nextFloat(); pfy[i] = g.nextFloat(); pph[i] = g.nextFloat() * 6.28f; psp[i] = 0.015f + g.nextFloat() * 0.02f;
        }
        int[] cols = {Color.parseColor("#FFB8CC"), Color.WHITE, Color.parseColor("#FFE49A"), Color.parseColor("#D9CCF2")};
        for (int i = 0; i < FLOWERS; i++) {
            ffx[i] = g.nextFloat(); ffy[i] = g.nextFloat(); fcol[i] = cols[g.nextInt(cols.length)];
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        sky = new LinearGradient(0f, 0f, 0f, h,
                new int[]{Color.parseColor("#B7DFF5"), Color.parseColor("#E3EFF8"), Color.parseColor("#FFE8D4")},
                null, Shader.TileMode.CLAMP);
        far.reset();
        far.moveTo(0f, h * 0.80f);
        far.quadTo(w * 0.25f, h * 0.66f, w * 0.55f, h * 0.78f);
        far.quadTo(w * 0.82f, h * 0.88f, w, h * 0.74f);
        far.lineTo(w, h); far.lineTo(0f, h); far.close();
        near.reset();
        near.moveTo(0f, h * 0.90f);
        near.quadTo(w * 0.3f, h * 0.81f, w * 0.6f, h * 0.91f);
        near.quadTo(w * 0.85f, h * 0.97f, w, h * 0.87f);
        near.lineTo(w, h); near.lineTo(0f, h); near.close();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
        float t = SystemClock.uptimeMillis() / 1000f;

        p.setStyle(Paint.Style.FILL);
        p.setShader(sky);
        c.drawRect(0f, 0f, w, h, p);
        p.setShader(null);

        // soft sun
        float sx = w * 0.18f, sy = h * 0.11f, sr = 26f * d;
        p.setColor(Color.argb(40, 255, 224, 140));
        c.drawCircle(sx, sy, sr * 2.2f, p);
        p.setColor(Color.argb(70, 255, 224, 140));
        c.drawCircle(sx, sy, sr * 1.5f, p);
        p.setColor(Color.parseColor("#FFEBA8"));
        c.drawCircle(sx, sy, sr, p);

        // clouds
        c.save();
        c.scale(d, d);
        float dw = w / d;
        for (int i = 0; i < cx.length; i++) {
            float cw = 80f * cs[i];
            float x = (cx[i] * dw + t * cv[i]) % (dw + cw * 2f) - cw;
            Sprites.cloud(c, p, x, cy[i] * (h / d), cw, 18f * cs[i], Color.argb(240, 255, 255, 255), Color.argb(80, 255, 255, 255));
        }
        c.restore();

        // hills + flowers
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#C4E7CB"));
        c.drawPath(far, p);
        p.setColor(GameTheme.GRASS);
        c.drawPath(near, p);
        for (int i = 0; i < FLOWERS; i++) {
            p.setColor(fcol[i]);
            c.drawCircle(ffx[i] * w, h * (0.925f + 0.06f * ffy[i]), 2.6f * d, p);
        }

        // floating petals
        for (int i = 0; i < PETALS; i++) {
            float y = ((pfy[i] - t * psp[i]) % 1f + 1f) % 1f * h;
            float x = pfx[i] * w + (float) Math.sin(t * 0.7f + pph[i]) * 22f * d;
            p.setColor(Color.argb(150, 255, 190, 205));
            c.save();
            c.rotate((float) Math.sin(t + pph[i]) * 40f, x, y);
            r.set(x - 3.5f * d, y - 2f * d, x + 3.5f * d, y + 2f * d);
            c.drawOval(r, p);
            c.restore();
        }
        postInvalidateOnAnimation();
    }
}
