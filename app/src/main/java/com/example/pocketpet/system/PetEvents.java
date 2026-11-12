package com.example.pocketpet.system;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * The app's own broadcasts. Services, receivers and the UI tell each other what happened to Mochi:
 *   PET_WOKE      Mochi finished sleeping
 *   LEVEL_UP      Mochi reached a new level (EXTRA_LEVEL)
 *   PET_CRITICAL  a need is very low (EXTRA_KIND = mood | hunger | energy)
 *   PET_CHANGED   something changed Mochi in the background (notification button, widget, alarm)
 * They are sent to this app only (setPackage) and are protected from other apps.
 */
public final class PetEvents {
    private PetEvents() {}

    public static final String ACTION_PET_WOKE     = "com.example.pocketpet.action.PET_WOKE";
    public static final String ACTION_LEVEL_UP     = "com.example.pocketpet.action.LEVEL_UP";
    public static final String ACTION_PET_CRITICAL = "com.example.pocketpet.action.PET_CRITICAL";
    public static final String ACTION_PET_CHANGED  = "com.example.pocketpet.action.PET_CHANGED";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_KIND  = "kind";

    public static void send(Context c, String action, Bundle extras) {
        Intent i = new Intent(action).setPackage(c.getPackageName());
        if (extras != null) i.putExtras(extras);
        c.sendBroadcast(i);
    }

    public static void send(Context c, String action) { send(c, action, null); }
}
