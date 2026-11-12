package com.example.pocketpet.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.pocketpet.Bg;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.R;
import com.example.pocketpet.system.AlarmScheduler;
import com.example.pocketpet.system.LocationHelper;
import com.example.pocketpet.system.NotificationHelper;

/**
 * Asks for the runtime permissions one at a time, each with a friendly explanation first (KawaiiDialog), and only once:
 *   1. POST_NOTIFICATIONS   so Mochi can tell you when he is awake, hungry or wants to play
 *   2. ACCESS_COARSE_LOCATION so the weather in Mochi's window is the weather where you are
 *   3. exact alarms         so "Mochi is awake" arrives on time
 * Every answer has a graceful fallback: the game works without any of them.
 * Create it in onCreate (the launchers must be registered before the activity starts), call start() from onResume.
 */
final class PermissionFlow {
    private final AppCompatActivity a;
    private final Prefs prefs;
    private final ActivityResultLauncher<String> notifLauncher;
    private final ActivityResultLauncher<String> locLauncher;
    private boolean running = false;

    PermissionFlow(AppCompatActivity activity) {
        a = activity;
        prefs = new Prefs(activity);
        notifLauncher = a.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) NotificationHelper.ensureChannels(a);
            else KawaiiToast.show(a, "No problem! Mochi will talk to you inside the app.");
            askLocation();
        });
        locLauncher = a.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                Bg.IO.execute(() -> LocationHelper.refresh(a));
                KawaiiToast.show(a, "Now Mochi's window shows your local weather!");
            } else {
                KawaiiToast.show(a, "Okay, using the place saved in Settings.");
            }
            askExactAlarm();
        });
    }

    void start() {
        if (running) return;
        running = true;
        askNotifications();
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS) || prefs.getFlag("asked_notif")) { askLocation(); return; }
        prefs.setFlag("asked_notif", true);
        KawaiiDialog.show(a, R.drawable.ic_mochi_awake, "Let Mochi call you?",
                "Mochi would like to tell you when he wakes up, when he is hungry and when he wants to play with you.",
                "Yes please", () -> notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS),
                "Not now", this::askLocation);
    }

    private void askLocation() {
        if (LocationHelper.hasPermission(a) || prefs.getFlag("asked_loc")) { askExactAlarm(); return; }
        prefs.setFlag("asked_loc", true);
        KawaiiDialog.show(a, R.drawable.ic_mochi_happy, "Show your weather?",
                "Mochi's windows show the weather outside. Allow your approximate location and they will match the sky above you.",
                "Allow", () -> locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION),
                "Not now", this::askExactAlarm);
    }

    private void askExactAlarm() {
        if (AlarmScheduler.canExact(a) || prefs.getFlag("asked_exact")) { finish(); return; }
        prefs.setFlag("asked_exact", true);
        KawaiiDialog.show(a, R.drawable.ic_mochi_sleep, "On-time wake-ups",
                "Allow alarms and reminders so Mochi can tell you the very minute he wakes up.",
                "Open settings", () -> {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + a.getPackageName())));
                    } catch (Exception ignored) { }
                    finish();
                },
                "Not now", this::finish);
    }

    private void finish() {
        running = false;
        AlarmScheduler.schedule(a);
        AlarmScheduler.scheduleCareChecks(a);
    }

    private boolean has(String perm) {
        return ContextCompat.checkSelfPermission(a, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
