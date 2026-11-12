package com.example.pocketpet.system;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.pocketpet.Prefs;

import java.util.Calendar;

/**
 * Three alarms, all only used to send notifications (Mochi's numbers themselves come from timestamps):
 *   daily     once a day at the hour chosen in Settings (HungerAlarmReceiver)
 *   care      every ~15 minutes: "is Mochi OK?" (CareCheckReceiver)
 *   wake      the exact moment a sleeping Mochi will be rested (WakeAlarmReceiver)
 */
public final class AlarmScheduler {
    private AlarmScheduler() {}

    private static PendingIntent pi(Context c, Class<?> receiver, int req) {
        return PendingIntent.getBroadcast(c, req, new Intent(c, receiver), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void schedule(Context c) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, new Prefs(c).getNotifHour());
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi(c, HungerAlarmReceiver.class, 0));
    }

    public static void scheduleCareChecks(Context c) {
        c.getSystemService(AlarmManager.class).setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pi(c, CareCheckReceiver.class, 1));
    }

    /** True when the user allowed exact alarms (Android 12+ asks for "Alarms & reminders"). */
    public static boolean canExact(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    public static void scheduleWake(Context c, long atMillis) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent p = pi(c, WakeAlarmReceiver.class, 2);
        try {
            if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p);
        }
    }

    public static void cancelWake(Context c) {
        c.getSystemService(AlarmManager.class).cancel(pi(c, WakeAlarmReceiver.class, 2));
    }
}
