package com.example.pocketpet.system;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.pocketpet.R;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.ui.InventoryActivity;
import com.example.pocketpet.ui.MainActivity;
import com.example.pocketpet.ui.ShopActivity;

import java.util.List;

/**
 * Every notification Mochi sends. They use the kawaii storybook layouts (bubble font, Mochi's face, stitched
 * pill buttons) with these channels:
 *   alerts     Mochi needs you (hungry, wants to play, sleepy)  - heads-up
 *   wake       Mochi woke up
 *   milestones level-ups, unlocks, daily summary
 *   session    the quiet "sleeping" notification with a progress bar (foreground service)
 * Tapping a card opens the right screen with a proper Back stack; the buttons inside the card
 * (Feed now, Play, Sleep...) go to ActionReceiver and work without opening the app.
 */
public final class NotificationHelper {
    private NotificationHelper() {}

    public static final String CH_ALERTS = "alerts", CH_WAKE = "wake", CH_MILESTONES = "milestones", CH_SESSION = "session";
    public static final int ID_SESSION = 1, ID_HUNGRY = 2, ID_PLAY = 3, ID_AWAKE = 4, ID_LEVEL = 5, ID_DAILY = 6, ID_TIRED = 7, ID_RESULT = 8, ID_SUMMARY = 100;
    private static final String GROUP = "mochi_group";

    public static final String ACTION_FEED = "com.example.pocketpet.action.FEED";
    public static final String ACTION_PLAY = "com.example.pocketpet.action.PLAY";
    public static final String ACTION_SLEEP = "com.example.pocketpet.action.SLEEP";
    public static final String EXTRA_NOTIF_ID = "notifId";

