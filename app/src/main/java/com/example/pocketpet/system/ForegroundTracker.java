package com.example.pocketpet.system;

/** True while the app is on screen: then Mochi's speech bubbles speak for him and we skip duplicate notifications. */
public final class ForegroundTracker {
    private ForegroundTracker() {}
    public static volatile boolean visible = false;
}
