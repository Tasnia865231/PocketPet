package com.example.pocketpet.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.example.pocketpet.R;
import com.example.pocketpet.databinding.DialogKawaiiBinding;

/**
 * The storybook dialog used for explanations ("Let Mochi call you"), confirmations ("Buy this?") and permission
 * requests: cream card, stitched border, Mochi's face and bubble-font text. The Android permission pop-up itself is drawn
 * by the system, so we ask in our own words first and only then show it.
 */
public final class KawaiiDialog {
    private KawaiiDialog() {}

    /** Shows a dialog. Pass null for noText to show a single button. */
    public static Dialog show(Activity a, int iconRes, String title, String message,
                              String yesText, Runnable onYes, String noText, Runnable onNo) {
        if (a == null || a.isFinishing()) return null;
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogKawaiiBinding b = DialogKawaiiBinding.inflate(LayoutInflater.from(a));
        d.setContentView(b.getRoot());
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            float dp = a.getResources().getDisplayMetrics().density;
            int w = Math.min(a.getResources().getDisplayMetrics().widthPixels - (int) (36 * dp), (int) (380 * dp));
            d.getWindow().setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        b.dlgIcon.setImageResource(iconRes == 0 ? R.drawable.ic_mochi_happy : iconRes);
        b.dlgTitle.setText(title);
        b.dlgMessage.setText(message);
        b.dlgYes.setText(yesText);
        b.dlgYes.setOnClickListener(v -> { d.dismiss(); if (onYes != null) onYes.run(); });
        if (noText == null) {
            b.dlgNo.setVisibility(View.GONE);
        } else {
            b.dlgNo.setText(noText);
            b.dlgNo.setOnClickListener(v -> { d.dismiss(); if (onNo != null) onNo.run(); });
        }
        d.setOnCancelListener(x -> { if (onNo != null) onNo.run(); });
        d.show();
        return d;
    }
}
