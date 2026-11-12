package com.example.pocketpet.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Little storybook icons for the game menu. Call setType() with one of the constants. */
public class GameIconView extends View {
    public static final int TIC_TAC_TOE = 0, MINESWEEPER = 1, JUMP = 2, HIDE_SEEK = 3, CATCH = 4;
    private int type = TIC_TAC_TOE;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public GameIconView(Context c) { this(c, null); }
    public GameIconView(Context c, AttributeSet a) { super(c, a); }

    public void setType(int t) { type = t; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float d = getResources().getDisplayMetrics().density;
        float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
        int[] bg = {Color.parseColor("#FFE3D0"), Color.parseColor("#E4DAF8"), Color.parseColor("#CDEBFA"),
                    Color.parseColor("#D3F0DA"), Color.parseColor("#FFF0BF")};

        c.save();
        c.translate(ox, oy);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg[type]);
        r.set(0, 0, s, s);
        c.drawRoundRect(r, s * 0.24f, s * 0.24f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * d);
        p.setColor(GameTheme.TAN);
        r.set(1.2f * d, 1.2f * d, s - 1.2f * d, s - 1.2f * d);
        c.drawRoundRect(r, s * 0.24f, s * 0.24f, p);

        switch (type) {
            case TIC_TAC_TOE: drawTtt(c, s, d); break;
            case MINESWEEPER: drawMine(c, s, d); break;
            case JUMP:        drawJump(c, s, d); break;
            case HIDE_SEEK:   drawHide(c, s, d); break;
            default:          drawCatch(c, s, d); break;
        }
        c.restore();
    }

    private void drawTtt(Canvas c, float s, float d) {
        float g = s * 0.17f, cell = (s - 2 * g) / 3f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(((row + col) % 2 == 0) ? GameTheme.CREAM : GameTheme.PINK_2);
                r.set(g + col * cell + d, g + row * cell + d, g + (col + 1) * cell - d, g + (row + 1) * cell - d);
                c.drawRoundRect(r, 4 * d, 4 * d, p);
            }
        }
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3.2f * d);
        p.setColor(GameTheme.USER);
        float m = cell * 0.24f, cx = g + cell * 0.5f, cy = g + cell * 0.5f;
        c.drawLine(cx - m, cy - m, cx + m, cy + m, p);
        c.drawLine(cx + m, cy - m, cx - m, cy + m, p);
        cx = g + cell * 2.5f; cy = g + cell * 2.5f;
        c.drawLine(cx - m, cy - m, cx + m, cy + m, p);
        c.drawLine(cx + m, cy - m, cx - m, cy + m, p);
        p.setColor(GameTheme.CPU);
        c.drawCircle(g + cell * 1.5f, g + cell * 1.5f, m * 1.05f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawMine(Canvas c, float s, float d) {
        float g = s * 0.17f, cell = (s - 2 * g) / 3f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                boolean center = row == 1 && col == 1;
                p.setStyle(Paint.Style.FILL);
                p.setColor(center ? GameTheme.OPEN_BOOM : GameTheme.TILE_BASE);
                r.set(g + col * cell + d, g + row * cell + d, g + (col + 1) * cell - d, g + (row + 1) * cell - d);
                c.drawRoundRect(r, 4 * d, 4 * d, p);
                if (!center) {
                    p.setColor(GameTheme.TILE_TOP);
                    r.set(g + col * cell + 2.5f * d, g + row * cell + 2.5f * d,
                          g + (col + 1) * cell - 2.5f * d, g + row * cell + cell * 0.45f);
                    c.drawRoundRect(r, 3 * d, 3 * d, p);
                }
            }
        }
        Sprites.soot(c, p, s / 2f, s / 2f, cell * 0.28f, 0f);
    }

    private void drawJump(Canvas c, float s, float d) {
        Sprites.cloud(c, p, s * 0.18f, s * 0.72f, s * 0.5f, s * 0.12f, Color.WHITE, Color.parseColor("#E4D3C0"));
        Sprites.cloud(c, p, s * 0.52f, s * 0.38f, s * 0.3f, s * 0.08f, Color.parseColor("#E8E0FB"), Color.parseColor("#D3C8EE"));
        Sprites.mochi(c, p, s * 0.40f, s * 0.52f, s * 0.2f, Sprites.HAPPY, 1f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(GameTheme.GOLD);
        c.drawCircle(s * 0.78f, s * 0.2f, 2.5f * d, p);
        c.drawCircle(s * 0.2f, s * 0.25f, 2f * d, p);
    }

    private void drawHide(Canvas c, float s, float d) {
        // bush with two eyes peeking out
        p.setStyle(Paint.Style.FILL);
        Sprites.mochi(c, p, s * 0.5f, s * 0.45f, s * 0.2f, Sprites.NORMAL, 1f);
        p.setColor(Color.parseColor("#7CC38E"));
        c.drawCircle(s * 0.30f, s * 0.68f, s * 0.17f, p);
        c.drawCircle(s * 0.50f, s * 0.62f, s * 0.2f, p);
        c.drawCircle(s * 0.70f, s * 0.68f, s * 0.17f, p);
        r.set(s * 0.18f, s * 0.68f, s * 0.82f, s * 0.84f);
        c.drawRoundRect(r, s * 0.08f, s * 0.08f, p);
        p.setColor(Color.parseColor("#A6DDB2"));
        c.drawCircle(s * 0.44f, s * 0.57f, s * 0.05f, p);
    }

    private void drawCatch(Canvas c, float s, float d) {
        Sprites.strawberry(c, p, s * 0.32f, s * 0.28f, s * 0.12f);
        Sprites.cookie(c, p, s * 0.72f, s * 0.38f, s * 0.1f);
        Sprites.mochi(c, p, s * 0.5f, s * 0.68f, s * 0.2f, Sprites.OPEN, 1f);
    }
}
