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
import java.util.ArrayDeque;
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
    public interface OnTap         { void onTap(); }

    private OnFoodEaten   foodEatenListener;
    private OnBathFinished bathFinishedListener;
    private OnWake        wakeListener;
    private OnTap         tapListener;
    private boolean       tapArmed;
    private float         tapDownX, tapDownY;
    private long          tapDownT;
    private Runnable      onDizzyTick;

    // ---- state pushed from the activity ----
    private WeatherType weather = WeatherType.CLEAR_DAY;
    private int   stage   = 0;
    private String hat    = "";
    private String shell  = "";
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

    // ---- Mochi's voice: speech bubbles ----
    private final ArrayDeque<String> sayQueue = new ArrayDeque<>();
    private String sayText = "";
    private float  sayT    = 0f;
    private static final float SAY_DUR = 2.7f;
    private float  idleTalk = 12f;            // seconds until the next idle remark
    private boolean talkSleepyNext = false;
    private float  energy = 100f;
    private android.graphics.Typeface speechFont;

    // ---- dizzy -> angry ("No no!") ----
    private boolean wasDizzy = false;
    private float   angryT   = 0f;

    // ---- eating: Mochi holds the food in both hands at his mouth ----
    private boolean foodHeld = false;

    // where Mochi was last drawn (the speech bubble and the hands follow this)
    private float petX, petY, petR;

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
    private final float[][]particles= new float[70][];
    private final int    outline   = Color.parseColor("#8B5E4B");


    // ---- dirty-spot positions for cleanliness visual ----
    private final float[] dirtRx = {-0.55f, 0.60f, 0.20f, -0.30f};
    private final float[] dirtRy = { 0.20f, 0.50f,-0.10f,  0.60f};
    private final float[] dirtRr = { 0.09f, 0.07f, 0.06f,  0.08f};

    public PetView(Context c) { this(c, null); }

    public PetView(Context c, AttributeSet a) {
        super(c, a);
        try {   // bubble-style font bundled in res/font
            int id = getResources().getIdentifier("fredoka_bold", "font", getContext().getPackageName());
            if (id != 0) speechFont = androidx.core.content.res.ResourcesCompat.getFont(getContext(), id);
            bath.setFont(speechFont);
        } catch (Exception ignored) { }
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
    public void setWeather(WeatherType w)    { weather = w; }
    public void setStage(int s)              { stage = s; }
    public void setHat(String h)             { hat = h == null ? "" : h; }
    public void setShell(String sh)          { shell = sh == null ? "" : sh; }
    public void setMood(float m)             { mood = m; }
    public void setEnergy(float e)           { energy = e; }

    /** Mochi says something: one or more short lines shown one after another in a speech bubble. */
    public void say(String... lines) {
        for (String l : lines) if (l != null && !l.isEmpty()) sayQueue.add(l);
        if (sayT <= 0f) nextSay();
    }

    private void nextSay() {
        String n = sayQueue.poll();
        if (n != null) { sayText = n; sayT = SAY_DUR; } else { sayText = ""; sayT = 0f; }
    }
    public void setCleanliness(float c)      { cleanliness = c; }
    public void setOnDizzyTick(Runnable r)   { onDizzyTick = r; }
    public void setOnFoodEaten(OnFoodEaten l)       { foodEatenListener = l; }
    public void setOnBathFinished(OnBathFinished l) { bathFinishedListener = l; }
    public void setOnWake(OnWake l)                 { wakeListener = l; }
    /** Called when the player taps Mochi's body while he is awake and not eating or bathing. */
    public void setOnTap(OnTap l)                   { tapListener = l; }
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
        foodHeld     = false;
        // Start food at a fixed offset to the right of centre
        eatFoodX = getWidth() * 0.5f + getWidth() * 0.28f;
        eatFoodY = getHeight() * 0.72f;
        // Mouth target
        foodTargetX = getWidth() * 0.5f;
        foodTargetY = getHeight() * 0.76f + radius() * 0.12f;      // right at Mochi's mouth
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
        foodHeld      = false;
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

    private String wakeLine = null;      // set by the screen before waking Mochi up early; null = the happy default

    /** What Mochi says when he wakes up next (use null for the normal "nice sleep" line). */
    public void setWakeLine(String line) { wakeLine = line; }

    public void exitSleeping() {
        state         = PetState.IDLE;
        physicsPaused = false;
        say(wakeLine != null ? wakeLine : "Hello! It was a nice sleep!");
        wakeLine = null;
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
        // A quick tap on Mochi himself (idle or tired): used for the Mochi voice. Taps anywhere else are untouched.
        if (tapListener != null && (state == PetState.IDLE || state == PetState.TIRED)) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float r = petR > 0f ? petR : radius();
                    float dx = ev.getX() - petX, dy = ev.getY() - petY;
                    tapArmed = dx * dx + dy * dy <= (r * 1.15f) * (r * 1.15f);
                    tapDownX = ev.getX(); tapDownY = ev.getY(); tapDownT = ev.getEventTime();
                    if (tapArmed) return true;              // we want the matching "finger up"
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    boolean tap = tapArmed && ev.getEventTime() - tapDownT < 450
                            && Math.abs(ev.getX() - tapDownX) < 30f && Math.abs(ev.getY() - tapDownY) < 30f;
                    tapArmed = false;
                    if (tap) { tapListener.onTap(); return true; }
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    tapArmed = false;
                    break;
                default:
                    if (tapArmed) return true;
            }
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
                    float dpy = eatFoodY - foodTargetY;
                    if (dpx * dpx + dpy * dpy < (radius() * 0.7f) * (radius() * 0.7f)) {
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
        return Math.min(getWidth(), getHeight()) * 0.17f * SCENE_PET_SCALE * (1f + 0.12f * stage);
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
            foodTargetX = getWidth() * 0.5f;
            foodTargetY = getHeight() * 0.76f + radius() * 0.12f;
            if (foodFloating) foodHeld = true;
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
                foodHeld = false;                      // hands come down again
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

        // ---- dizzy -> angry: once the spinning stops Mochi gets cross and says "No no!" ----
        if (dizzy > 40f) wasDizzy = true;
        if (wasDizzy && dizzy <= 25f && state != PetState.SLEEPING) {
            wasDizzy = false;
            angryT = 3.6f;
            sayQueue.clear();
            sayText = ""; sayT = 0f;
            say("No no!");
        }
        if (angryT > 0f) angryT -= dt;

        // ---- Mochi's voice ----
        if (sayT > 0f) {
            sayT -= dt;
            if (sayT <= 0f) nextSay();
        }
        boolean calm = (state == PetState.IDLE || state == PetState.TIRED) && sleepDim < 0.01f
                && dizzy < 10f && angryT <= 0f && sayT <= 0f && sayQueue.isEmpty();
        if (calm) {
            idleTalk -= dt;
            if (idleTalk <= 0f) {
                idleTalk = 35f;
                boolean sleepy = energy <= 30f, lonely = mood < 99.5f;
                if (sleepy && (!lonely || talkSleepyNext)) { say("Let's sleep!"); talkSleepyNext = false; }
                else if (lonely) { say("Let's play!"); talkSleepyNext = true; }
            }
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
        drawSpeech(canvas, w, h);     // Mochi's speech bubble
        drawSleepDim(canvas, w, h);   // dark overlay
        drawLevelUpBanner(canvas, w, h);
        postInvalidateOnAnimation();
    }

    // =============================== room + sky ===============================
    // Both scenes are drawn by dedicated Cute Kawaii Storybook classes. The view through each window is
    // the live weather (Open-Meteo, see WeatherRepository) painted by KawaiiWeather.
    private final KawaiiLivingRoom livingRoom = new KawaiiLivingRoom();
    private final KawaiiBedroom    bedroom    = new KawaiiBedroom();
    private final KawaiiBathroom   bathroom   = new KawaiiBathroom();

    /**
     * Mochi is drawn a little smaller than before so the pet matches the scale of the room in the
     * reference pictures. Set to 1f for the old size.
     */
    private static final float SCENE_PET_SCALE = 0.60f;

    private void drawRoom(Canvas c, float w, float h) {
        if (state == PetState.BATHING) bathroom.draw(c, w, h, time, weather, flash);   // a real bathroom for bath time
        else livingRoom.draw(c, w, h, time, weather, flash);
    }

    // =============================== sleep scene ===============================
    /** Back layer of the sleep scene (everything behind Mochi), fades in with sleepDim. */
    private void drawSleepScene(Canvas c, float w, float h) {
        if (sleepDim <= 0.01f) return;
        int sc = 0;
        boolean layer = sleepDim < 0.999f;
        if (layer) sc = c.saveLayerAlpha(0f, 0f, w, h, (int) (sleepDim * 255));
        bedroom.drawBack(c, w, h, time, weather, flash);
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

        // ---- blanket, hem and footboard (Cute Kawaii Storybook bedroom) ----
        bedroom.drawFront(c, w, h, time, cx, cy, r);

        if (layer) c.restoreToCount(sc);

        // ---- Mochi's arms rest on top of the blanket ----
        int cream = sleepTint(Color.parseColor("#F9E7A0"));
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
        float heldR = petR > 0f ? petR : radius();
        float fr = foodHeld ? heldR * 0.42f * (1f - eatBites * 0.22f)        // held: big and clear, right under the mouth
                            : foodRadius() * (1f - eatBites * 0.28f);
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
            // arms reach out behind the food, the food sits at his mouth, the hands wrap around it
            drawFoodByName(c, eatItemName, fx, fy, fr);
        }
        if (foodHeld) drawHandsOnFood(c, fx, fy, fr);
    }

    /**
     * Mochi holds the food with both hands: two little oval hands on his belly, one on each side of the food,
     * slightly tilted toward it, drawn in front of the food (no arms, just like the reference picture).
     */
    private void drawHandsOnFood(Canvas c, float fx, float fy, float fr) {
        float r = petR > 0f ? petR : radius();
        float sw = r * 0.045f;
        int cream = Color.parseColor("#F9E7A0");
        int shade = Color.parseColor("#EBCF73");
        for (int s : SIDES) {
            float hx = fx + s * fr * 0.8f;
            float hy = fy + fr * (s < 0 ? 0.22f : 0.14f);
            c.save();
            c.translate(hx, hy);
            c.rotate(-s * 18f);
            rect.set(-0.2f * r, -0.14f * r, 0.2f * r, 0.14f * r);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(cream);
            c.drawOval(rect, paint);
            // soft shading along the lower edge
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.045f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(shade);
            rect.set(-0.16f * r, -0.1f * r, 0.16f * r, 0.1f * r);
            c.drawArc(rect, 25f, 130f, false, paint);
            // outline
            paint.setStrokeWidth(sw);
            paint.setColor(outline);
            rect.set(-0.2f * r, -0.14f * r, 0.2f * r, 0.14f * r);
            c.drawOval(rect, paint);
            c.restore();
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    // =============================== speech bubble ===============================
    private void drawSpeech(Canvas c, float w, float h) {
        if (sayT <= 0f || sayText.isEmpty() || state == PetState.SLEEPING || state == PetState.BATHING || sleepDim > 0.2f) return;
        float r = petR > 0f ? petR : radius();
        float fade = Math.min(1f, Math.min(sayT / 0.3f, (SAY_DUR - sayT) / 0.2f));
        int a = (int) (255 * Math.max(0f, fade));
        float ts = Math.max(r * 0.36f, 13f);
        paint.setTextSize(ts);
        if (speechFont != null) paint.setTypeface(speechFont);
        paint.setTextAlign(Paint.Align.CENTER);
        float tw = paint.measureText(sayText);
        float padX = ts * 0.85f, padY = ts * 0.6f;
        float bw = tw + padX * 2f, bh = ts + padY * 2f;
        float hatUp = Math.max(1.35f, -ItemArt.hatTop(hat)) * r;
        float tail = ts * 0.55f;
        float bottom = petY - hatUp - tail - ts * 0.15f;
        float top = Math.max(6f, bottom - bh);
        bottom = top + bh;
        float left = Math.max(6f, Math.min(w - bw - 6f, petX - bw / 2f));
        float right = left + bw;
        float rad = bh * 0.5f;
        float osw = ts * 0.09f;
        // bubble
        rect.set(left, top, right, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(a, 255, 249, 236));
        c.drawRoundRect(rect, rad, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(osw);
        paint.setColor(Color.argb(a, 240, 143, 176));
        c.drawRoundRect(rect, rad, rad, paint);
        // tail
        float tx = Math.max(left + rad, Math.min(right - rad, petX));
        path.reset();
        path.moveTo(tx - tail * 0.7f, bottom - osw * 0.3f);
        path.lineTo(tx, bottom + tail);
        path.lineTo(tx + tail * 0.7f, bottom - osw * 0.3f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(a, 255, 249, 236));
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.argb(a, 240, 143, 176));
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(a, 255, 249, 236));
        c.drawRect(tx - tail * 0.7f + osw, bottom - osw * 1.1f, tx + tail * 0.7f - osw, bottom + osw * 0.2f, paint);
        // text + a tiny heart
        paint.setColor(Color.argb(a, 122, 74, 58));
        c.drawText(sayText, (left + right) / 2f, top + bh / 2f + ts * 0.34f, paint);
        paint.setColor(Color.argb((int) (a * 0.8f), 243, 119, 159));
        drawHeart(c, right - rad * 0.55f, top + rad * 0.45f, ts * 0.13f);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(null);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFoodByName(Canvas c, String name, float x, float y, float r) {
        // same kawaii artwork as the shop and bag (see ItemArt), without the little face while it is being eaten
        ItemArt.food(c, name, x, y, r, false);
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
            if (speechFont != null) paint.setTypeface(speechFont);
            c.drawText("Level Up! Level " + levelUpNumber, w * 0.5f, by + h * 0.067f, paint);
            paint.setTypeface(null);
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

    /** Warm, dim golden tint for Mochi while the bedroom lights are low. */
    private int sleepTint(int col) {
        return sleepDim <= 0.01f ? col : ColorUtils.blendARGB(col, 0xFF6A5232, 0.48f * sleepDim);
    }

    private void drawPet(Canvas c, float w, float h) {
        // a little smaller while tucked into the bed so he fits the bed like the reference picture
        float r = radius() * (1f - 0.18f * sleepDim);
        boolean upside = tumble > 90f;
        float wobX = dizzy > 30f ? sin(time * 7f) * dizzy * 0.25f : 0f;

        // Shake-dry wobble is applied via squish; use existing rotation calc
        float rot = sin(time * 5f) * dizzy * 0.12f + sin(time * 30f) * 10f * shakeOff;
        if (shakeAnimation) rot += sin(shakeAnimT * 18f) * 15f;
        if (angryT > 0f) rot += sin(time * 34f) * 2.5f;       // trembling with anger

        // In sleeping mode centre the pet on the bed pillow
        float cx, cy;
        if (state == PetState.SLEEPING || sleepDim > 0.3f) {
            cx = bedroom.bedCx(w, h);
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
        petX = cx; petY = cy; petR = r;

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

        int cream = sleepTint(Color.parseColor("#F9E7A0"));
        int egg   = sleepTint(ItemArt.shellFill(shell));

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

        // egg pattern (depends on the equipped eggshell)
        ItemArt.shellPattern(c, shell, r);

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
        if (!sleepMode && !foodHeld) {   // while sleeping the arms are drawn on top of the blanket; while eating the hands hold the food
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
        if (angryT > 0f && dizzy <= 40f && !sleepMode) drawAngryMarks(c, r);
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
        } else if (angryT > 0f) {
            // Cross face: slanted brows, narrowed eyes
            paint.setStyle(Paint.Style.FILL);
            for (int s : SIDES) {
                paint.setColor(dark);
                rect.set(s * ex - 0.075f * r, ey - 0.07f * r, s * ex + 0.075f * r, ey + 0.085f * r);
                c.drawOval(rect, paint);
                paint.setColor(Color.WHITE);
                c.drawCircle(s * ex - 0.025f * r, ey - 0.03f * r, 0.024f * r, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(r * 0.055f);
            paint.setColor(dark);
            for (int s : SIDES) {
                c.drawLine(s * (ex + 0.13f * r), ey - 0.19f * r, s * (ex - 0.11f * r), ey - 0.085f * r, paint);
            }
            paint.setStrokeCap(Paint.Cap.BUTT);
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
            if (foodHeld) {
                // chewing mouth: two little bumps that bob while he chews (replaces the beak, as in the reference)
                float mo = 0.75f + 0.55f * mouthOpenAng;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(r * 0.035f);
                paint.setColor(dark);
                path.reset();
                path.moveTo(-0.125f * r, -0.27f * r);
                path.quadTo(-0.065f * r, -0.27f * r + 0.13f * r * mo, 0f, -0.27f * r);
                path.quadTo(0.065f * r, -0.27f * r + 0.13f * r * mo, 0.125f * r, -0.27f * r);
                c.drawPath(path, paint);
                c.drawLine(-0.145f * r, -0.3f * r, -0.125f * r, -0.27f * r, paint);
                c.drawLine(0.145f * r, -0.3f * r, 0.125f * r, -0.27f * r, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStyle(Paint.Style.FILL);
            } else {
                // Open beak
                path.reset();
                float openAmt = mouthOpenAng * 0.15f * r;
                path.moveTo(-0.08f * r, -0.35f * r - openAmt * 0.3f);
                path.lineTo(0.08f * r, -0.35f * r - openAmt * 0.3f);
                path.lineTo(0f, -0.26f * r + openAmt);
                path.close();
                paint.setColor(Color.parseColor("#F29A87"));
                c.drawPath(path, paint);
            }
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

        if (angryT > 0f && dizzy <= 40f) {          // little frown
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(r * 0.04f);
            paint.setColor(dark);
            path.reset();
            path.moveTo(-0.1f * r, -0.1f * r);
            path.quadTo(0f, -0.2f * r, 0.1f * r, -0.1f * r);
            c.drawPath(path, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** The red anger mark on his head and the little "huff" strokes beside his body (pet space). */
    private void drawAngryMarks(Canvas c, float r) {
        float pulse = 1f + 0.12f * sin(time * 14f);
        float vx = 0.66f * r, vy = -1.22f * r, u = 0.22f * r * pulse;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int pass = 0; pass < 2; pass++) {
            paint.setStrokeWidth(pass == 0 ? r * 0.105f : r * 0.065f);
            paint.setColor(pass == 0 ? Color.parseColor("#B71C1C") : Color.parseColor("#F0453C"));
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    path.reset();
                    path.moveTo(vx + sx * 0.34f * u, vy + sy * 1.2f * u);
                    path.quadTo(vx + sx * 1.2f * u, vy + sy * 1.2f * u, vx + sx * 1.2f * u, vy + sy * 0.34f * u);
                    c.drawPath(path, paint);
                }
            }
        }
        // huff strokes beside the body
        paint.setStrokeWidth(r * 0.045f);
        paint.setColor(Color.parseColor("#7A4A3A"));
        for (int s : SIDES) {
            float bx = s * 1.2f * r, by = -0.62f * r;
            for (int k = -1; k <= 1; k++) {
                float a = (float) Math.toRadians(-90 + k * 38) ;
                float dx = s * (float) Math.cos(a + (s < 0 ? Math.PI : 0) * 0f);
                c.drawLine(bx + s * 0.02f * r, by + k * 0.07f * r, bx + s * (0.2f + 0.04f * (1 - Math.abs(k))) * r * pulse, by + k * 0.2f * r, paint);
            }
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHat(Canvas c, float r, float sw) {
        ItemArt.hat(c, hat, r);     // Straw Hat, Leaf Hat, Pink Bow, Flower Crown, Cat Ears, Chef/Party/Wizard Hat, Royal Crown
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
