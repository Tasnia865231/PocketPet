package com.example.pocketpet.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Registered IN CODE from MainActivity (ACTION_POWER_CONNECTED can't be declared in the manifest). */
public class PowerReceiver extends BroadcastReceiver {
    private final Runnable onPower;

    public PowerReceiver(Runnable onPower) {
        this.onPower = onPower;
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_POWER_CONNECTED.equals(i.getAction())) onPower.run();
    }
}
