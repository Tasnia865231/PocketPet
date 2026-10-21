package com.example.pocketpet.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.graphics.ColorUtils;
import com.example.pocketpet.weather.WeatherType;
import java.util.Random;

/**
 * The heart of the app: a 2D Canvas scene (Ghibli-style room + weather sky) with the pet and
 * its tilt/shake physics. Everything is drawn with Canvas primitives.
 *
 * State machine (PetState): IDLE, TIRED, EATING, SLEEPING, BATHING, DIZZY (layered).
 * MainActivity talks to PetView through setters and listener interfaces.
 *
 * Performance: all Paint, Path and RectF objects are cached as fields; no allocation in onDraw.
 * The sky LinearGradient is recreated only when the weather type or view size changes.
 */
public class PetView extends View {

    // ---- state machine ----
    public enum PetState { IDLE, TIRED, EATING, SLEEPING, BATHING }

    private PetState state = PetState.IDLE;

    // ---- listener interfaces ----
    public interface OnFoodEaten   { void onFoodEaten(int itemId); }
    public interface OnBathFinished { void onBathFinished(); }
    public interface OnWake        { void onWake(); }

    private OnFoodEaten   foodEatenListener;
    private OnBathFinished bathFinishedListener;
    private OnWake        wakeListener;
    private Runnable      onDizzyTick;

    // ---- state pushed from the activity ----
    private WeatherType weather = WeatherType.CLEAR_DAY;
    private int   stage   = 0;
    private String hat    = "";
    private float mood    = 80f;
    private float cleanliness = 100f;

    // ---- physics state ----
    private float gx = 0f;
    private float gy = 9.8f;
    private float px = 0f;
    private float vx = 0f;
    private float squish   = 0f;
    private float dizzy    = 0f;
    private float dizzyAcc = 0f;
    private float tumble   = 0f;
    private float shakeOff = 0f;
    private boolean wasPinned = false;
    private float flash    = 0f;
    private float time     = 0f;
    private long  last     = 0L;
    // Physics paused while eating or bathing
    private boolean physicsPaused = false;

    // ---- eating state ----
    private int   eatItemId     = -1;
    private String eatItemName  = "";
    private int   eatBites      = 0;    // 0..3 for solid food, milk uses progress
    private float eatProgress   = 0f;   // 0..1 (for milk drain animation)
    private float eatFoodX      = 0f;   // food sprite world position
    private float eatFoodY      = 0f;
    private boolean foodDragging  = false;
    private boolean foodFloating  = false;  // auto-floating to mouth
    private float   foodTargetX  = 0f;
    private float   foodTargetY  = 0f;
    private float   mouthOpenAng = 0f;     // 0..1 mouth open fraction
    private float   chewPuff     = 0f;     // 0..1 cheek puff
    private float   eatTimer     = 0f;     // seconds since food appeared
    private boolean isMilk       = false;
    private float   heartAlpha[] = new float[4];  // floating hearts after eating
    private float   heartY[]     = new float[4];
    private boolean heartsActive = false;

    // ---- level-up celebration ----
    private float levelUpTimer  = 0f;
    private int   levelUpNumber = 0;
    private float sparkleTimer  = 0f;

    // ---- sleeping state ----
    private float sleepDim      = 0f;   // 0..1 overlay darkness
    private float zzzPhase      = 0f;
    // Floating Z particles: staggered bubbles that drift upward from Mochi while sleeping
    private static final int Z_COUNT = 1;
    private final float[] zPartX     = new float[Z_COUNT];  // horizontal offset from spawn (px)
    private final float[] zPartY     = new float[Z_COUNT];  // current y offset (grows negative = up)
    private final float[] zPartAlpha = new float[Z_COUNT];
    private final float[] zPartScale = new float[Z_COUNT];
    private final float[] zPartDelay = new float[Z_COUNT];  // stagger delay in seconds
    private boolean lampOn = true; // true = lit (awake), false = dim (sleeping)

    // ---- bath state (all logic lives in BathScene) ----
    private final BathScene bath = new BathScene();
    private boolean shakeAnimation = false;  // pet shakes dry after bath / waking
    private float   shakeAnimT  = 0f;

    // ---- tired state ----
    private float yawnTimer     = 0f;
    private float eyeHalf       = 0f;   // 0 = normal, 1 = half-closed

    // ---- shared drawing objects (never allocated in onDraw) ----
    private final Random rnd       = new Random(7);
    private final Paint  paint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path   path      = new Path();
    private final RectF  rect      = new RectF();
    private final Path   path2     = new Path();
    private final RectF  winRect   = new RectF();
    private final CornerPathEffect cornerFx = new CornerPathEffect(10f);
    private LinearGradient sleepSkyGrad = null;
    private float          sleepSkyH    = 0f;
    private final float[][]particles= new float[70][];
    private final int    outline   = Color.parseColor("#8B5E4B");

    // Sky gradient cache — recreated only when weather or size changes
    private LinearGradient skyGrad       = null;
    private WeatherType    skyGradWeather = null;
    private float          skyGradW      = 0f;
    private float          skyGradH      = 0f;

    // ---- dirty-spot positions for cleanliness visual ----
    private final float[] dirtRx = {-0.55f, 0.60f, 0.20f, -0.30f};
    private final float[] dirtRy = { 0.20f, 0.50f,-0.10f,  0.60f};
    private final float[] dirtRr = { 0.09f, 0.07f, 0.06f,  0.08f};

    public PetView(Context c) { this(c, null); }

