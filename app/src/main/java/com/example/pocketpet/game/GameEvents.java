package com.example.pocketpet.game;

/** Callbacks a game view uses to talk to GameActivity. */
public interface GameEvents {
    int RESULT_WIN  = 1;
    int RESULT_LOSE = 2;
    int RESULT_DRAW = 3;

    /** Short status line, e.g. "Your turn". */
    void onStatus(String text);

    /**
     * Running progress number of the game (treats caught, metres climbed, cells opened, tries left...).
     * GameActivity turns it into the Score and Coins shown on screen (see GameRewards).
     */
    default void onScore(int raw) {}

    /** The game finished (after its end animation). */
    void onGameOver(int result);
}
