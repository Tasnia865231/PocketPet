package com.example.pocketpet.game;

import android.content.Context;
import android.graphics.Color;

/** Cozy pastel palette shared by every mini-game. */
final class GameTheme {
    static final int CREAM     = Color.parseColor("#FFF8EA");
    static final int CREAM_2   = Color.parseColor("#FFF0D8");
    static final int PINK_2    = Color.parseColor("#FFE6EE");
    static final int TAN       = Color.parseColor("#E2C9AE");
    static final int OUTLINE   = Color.parseColor("#8B5E4B");
    static final int BROWN     = Color.parseColor("#6B4A3A");
    static final int SHADOW    = Color.parseColor("#E8D3BC");

    static final int USER      = Color.parseColor("#7CC9A5");   // mint
    static final int CPU       = Color.parseColor("#FF9EB5");   // pink
    static final int GOLD      = Color.parseColor("#FFC857");
    static final int SKY       = Color.parseColor("#BDE3F6");
    static final int GRASS     = Color.parseColor("#A8D8B0");
    static final int GRASS_DK  = Color.parseColor("#8CC79A");

    static final int TILE_BASE = Color.parseColor("#CDBDF2");   // lilac cushion
    static final int TILE_TOP  = Color.parseColor("#E8E0FB");
    static final int TILE_SHADE= Color.parseColor("#A99BDB");
    static final int OPEN_USER = Color.parseColor("#DFF4E8");
    static final int OPEN_CPU  = Color.parseColor("#FFE1E9");
    static final int OPEN_BOOM = Color.parseColor("#FFB8BC");
    static final int FIRE      = Color.parseColor("#FFA45C");

    private GameTheme() {}

    static int numberColor(int n) {
        switch (n) {
            case 1:  return Color.parseColor("#4F93C9");
            case 2:  return Color.parseColor("#4E9E74");
            case 3:  return Color.parseColor("#E0647E");
            case 4:  return Color.parseColor("#8B70CC");
            case 5:  return Color.parseColor("#D98B2B");
            default: return Color.parseColor("#C6694B");
        }
    }

    static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }
}