    // ------------------------------------------------------------------ channels + permission
    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel(CH_ALERTS, "Mochi needs you", "Hungry, wants to play or sleepy", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel(CH_WAKE, "Mochi wakes up", "Tells you when Mochi is awake", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(channel(CH_MILESTONES, "Level-ups and daily news", "Level-ups, unlocks and Mochi's daily summary", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(channel(CH_SESSION, "Sleeping", "Shows while Mochi sleeps", NotificationManager.IMPORTANCE_LOW));
    }

    private static NotificationChannel channel(String id, String name, String desc, int imp) {
        NotificationChannel ch = new NotificationChannel(id, name, imp);
        ch.setDescription(desc);
        return ch;
    }

    /** POST_NOTIFICATIONS (Android 13+) and the user's own notification switch. */
    public static boolean canPost(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    public static void cancel(Context c, int id) {
        NotificationManagerCompat.from(c).cancel(id);
    }

    // ------------------------------------------------------------------ the messages
    public static void awake(Context c, Pet p) {
        post(c, ID_AWAKE, CH_WAKE, "Mochi is awake!", "Hello! It was a nice sleep. Want to play with me?",
                R.drawable.ic_mochi_awake, "Say hi", openMain(c, 11), "Play", actionBroadcast(c, ACTION_PLAY, ID_AWAKE, 12), -1);
    }

    public static void wantsPlay(Context c, Pet p) {
        post(c, ID_PLAY, CH_ALERTS, "Mochi wants to play!", "Mood is down to " + Math.round(p.mood) + "%. Let's play together?",
                R.drawable.ic_mochi_sad, "Play now", actionBroadcast(c, ACTION_PLAY, ID_PLAY, 21), "Open", openMain(c, 22), -1);
    }

    public static void hungry(Context c, Pet p) {
        post(c, ID_HUNGRY, CH_ALERTS, "Mochi is hungry!", "Hunger is down to " + Math.round(p.hunger) + "%. A yummy snack please?",
                R.drawable.ic_mochi_hungry, "Feed now", actionBroadcast(c, ACTION_FEED, ID_HUNGRY, 31), "Shop", openScreen(c, ShopActivity.class, 32), -1);
    }

    public static void needsSleep(Context c, Pet p) {
        post(c, ID_TIRED, CH_ALERTS, "Mochi needs to sleep", "Energy is at " + Math.round(p.energy) + "%. Time for a nap!",
                R.drawable.ic_mochi_sleep, "Sleep now", actionBroadcast(c, ACTION_SLEEP, ID_TIRED, 41), "Open", openMain(c, 42), -1);
    }

    public static void levelUp(Context c, int level, List<String> unlocked) {
        String more = unlocked == null || unlocked.isEmpty() ? "Mochi grew a little bigger!" : "New in the shop: " + join(unlocked) + ".";
        post(c, ID_LEVEL, CH_MILESTONES, "Level " + level + "!", more,
                R.drawable.ic_mochi_level, "Open shop", openScreen(c, ShopActivity.class, 51), "Bag", openScreen(c, InventoryActivity.class, 52), -1);
    }

    public static void daily(Context c, Pet p) {
        String text = "Hunger " + Math.round(p.hunger) + "%  |  Energy " + Math.round(p.energy) + "%  |  Mood " + Math.round(p.mood)
                + "%  |  Clean " + Math.round(p.cleanliness) + "%.\nMochi missed you today. Come and say hi!";
        post(c, ID_DAILY, CH_MILESTONES, "Mochi's day", text,
                R.drawable.ic_mochi_happy, "Open", openMain(c, 61), null, null, -1);
    }

    /** One-line result after a notification button was pressed (replaces the card, so the buttons disappear). */
    public static void result(Context c, int replaceId, String title, String text, int icon) {
        post(c, replaceId, CH_WAKE, title, text, icon, null, null, null, null, -1);
    }

    /** The quiet notification of the sleep foreground service, with a live energy progress bar. */
    public static Notification sleeping(Context c, Pet p, int minutesLeft) {
        String text = minutesLeft <= 0 ? "Mochi is waking up..." : "Wakes up in about " + minutesLeft + " min";
        RemoteViews small = views(c, R.layout.notif_small, "Mochi is sleeping", text, R.drawable.ic_mochi_sleep);
        RemoteViews big = views(c, R.layout.notif_big, "Mochi is sleeping", text, R.drawable.ic_mochi_sleep);
        big.setViewVisibility(R.id.notif_progress, android.view.View.VISIBLE);
        big.setProgressBar(R.id.notif_progress, 100, Math.round(p.energy), false);
        big.setViewVisibility(R.id.notif_buttons, android.view.View.GONE);
        return new NotificationCompat.Builder(c, CH_SESSION)
                .setSmallIcon(R.drawable.ic_stat_mochi)
                .setContentTitle("Mochi is sleeping").setContentText(text)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small).setCustomBigContentView(big)
                .setContentIntent(openMain(c, 71))
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(100, Math.round(p.energy), false)
                .build();
    }

    // ------------------------------------------------------------------ building blocks
    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < 3; i++) { if (i > 0) sb.append(", "); sb.append(l.get(i)); }
        if (l.size() > 3) sb.append(" and more");
        return sb.toString();
    }

    private static RemoteViews views(Context c, int layout, String title, String text, int icon) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), layout);
        rv.setTextViewText(R.id.notif_title, title);
        rv.setTextViewText(R.id.notif_text, text);
        rv.setImageViewResource(R.id.notif_icon, icon);
        return rv;
    }

    private static void post(Context c, int id, String channel, String title, String text, int icon,
                             String label1, PendingIntent act1, String label2, PendingIntent act2, int unused) {
        if (!canPost(c)) return;
        ensureChannels(c);
        RemoteViews small = views(c, R.layout.notif_small, title, text, icon);
        RemoteViews big = views(c, R.layout.notif_big, title, text, icon);
        big.setViewVisibility(R.id.notif_progress, android.view.View.GONE);
        if (label1 == null) {
            big.setViewVisibility(R.id.notif_buttons, android.view.View.GONE);
        } else {
            big.setTextViewText(R.id.notif_btn1, label1);
            big.setOnClickPendingIntent(R.id.notif_btn1, act1);
            if (label2 == null) {
                big.setViewVisibility(R.id.notif_btn2, android.view.View.GONE);
            } else {
                big.setTextViewText(R.id.notif_btn2, label2);
                big.setOnClickPendingIntent(R.id.notif_btn2, act2);
            }
        }
        Notification n = new NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_stat_mochi)
                .setColor(0xFFF08FB0)
                .setContentTitle(title).setContentText(text)                 // plain text for accessibility / wearables
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small).setCustomBigContentView(big)
                .setContentIntent(openMain(c, id))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setCategory(CH_ALERTS.equals(channel) ? NotificationCompat.CATEGORY_REMINDER : NotificationCompat.CATEGORY_STATUS)
                .setPriority(CH_ALERTS.equals(channel) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .build();
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        try {
            nm.notify(id, n);
            Notification summary = new NotificationCompat.Builder(c, CH_MILESTONES)
                    .setSmallIcon(R.drawable.ic_stat_mochi)
                    .setContentTitle("Mochi").setContentText("Mochi has news for you")
                    .setStyle(new NotificationCompat.InboxStyle().setSummaryText("Mochi"))
                    .setGroup(GROUP).setGroupSummary(true).setAutoCancel(true).setOnlyAlertOnce(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .build();
            nm.notify(ID_SUMMARY, summary);
        } catch (SecurityException ignored) { }
    }

    /** Opens the main screen; Back leaves the app normally. */
    private static PendingIntent openMain(Context c, int req) {
        return openScreen(c, MainActivity.class, req);
    }

    /** Opens any screen with MainActivity underneath it in the back stack (TaskStackBuilder). */
    private static PendingIntent openScreen(Context c, Class<?> screen, int req) {
        Intent i = new Intent(c, screen).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        TaskStackBuilder tsb = TaskStackBuilder.create(c);
        tsb.addNextIntentWithParentStack(i);
        return tsb.getPendingIntent(req, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent actionBroadcast(Context c, String action, int notifId, int req) {
        Intent i = new Intent(c, ActionReceiver.class).setAction(action).putExtra(EXTRA_NOTIF_ID, notifId);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
