package com.example.pocketpet.ui;

import com.example.pocketpet.R;
import com.example.pocketpet.view.BubbleButton;

/**
 * Same idea as StoreTabs (Shop: Food / Dresses), but each tab has its own pastel colour:
 * Mochi Friends = mint green, What is happening = lilac. Selected = raised coloured button with white bubble text;
 * unselected = flat cream button with coloured text.
 */
final class CommunityTabs {
    private CommunityTabs() {}

    static void style(BubbleButton friends, BubbleButton feed, boolean feedSelected) {
        apply(friends, !feedSelected, R.drawable.btn_tab_mint_on, 0xFF4F8F69, 0xFF5E9C78);
        apply(feed, feedSelected, R.drawable.btn_tab_lilac_on, 0xFF6E559A, 0xFF8268B5);
    }

    private static void apply(BubbleButton t, boolean on, int onBackground, int outline, int offTextColor) {
        float dp = t.getResources().getDisplayMetrics().density;
        t.setBackgroundResource(on ? onBackground : R.drawable.btn_tab_off);
        t.setTextColor(on ? 0xFFFFFFFF : offTextColor);
        t.setOutline(outline, on ? 3f * dp : 0f);
    }
}
