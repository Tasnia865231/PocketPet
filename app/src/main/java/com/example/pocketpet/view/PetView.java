package com.example.pocketpet.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.graphics.ColorUtils;
import com.example.pocketpet.weather.WeatherType;
import java.util.Random;

/**
 * The heart of the app: a 2D Canvas scene (Ghibli-style room + weather sky) with the pet and
 * its tilt/shake physics. Everything is drawn with Canvas primitives, so no image assets are needed.
 * To use your own artwork, replace drawPet() with layered bitmaps (see README).
 */
public class PetView extends View {

    // ---- state pushed in from the activity ----
    private WeatherType weather = WeatherType.CLEAR_DAY;
    private int stage = 0;
    private String hat = "";
    private float mood = 80f;
    private Runnable onDizzyTick;

    // ---- physics state ----
    private float gx = 0f;          // filtered gravity from accelerometer (m/s^2)
    private float gy = 9.8f;
    private float px = 0f;          // horizontal offset from room center (px)
    private float vx = 0f;
    private float squish = 0f;      // 0..1 squash when hitting a wall
    private float dizzy = 0f;       // 0..100
    private float dizzyAcc = 0f;
    private float tumble = 0f;      // rotation when upside down
    private float shakeOff = 0f;    // "shakes itself off" timer
    private boolean wasPinned = false;
    private float flash = 0f;
    private float time = 0f;
    private long last = 0L;

    private final Random rnd = new Random(7);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[][] particles = new float[70][];
    private final int outline = Color.parseColor("#8B5E4B");

    public PetView(Context c) { this(c, null); }

