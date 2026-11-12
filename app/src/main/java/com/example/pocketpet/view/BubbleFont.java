package com.example.pocketpet.view;

import android.content.Context;
import android.graphics.Typeface;

import androidx.core.content.res.ResourcesCompat;

import com.example.pocketpet.R;

/** The bubble font (Fredoka) for text drawn straight onto a Canvas, where XML styles do not reach. */
public final class BubbleFont {
    private BubbleFont() {}
    private static Typeface bold;

    public static Typeface bold(Context c) {
        if (bold == null) {
            try { bold = ResourcesCompat.getFont(c.getApplicationContext(), R.font.fredoka_bold); } catch (Exception ignored) { }
        }
        return bold == null ? Typeface.DEFAULT_BOLD : bold;
    }
}
