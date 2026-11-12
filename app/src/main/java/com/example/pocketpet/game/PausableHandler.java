package com.example.pocketpet.game;

import android.os.Handler;
import android.os.Looper;

/** A Handler whose delayed callbacks wait while the game is paused. */
final class PausableHandler {
    private final Handler h = new Handler(Looper.getMainLooper());
    boolean paused;

    void postDelayed(final Runnable r, long delayMs) {
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (paused) h.postDelayed(this, 120);
                else r.run();
            }
        }, delayMs);
    }

    void clear() { h.removeCallbacksAndMessages(null); }
}