    public PetView(Context c, AttributeSet a) {
        super(c, a);
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new float[]{rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat()};
        }
    }

    public void setWeather(WeatherType w) { weather = w; }
    public void setStage(int s) { stage = s; }
    public void setHat(String h) { hat = h == null ? "" : h; }
    public void setMood(float m) { mood = m; }
    public void setOnDizzyTick(Runnable r) { onDizzyTick = r; }
    public void setGravity(float x, float y) { gx = x; gy = y; }
    public void addShake() {
        dizzy = Math.min(100f, dizzy + 25f);
        vx += (rnd.nextFloat() - 0.5f) * getWidth() * 1.2f;
    }
    public void cheer() { squish = 0.7f; }

    public Bitmap snapshot() {
        Bitmap b = Bitmap.createBitmap(Math.max(getWidth(), 1), Math.max(getHeight(), 1), Bitmap.Config.ARGB_8888);
        draw(new Canvas(b));
        return b;
    }

    private static float sin(float x) { return (float) Math.sin(x); }
    private static float cos(float x) { return (float) Math.cos(x); }

    private float radius() {
        return Math.min(getWidth(), getHeight()) * 0.17f * (1f + 0.12f * stage);
    }

    // =============================== physics ===============================
    private void update(float dt) {
        time += dt;
        boolean upside = gy < -4f;
        float limit = Math.max(0f, getWidth() / 2f - radius() * 1.1f);

        // tilt: the pet slides toward the low edge (accelerometer x is negative when the right edge is low)
        float ax = upside ? 0f : -gx * getWidth() * 0.12f;
        vx += ax * dt;
        if (Math.abs(gx) < 1.5f && !upside) vx += -px * 6f * dt;   // flat: walk back to the center
        vx *= Math.max(0f, 1f - 2.2f * dt);
        px += vx * dt;

        if (Math.abs(px) > limit) {                                 // wall bump
            if (Math.abs(vx) > 150f) squish = 1f;
            px = limit * Math.signum(px);
            vx = -vx * 0.25f;
        }
        boolean pinned = Math.abs(gx) > 3f && Math.abs(px) >= limit * 0.9f;
        if (pinned) squish = Math.max(squish, 0.5f);                // squished against the wall
        if (wasPinned && Math.abs(gx) < 2f) shakeOff = 1f;          // got up: shake itself off
        wasPinned = pinned || (wasPinned && Math.abs(gx) >= 2f);

        squish = Math.max(0f, squish - 2.5f * dt);
        shakeOff = Math.max(0f, shakeOff - 1.2f * dt);
        tumble += ((upside ? 180f : 0f) - tumble) * Math.min(1f, 5f * dt);

        // dizziness drains slowly; while very dizzy it costs mood/energy (callback)
        dizzy = Math.max(0f, dizzy - 8f * dt);
        if (dizzy > 70f) {
            dizzyAcc += dt;
            if (dizzyAcc >= 3f) {
                dizzyAcc = 0f;
                if (onDizzyTick != null) onDizzyTick.run();
            }
        } else {
            dizzyAcc = 0f;
        }

        flash = Math.max(0f, flash - 3f * dt);
        if (weather == WeatherType.STORM && rnd.nextFloat() < 0.4f * dt) flash = 1f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = last == 0L ? 0f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        update(dt);
        float w = getWidth();
        float h = getHeight();
        drawRoom(canvas, w, h);
        drawPet(canvas, w, h);
        postInvalidateOnAnimation();
    }

    // =============================== room + sky ===============================
    private void drawRoom(Canvas c, float w, float h) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F3E6CF"));
        c.drawRect(0f, 0f, w, h, paint);

        RectF win = new RectF(w * 0.07f, h * 0.05f, w * 0.93f, h * 0.58f);
        c.save();
        c.clipRect(win);
        drawSky(c, win);
        c.restore();

        // wooden window frame + sill
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w * 0.025f);
        paint.setColor(Color.parseColor("#9C6B45"));
        c.drawRect(win, paint);
        paint.setStrokeWidth(w * 0.014f);
        c.drawLine(win.centerX(), win.top, win.centerX(), win.bottom, paint);
        c.drawLine(win.left, win.centerY(), win.right, win.centerY(), paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(win.left - w * 0.02f, win.bottom, win.right + w * 0.02f, win.bottom + h * 0.02f, paint);

        // plant on the sill
        paint.setColor(Color.parseColor("#B5734A"));
        c.drawRect(w * 0.12f, win.bottom - h * 0.035f, w * 0.18f, win.bottom, paint);
        paint.setColor(Color.parseColor("#6FA86B"));
        c.drawCircle(w * 0.15f, win.bottom - h * 0.06f, w * 0.045f, paint);
        c.drawCircle(w * 0.125f, win.bottom - h * 0.05f, w * 0.03f, paint);
        c.drawCircle(w * 0.175f, win.bottom - h * 0.05f, w * 0.03f, paint);

        // floor with planks
        float fy = h * 0.62f;
        paint.setColor(Color.parseColor("#D2A474"));
        c.drawRect(0f, fy, w, h, paint);
        paint.setColor(Color.argb(35, 80, 50, 20));
        float step = (h - fy) / 5f;
        for (int i = 1; i <= 4; i++) c.drawRect(0f, fy + i * step, w, fy + i * step + 3f, paint);
    }

    private void drawSky(Canvas c, RectF r) {
        String top, bottom;
        switch (weather) {
            case CLEAR_NIGHT: top = "#1B2A5C"; bottom = "#4A5C9A"; break;
            case CLOUDY:      top = "#9FB4C4"; bottom = "#D5DEE0"; break;
            case RAIN:        top = "#6F8796"; bottom = "#A9BAC0"; break;
            case STORM:       top = "#3B4655"; bottom = "#667487"; break;
            case SNOW:        top = "#C8D7E4"; bottom = "#F2F6FA"; break;
            case FOG:         top = "#C9D2D0"; bottom = "#E6EAE6"; break;
            default:          top = "#7EC3F0"; bottom = "#E6F5DC"; break;   // CLEAR_DAY
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, r.top, 0f, r.bottom,
                Color.parseColor(top), Color.parseColor(bottom), Shader.TileMode.CLAMP));
        c.drawRect(r, paint);
        paint.setShader(null);

        float rw = r.width();
        float rh = r.height();

        if (weather == WeatherType.CLEAR_DAY) {
            paint.setColor(Color.argb(60, 255, 236, 150));
            c.drawCircle(r.left + rw * 0.78f, r.top + rh * 0.25f, rw * 0.13f, paint);
            paint.setColor(Color.parseColor("#FFE08A"));
            c.drawCircle(r.left + rw * 0.78f, r.top + rh * 0.25f, rw * 0.07f, paint);
        }
        if (weather == WeatherType.CLEAR_NIGHT) {
            for (int i = 0; i < 30; i++) {
                float[] p = particles[i];
                int a = (int) Math.max(40, Math.min(255, 120 + 120 * sin(time * 2f + i)));
                paint.setColor(Color.argb(a, 255, 250, 220));
                c.drawCircle(r.left + p[0] * rw, r.top + p[1] * rh * 0.7f, 2.5f * p[2], paint);
            }
            paint.setColor(Color.parseColor("#FFF6D6"));
            c.drawCircle(r.left + rw * 0.78f, r.top + rh * 0.22f, rw * 0.06f, paint);
            paint.setColor(Color.parseColor("#223166"));
            c.drawCircle(r.left + rw * 0.80f, r.top + rh * 0.20f, rw * 0.055f, paint);
        }

        // drifting clouds
        boolean clear = weather == WeatherType.CLEAR_DAY || weather == WeatherType.CLEAR_NIGHT;
        int clouds = clear ? 2 : 4;
        int cloudColor;
        if (weather == WeatherType.RAIN || weather == WeatherType.STORM) cloudColor = Color.argb(235, 130, 145, 158);
        else if (weather == WeatherType.CLEAR_NIGHT) cloudColor = Color.argb(200, 70, 85, 140);
        else cloudColor = Color.argb(235, 255, 255, 255);
        paint.setColor(cloudColor);
        for (int i = 0; i < clouds; i++) {
            float x = r.left + (((time * 0.02f * (1f + i * 0.3f) + i * 0.27f) % 1.4f) - 0.2f) * rw;
            cloud(c, x, r.top + rh * (0.1f + 0.12f * i), rw * 0.09f);
        }

        // rolling green hills (the Ghibli touch), tinted by the weather
        String tint;
        float amt;
        switch (weather) {
            case CLEAR_NIGHT: tint = "#1B2A5C"; amt = 0.6f; break;
            case STORM:       tint = "#2A3340"; amt = 0.55f; break;
            case RAIN:        tint = "#5A6E7A"; amt = 0.35f; break;
            case CLOUDY:      tint = "#9FB0B8"; amt = 0.2f; break;
            case SNOW:        tint = "#FFFFFF"; amt = 0.75f; break;
            case FOG:         tint = "#E6EAE6"; amt = 0.5f; break;
            default:          tint = "#000000"; amt = 0f; break;
        }
        int tc = Color.parseColor(tint);
        hill(c, r, 0.62f, 0.07f, ColorUtils.blendARGB(Color.parseColor("#9CCB8E"), tc, amt), 0f);
        hill(c, r, 0.78f, 0.06f, ColorUtils.blendARGB(Color.parseColor("#6FA86B"), tc, amt), 2f);

        // weather particles
        if (weather == WeatherType.RAIN || weather == WeatherType.STORM) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(Color.argb(170, 207, 227, 242));
            for (int i = 0; i < 55; i++) {
                float[] p = particles[i % particles.length];
                float y = r.top + ((p[1] + time * (1.2f + p[2])) % 1f) * rh;
                float x = r.left + p[0] * rw;
                c.drawLine(x, y, x - 6f, y + 24f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else if (weather == WeatherType.SNOW) {
            paint.setColor(Color.WHITE);
            for (int i = 0; i < 60; i++) {
                float[] p = particles[i % particles.length];
                float y = r.top + ((p[1] + time * 0.12f * p[2]) % 1f) * rh;
                float x = r.left + (p[0] + sin(time + p[1] * 6f) * 0.02f) * rw;
                c.drawCircle(x, y, 3f * p[2] + 1.5f, paint);
            }
        } else if (weather == WeatherType.FOG) {
            paint.setColor(Color.argb(95, 232, 236, 232));
            c.drawRect(r, paint);
        }
        if (flash > 0f) {
            paint.setColor(Color.argb((int) (flash * 200), 255, 255, 255));
            c.drawRect(r, paint);
        }
    }

    private void cloud(Canvas c, float x, float y, float s) {
        c.drawCircle(x, y, s, paint);
        c.drawCircle(x + s * 1.1f, y + s * 0.15f, s * 0.8f, paint);
        c.drawCircle(x - s * 1.0f, y + s * 0.2f, s * 0.7f, paint);
        rect.set(x - s * 1.7f, y + s * 0.2f, x + s * 1.9f, y + s * 0.95f);
        c.drawRoundRect(rect, s * 0.4f, s * 0.4f, paint);
    }

    private void hill(Canvas c, RectF r, float base, float amp, int color, float phase) {
        path.reset();
        path.moveTo(r.left, r.bottom);
        for (int i = 0; i <= 24; i++) {
            float t = i / 24f;
            path.lineTo(r.left + t * r.width(),
                    r.top + r.height() * base - sin(t * (float) Math.PI * 2f + phase) * amp * r.height());
        }
        path.lineTo(r.right, r.bottom);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    // =============================== pet ===============================
    private void fillStroke(Canvas c, int fill, float sw) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(sw);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(outline);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static final int[] SIDES = {-1, 1};

    private void drawPet(Canvas c, float w, float h) {
        float r = radius();
        boolean upside = tumble > 90f;
        float wobX = dizzy > 30f ? sin(time * 7f) * dizzy * 0.25f : 0f;
        float rot = sin(time * 5f) * dizzy * 0.12f + sin(time * 30f) * 10f * shakeOff;
        float cx = w / 2f + px + wobX;
        float cy = h * 0.76f;
        float sw = r * 0.045f;

        // soft shadow
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(45, 60, 40, 20));
        rect.set(cx - r * (0.9f + 0.2f * squish), cy + r * 0.88f, cx + r * (0.9f + 0.2f * squish), cy + r * 1.08f);
        c.drawOval(rect, paint);

        c.save();
        c.translate(cx, cy);
        c.rotate(rot + tumble);
        c.scale(1f + 0.25f * squish, 1f - 0.25f * squish);

        int cream = Color.parseColor("#F9E7A0");
        int egg = Color.parseColor("#FFFDF4");

        // feet (kick while sliding)
        float kick = Math.abs(vx) > 100f ? sin(time * 16f) * r * 0.06f : 0f;
        for (int s : SIDES) {
            path.reset();
            rect.set(s * 0.45f * r - 0.17f * r, 0.8f * r + kick * s, s * 0.45f * r + 0.17f * r, 1.0f * r + kick * s);
            path.addOval(rect, Path.Direction.CW);
            fillStroke(c, cream, sw);
        }

        // egg body
        path.reset();
        rect.set(-r, -0.85f * r, r, 0.95f * r);
        path.addOval(rect, Path.Direction.CW);
        fillStroke(c, egg, sw);

        // egg pattern
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F27C7C"));
        c.drawCircle(-0.55f * r, 0.4f * r, 0.13f * r, paint);
        paint.setColor(Color.parseColor("#F7A07C"));
        c.drawCircle(0.62f * r, 0.42f * r, 0.1f * r, paint);
        path.reset();
        path.moveTo(-0.05f * r, 0.28f * r);
        path.lineTo(0.3f * r, 0.62f * r);
        path.lineTo(-0.2f * r, 0.66f * r);
        path.close();
        paint.setColor(Color.parseColor("#8FD0F0"));
        c.drawPath(path, paint);

        // cracked shell on top (fluffy cream dome with a zigzag edge)
        path.reset();
        path.moveTo(-0.95f * r, -0.05f * r);
        path.lineTo(-0.6f * r, -0.25f * r); path.lineTo(-0.3f * r, -0.02f * r);
        path.lineTo(0f, -0.25f * r); path.lineTo(0.3f * r, -0.02f * r);
        path.lineTo(0.6f * r, -0.25f * r); path.lineTo(0.95f * r, -0.05f * r);
        path.cubicTo(1.15f * r, -0.9f * r, 0.5f * r, -1.25f * r, 0f, -1.2f * r);
        path.cubicTo(-0.5f * r, -1.25f * r, -1.15f * r, -0.9f * r, -0.95f * r, -0.05f * r);
        path.close();
        fillStroke(c, cream, sw);

        // little arms
        float wave = sin(time * 3f) * r * 0.03f + (upside ? -r * 0.15f : 0f);
        for (int s : SIDES) {
            path.reset();
            rect.set(s * 0.98f * r - 0.16f * r, -0.2f * r + wave, s * 0.98f * r + 0.16f * r, 0.2f * r + wave);
            path.addOval(rect, Path.Direction.CW);
            fillStroke(c, cream, sw);
        }

        drawFace(c, r, upside);
        drawHat(c, r, sw);
        c.restore();

        // sparkles: always a couple when happy, orbiting stars when dizzy
        int gold = Color.parseColor("#F7C948");
        if (mood > 60f) {
            sparkle(c, cx + r * 1.05f, cy - r * 0.9f, r * 0.12f * (0.8f + 0.2f * sin(time * 3f)), gold);
            sparkle(c, cx - r * 1.15f, cy - r * 0.2f, r * 0.09f * (0.8f + 0.2f * sin(time * 3f + 1f)), gold);
        }
        if (dizzy > 25f) {
            for (int i = 0; i < 3; i++) {
                float a = time * 4f + i * 2.09f;
                sparkle(c, cx + cos(a) * r * 0.7f, cy - r * 1.4f + sin(a) * r * 0.18f, r * 0.13f, gold);
            }
        }
    }

    private void drawFace(Canvas c, float r, boolean upside) {
        float ey = -0.42f * r;
        float ex = 0.27f * r;
        int dark = Color.parseColor("#5A3A2E");
        paint.setColor(dark);
        if (dizzy > 40f) {                                   // swirly eyes
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.035f);
            for (int s : SIDES) {
                rect.set(s * ex - 0.11f * r, ey - 0.11f * r, s * ex + 0.11f * r, ey + 0.11f * r);
                c.drawArc(rect, (time * 300f) % 360f, 270f, false, paint);
                rect.set(s * ex - 0.05f * r, ey - 0.05f * r, s * ex + 0.05f * r, ey + 0.05f * r);
                c.drawArc(rect, (time * 300f + 180f) % 360f, 270f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else if (upside) {                                 // scared: big round eyes + sweat drop
            for (int s : SIDES) {
                paint.setColor(Color.WHITE);
                c.drawCircle(s * ex, ey, 0.13f * r, paint);
                paint.setColor(dark);
                c.drawCircle(s * ex, ey, 0.05f * r, paint);
            }
            paint.setColor(Color.parseColor("#8FD0F0"));
            c.drawCircle(0.62f * r, -0.75f * r, 0.06f * r, paint);
        } else if (mood < 30f) {                             // sad: droopy eyes
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.04f);
            for (int s : SIDES) {
                rect.set(s * ex - 0.08f * r, ey - 0.05f * r, s * ex + 0.08f * r, ey + 0.1f * r);
                c.drawArc(rect, 200f, 140f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else {                                             // happy dot eyes with a shine
            for (int s : SIDES) {
                paint.setColor(dark);
                c.drawCircle(s * ex, ey, 0.07f * r, paint);
                paint.setColor(Color.WHITE);
                c.drawCircle(s * ex - 0.02f * r, ey - 0.025f * r, 0.022f * r, paint);
            }
        }
        // blush (green when sick)
        paint.setColor(dizzy > 90f ? Color.argb(150, 150, 200, 120) : Color.argb(150, 247, 160, 150));
        for (int s : SIDES) {
            rect.set(s * 0.5f * r - 0.17f * r, -0.31f * r, s * 0.5f * r + 0.17f * r, -0.21f * r);
            c.drawOval(rect, paint);
        }
        // beak
        path.reset();
        path.moveTo(-0.06f * r, -0.32f * r);
        path.lineTo(0.06f * r, -0.32f * r);
        path.lineTo(0f, -0.23f * r);
        path.close();
        paint.setColor(Color.parseColor("#F29A87"));
        c.drawPath(path, paint);
    }

    private void drawHat(Canvas c, float r, float sw) {
        if ("Straw Hat".equals(hat)) {
            path.reset();
            rect.set(-0.85f * r, -1.12f * r, 0.85f * r, -0.88f * r);
            path.addOval(rect, Path.Direction.CW);
            fillStroke(c, Color.parseColor("#EBC76A"), sw);
            path.reset();
            rect.set(-0.5f * r, -1.55f * r, 0.5f * r, -0.8f * r);
            path.addArc(rect, 180f, 180f);
            path.close();
            fillStroke(c, Color.parseColor("#F2D585"), sw);
            paint.setColor(Color.parseColor("#D9625B"));
            c.drawRect(-0.5f * r, -1.02f * r, 0.5f * r, -0.92f * r, paint);
        } else if ("Leaf Hat".equals(hat)) {
            path.reset();
            path.moveTo(-0.1f * r, -1.12f * r);
            path.cubicTo(0.1f * r, -1.7f * r, 0.8f * r, -1.7f * r, 0.9f * r, -1.45f * r);
            path.cubicTo(0.7f * r, -1.2f * r, 0.3f * r, -1.1f * r, -0.1f * r, -1.12f * r);
            path.close();
            fillStroke(c, Color.parseColor("#8CC78A"), sw);
        }
    }

    private void sparkle(Canvas c, float x, float y, float s, int color) {
        path.reset();
        path.moveTo(x, y - s);
        path.quadTo(x, y, x + s, y);
        path.quadTo(x, y, x, y + s);
        path.quadTo(x, y, x - s, y);
        path.quadTo(x, y, x, y - s);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawPath(path, paint);
    }
}
