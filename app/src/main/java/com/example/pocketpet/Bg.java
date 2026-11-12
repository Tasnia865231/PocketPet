package com.example.pocketpet;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared background executor (database + network) and main-thread handler. */
public final class Bg {
    public static final ExecutorService IO = Executors.newFixedThreadPool(2);
    public static final Handler UI = new Handler(Looper.getMainLooper());
    private Bg() {}
}