    public PetView(Context c, AttributeSet a) {
        super(c, a);
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new float[]{rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat()};
        }
        for (int i = 0; i < heartAlpha.length; i++) {
            heartAlpha[i] = 0f;
            heartY[i]     = 0f;
        }
        // Init Z-particle stagger delays and per-particle properties
        for (int i = 0; i < Z_COUNT; i++) {
            zPartDelay[i] = i * 0.9f;          // each particle spawns 0.9 s later than the previous
            zPartAlpha[i] = 0f;
            zPartY[i]     = 0f;
            zPartScale[i] = 0.7f + i * 0.15f;  // grow from small to large across the set
            zPartX[i]     = (i % 3) * 14f - 14f; // slight horizontal spread: -14, 0, +14, -14, 0
        }
    }

    // ---- public setters ----
    public void setWeather(WeatherType w)    { weather = w; skyGrad = null; }
    public void setStage(int s)              { stage = s; }
    public void setHat(String h)             { hat = h == null ? "" : h; }
    public void setMood(float m)             { mood = m; }
    public void setCleanliness(float c)      { cleanliness = c; }
    public void setOnDizzyTick(Runnable r)   { onDizzyTick = r; }
    public void setOnFoodEaten(OnFoodEaten l)       { foodEatenListener = l; }
    public void setOnBathFinished(OnBathFinished l) { bathFinishedListener = l; }
    public void setOnWake(OnWake l)                 { wakeListener = l; }
    /** Toggle the nightstand lamp: false = dim/unlit (sleeping), true = lit (awake). */
    public void setLampOn(boolean on)               { lampOn = on; }

    public PetState getState() { return state; }

    public void setGravity(float x, float y) { if (!physicsPaused) { gx = x; gy = y; } }
    public void addShake() {
        if (state == PetState.SLEEPING) {
            // Shake wakes sleeping pet
            if (wakeListener != null) wakeListener.onWake();
            return;
        }
        dizzy = Math.min(100f, dizzy + 25f);
        if (!physicsPaused) vx += (rnd.nextFloat() - 0.5f) * getWidth() * 1.2f;
    }
    public void cheer() { squish = 0.7f; }

    // ---- state machine transitions (called from MainActivity) ----
    public void enterEating(int itemId, String itemName, boolean milk) {
        state        = PetState.EATING;
        physicsPaused = true;
        eatItemId    = itemId;
        eatItemName  = itemName;
        eatBites     = 0;
        eatProgress  = 0f;
        isMilk       = milk;
        heartsActive = false;
        mouthOpenAng = 0f;
        chewPuff     = 0f;
        eatTimer     = 0f;
        foodFloating = false;
        foodDragging = false;
        // Start food at a fixed offset to the right of centre
        eatFoodX = getWidth() * 0.5f + getWidth() * 0.28f;
        eatFoodY = getHeight() * 0.72f;
        // Mouth target
        foodTargetX = getWidth() * 0.5f;
        foodTargetY = getHeight() * 0.65f;
        px = 0f; vx = 0f; // centre the pet
        postInvalidateOnAnimation();
    }

    public void exitEating() {
        state         = PetState.IDLE;
        physicsPaused = false;
        eatItemId     = -1;
        mouthOpenAng  = 0f;
        chewPuff      = 0f;
        heartsActive  = false;
    }

    /** Called when energy < 20 in the activity. */
    public void setTired(boolean tired) {
        if (state == PetState.IDLE || state == PetState.TIRED) {
            state = tired ? PetState.TIRED : PetState.IDLE;
        }
    }

    public void enterSleeping() {
        state         = PetState.SLEEPING;
        physicsPaused = true;
        px = 0f; vx = 0f;
        postInvalidateOnAnimation();
    }

    public void exitSleeping() {
        state         = PetState.IDLE;
        physicsPaused = false;
        shakeAnimation = true;
        shakeAnimT    = 0f;
    }

    public void enterBathing() {
        state         = PetState.BATHING;
        physicsPaused = true;
        px = 0f; vx = 0f;
        bath.start(getWidth(), getHeight());
        postInvalidateOnAnimation();
    }

    /** Early exit from bath: return current progress (0..1) so the activity can apply partial clean. */
    public float exitBathEarly() {
        float prog = bath.progress();
        bath.stop();
        state         = PetState.IDLE;
        physicsPaused = false;
        return prog;
    }

    /** Trigger level-up celebration. */
    public void showLevelUp(int newLevel) {
        levelUpTimer  = 3.0f;
        levelUpNumber = newLevel;
        sparkleTimer  = 2.5f;
    }

    public Bitmap snapshot() {
        Bitmap b = Bitmap.createBitmap(Math.max(getWidth(),1), Math.max(getHeight(),1), Bitmap.Config.ARGB_8888);
        draw(new Canvas(b));
        return b;
    }

    // ---- touch handling for eating and bathing ----
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (state == PetState.EATING) {
            handleEatTouch(ev);
            return true;
        }
        if (state == PetState.BATHING) {
            bath.onTouch(ev, getWidth(), getHeight(), radius());
            return true;
        }
        return super.onTouchEvent(ev);
    }

    private float foodRadius() { return radius() * 0.28f; }

    private void handleEatTouch(MotionEvent ev) {
        float ex = ev.getX();
        float ey = ev.getY();
        float fr = foodRadius();
        float petCx = getWidth() * 0.5f;
        float petCy = getHeight() * 0.76f;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                // Tap food sprite -> start drag
                float dx = ex - eatFoodX;
                float dy = ey - eatFoodY;
                if (dx * dx + dy * dy < (fr * 2f) * (fr * 2f)) {
                    foodDragging = true;
                }
                // Tap pet body -> float food to mouth
                float dpx = ex - petCx;
                float dpy = ey - petCy;
                if (!foodDragging && dpx * dpx + dpy * dpy < (radius() * 1.2f) * (radius() * 1.2f)) {
                    foodFloating = true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (foodDragging) {
                    eatFoodX = ex;
                    eatFoodY = ey;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (foodDragging) {
                    foodDragging = false;
                    // Check if dropped near the mouth
                    float dpx = eatFoodX - petCx;
                    float dpy = eatFoodY - (petCy - radius() * 0.28f);
                    if (dpx * dpx + dpy * dpy < (radius() * 0.5f) * (radius() * 0.5f)) {
                        foodFloating = true;   // snap to mouth
                    } else {
                        // Return to original position (no quantity lost)
                        eatFoodX = getWidth() * 0.5f + getWidth() * 0.28f;
                        eatFoodY = getHeight() * 0.72f;
                    }
                }
                break;
        }
    }

    // ---- helpers ----
    private static float sin(float x) { return (float) Math.sin(x); }
    private static float cos(float x) { return (float) Math.cos(x); }

    private float radius() {
        return Math.min(getWidth(), getHeight()) * 0.17f * (1f + 0.12f * stage);
    }

    // =============================== physics update ===============================
    private void update(float dt) {
        time += dt;

        // ---- sleeping dim/undim ----
        if (state == PetState.SLEEPING) {
            sleepDim = Math.min(1f, sleepDim + dt * 1.2f);
            zzzPhase += dt;
            // Animate the floating Z bubble: it rises gently from above the hat, then fades out
            for (int i = 0; i < Z_COUNT; i++) {
                if (zPartDelay[i] > 0f) {
                    zPartDelay[i] -= dt;
                    zPartAlpha[i] = 0f;
                    continue; // bubble not yet active
                }
                float maxTravel = radius() * 0.9f;
                zPartY[i] -= dt * radius() * 0.25f;
                float tr = Math.min(1f, -zPartY[i] / maxTravel);
                float fadeIn  = Math.min(1f, 0.35f + tr * 6.5f);
                float fadeOut = tr < 0.7f ? 1f : Math.max(0f, (1f - tr) / 0.3f);
                zPartAlpha[i] = fadeIn * fadeOut;
                if (tr >= 1f) {
                    zPartY[i]     = 0f;
                    zPartAlpha[i] = 0f;
                    zPartDelay[i] = 0.8f;
                }
            }
        } else {
            sleepDim = Math.max(0f, sleepDim - dt * 1.5f);
            // Fade out any active Z particles when waking
            for (int i = 0; i < Z_COUNT; i++) {
                zPartAlpha[i] = Math.max(0f, zPartAlpha[i] - dt * 2f);
            }
        }

        // ---- bath ----
        if (state == PetState.BATHING) {
            bath.update(dt, getWidth(), getHeight(), radius());
            if (bath.consumeFinished()) {
                state          = PetState.IDLE;
                physicsPaused  = false;
                shakeAnimation = true;
                shakeAnimT     = 0f;
                if (bathFinishedListener != null) bathFinishedListener.onBathFinished();
            }
        }

        // ---- shake-dry animation ----
        if (shakeAnimation) {
            shakeAnimT += dt;
            squish = sin(shakeAnimT * 18f) * 0.4f;
            if (shakeAnimT > 1.0f) {
                shakeAnimation = false;
                squish = 0f;
            }
        }

        // ---- eating update ----
        if (state == PetState.EATING) {
            eatTimer += dt;
            // Timeout after 10 seconds -> return food, no quantity lost
            if (eatTimer > 10f && !foodFloating && eatBites == 0) {
                exitEating();
                return;
            }
            // Auto-float to mouth
            if (foodFloating) {
                eatFoodX += (foodTargetX - eatFoodX) * Math.min(1f, 6f * dt);
                eatFoodY += (foodTargetY - eatFoodY) * Math.min(1f, 6f * dt);
                float dx = eatFoodX - foodTargetX;
                float dy = eatFoodY - foodTargetY;
                if (dx * dx + dy * dy < 25f) {
                    // At the mouth: begin eating
                    foodFloating  = false;
                    mouthOpenAng  = 1f;
                }
            }
            // Mouth animation
            if (mouthOpenAng > 0f && eatBites < (isMilk ? 3 : 3)) {
                mouthOpenAng = sin(time * 10f) * 0.5f + 0.5f;
                chewPuff     = mouthOpenAng * 0.5f;
                if (isMilk) {
                    eatProgress += dt * 0.4f;
                    if (eatProgress >= 1f) {
                        eatProgress = 1f;
                        eatBites    = 3;
                        mouthOpenAng = 0f;
                    }
                } else {
                    // One bite every ~1 second
                    int expectedBites = Math.min(3, (int)(mouthOpenAng > 0f ? (time - (eatTimer - eatTimer) + eatBites) : 0));
                    // simpler: advance bite on a timer
                    eatProgress += dt * 0.35f;
                    if (eatProgress >= 1f) {
                        eatBites++;
                        eatProgress = 0f;
                        if (eatBites >= 3) {
                            mouthOpenAng = 0f;
                        }
                    }
                }
            }
            // After all bites: show hearts, notify listener
            if (eatBites >= 3 && !heartsActive) {
                heartsActive = true;
                for (int i = 0; i < heartAlpha.length; i++) {
                    heartAlpha[i] = 1f;
                    heartY[i]     = 0f;
                }
                if (foodEatenListener != null) foodEatenListener.onFoodEaten(eatItemId);
            }
            // Animate hearts
            if (heartsActive) {
                boolean any = false;
                for (int i = 0; i < heartAlpha.length; i++) {
                    if (heartAlpha[i] > 0f) {
                        heartAlpha[i] -= dt * 0.6f;
                        heartY[i]     -= dt * 40f;
                        any = true;
                    }
                }
                if (!any) exitEating();
            }
        }

        // ---- tired yawn ----
        if (state == PetState.TIRED) {
            yawnTimer += dt;
            // Yawn every ~5 seconds: half-close eyes for 1.5s
            float cycle = yawnTimer % 5f;
            eyeHalf = cycle < 1.5f ? Math.min(1f, cycle / 0.5f) : 0f;
        } else {
            eyeHalf = 0f;
        }

        // ---- level-up banners ----
        if (levelUpTimer > 0f) levelUpTimer -= dt;
        if (sparkleTimer > 0f) sparkleTimer -= dt;

        // ---- physics ----
        if (!physicsPaused) {
            boolean upside = gy < -4f;
            float limit = Math.max(0f, getWidth() / 2f - radius() * 1.1f);
            float ax = upside ? 0f : -gx * getWidth() * 0.12f;
            vx += ax * dt;
            if (Math.abs(gx) < 1.5f && !upside) vx += -px * 6f * dt;
            vx *= Math.max(0f, 1f - 2.2f * dt);
            px += vx * dt;
            if (Math.abs(px) > limit) {
                if (Math.abs(vx) > 150f) squish = 1f;
                px = limit * Math.signum(px);
                vx = -vx * 0.25f;
            }
            boolean pinned = Math.abs(gx) > 3f && Math.abs(px) >= limit * 0.9f;
            if (pinned) squish = Math.max(squish, 0.5f);
            if (wasPinned && Math.abs(gx) < 2f) shakeOff = 1f;
            wasPinned = pinned || (wasPinned && Math.abs(gx) >= 2f);
            squish    = Math.max(0f, squish - 2.5f * dt);
            shakeOff  = Math.max(0f, shakeOff - 1.2f * dt);
            tumble   += ((upside ? 180f : 0f) - tumble) * Math.min(1f, 5f * dt);
        }

        // ---- dizziness ----
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

    // =============================== onDraw ===============================
    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = last == 0L ? 0f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        update(dt);

        float w = getWidth();
        float h = getHeight();
        if (sleepDim < 0.999f) drawRoom(canvas, w, h);
        drawSleepScene(canvas, w, h); // night bedroom scene (replaces the room while sleeping)
        float r = radius();
        bath.drawBehind(canvas, w, h, r);   // shower + tub back (window/room stay untouched)
        drawPet(canvas, w, h);
        bath.drawOnPet(canvas, w, h, r);    // foam + water drops on Mochi
        bath.drawFront(canvas, w, h, r);    // tub front, water, soap, towel
        drawFoodSprite(canvas, w, h); // food overlay (eating mode)
        drawSleepDim(canvas, w, h);   // dark overlay
        drawLevelUpBanner(canvas, w, h);
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

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w * 0.025f);
        paint.setColor(Color.parseColor("#9C6B45"));
        c.drawRect(win, paint);
        paint.setStrokeWidth(w * 0.014f);
        c.drawLine(win.centerX(), win.top, win.centerX(), win.bottom, paint);
        c.drawLine(win.left, win.centerY(), win.right, win.centerY(), paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(win.left - w * 0.02f, win.bottom, win.right + w * 0.02f, win.bottom + h * 0.02f, paint);

        paint.setColor(Color.parseColor("#B5734A"));
        c.drawRect(w * 0.12f, win.bottom - h * 0.035f, w * 0.18f, win.bottom, paint);
        paint.setColor(Color.parseColor("#6FA86B"));
        c.drawCircle(w * 0.15f, win.bottom - h * 0.06f, w * 0.045f, paint);
        c.drawCircle(w * 0.125f, win.bottom - h * 0.05f, w * 0.03f, paint);
        c.drawCircle(w * 0.175f, win.bottom - h * 0.05f, w * 0.03f, paint);

        float fy = h * 0.62f;
        paint.setColor(Color.parseColor("#D2A474"));
        c.drawRect(0f, fy, w, h, paint);
        paint.setColor(Color.argb(35, 80, 50, 20));
        float step = (h - fy) / 5f;
        for (int i = 1; i <= 4; i++) c.drawRect(0f, fy + i * step, w, fy + i * step + 3f, paint);
    }

    private void drawSky(Canvas c, RectF r) {
        // Cache gradient
        if (skyGrad == null || skyGradWeather != weather
                || Math.abs(skyGradH - r.height()) > 1f || Math.abs(skyGradW - r.width()) > 1f) {
            String top, bottom;
            switch (weather) {
                case CLEAR_NIGHT: top = "#1B2A5C"; bottom = "#4A5C9A"; break;
                case CLOUDY:      top = "#9FB4C4"; bottom = "#D5DEE0"; break;
                case RAIN:        top = "#6F8796"; bottom = "#A9BAC0"; break;
                case STORM:       top = "#3B4655"; bottom = "#667487"; break;
                case SNOW:        top = "#C8D7E4"; bottom = "#F2F6FA"; break;
                case FOG:         top = "#C9D2D0"; bottom = "#E6EAE6"; break;
                default:          top = "#7EC3F0"; bottom = "#E6F5DC"; break;
            }
            skyGrad       = new LinearGradient(0f, r.top, 0f, r.bottom,
                    Color.parseColor(top), Color.parseColor(bottom), Shader.TileMode.CLAMP);
            skyGradWeather = weather;
            skyGradW = r.width(); skyGradH = r.height();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(skyGrad);
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

        boolean clear = weather == WeatherType.CLEAR_DAY || weather == WeatherType.CLEAR_NIGHT;
        int clouds = clear ? 2 : 4;
        int cloudColor;
        if (weather == WeatherType.RAIN || weather == WeatherType.STORM)
            cloudColor = Color.argb(235, 130, 145, 158);
        else if (weather == WeatherType.CLEAR_NIGHT)
            cloudColor = Color.argb(200, 70, 85, 140);
        else
            cloudColor = Color.argb(235, 255, 255, 255);
        paint.setColor(cloudColor);
        for (int i = 0; i < clouds; i++) {
            float x = r.left + (((time * 0.02f * (1f + i * 0.3f) + i * 0.27f) % 1.4f) - 0.2f) * rw;
            cloud(c, x, r.top + rh * (0.1f + 0.12f * i), rw * 0.09f);
        }

        String tint; float amt;
        switch (weather) {
            case CLEAR_NIGHT: tint = "#1B2A5C"; amt = 0.6f; break;
            case STORM:       tint = "#2A3340"; amt = 0.55f; break;
            case RAIN:        tint = "#5A6E7A"; amt = 0.35f; break;
            case CLOUDY:      tint = "#9FB0B8"; amt = 0.2f;  break;
            case SNOW:        tint = "#FFFFFF"; amt = 0.75f; break;
            case FOG:         tint = "#E6EAE6"; amt = 0.5f;  break;
            default:          tint = "#000000"; amt = 0f;    break;
        }
        int tc = Color.parseColor(tint);
        hill(c, r, 0.62f, 0.07f, ColorUtils.blendARGB(Color.parseColor("#9CCB8E"), tc, amt), 0f);
        hill(c, r, 0.78f, 0.06f, ColorUtils.blendARGB(Color.parseColor("#6FA86B"), tc, amt), 2f);

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
            paint.setColor(Color.argb((int)(flash * 200), 255, 255, 255));
            c.drawRect(r, paint);
        }
    }

    private void cloud(Canvas c, float x, float y, float s) {
        c.drawCircle(x, y, s, paint);
        c.drawCircle(x + s * 1.1f, y + s * 0.15f, s * 0.8f, paint);
        c.drawCircle(x - s * 1.0f, y + s * 0.2f,  s * 0.7f, paint);
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

    // =============================== sleep scene ===============================
    // Night bedroom drawn to match the reference screenshot. Positions are fractions of the
    // view so the scene scales with any screen size. Colours are the reference colours with the
    // very light sleep overlay (see drawSleepDim) already accounted for.
    private static final int SL_WALL   = 0xFF8A8287;
    private static final int SL_FRAME  = 0xFF593E3E;
    private static final int SL_SKY_T  = 0xFF171F4E;
    private static final int SL_SKY_B  = 0xFF232C5C;
    private static final int SL_HILL1  = 0xFF323F55;
    private static final int SL_HILL2  = 0xFF28384E;
    private static final int SL_MOON   = 0xFF8F8C8C;
    private static final int SL_FLOOR  = 0xFF795F56;
    private static final int SL_BUSH   = 0xFF436052;
    private static final int SL_POT    = 0xFF6B453F;
    private static final int SL_HEAD   = 0xFF553933;
    private static final int SL_PILLOW = 0xFF9FB4D0;
    private static final int SL_BLANK  = 0xFFB6C7DF;
    private static final int SL_BLINE  = 0xFF6A5058;
    private static final int SL_CUFF   = 0xFF6C6779;
    private static final int SL_LUMP   = 0xFF8B97B1;
    private static final int SL_RAIL   = 0xFF6C4D48;
    private static final int SL_HEM    = 0xFFB4C6DC;
    private static final int SL_TEAL   = 0xFF63868E;
    private static final int SL_STAND  = 0xFF5E3F38;
    private static final int SL_SHADE  = 0xFF61606F;
    private static final int SL_POLE   = 0xFF664F4A;

    // star positions inside the window (fractions of the window glass)
    private static final float[][] SL_STARS = {
            {0.108f, 0.163f}, {0.378f, 0.433f}, {0.696f, 0.040f}, {0.944f, 0.059f},
            {0.573f, 0.232f}, {0.949f, 0.240f}, {0.780f, 0.303f}, {0.668f, 0.419f},
            {0.737f, 0.444f}, {0.103f, 0.548f}, {0.844f, 0.604f}, {0.913f, 0.517f},
            {0.182f, 0.511f}, {0.445f, 0.034f}
    };

    /** Back layer of the sleep scene: wall, window, floor, headboard, pillow, nightstand + lamp. */
    private void drawSleepScene(Canvas c, float w, float h) {
        if (sleepDim <= 0.01f) return;
        int sc = 0;
        boolean layer = sleepDim < 0.999f;
        if (layer) sc = c.saveLayerAlpha(0f, 0f, w, h, (int) (sleepDim * 255));

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        // wall + wooden floor
        paint.setColor(SL_WALL);
        c.drawRect(0f, 0f, w, h, paint);
        float fy = h * 0.62f;
        paint.setColor(SL_FLOOR);
        c.drawRect(0f, fy, w, h, paint);
        paint.setColor(Color.argb(28, 60, 30, 20));
        float step = (h - fy) / 5f;
        for (int i = 1; i <= 4; i++) c.drawRect(0f, fy + i * step, w, fy + i * step + 3f, paint);

        // window frame + glass
        float gl = w * 0.082f, gt = h * 0.060f, gr = w * 0.918f, gb = h * 0.570f;
        paint.setColor(SL_FRAME);
        rect.set(w * 0.059f, h * 0.041f, w * 0.944f, h * 0.600f);
        c.drawRect(rect, paint);

        if (sleepSkyGrad == null || Math.abs(sleepSkyH - h) > 1f) {
            sleepSkyGrad = new LinearGradient(0f, gt, 0f, gb, SL_SKY_T, SL_SKY_B, Shader.TileMode.CLAMP);
            sleepSkyH = h;
        }
        float rw = gr - gl, rh = gb - gt;
        winRect.set(gl, gt, gr, gb);
        c.save();
        c.clipRect(winRect);
        paint.setShader(sleepSkyGrad);
        c.drawRect(winRect, paint);
        paint.setShader(null);

        // twinkling stars
        for (int i = 0; i < SL_STARS.length; i++) {
            int a = (int) (170 + 70 * sin(time * 1.6f + i * 1.7f));
            paint.setColor(Color.argb(a, 215, 215, 228));
            c.drawCircle(gl + SL_STARS[i][0] * rw, gt + SL_STARS[i][1] * rh,
                    w * (0.0032f + 0.0008f * (i % 3)), paint);
        }
        // soft clouds
        paint.setColor(Color.argb(77, 80, 90, 140));
        cloud(c, gl + rw * 0.236f, gt + rh * 0.2125f, rw * 0.09f);
        cloud(c, gl + rw * 0.695f, gt + rh * 0.091f, rw * 0.09f);
        // crescent moon
        paint.setColor(SL_MOON);
        c.drawCircle(gl + rw * 0.786f, gt + rh * 0.214f, rw * 0.058f, paint);
        paint.setColor(0xFF1D2455);
        c.drawCircle(gl + rw * 0.808f, gt + rh * 0.184f, rw * 0.052f, paint);
        // rolling hills
        hill(c, winRect, 0.62f, 0.07f, SL_HILL1, 0.19f);
        hill(c, winRect, 0.79f, 0.058f, SL_HILL2, 2.2f);
        c.restore();

        // mullions + sill
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SL_FRAME);
        c.drawRect(w * 0.5f - w * 0.0064f, gt, w * 0.5f + w * 0.0064f, gb, paint);
        c.drawRect(gl, h * 0.314f - h * 0.0047f, gr, h * 0.314f + h * 0.0047f, paint);
        c.drawRect(w * 0.0486f, h * 0.570f, w * 0.951f, h * 0.600f, paint);

        // potted plant on the sill
        paint.setColor(SL_POT);
        c.drawRect(w * 0.118f, h * 0.548f, w * 0.182f, h * 0.570f, paint);
        paint.setColor(SL_BUSH);
        rect.set(w * 0.092f, h * 0.486f, w * 0.202f, h * 0.556f);
        c.drawOval(rect, paint);

        float cx = w * 0.5f;

        // headboard (dark wood) + pillow behind Mochi
        paint.setColor(SL_HEAD);
        rect.set(cx - w * 0.30f, h * 0.72f, cx + w * 0.30f, h * 0.83f);
        c.drawRoundRect(rect, w * 0.02f, w * 0.02f, paint);
        paint.setColor(SL_PILLOW);
        rect.set(cx - w * 0.237f, h * 0.664f, cx + w * 0.237f, h * 0.79f);
        c.drawRoundRect(rect, w * 0.04f, w * 0.04f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(120, 110, 90, 110));
        c.drawRoundRect(rect, w * 0.04f, w * 0.04f, paint);
        paint.setStyle(Paint.Style.FILL);

        // nightstand
        paint.setColor(SL_STAND);
        rect.set(w * 0.839f, h * 0.750f, w * 0.980f, h * 0.889f);
        c.drawRoundRect(rect, 3f, 3f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xFF4A312C);
        c.drawLine(w * 0.890f, h * 0.792f, w * 0.923f, h * 0.792f, paint);
        paint.setStyle(Paint.Style.FILL);

        // lamp (unlit while sleeping)
        float lx = w * 0.9095f;
        float baseY = h * 0.742f, shadeB = h * 0.687f, shadeT = h * 0.645f;
        if (lampOn) {
            paint.setColor(Color.argb(60, 255, 220, 120));
            c.drawCircle(lx, (shadeB + shadeT) * 0.5f, w * 0.12f, paint);
        }
        paint.setColor(SL_POLE);
        c.drawRect(lx - w * 0.006f, shadeB, lx + w * 0.006f, baseY, paint);
        rect.set(lx - w * 0.026f, baseY - 3f, lx + w * 0.026f, baseY + 3f);
        c.drawOval(rect, paint);
        path.reset();
        path.moveTo(lx - w * 0.051f, shadeB);
        path.lineTo(lx + w * 0.051f, shadeB);
        path.lineTo(lx + w * 0.033f, shadeT);
        path.lineTo(lx - w * 0.033f, shadeT);
        path.close();
        paint.setColor(lampOn ? 0xFFFFDE80 : SL_SHADE);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(120, 60, 50, 70));
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        if (layer) c.restoreToCount(sc);
    }

    /**
     * Front layer of the sleep scene, drawn after the pet: the cable-knit blanket tucked up to
     * Mochi's chin, the bed footboard, Mochi's arms resting on the blanket, and the floating Z.
     */
    private void drawSleepFront(Canvas c, float w, float h, float cx, float cy, float r, float sw) {
        int sc = 0;
        boolean layer = sleepDim < 0.999f;
        if (layer) sc = c.saveLayerAlpha(0f, 0f, w, h, (int) (sleepDim * 255));

        // ---- blanket silhouette ----
        path.reset();
        path.moveTo(cx - w * 0.349f, h * 0.912f);
        path.lineTo(cx - w * 0.331f, h * 0.861f);
        path.lineTo(cx - w * 0.318f, h * 0.818f);
        path.quadTo(cx - w * 0.30f, h * 0.795f, cx - w * 0.231f, h * 0.771f);
        path.lineTo(cx - r * 1.0f, cy + r * 0.06f);
        path.cubicTo(cx - r * 0.8f, cy - r * 0.07f, cx - r * 0.4f, cy - r * 0.23f, cx, cy - r * 0.23f);
        path.cubicTo(cx + r * 0.4f, cy - r * 0.23f, cx + r * 0.8f, cy - r * 0.07f, cx + r * 1.0f, cy + r * 0.06f);
        path.lineTo(cx + w * 0.231f, h * 0.771f);
        path.quadTo(cx + w * 0.30f, h * 0.795f, cx + w * 0.318f, h * 0.818f);
        path.lineTo(cx + w * 0.331f, h * 0.861f);
        path.lineTo(cx + w * 0.349f, h * 0.912f);
        path.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setPathEffect(cornerFx);
        paint.setColor(SL_BLANK);
        c.drawPath(path, paint);

        // cable-knit braids, clipped to the blanket
        c.save();
        c.clipPath(path);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(w * 0.006f);
        paint.setColor(Color.argb(95, 120, 140, 180));
        float topY = cy - r * 0.3f, botY = h * 0.912f, seg = h * 0.05f;
        for (int k = -4; k <= 4; k++) {
            float x0 = cx + k * w * 0.07f;
            path2.reset();
            path2.moveTo(x0, topY);
            int n = 0;
            for (float y = topY; y < botY; y += seg, n++) {
                float dx = (n % 2 == 0 ? 1f : -1f) * w * 0.013f;
                path2.quadTo(x0 + dx, y + seg * 0.5f, x0, y + seg);
            }
            c.drawPath(path2, paint);
        }
        c.restore();

        // blanket outline
        paint.setPathEffect(cornerFx);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(w * 0.0065f);
        paint.setColor(SL_BLINE);
        c.drawPath(path, paint);
        paint.setPathEffect(null);

        // folded cuff
        path.reset();
        path.moveTo(cx - w * 0.318f, h * 0.818f);
        path.cubicTo(cx - w * 0.20f, h * 0.796f, cx - w * 0.10f, cy + r * 0.05f, cx, cy + r * 0.05f);
        path.cubicTo(cx + w * 0.10f, cy + r * 0.05f, cx + w * 0.20f, h * 0.796f, cx + w * 0.318f, h * 0.818f);
        paint.setStrokeWidth(w * 0.007f);
        paint.setColor(SL_CUFF);
        c.drawPath(path, paint);

        // lumps made by Mochi's body under the blanket
        path.reset();
        path.moveTo(cx - w * 0.18f, h * 0.872f);
        path.cubicTo(cx - w * 0.16f, h * 0.82f, cx - w * 0.12f, h * 0.82f, cx - w * 0.04f, h * 0.858f);
        path.cubicTo(cx, h * 0.88f, cx + w * 0.02f, h * 0.878f, cx + w * 0.06f, h * 0.858f);
        path.cubicTo(cx + w * 0.12f, h * 0.82f, cx + w * 0.17f, h * 0.82f, cx + w * 0.19f, h * 0.865f);
        paint.setStrokeWidth(w * 0.016f);
        paint.setColor(SL_LUMP);
        c.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);

        // ---- footboard ----
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SL_RAIL);
        c.drawRect(cx - w * 0.375f, h * 0.910f, cx + w * 0.375f, h, paint);
        paint.setColor(SL_HEM);
        c.drawRect(cx - w * 0.347f, h * 0.927f, cx + w * 0.347f, h * 0.953f, paint);
        paint.setColor(SL_TEAL);
        c.drawRect(cx - w * 0.347f, h * 0.974f, cx + w * 0.347f, h, paint);
        paint.setColor(SL_RAIL);
        float[] posts = {-0.262f, -0.102f, 0.102f, 0.262f};
        for (float pxo : posts) {
            c.drawRect(cx + pxo * w - w * 0.014f, h * 0.972f, cx + pxo * w + w * 0.014f, h, paint);
        }

        if (layer) c.restoreToCount(sc);

        // ---- Mochi's arms rest on top of the blanket ----
        int cream = Color.parseColor("#F9E7A0");
        float wave = sin(time * 3f) * r * 0.03f;
        float armPuff = 1f + chewPuff * 0.15f;
        for (int s : SIDES) {
            path.reset();
            rect.set(cx + s * 0.98f * r - 0.16f * r * armPuff, cy - 0.2f * r + wave,
                     cx + s * 0.98f * r + 0.16f * r * armPuff, cy + 0.2f * r + wave);
            path.addOval(rect, Path.Direction.CW);
            fillStroke(c, cream, sw);
        }

        // ---- floating Z bubble above the hat ----
        for (int i = 0; i < Z_COUNT; i++) {
            float al = zPartAlpha[i] * sleepDim;
            if (al <= 0.01f) continue;
            float zx = cx + sin(zzzPhase * 2f + i) * r * 0.03f;
            float zy = cy - r * 1.8f + zPartY[i];
            float r2 = r * 0.158f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int) (al * 255), 0x95, 0xAD, 0xDF));
            c.drawCircle(zx, zy, r2, paint);
            path.reset();
            path.moveTo(zx - r2 * 0.30f, zy + r2 * 0.85f);
            path.lineTo(zx + r2 * 0.30f, zy + r2 * 0.85f);
            path.lineTo(zx, zy + r2 * 1.55f);
            path.close();
            c.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r2 * 0.12f);
            paint.setColor(Color.argb((int) (al * 255), 0x8F, 0x9D, 0xCE));
            c.drawCircle(zx, zy, r2, paint);

            float gz = r2 * 0.42f;
            paint.setStrokeWidth(r2 * 0.2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.argb((int) (al * 255), 0x65, 0x80, 0xC0));
            c.drawLine(zx - gz, zy - gz, zx + gz, zy - gz, paint);
            c.drawLine(zx + gz, zy - gz, zx - gz, zy + gz, paint);
            c.drawLine(zx - gz, zy + gz, zx + gz, zy + gz, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }


    // =============================== food sprite ===============================
    private void drawFoodSprite(Canvas c, float w, float h) {
        if (state != PetState.EATING) return;
        if (eatBites >= 3 && heartsActive) {
            // Draw floating hearts
            float cx = w * 0.5f;
            float cy = h * 0.65f;
            for (int i = 0; i < heartAlpha.length; i++) {
                if (heartAlpha[i] > 0f) {
                    paint.setColor(Color.argb((int)(heartAlpha[i] * 255), 255, 80, 120));
                    float hx = cx + (i - 1.5f) * radius() * 0.4f;
                    float hy = cy - radius() * 0.5f + heartY[i];
                    drawHeart(c, hx, hy, radius() * 0.12f);
                }
            }
            return;
        }
        if (eatBites >= 3) return;

        // Compute food size (shrinks bite by bite)
        float fr = foodRadius() * (1f - eatBites * 0.28f);
        float fx = eatFoodX;
        float fy = eatFoodY;

        paint.setStyle(Paint.Style.FILL);
        if (isMilk) {
            // Glass of milk: draw with remaining liquid
            float remaining = 1f - eatProgress;
            // glass body
            paint.setColor(Color.argb(220, 240, 240, 255));
            rect.set(fx - fr * 0.6f, fy - fr, fx + fr * 0.6f, fy + fr);
            c.drawRoundRect(rect, fr * 0.15f, fr * 0.15f, paint);
            // milk liquid
            paint.setColor(Color.argb(230, 250, 250, 255));
            float liquidTop = fy - fr + fr * 2f * (1f - remaining);
            rect.set(fx - fr * 0.52f, liquidTop, fx + fr * 0.52f, fy + fr * 0.85f);
            c.drawRect(rect, paint);
            // glass outline
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(fr * 0.08f);
            paint.setColor(Color.argb(200, 180, 180, 220));
            rect.set(fx - fr * 0.6f, fy - fr, fx + fr * 0.6f, fy + fr);
            c.drawRoundRect(rect, fr * 0.15f, fr * 0.15f, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            // Draw food based on name
            drawFoodByName(c, eatItemName, fx, fy, fr);
        }
    }

    private void drawFoodByName(Canvas c, String name, float x, float y, float r) {
        paint.setStyle(Paint.Style.FILL);
        switch (name) {
            case "Berry":
                paint.setColor(Color.parseColor("#CC3366"));
                c.drawCircle(x, y, r, paint);
                paint.setColor(Color.parseColor("#44AA44"));
                path.reset();
                path.moveTo(x, y - r);
                path.lineTo(x - r * 0.25f, y - r * 1.3f);
                path.lineTo(x + r * 0.1f, y - r * 1.1f);
                path.lineTo(x + r * 0.3f, y - r * 1.4f);
                path.lineTo(x + r * 0.1f, y - r * 1.0f);
                path.close();
                c.drawPath(path, paint);
                paint.setColor(Color.argb(80, 255, 255, 255));
                c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.25f, paint);
                break;
            case "Rice Ball":
                paint.setColor(Color.parseColor("#F8F4E8"));
                path.reset();
                path.moveTo(x - r, y + r * 0.3f);
                path.cubicTo(x - r * 1.1f, y - r, x - r * 0.3f, y - r * 1.3f, x, y - r * 1.3f);
                path.cubicTo(x + r * 0.3f, y - r * 1.3f, x + r * 1.1f, y - r, x + r, y + r * 0.3f);
                path.lineTo(x - r, y + r * 0.3f);
                path.close();
                c.drawPath(path, paint);
                paint.setColor(Color.parseColor("#333333"));
                rect.set(x - r * 0.9f, y + r * 0.0f, x + r * 0.9f, y + r * 0.55f);
                c.drawRoundRect(rect, r * 0.1f, r * 0.1f, paint);
                break;
            case "Apple":
                paint.setColor(Color.parseColor("#E83030"));
                c.drawCircle(x, y + r * 0.1f, r, paint);
                // dent at top
                paint.setColor(Color.parseColor("#C82020"));
                c.drawCircle(x, y - r * 0.65f, r * 0.3f, paint);
                // stem
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * 0.12f);
                paint.setColor(Color.parseColor("#885533"));
                c.drawLine(x, y - r, x + r * 0.2f, y - r * 1.35f, paint);
                paint.setStyle(Paint.Style.FILL);
                // leaf
                paint.setColor(Color.parseColor("#44AA22"));
                path.reset();
                path.moveTo(x + r * 0.2f, y - r * 1.35f);
                path.cubicTo(x + r * 0.6f, y - r * 1.5f, x + r * 0.7f, y - r * 1.1f, x + r * 0.35f, y - r * 1.15f);
                path.close();
                c.drawPath(path, paint);
                paint.setColor(Color.argb(80, 255, 255, 255));
                c.drawCircle(x - r * 0.3f, y - r * 0.1f, r * 0.2f, paint);
                break;
            case "Cake":
                // Base
                paint.setColor(Color.parseColor("#F2C97A"));
                rect.set(x - r * 0.9f, y, x + r * 0.9f, y + r * 0.8f);
                c.drawRoundRect(rect, r * 0.1f, r * 0.1f, paint);
                // Frosting
                paint.setColor(Color.parseColor("#FFFDF5"));
                rect.set(x - r * 0.9f, y - r * 0.25f, x + r * 0.9f, y + r * 0.1f);
                c.drawRoundRect(rect, r * 0.1f, r * 0.1f, paint);
                // Drip
                for (int i = 0; i < 3; i++) {
                    float dx = x - r * 0.55f + i * r * 0.55f;
                    paint.setColor(Color.parseColor("#FFF8E0"));
                    c.drawCircle(dx, y + r * 0.15f, r * 0.12f, paint);
                }
                // Candle
                paint.setColor(Color.parseColor("#FF88AA"));
                c.drawRect(x - r * 0.08f, y - r * 0.8f, x + r * 0.08f, y - r * 0.2f, paint);
                paint.setColor(Color.parseColor("#FFEE44"));
                c.drawCircle(x, y - r * 0.85f, r * 0.1f, paint);
                break;
            default:
                // Generic round food
                paint.setColor(Color.parseColor("#FF9966"));
                c.drawCircle(x, y, r, paint);
                break;
        }
    }

    private void drawHeart(Canvas c, float x, float y, float s) {
        // Simple heart shape
        path.reset();
        path.moveTo(x, y + s);
        path.cubicTo(x - s * 2f, y - s * 0.5f, x - s * 2.5f, y - s * 2f, x, y - s);
        path.cubicTo(x + s * 2.5f, y - s * 2f, x + s * 2f, y - s * 0.5f, x, y + s);
        path.close();
        c.drawPath(path, paint);
    }

    // =============================== overlays ===============================
    private void drawSleepDim(Canvas c, float w, float h) {
        if (sleepDim <= 0.01f) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int)(sleepDim * 23), 20, 20, 60));
        c.drawRect(0f, 0f, w, h, paint);
    }

    private void drawLevelUpBanner(Canvas c, float w, float h) {
        if (levelUpTimer <= 0f && sparkleTimer <= 0f) return;
        // Sparkle burst
        if (sparkleTimer > 0f) {
            int gold = Color.parseColor("#F7C948");
            float cx = w * 0.5f;
            float cy = h * 0.55f;
            float r = radius();
            for (int i = 0; i < 8; i++) {
                float ang = time * 2f + i * (float)(Math.PI * 2 / 8);
                float dist = r * 1.2f * (1f - sparkleTimer / 2.5f) + r * 0.3f;
                sparkle(c, cx + cos(ang) * dist, cy + sin(ang) * dist, r * 0.15f, gold);
            }
        }
        // Banner
        if (levelUpTimer > 0f) {
            float alpha = Math.min(1f, levelUpTimer / 0.4f) * Math.min(1f, levelUpTimer);
            float by = h * 0.38f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int)(alpha * 180), 255, 220, 60));
            rect.set(w * 0.15f, by, w * 0.85f, by + h * 0.1f);
            c.drawRoundRect(rect, 16f, 16f, paint);
            paint.setColor(Color.argb((int)(alpha * 255), 80, 40, 0));
            paint.setTextSize(h * 0.045f);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("Level Up! Level " + levelUpNumber, w * 0.5f, by + h * 0.067f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
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

        // Shake-dry wobble is applied via squish; use existing rotation calc
        float rot = sin(time * 5f) * dizzy * 0.12f + sin(time * 30f) * 10f * shakeOff;
        if (shakeAnimation) rot += sin(shakeAnimT * 18f) * 15f;

        // In sleeping mode centre the pet on the bed pillow
        float cx, cy;
        if (state == PetState.SLEEPING || sleepDim > 0.3f) {
            cx = w * 0.5f;
            cy = h * 0.74f;
        } else if (state == PetState.BATHING && bath.isActive()) {
            cx = bath.petX(w);
            cy = bath.petY(h);
        } else if (physicsPaused) {
            cx = w * 0.5f + wobX;
            cy = h * 0.76f;
        } else {
            cx = w / 2f + px + wobX;
            cy = h * 0.76f;
        }
        float sw = r * 0.045f;

        boolean sleepMode = sleepDim > 0.01f;

        // soft shadow (hidden by the bed while sleeping)
        if (!sleepMode) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(45, 60, 40, 20));
            rect.set(cx - r * (0.9f + 0.2f * squish), cy + r * 0.88f,
                     cx + r * (0.9f + 0.2f * squish), cy + r * 1.08f);
            c.drawOval(rect, paint);
        }

        c.save();
        c.translate(cx, cy);
        c.rotate(rot + tumble);
        c.scale(1f + 0.25f * squish, 1f - 0.25f * squish);

        int cream = Color.parseColor("#F9E7A0");
        int egg   = Color.parseColor("#FFFDF4");

        // feet
        float kick = Math.abs(vx) > 100f ? sin(time * 16f) * r * 0.06f : 0f;
        for (int s : SIDES) {
            path.reset();
            rect.set(s * 0.45f * r - 0.17f * r, 0.8f * r + kick * s,
                     s * 0.45f * r + 0.17f * r, 1.0f * r + kick * s);
            path.addOval(rect, Path.Direction.CW);
            fillStroke(c, cream, sw);
        }

        // egg body
        path.reset();
        rect.set(-r, -0.85f * r, r, 0.95f * r);
        path.addOval(rect, Path.Direction.CW);
        fillStroke(c, egg, sw);

        // dirt spots when cleanliness is low
        if (cleanliness < 60f) {
            float dirtAlpha = 1f - cleanliness / 60f;
            int nDirt = cleanliness < 30f ? 4 : 2;
            for (int i = 0; i < nDirt; i++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb((int)(dirtAlpha * 180), 0x8B, 0x6F, 0x47));
                c.drawCircle(dirtRx[i] * r, dirtRy[i] * r, dirtRr[i] * r, paint);
            }
            // Stink lines when very dirty
            if (cleanliness < 30f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * 0.025f);
                paint.setColor(Color.argb(140, 160, 180, 80));
                for (int i = 0; i < 3; i++) {
                    float lx = (-0.4f + i * 0.4f) * r;
                    float lyBase = -r * 0.8f;
                    float phase2 = time * 1.5f + i;
                    path.reset();
                    path.moveTo(lx, lyBase);
                    path.cubicTo(lx + sin(phase2) * r * 0.12f, lyBase - r * 0.2f,
                                 lx - sin(phase2) * r * 0.12f, lyBase - r * 0.4f,
                                 lx + sin(phase2 + 1f) * r * 0.1f, lyBase - r * 0.55f);
                    c.drawPath(path, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
        }

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

        // shell (cream dome with zigzag edge)
        path.reset();
        path.moveTo(-0.95f * r, -0.05f * r);
        path.lineTo(-0.6f * r, -0.25f * r); path.lineTo(-0.3f * r, -0.02f * r);
        path.lineTo(0f, -0.25f * r);       path.lineTo(0.3f * r, -0.02f * r);
        path.lineTo(0.6f * r, -0.25f * r); path.lineTo(0.95f * r, -0.05f * r);
        path.cubicTo(1.15f * r, -0.9f * r, 0.5f * r, -1.25f * r, 0f, -1.2f * r);
        path.cubicTo(-0.5f * r, -1.25f * r, -1.15f * r, -0.9f * r, -0.95f * r, -0.05f * r);
        path.close();
        fillStroke(c, cream, sw);

        // arms
        float wave = sin(time * 3f) * r * 0.03f + (upside ? -r * 0.15f : 0f);
        // Chew puff enlarges arms slightly
        float armPuff = 1f + chewPuff * 0.15f;
        if (!sleepMode) {   // while sleeping the arms are drawn on top of the blanket instead
            for (int s : SIDES) {
                path.reset();
                rect.set(s * 0.98f * r - 0.16f * r * armPuff, -0.2f * r + wave,
                         s * 0.98f * r + 0.16f * r * armPuff,  0.2f * r + wave);
                path.addOval(rect, Path.Direction.CW);
                fillStroke(c, cream, sw);
            }
        }

        // face (state-aware)
        drawFace(c, r, upside);
        drawHat(c, r, sw);
        c.restore();

        // blanket, footboard, arms and Z bubble sit in front of the pet while sleeping
        if (sleepMode) drawSleepFront(c, w, h, cx, cy, r, sw);

        // sparkles
        int gold = Color.parseColor("#F7C948");
        if (mood > 60f && state != PetState.SLEEPING && state != PetState.BATHING) {
            sparkle(c, cx + r * 1.05f, cy - r * 0.9f, r * 0.12f * (0.8f + 0.2f * sin(time * 3f)), gold);
            sparkle(c, cx - r * 1.15f, cy - r * 0.2f, r * 0.09f * (0.8f + 0.2f * sin(time * 3f + 1f)), gold);
        }
        if (dizzy > 25f) {
            for (int i = 0; i < 3; i++) {
                float a = time * 4f + i * 2.09f;
                sparkle(c, cx + cos(a) * r * 0.7f, cy - r * 1.4f + sin(a) * r * 0.18f, r * 0.13f, gold);
            }
        }
        // Level-up sparkle burst
        if (sparkleTimer > 0f) {
            for (int i = 0; i < 6; i++) {
                float a = time * 3f + i * (float)(Math.PI / 3);
                float d = radius() * 1.5f;
                sparkle(c, cx + cos(a) * d, cy + sin(a) * d - radius() * 0.5f, radius() * 0.18f, gold);
            }
        }
    }

    private void drawFace(Canvas c, float r, boolean upside) {
        float ey = -0.42f * r;
        float ex = 0.27f * r;
        int dark = Color.parseColor("#5A3A2E");
        paint.setColor(dark);

        if (dizzy > 40f) {
            // Swirly eyes
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.035f);
            for (int s : SIDES) {
                rect.set(s * ex - 0.11f * r, ey - 0.11f * r, s * ex + 0.11f * r, ey + 0.11f * r);
                c.drawArc(rect, (time * 300f) % 360f, 270f, false, paint);
                rect.set(s * ex - 0.05f * r, ey - 0.05f * r, s * ex + 0.05f * r, ey + 0.05f * r);
                c.drawArc(rect, (time * 300f + 180f) % 360f, 270f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else if (state == PetState.SLEEPING || sleepDim > 0.5f) {
            // Closed eyes (sleeping)
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.05f);
            paint.setColor(dark);
            for (int s : SIDES) {
                c.drawLine(s * ex - 0.08f * r, ey, s * ex + 0.08f * r, ey, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else if (upside) {
            // Scared
            for (int s : SIDES) {
                paint.setColor(Color.WHITE);
                c.drawCircle(s * ex, ey, 0.13f * r, paint);
                paint.setColor(dark);
                c.drawCircle(s * ex, ey, 0.05f * r, paint);
            }
            paint.setColor(Color.parseColor("#8FD0F0"));
            c.drawCircle(0.62f * r, -0.75f * r, 0.06f * r, paint);
        } else if (state == PetState.TIRED || eyeHalf > 0.3f) {
            // Half-closed tired eyes
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.04f);
            paint.setColor(dark);
            for (int s : SIDES) {
                rect.set(s * ex - 0.08f * r, ey - 0.07f * r * (1f - eyeHalf * 0.8f),
                         s * ex + 0.08f * r, ey + 0.07f * r);
                c.drawArc(rect, 0f, 180f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            // Yawn mouth open during yawn phase
            if (eyeHalf > 0.6f && state == PetState.TIRED) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#5A3A2E"));
                c.drawCircle(0f, -0.25f * r, r * 0.09f * eyeHalf, paint);
            }
        } else if (state == PetState.EATING && mouthOpenAng > 0f) {
            // Eating: open/close mouth with chew puff on cheeks
            for (int s : SIDES) {
                paint.setColor(dark);
                c.drawCircle(s * ex, ey, 0.07f * r, paint);
                paint.setColor(Color.WHITE);
                c.drawCircle(s * ex - 0.02f * r, ey - 0.025f * r, 0.022f * r, paint);
            }
            // Cheek puff
            paint.setColor(Color.argb((int)(chewPuff * 150), 247, 160, 150));
            for (int s : SIDES) {
                rect.set(s * 0.5f * r - 0.22f * r * (1f + chewPuff * 0.4f),
                         -0.31f * r,
                         s * 0.5f * r + 0.22f * r * (1f + chewPuff * 0.4f),
                         -0.18f * r);
                c.drawOval(rect, paint);
            }
            // Open beak
            path.reset();
            float openAmt = mouthOpenAng * 0.15f * r;
            path.moveTo(-0.08f * r, -0.35f * r - openAmt * 0.3f);
            path.lineTo(0.08f * r, -0.35f * r - openAmt * 0.3f);
            path.lineTo(0f, -0.26f * r + openAmt);
            path.close();
            paint.setColor(Color.parseColor("#F29A87"));
            c.drawPath(path, paint);
            return; // skip default beak
        } else if (mood < 30f) {
            // Sad droopy eyes
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.04f);
            paint.setColor(dark);
            for (int s : SIDES) {
                rect.set(s * ex - 0.08f * r, ey - 0.05f * r, s * ex + 0.08f * r, ey + 0.1f * r);
                c.drawArc(rect, 200f, 140f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else {
            // Happy
            for (int s : SIDES) {
                paint.setColor(dark);
                c.drawCircle(s * ex, ey, 0.07f * r, paint);
                paint.setColor(Color.WHITE);
                c.drawCircle(s * ex - 0.02f * r, ey - 0.025f * r, 0.022f * r, paint);
            }
        }

        // Blush (green when sick / dizzy)
        paint.setColor(dizzy > 90f
                ? Color.argb(150, 150, 200, 120)
                : Color.argb(150, 247, 160, 150));
        for (int s : SIDES) {
            rect.set(s * 0.5f * r - 0.17f * r, -0.31f * r,
                     s * 0.5f * r + 0.17f * r, -0.21f * r);
            c.drawOval(rect, paint);
        }

        // Head turn away when refusing food (full)
        // (Head turn is handled by the state machine exit in MainActivity)

        // Beak (normal)
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
