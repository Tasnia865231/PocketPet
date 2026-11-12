package com.example.pocketpet.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.core.graphics.ColorUtils;

import com.example.pocketpet.weather.WeatherType;

import java.util.Random;

/**
 * The view through every window in the room (living room and bedroom), in the Cute Kawaii
 * Storybook style: pastel sky, rolling flower meadow, puffy clouds, a crescent moon at night.
 *
 * The scenery itself is fixed (it matches the reference pictures); only the WEATHER on top of it
 * changes, driven by the Open-Meteo result held in {@link WeatherType}.
 */
final class KawaiiWeather {
    private static final int FLOWERS = 46, DROPS = 46, SNOW = 52, STARS = 14;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[][] flower = new float[FLOWERS][4];   // x, y (0..1 in the meadow), size, colour index
    private final float[][] drop = new float[DROPS][3];
    private final float[][] snow = new float[SNOW][3];
    // night stars, measured from the reference picture: x, y (0..1 of the glass), size, kind (1 = twinkle, 0 = dot)
    private final float[][] star = {
            {0.06f, 0.11f, 0.4f, 0}, {0.10f, 0.22f, 1.0f, 1}, {0.17f, 0.32f, 0.9f, 1}, {0.29f, 0.07f, 0.5f, 0},
            {0.42f, 0.35f, 1.0f, 1}, {0.51f, 0.22f, 0.4f, 0}, {0.565f, 0.096f, 0.9f, 1}, {0.62f, 0.32f, 1.0f, 1},
            {0.72f, 0.043f, 0.5f, 0}, {0.80f, 0.36f, 0.9f, 1}, {0.877f, 0.067f, 0.5f, 0}, {0.918f, 0.37f, 1.0f, 1},
            {0.945f, 0.24f, 0.5f, 0}, {0.36f, 0.05f, 0.4f, 0}
    };
    private static final int[] FLOWER_COL = {0xFFF2677F, 0xFFFFA9C4, 0xFFFFDA5E, 0xFFFFFFFF, 0xFFB9A0EA, 0xFFFF9A6B};

    private LinearGradient grad;
    private WeatherType gradWeather;
    private float gradTop, gradH;
    private boolean gradNight;

    KawaiiWeather() {
        Random g = new Random(11);
        for (int i = 0; i < FLOWERS; i++) {
            flower[i][0] = g.nextFloat();
            flower[i][1] = g.nextFloat();
            flower[i][2] = 0.6f + g.nextFloat() * 0.8f;
            flower[i][3] = g.nextInt(FLOWER_COL.length);
        }
        for (int i = 0; i < DROPS; i++) { drop[i][0] = g.nextFloat(); drop[i][1] = g.nextFloat(); drop[i][2] = 0.6f + g.nextFloat(); }
        for (int i = 0; i < SNOW; i++) { snow[i][0] = g.nextFloat(); snow[i][1] = g.nextFloat(); snow[i][2] = 0.5f + g.nextFloat(); }
    }

    // ------------------------------------------------------------------ palette helpers
    private static int skyTop(WeatherType w) {
        switch (w) {
            case CLEAR_NIGHT: return 0xFF1A1F4C;
            case CLOUDY:      return 0xFFB4C9DA;
            case RAIN:        return 0xFF93AABF;
            case STORM:       return 0xFF6C7899;
            case SNOW:        return 0xFFD3E2F1;
            case FOG:         return 0xFFD2DCDC;
            default:          return 0xFF8FD3F3;
        }
    }

    private static int skyBottom(WeatherType w) {
        switch (w) {
            case CLEAR_NIGHT: return 0xFF182258;
            case CLOUDY:      return 0xFFE2EBF0;
            case RAIN:        return 0xFFC6D4DD;
            case STORM:       return 0xFFA2ADC4;
            case SNOW:        return 0xFFF6F9FC;
            case FOG:         return 0xFFEDF1EF;
            default:          return 0xFFDDF2F8;
        }
    }

    private static int tint(WeatherType w) {
        switch (w) {
            case STORM: return 0xFF4A5470;
            case RAIN:  return 0xFF7F93A3;
            case CLOUDY: return 0xFFA9B8BE;
            case SNOW:  return 0xFFFFFFFF;
            case FOG:   return 0xFFE8ECE8;
            default:    return 0xFF000000;
        }
    }

