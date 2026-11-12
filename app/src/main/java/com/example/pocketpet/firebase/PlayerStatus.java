package com.example.pocketpet.firebase;

/** One row of the "who is online" list (read from Realtime Database: status/{uid}). */
public final class PlayerStatus {
    public final String uid;
    public final String displayName;
    public final boolean online;
    /** Milliseconds since 1970 (Firebase server clock) of the last time this player's state changed = "last seen" when offline. */
    public final long lastChanged;

    public PlayerStatus(String uid, String displayName, boolean online, long lastChanged) {
        this.uid = uid; this.displayName = displayName; this.online = online; this.lastChanged = lastChanged;
    }
}
