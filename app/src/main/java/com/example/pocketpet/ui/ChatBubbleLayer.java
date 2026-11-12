package com.example.pocketpet.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.pocketpet.R;
import com.example.pocketpet.view.BubbleFont;

import java.util.ArrayList;

/**
 * The floating chat bubbles of the match screen. A bubble pops out of the sender's player card, drifts up while
 * bobbing, stays about 4 seconds and fades away. At most 3 are shown at once (the oldest goes first).
 *
 * This layer can NEVER block a tap: it is not clickable, its bubbles are not clickable, and it refuses every touch,
 * so the finger always reaches the board (or whatever is) underneath.
 */
public class ChatBubbleLayer extends FrameLayout {
    private static final int MAX_BUBBLES = 3;
    private static final long LIFE_MS = 4000;
    private static final float RISE_DP = 60f;

    private static final int MINT_NAME = 0xFF3F8F63, PINK_NAME = 0xFFC65A78;
    private static final int MINT_DOT = 0xFFCDEBD7, PINK_DOT = 0xFFFAD0DB;
    private static final int MINT_DOT_EDGE = 0xFF8CCBA3, PINK_DOT_EDGE = 0xFFEFA3B8;

    private static final class Bubble { View view; ValueAnimator anim; }

    private final float dp;
    private final ArrayList<Bubble> live = new ArrayList<>();
    private int flip = 0;

    public ChatBubbleLayer(Context c) { this(c, null); }

    public ChatBubbleLayer(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClipChildren(false);
    }

    // never take a touch: the board underneath must always stay tappable
    @Override public boolean onTouchEvent(MotionEvent e) { return false; }
    @Override public boolean onInterceptTouchEvent(MotionEvent e) { return false; }

    /** Shows one bubble that rises from the given player card. isX = Player 1 (mint), otherwise Player 2 (pink). */
    public void show(@NonNull View card, boolean isX, @NonNull String name, @NonNull String text) {
        if (getWidth() == 0 || card.getWidth() == 0) return;     // screen not drawn yet
        while (live.size() >= MAX_BUBBLES) dismissNow(live.get(0));

        final Context ctx = getContext();
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setClickable(false);
        root.setFocusable(false);

        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundResource(isX ? R.drawable.bg_chat_bubble_mint : R.drawable.bg_chat_bubble_pink);
        body.setPadding((int) (13 * dp), (int) (7 * dp), (int) (13 * dp), (int) (11 * dp));
        int maxW = (int) (getWidth() * 0.44f);

        TextView tvName = new TextView(ctx);
        tvName.setText(name);
        tvName.setTextSize(11f);
        tvName.setTextColor(isX ? MINT_NAME : PINK_NAME);
        tvName.setTypeface(BubbleFont.bold(ctx));
        tvName.setSingleLine(true);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        tvName.setMaxWidth(maxW - (int) (26 * dp));
        body.addView(tvName, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        TextView tvText = new TextView(ctx);
        tvText.setText(text);
        tvText.setTextSize(16f);
        tvText.setTextColor(0xFF5B4636);
        tvText.setTypeface(BubbleFont.bold(ctx));
        tvText.setMaxWidth(maxW - (int) (26 * dp));
        tvText.setLinksClickable(false);                          // links are only text
        tvText.setAutoLinkMask(0);
        tvText.setTextIsSelectable(false);
        body.addView(tvText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        // WRAP_CONTENT on purpose: a default "match parent" child would collapse the bubble to the width of its tail dots
        root.addView(body, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // two little round "thought" dots under the bubble, pointing back at the player card
        root.addView(dot(isX ? MINT_DOT : PINK_DOT, isX ? MINT_DOT_EDGE : PINK_DOT_EDGE, 9, -2, 0));
        root.addView(dot(isX ? MINT_DOT : PINK_DOT, isX ? MINT_DOT_EDGE : PINK_DOT_EDGE, 5, 0, isX ? -9 : 9));

        root.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        final int w = root.getMeasuredWidth(), h = root.getMeasuredHeight();

        int[] a = new int[2], l = new int[2];
        card.getLocationInWindow(a);
        getLocationInWindow(l);
        float cx = a[0] - l[0] + card.getWidth() / 2f;
        float cardTop = a[1] - l[1];
        float x = Math.max(4 * dp, Math.min(getWidth() - w - 4 * dp, cx - w / 2f));
        float y = cardTop + card.getHeight() * 0.55f - h;          // the bubble's tail starts on the card

        final float jitter = ((flip++ & 1) == 0 ? -1 : 1) * 10 * dp;
        final float phase = (float) (Math.random() * Math.PI * 2);
        LayoutParams lp = new LayoutParams(w, h);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.round(x);                 // the start position lives in the margins, so the
        lp.topMargin = Math.round(y);                  // animation below only ever adds small offsets to it
        addView(root, lp);
        root.setPivotX(w / 2f);
        root.setPivotY(h);
        root.setAlpha(0f);

        final Bubble bub = new Bubble();
        bub.view = root;
        final OvershootInterpolator pop = new OvershootInterpolator(2.2f);
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(LIFE_MS);
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            float rise = 1f - (1f - t) * (1f - t);                              // rises fast, then slows
            root.setTranslationY(-rise * RISE_DP * dp + (float) Math.sin(t * Math.PI * 4) * 3f * dp);
            root.setTranslationX(jitter + (float) Math.sin(t * Math.PI * 3 + phase) * 4f * dp);
            float in = Math.min(1f, t / 0.07f);                                 // pops in during the first ~0.3 s
            float out = t > 0.85f ? (1f - t) / 0.15f : 1f;                      // fades during the last ~0.6 s
            root.setAlpha(Math.max(0f, Math.min(1f, in)) * out);
            float sc = 0.55f + 0.45f * pop.getInterpolation(in);
            root.setScaleX(sc);
            root.setScaleY(sc);
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { remove(bub); }
        });
        bub.anim = va;
        live.add(bub);
        va.start();
    }

    private View dot(int fill, int edge, int sizeDp, int topMarginDp, int shiftDp) {
        View v = new View(getContext());
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke((int) Math.max(1, 1.5f * dp), edge);
        v.setBackground(g);
        v.setClickable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (sizeDp * dp), (int) (sizeDp * dp));
        lp.topMargin = (int) (topMarginDp * dp);
        lp.leftMargin = Math.max(0, (int) (shiftDp * dp));
        lp.rightMargin = Math.max(0, (int) (-shiftDp * dp));
        v.setLayoutParams(lp);
        return v;
    }

    /** The oldest bubble makes room for a new one: a very quick fade. */
    private void dismissNow(final Bubble b) {
        live.remove(b);
        b.anim.removeAllListeners();
        b.anim.removeAllUpdateListeners();
        b.anim.cancel();
        b.view.animate().alpha(0f).setDuration(140).withEndAction(() -> removeView(b.view)).start();
    }

    private void remove(Bubble b) {
        live.remove(b);
        removeView(b.view);
    }

    /** Removes every bubble at once (the screen is stopping or the match is being left). */
    public void clear() {
        for (Bubble b : new ArrayList<>(live)) {
            b.anim.removeAllListeners();
            b.anim.removeAllUpdateListeners();
            b.anim.cancel();
            b.view.animate().cancel();
        }
        live.clear();
        removeAllViews();
    }

    @Override protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }
}