    private static float tintAmt(WeatherType w) {
        switch (w) {
            case STORM: return 0.50f;
            case RAIN:  return 0.38f;
            case CLOUDY: return 0.22f;
            case SNOW:  return 0.78f;
            case FOG:   return 0.50f;
            default:    return 0f;
        }
    }

    // ------------------------------------------------------------------ main entry
    /**
     * Draws the whole view into r (the caller clips to the glass shape first).
     * @param dim 0..1: extra dusk tint for daytime weather seen from the dark bedroom
     */
    void draw(Canvas c, RectF r, WeatherType w, float time, float flash, float dim) {
        float rw = r.width(), rh = r.height();
        boolean night = w == WeatherType.CLEAR_NIGHT;

        if (grad == null || gradWeather != w || Math.abs(gradH - rh) > 1f || Math.abs(gradTop - r.top) > 1f) {
            grad = new LinearGradient(0f, r.top, 0f, r.bottom, skyTop(w), skyBottom(w), Shader.TileMode.CLAMP);
            gradWeather = w; gradH = rh; gradTop = r.top;
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000);
        p.setShader(grad);
        c.drawRect(r, p);
        p.setShader(null);

        // ---- celestial body ----
        if (w == WeatherType.CLEAR_DAY) {
            float sx = r.left + rw * 0.74f, sy = r.top + rh * 0.24f, sr = rw * 0.07f;
            p.setColor(Color.argb(45, 255, 236, 150));
            c.drawCircle(sx, sy, sr * 2.3f, p);
            p.setColor(Color.argb(80, 255, 236, 150));
            c.drawCircle(sx, sy, sr * 1.6f, p);
            p.setColor(0xFFFFE58F);
            c.drawCircle(sx, sy, sr, p);
            KawaiiArt.shine(c, sx - sr * 0.3f, sy - sr * 0.35f, sr * 0.28f, sr * 0.2f, 190);
        } else if (night) {
            for (int i = 0; i < STARS; i++) {
                float a = 170 + 70 * (float) Math.sin(time * 1.6f + i * 1.7f);
                float sx = r.left + star[i][0] * rw, sy = r.top + star[i][1] * rh;
                int col = Color.argb((int) Math.max(60, Math.min(255, a)), 232, 230, 255);
                if (star[i][3] > 0.5f) KawaiiArt.sparkle(c, sx, sy, rw * 0.013f * star[i][2], col);
                else { p.setColor(Color.argb(150, 232, 230, 255)); c.drawCircle(sx, sy, rw * 0.0042f, p); }
            }
            // crescent moon with its soft halo
            float mx = r.left + rw * 0.757f, my = r.top + rh * 0.212f, mr = rw * 0.069f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(46, 170, 168, 232));
            c.drawCircle(mx, my, mr * 2.0f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, rw * 0.006f));
            p.setColor(Color.argb(54, 190, 188, 240));
            c.drawCircle(mx, my, mr * 2.0f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFD3CFEC);
            c.drawCircle(mx, my, mr, p);
            p.setColor(0xFF1C2250);
            c.drawCircle(mx + mr * 0.44f, my - mr * 0.30f, mr * 0.86f, p);
        }

        // ---- clouds ----
        int cloudCol;
        int clouds;
        switch (w) {
            case CLEAR_DAY:   clouds = 0; cloudCol = 0; break;
            case CLEAR_NIGHT: clouds = 2; cloudCol = Color.argb(130, 92, 96, 176); break;
            case STORM:       clouds = 4; cloudCol = Color.argb(240, 116, 124, 152); break;
            case RAIN:        clouds = 4; cloudCol = Color.argb(240, 150, 164, 180); break;
            case FOG:         clouds = 2; cloudCol = Color.argb(200, 240, 244, 242); break;
            case SNOW:        clouds = 3; cloudCol = Color.argb(245, 250, 252, 255); break;
            default:          clouds = 4; cloudCol = Color.argb(245, 255, 255, 255); break;
        }
        for (int i = 0; i < clouds; i++) {
            float cw = rw * (night ? (i == 0 ? 0.25f : 0.235f) : (0.30f - 0.03f * (i % 2)));
            float x, y;
            if (night) {
                x = r.left + rw * (i == 0 ? 0.255f : 0.62f) + (float) Math.sin(time * 0.3f + i) * rw * 0.008f;
                y = r.top + rh * (i == 0 ? 0.215f : 0.118f);
            } else {
                float t = ((time * 0.012f * (1f + i * 0.35f) + i * 0.29f) % 1.5f) - 0.25f;
                x = r.left + t * rw;
                y = r.top + rh * (0.12f + 0.11f * i);
            }
            if (night) {
                nightCloud(c, x, y, cw);
                continue;
            }
            KawaiiArt.cloud(c, x, y + cw * 0.05f, cw, Color.argb(70, 120, 150, 190));   // soft under-shadow
            KawaiiArt.cloud(c, x, y, cw, cloudCol);
        }

        // ---- scenery ----
        scenery(c, r, w, time, night, dim);

        // ---- precipitation / fog / lightning ----
        if (w == WeatherType.RAIN || w == WeatherType.STORM) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(2f, rw * 0.0065f));
            p.setColor(Color.argb(200, 196, 224, 248));
            for (int i = 0; i < DROPS; i++) {
                float y = r.top + ((drop[i][1] + time * (0.8f + 0.5f * drop[i][2])) % 1f) * rh;
                float x = r.left + drop[i][0] * rw - (y - r.top) * 0.05f;
                c.drawLine(x, y, x - rw * 0.007f, y + rh * 0.045f, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setStrokeCap(Paint.Cap.BUTT);
        } else if (w == WeatherType.SNOW) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < SNOW; i++) {
                float y = r.top + ((snow[i][1] + time * 0.07f * snow[i][2]) % 1f) * rh;
                float x = r.left + (snow[i][0] + (float) Math.sin(time * 0.9f + snow[i][1] * 6f) * 0.02f) * rw;
                p.setColor(Color.argb(235, 255, 255, 255));
                c.drawCircle(x, y, rw * 0.007f * (0.8f + snow[i][2]), p);
                if (i % 6 == 0) KawaiiArt.sparkle(c, x, y, rw * 0.014f, Color.argb(200, 255, 255, 255));
            }
        } else if (w == WeatherType.FOG) {
            p.setColor(Color.argb(95, 238, 242, 238));
            c.drawRect(r, p);
            p.setColor(Color.argb(70, 255, 255, 255));
            float drift = (float) Math.sin(time * 0.25f) * rw * 0.04f;
            rect.set(r.left - rw * 0.1f + drift, r.top + rh * 0.55f, r.right + rw * 0.1f + drift, r.top + rh * 0.72f);
            c.drawRoundRect(rect, rh * 0.09f, rh * 0.09f, p);
        }
        if (w == WeatherType.STORM) {
            float bx = r.left + rw * 0.68f, by = r.top + rh * 0.20f, bs = rh * 0.12f;
            float pulse = Math.max(flash, 0f);
            if (pulse > 0.15f) {
                path.reset();
                path.moveTo(bx, by);
                path.lineTo(bx - bs * 0.45f, by + bs * 0.9f);
                path.lineTo(bx - bs * 0.05f, by + bs * 0.9f);
                path.lineTo(bx - bs * 0.55f, by + bs * 2.0f);
                path.lineTo(bx + bs * 0.35f, by + bs * 0.75f);
                path.lineTo(bx - bs * 0.05f, by + bs * 0.75f);
                path.lineTo(bx + bs * 0.3f, by);
                path.close();
                KawaiiArt.fillStroke(c, path, Color.argb((int) (pulse * 255), 255, 232, 110), Color.argb((int) (pulse * 255), 255, 190, 70), bs * 0.08f);
            }
        }
        if (flash > 0f) {
            p.setColor(Color.argb((int) (flash * 150), 255, 255, 255));
            c.drawRect(r, p);
        }
        if (dim > 0f && !night) {
            p.setColor(Color.argb((int) (dim * 255), 30, 36, 92));
            c.drawRect(r, p);
        }
    }

    // ------------------------------------------------------------------ hills, flowers, tulips, tree
    private void hillPath(RectF r, float y0, float y1, float y2, float y3) {
        float rw = r.width(), rh = r.height();
        path.reset();
        path.moveTo(r.left - 2f, r.top + rh * y0);
        path.cubicTo(r.left + rw * 0.22f, r.top + rh * y1, r.left + rw * 0.42f, r.top + rh * y2, r.left + rw * 0.62f, r.top + rh * ((y1 + y2) * 0.5f + 0.02f));
        path.cubicTo(r.left + rw * 0.80f, r.top + rh * y3, r.left + rw * 0.92f, r.top + rh * y2, r.right + 2f, r.top + rh * y3);
        path.lineTo(r.right + 2f, r.bottom + 2f);
        path.lineTo(r.left - 2f, r.bottom + 2f);
        path.close();
    }

    private void scenery(Canvas c, RectF r, WeatherType w, float time, boolean night, float dim) {
        float rw = r.width(), rh = r.height();
        int tc = tint(w);
        float ta = tintAmt(w);
        int far, mid, near;
        if (night) {
            far = 0xFF2C3F5A; mid = 0xFF243850; near = 0xFF1D3047;
        } else {
            far = ColorUtils.blendARGB(0xFFA9D8A0, tc, ta);
            mid = ColorUtils.blendARGB(0xFF8FCB72, tc, ta);
            near = ColorUtils.blendARGB(0xFF79BE5C, tc, ta);
        }

        if (night) {
            // three soft wavy bands of hills, exactly like the reference picture (no tree)
            waves(c, r, 0.51f, 0.020f, 0.0f, 0xFF2A3B56);
            waves(c, r, 0.635f, 0.022f, 2.1f, 0xFF222E46);
            waves(c, r, 0.765f, 0.020f, 4.0f, 0xFF1D2943);
            // diagonal sheen on the glass
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(13, 255, 255, 255));
            path.reset();
            path.moveTo(r.left, r.top);
            path.lineTo(r.left + rw * 0.19f, r.top);
            path.lineTo(r.left, r.top + rh * 0.41f);
            path.close();
            c.drawPath(path, p);
            return;
        }

        // far hill
        hillPath(r, 0.56f, 0.47f, 0.55f, 0.50f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(far);
        c.drawPath(path, p);

        // distant little tree (shown at night in the bedroom view)
        if (night) {
            float tx = r.left + rw * 0.12f, ty = r.top + rh * 0.80f;
            p.setColor(0xFF1F4A3E);
            rect.set(tx - rw * 0.011f, ty, tx + rw * 0.011f, ty + rh * 0.12f);
            c.drawRoundRect(rect, 3f, 3f, p);
            p.setColor(0xFF2E6455);
            c.drawCircle(tx, ty - rw * 0.012f, rw * 0.062f, p);
            p.setColor(0xFF387564);
            c.drawCircle(tx - rw * 0.02f, ty - rw * 0.03f, rw * 0.028f, p);
        } else {
            // far-away flower specks on the back hill
            for (int i = 0; i < 14; i++) {
                float fx = r.left + flower[i][0] * rw;
                float fy = r.top + rh * (0.50f + 0.07f * flower[i][1]);
                p.setColor(ColorUtils.blendARGB(FLOWER_COL[(int) flower[i][3]], tc, Math.min(1f, ta + 0.25f)));
                c.drawCircle(fx, fy, rw * 0.005f, p);
            }
        }

        // mid hill
        hillPath(r, 0.66f, 0.60f, 0.70f, 0.62f);
        p.setColor(mid);
        c.drawPath(path, p);

        // front meadow
        hillPath(r, 0.80f, 0.73f, 0.80f, 0.74f);
        p.setColor(near);
        c.drawPath(path, p);

        if (!night) {
            // meadow flowers
            for (int i = 0; i < FLOWERS; i++) {
                float fx = r.left + flower[i][0] * rw;
                float fy = r.top + rh * (0.66f + 0.32f * flower[i][1]);
                float s = rw * 0.012f * flower[i][2] * (0.6f + 0.6f * ((fy - r.top) / rh));
                int col = ColorUtils.blendARGB(FLOWER_COL[(int) flower[i][3]], tc, ta * 0.8f);
                if (flower[i][3] == 3f) { // daisy
                    p.setColor(col);
                    for (int k = 0; k < 6; k++) {
                        double a = k * Math.PI / 3.0;
                        c.drawCircle(fx + (float) Math.cos(a) * s * 0.95f, fy + (float) Math.sin(a) * s * 0.95f, s * 0.55f, p);
                    }
                    p.setColor(ColorUtils.blendARGB(0xFFFFCF4A, tc, ta * 0.8f));
                    c.drawCircle(fx, fy, s * 0.55f, p);
                } else {
                    p.setColor(col);
                    c.drawCircle(fx, fy, s, p);
                    p.setColor(ColorUtils.blendARGB(0xFFFFF3B0, tc, ta));
                    c.drawCircle(fx, fy, s * 0.35f, p);
                }
            }
            // big tulips at the window corners
            tulip(c, r.left + rw * 0.07f, r.bottom - rh * 0.02f, rh * 0.20f, 0xFFE8506A, tc, ta);
            tulip(c, r.left + rw * 0.17f, r.bottom - rh * 0.01f, rh * 0.17f, 0xFF8F67C9, tc, ta);
            tulip(c, r.right - rw * 0.08f, r.bottom - rh * 0.02f, rh * 0.20f, 0xFFFFC94A, tc, ta);
            tulip(c, r.right - rw * 0.18f, r.bottom - rh * 0.01f, rh * 0.16f, 0xFFE8506A, tc, ta);
        }
    }

    /** One soft band of rolling hills: a sine edge at fraction baseFy of the glass, filled down to the bottom. */
    private void waves(Canvas c, RectF r, float baseFy, float ampFy, float phase, int color) {
        float rw = r.width(), rh = r.height();
        path.reset();
        path.moveTo(r.left - 1f, r.bottom + 1f);
        int n = 28;
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            float y = r.top + rh * (baseFy + ampFy * (float) Math.sin(u * 7.4f + phase));
            path.lineTo(r.left + rw * u + (i == 0 ? -1f : (i == n ? 1f : 0f)), y);
        }
        path.lineTo(r.right + 1f, r.bottom + 1f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(path, p);
    }

    /** Night cloud: three translucent lavender puffs on a soft base, overlaps show through like the reference. */
    private void nightCloud(Canvas c, float x, float y, float w) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(96, 104, 104, 205));
        c.drawCircle(x - w * 0.20f, y, w * 0.21f, p);
        c.drawCircle(x + w * 0.02f, y - w * 0.09f, w * 0.27f, p);
        c.drawCircle(x + w * 0.27f, y + w * 0.01f, w * 0.20f, p);
        rect.set(x - w * 0.42f, y + w * 0.02f, x + w * 0.46f, y + w * 0.19f);
        p.setColor(Color.argb(86, 100, 100, 200));
        c.drawRoundRect(rect, w * 0.09f, w * 0.09f, p);
    }

    private void tulip(Canvas c, float x, float baseY, float h, int petal, int tc, float ta) {
        int stem = ColorUtils.blendARGB(0xFF4F9A4E, tc, ta);
        int pc = ColorUtils.blendARGB(petal, tc, ta * 0.8f);
        float w = h * 0.34f;
        KawaiiArt.line(c, x, baseY, x, baseY - h * 0.62f, Math.max(2f, h * 0.06f), stem);
        path.reset();
        path.moveTo(x, baseY - h * 0.22f);
        path.quadTo(x + h * 0.26f, baseY - h * 0.30f, x + h * 0.36f, baseY - h * 0.50f);
        path.quadTo(x + h * 0.12f, baseY - h * 0.40f, x, baseY - h * 0.22f);
        KawaiiArt.fillStroke(c, path, stem, stem, 1f);
        // cup
        path.reset();
        path.moveTo(x - w, baseY - h * 0.88f);
        path.lineTo(x - w * 0.5f, baseY - h * 0.74f);
        path.lineTo(x, baseY - h * 0.90f);
        path.lineTo(x + w * 0.5f, baseY - h * 0.74f);
        path.lineTo(x + w, baseY - h * 0.88f);
        path.quadTo(x + w * 1.05f, baseY - h * 0.55f, x, baseY - h * 0.55f);
        path.quadTo(x - w * 1.05f, baseY - h * 0.55f, x - w, baseY - h * 0.88f);
        path.close();
        KawaiiArt.fillStroke(c, path, pc, KawaiiArt.edge(pc, 0.35f), Math.max(1.5f, h * 0.035f));
        KawaiiArt.shine(c, x - w * 0.45f, baseY - h * 0.72f, w * 0.14f, w * 0.26f, 150);
    }
}
