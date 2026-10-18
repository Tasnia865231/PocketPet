package com.example.pocketpet.system;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.example.pocketpet.ui.MainActivity;

public final class NotificationHelper {
    private static final String CH_ALERT = "alerts";
    private static final String CH_SESSION = "session";

    private NotificationHelper() {}

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CH_ALERT, "Pet alerts", NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(CH_SESSION, "Pet sessions", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private static PendingIntent open(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static Notification session(Context c, String text) {
        return new NotificationCompat.Builder(c, CH_SESSION)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("Pocket Pet")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(open(c))
                .build();
    }

    @SuppressLint("MissingPermission")
    public static void hungry(Context c, String name) {
        ensureChannels(c);
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        Notification n = new NotificationCompat.Builder(c, CH_ALERT)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle(name + " is hungry!")
                .setContentText("Come feed your pet.")
                .setAutoCancel(true)
                .setContentIntent(open(c))
                .build();
        NotificationManagerCompat.from(c).notify(2, n);
    }
}
