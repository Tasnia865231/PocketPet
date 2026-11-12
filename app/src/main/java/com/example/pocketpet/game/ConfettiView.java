package com.example.pocketpet.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Falling confetti for a win. Call start() to play it once. */
public class ConfettiView extends View {
    private static final int N = 110;
    private static final int[] COLORS = {
            Color.parseColor("#FFB8CC"), Color.parseColor("#BFE8D0"), Color.parseColor("#FFE49A"),
            Color.parseColor("#B9DFF5"), Color.parseColor("#D9CCF2"), Color.parseColor("#FFD6C0")};

    private final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N];
    private final float[] rot = new float[N], vr = new float[N], sz = new float[N];
    private final int[] col = new int[N];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private boolean running = false;
    private long last;

    public ConfettiView(Context c) { this(c, null); }
    public ConfettiView(Context c, AttributeSet a) { super(c, a); }

    public void start() {
        float w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < N; i++) {
            x[i] = rnd.nextFloat() * w;
            y[i] = -rnd.nextFloat() * h * 0.6f;
            vx[i] = (rnd.nextFloat() - 0.5f) * 90f * d;
            vy[i] = (150f + rnd.nextFloat() * 220f) * d;
            rot[i] = rnd.nextFloat() * 360f;
            vr[i] = (rnd.nextFloat() - 0.5f) * 500f;
            sz[i] = (5f + rnd.nextFloat() * 6f) * d;
            col[i] = COLORS[rnd.nextInt(COLORS.length)];
        }
        running = true;
        last = SystemClock.uptimeMillis();
        setVisibility(VISIBLE);
        invalidate();
    }

    public void stop() { running = false; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        if (!running) return;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        boolean any = false;
        float h = getHeight();
        for (int i = 0; i < N; i++) {
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            rot[i] += vr[i] * dt;
            if (y[i] < h + 30) any = true;
            if (y[i] < -20 || y[i] > h + 30) continue;
            c.save();
            c.rotate(rot[i], x[i], y[i]);
            p.setColor(col[i]);
            c.drawRect(x[i] - sz[i], y[i] - sz[i] * 0.5f, x[i] + sz[i], y[i] + sz[i] * 0.5f, p);
            c.restore();
        }
        if (any) postInvalidateOnAnimation(); else running = false;
    }
}
