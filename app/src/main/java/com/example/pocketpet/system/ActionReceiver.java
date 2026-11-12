package com.example.pocketpet.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.example.pocketpet.R;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

/**
 * The buttons inside Mochi's notifications (Feed now / Play / Sleep now). Works with the app closed:
 * it changes Mochi through the repository, which notifies the content provider, so the open screen
 * and the home-screen widget update by themselves. Not exported: only our own notifications can fire it.
 */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final String action = intent.getAction();
        final int notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIF_ID, 0);
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                PetRepository repo = new PetRepository(app);
                Pet p = repo.load();
                String title, text;
                int icon = R.drawable.ic_mochi_happy;
                if (p.isSleeping && !NotificationHelper.ACTION_SLEEP.equals(action)) {
                    title = "Shh, Mochi is sleeping"; text = "Try again when he wakes up."; icon = R.drawable.ic_mochi_sleep;
                } else if (NotificationHelper.ACTION_FEED.equals(action)) {
                    Item best = null;
                    for (Item it : repo.ownedItems()) if ("food".equals(it.type) && it.owned > 0 && (best == null || it.effect > best.effect)) best = it;
                    if (best == null) {
                        title = "No food in the bag"; text = "Open the Shop to buy a snack for Mochi."; icon = R.drawable.ic_mochi_hungry;
                    } else {
                        String r = repo.feedItem(best.id);
                        if (r.startsWith("OK:")) { title = "Yummy!"; text = "Mochi ate " + r.substring(3) + ". I want more!"; Haptics.pulse(app, 60); }
                        else { title = "Mochi is full"; text = "He is not hungry right now."; }
                    }
                } else if (NotificationHelper.ACTION_PLAY.equals(action)) {
                    if (p.energy <= 0f) { title = "Mochi needs to sleep"; text = "He is too tired to play."; icon = R.drawable.ic_mochi_sleep; }
                    else if (p.energy < 10f) { title = "Too tired to play"; text = "Let Mochi rest a little first."; icon = R.drawable.ic_mochi_sleep; }
                    else {
                        repo.edit(pp -> repo.addCoins(repo.play(pp), 2));
                        title = "Yay, that was fun!"; text = "Mochi is happier now. +2 coins"; Haptics.pulse(app, 60);
                    }
                } else if (NotificationHelper.ACTION_SLEEP.equals(action)) {
                    if (!p.isSleeping) {
                        repo.edit(pp -> repo.startSleep(pp));
                        ContextCompat.startForegroundService(app, new Intent(app, PetSessionService.class).putExtra("mode", "SLEEP"));
                    }
                    title = "Good night!"; text = "Mochi is sleeping. I will tell you when he wakes up."; icon = R.drawable.ic_mochi_sleep;
                } else {
                    return;
                }
                NotificationHelper.result(app, notifId == 0 ? NotificationHelper.ID_RESULT : notifId, title, text, icon);
                PetEvents.send(app, PetEvents.ACTION_PET_CHANGED);
                PetWidget.updateAll(app);
            } finally {
                pending.finish();
            }
        }).start();
    }
}
