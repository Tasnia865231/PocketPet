package com.example.pocketpet.ui;

import com.example.pocketpet.R;
import com.example.pocketpet.view.BubbleButton;

/** Selected tab = raised pink button with white bubble text; unselected = flat cream button with brown text. */
final class StoreTabs {
    private StoreTabs() {}

    static void style(BubbleButton food, BubbleButton dress, boolean dressesSelected) {
        apply(food, !dressesSelected);
        apply(dress, dressesSelected);
    }

    private static void apply(BubbleButton t, boolean on) {
        float dp = t.getResources().getDisplayMetrics().density;
        t.setBackgroundResource(on ? R.drawable.btn_tab_on : R.drawable.btn_tab_off);
        t.setTextColor(on ? 0xFFFFFFFF : 0xFF8A6A55);
        t.setOutline(0xFFB84F74, on ? 3f * dp : 0f);
    }
}
