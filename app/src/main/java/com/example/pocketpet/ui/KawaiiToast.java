package com.example.pocketpet.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import com.example.pocketpet.R;

/**
 * A toast in the storybook style (bubble font, cream pill with a pink stitched outline). Android's own Toast cannot use
 * our font on modern versions, so this slides a small view over the screen instead and removes it after about 2 seconds.
 * Falls back to a normal Toast when there is no screen to draw on.
 */
public final class KawaiiToast {
    private KawaiiToast() {}


    public static void show(Context ctx, String text) {
        if (!(ctx instanceof Activity)) { Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show(); return; }
        final Activity a = (Activity) ctx;
        final ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) { Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show(); return; }
        float dp = a.getResources().getDisplayMetrics().density;

        View old = root.findViewWithTag("kawaii_toast");
        if (old != null) root.removeView(old);

        TextView t = new TextView(a);
        t.setTag("kawaii_toast");
        t.setText(text);
        t.setTextSize(15f);
        t.setTextColor(0xFF7A4A3A);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_toast_kawaii);
        t.setPadding((int) (20 * dp), (int) (11 * dp), (int) (20 * dp), (int) (14 * dp));
        t.setMaxWidth((int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f));
        try {
            Typeface tf = ResourcesCompat.getFont(a, R.font.fredoka_bold);
            if (tf != null) t.setTypeface(tf);
        } catch (Exception ignored) { }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = (int) (150 * dp);
        t.setLayoutParams(lp);
        t.setAlpha(0f);
        t.setTranslationY(24 * dp);
        t.setElevation(12 * dp);
        root.addView(t);
        t.animate().alpha(1f).translationY(0f).setDuration(180).start();
        t.postDelayed(() -> t.animate().alpha(0f).translationY(-12 * dp).setDuration(220)
                .withEndAction(() -> { if (t.getParent() != null) root.removeView(t); }).start(), 2200);
    }
}
