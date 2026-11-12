package com.example.pocketpet.firebase;

/**
 * One community moment (read from Realtime Database: activities/{eventId}).
 * It stores only WHAT happened, never the player's name or profile: the name is looked up when the feed is drawn.
 */
public final class ActivityEvent {
    public static final String LEVEL = "level";        // value = the level reached
    public static final String COINS = "coins";        // OLD events only (lifetime coins milestone); no longer posted
    public static final String MINIGAME = "minigame";  // value = mini-games finished, coins = the player's coins afterwards

    public final String key, uid, type;
    public final long value, createdAt;
    /** The player's coin total after the event (mini-game events). -1 when the event has none. */
    public final long coins;

    public ActivityEvent(String key, String uid, String type, long value, long coins, long createdAt) {
        this.key = key; this.uid = uid; this.type = type; this.value = value; this.coins = coins; this.createdAt = createdAt;
    }
}
