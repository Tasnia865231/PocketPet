package com.example.pocketpet.system;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.example.pocketpet.R;
import com.example.pocketpet.ui.MainActivity;

/** Home-screen widget: Mochi's face and his three bars, read through the content provider. Tap to open the game. */
public class PetWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(c));
    }

    public static void updateAll(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(c, PetWidget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews rv = build(c);
            for (int id : ids) mgr.updateAppWidget(id, rv);
        } catch (Exception ignored) { }
    }

    private static RemoteViews build(Context c) {
        PetClient.Snapshot s = PetClient.read(c);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_pet);
        int icon = s.sleeping ? R.drawable.ic_mochi_sleep
                : s.mood <= PetWatcher.MOOD_LOW ? R.drawable.ic_mochi_sad
                : s.hunger <= PetWatcher.HUNGER_LOW ? R.drawable.ic_mochi_hungry
                : R.drawable.ic_mochi_happy;
        rv.setImageViewResource(R.id.widgetIcon, icon);
        rv.setTextViewText(R.id.widgetTitle, s.name + (s.sleeping ? " is sleeping" : "  Lv." + s.level));
        rv.setProgressBar(R.id.widgetBarHunger, 100, Math.round(s.hunger), false);
        rv.setProgressBar(R.id.widgetBarEnergy, 100, Math.round(s.energy), false);
        rv.setProgressBar(R.id.widgetBarMood, 100, Math.round(s.mood), false);
        PendingIntent open = PendingIntent.getActivity(c, 90, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.widgetRoot, open);
        return rv;
    }
}
