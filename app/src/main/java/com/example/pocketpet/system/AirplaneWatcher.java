package com.example.pocketpet.system;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.example.pocketpet.Bg;
import com.example.pocketpet.R;

/**
 * App-wide airplane-mode watcher (a BROADCAST RECEIVER for Intent.ACTION_AIRPLANE_MODE_CHANGED, plus a settings observer
 * as a backup). When airplane mode is switched ON, a kawaii storybook banner floats over whichever Mochi screen is open
 * and stays about 6.5 seconds. Switching it OFF removes the banner. The banner never takes a tap.
 * Installed once from PocketPetApp.
 */
public final class AirplaneWatcher {
    private AirplaneWatcher() {}

    private static final String TAG_VIEW = "airplane_banner";
    private static final long SHOW_MS = 6500;                 // always at least 5 seconds
    private static final String MESSAGE = "Airplane mode turned on, so now you won't be able to track your friends.";

    private static Application app;
    private static Activity top;                              // the screen that is open right now
    private static boolean lastOn;
    private static long deadline = 0;                         // elapsedRealtime until which the banner should be visible
    private static ValueAnimator anim;

    public static void install(@NonNull Application a) {
        app = a;
        lastOn = isOn();
        a.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { check(); }
        }, new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        try {                                                  // backup: the setting itself changed
            a.getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), false,
                    new ContentObserver(Bg.UI) { @Override public void onChange(boolean selfChange) { check(); } });
        } catch (Exception ignored) { }
        a.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity act) {
                top = act;
                long left = deadline - SystemClock.elapsedRealtime();
                if (isOn() && left > 1200) show(act, left);   // moved to another screen while the banner was showing
            }
            @Override public void onActivityPaused(@NonNull Activity act) { if (top == act) { top = null; remove(act); } }
            @Override public void onActivityCreated(@NonNull Activity act, @Nullable Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity act) {}
            @Override public void onActivityStopped(@NonNull Activity act) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity act, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity act) {}
        });
    }

    public static boolean isOn() {
        return app != null && Settings.Global.getInt(app.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private static void check() {
        boolean on = isOn();
        if (on == lastOn) return;                              // the receiver and the observer both fire: act only once
        lastOn = on;
        if (on) {
            deadline = SystemClock.elapsedRealtime() + SHOW_MS;
            if (top != null) show(top, SHOW_MS);
        } else {
            deadline = 0;
            if (top != null) remove(top);
        }
    }

    // ------------------------------------------------------------------ the banner

    private static void show(final Activity a, final long durationMs) {
        final ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) return;
        remove(a);
        final float dp = a.getResources().getDisplayMetrics().density;

        final LinearLayout box = new LinearLayout(a);
        box.setTag(TAG_VIEW);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.bg_airplane_banner);
        box.setPadding((int) (16 * dp), (int) (12 * dp), (int) (22 * dp), (int) (18 * dp));
        box.setElevation(40 * dp);
        box.setClickable(false);                               // never takes a tap
        box.setFocusable(false);

        ImageView icon = new ImageView(a);
        icon.setImageResource(R.drawable.ic_airplane_cute);
        box.addView(icon, new LinearLayout.LayoutParams((int) (52 * dp), (int) (52 * dp)));

        TextView tv = new TextView(a);
        tv.setText(MESSAGE);
        tv.setTextSize(16f);
        tv.setTextColor(0xFF3F6E8F);
        tv.setLineSpacing(0f, 1.08f);
        try { tv.setTypeface(ResourcesCompat.getFont(a, R.font.fredoka_bold)); } catch (Exception ignored) { }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = (int) (10 * dp);
        tv.setMaxWidth((int) (a.getResources().getDisplayMetrics().widthPixels * 0.88f - 130 * dp));
        box.addView(tv, tlp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (84 * dp);
        root.addView(box, lp);
        box.setAlpha(0f);

        final OvershootInterpolator pop = new OvershootInterpolator(1.8f);
        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(durationMs);
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(an -> {
            float elapsed = an.getAnimatedFraction() * durationMs;
            float in = Math.min(1f, elapsed / 380f);                         // pops in
            float out = elapsed > durationMs - 450 ? Math.max(0f, (durationMs - elapsed) / 450f) : 1f;   // fades at the very end
            box.setAlpha(in * out);
            float sc = 0.7f + 0.3f * pop.getInterpolation(in);
            box.setScaleX(sc);
            box.setScaleY(sc);
            box.setTranslationY((float) Math.sin(elapsed / 1000f * Math.PI * 1.6) * 5f * dp);   // gentle bobbing
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { if (box.getParent() != null) ((ViewGroup) box.getParent()).removeView(box); }
        });
        anim = va;
        va.start();
    }

    private static void remove(Activity a) {
        if (anim != null) { anim.removeAllListeners(); anim.cancel(); anim = null; }
        ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) return;
        View old = root.findViewWithTag(TAG_VIEW);
        if (old != null) root.removeView(old);
    }
}
