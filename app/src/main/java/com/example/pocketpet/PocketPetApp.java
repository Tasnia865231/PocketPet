package com.example.pocketpet;

import android.app.Application;

import com.example.pocketpet.firebase.MatchManager;
import com.example.pocketpet.firebase.PresenceManager;
import com.example.pocketpet.system.AirplaneWatcher;

/** Runs once when the app process starts. Only job so far: let PresenceManager follow the app coming and going. */
public class PocketPetApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PresenceManager.install(this);
        MatchManager.install(this);          // online Tic-Tac-Toe: invitations + match
        AirplaneWatcher.install(this);       // airplane mode switched on: a kawaii banner on any screen
    }
}
