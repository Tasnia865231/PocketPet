package com.example.pocketpet.firebase;

import com.google.firebase.database.FirebaseDatabase;

/**
 * Firebase settings that are written in code (the rest lives in app/google-services.json).
 *
 * DATABASE_URL = the web address of YOUR Realtime Database. It must be written here because the database is in
 * Singapore, not the default US location. Find it in: Firebase Console > Realtime Database > Data tab (top of the page).
 * It is an address, not a password: it is safe to keep in the project (the security rules protect the data).
 */
public final class FirebaseConfig {
    private FirebaseConfig() {}

    public static final String DATABASE_URL = "https://mochi-pet-game-default-rtdb.asia-southeast1.firebasedatabase.app";

    /** The one place the app gets its Realtime Database from. */
    public static FirebaseDatabase rtdb() { return FirebaseDatabase.getInstance(DATABASE_URL); }
}
