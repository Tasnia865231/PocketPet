package com.example.pocketpet.game;

/** Every mini-game view implements this so GameActivity can treat them all the same way. */
public interface GameBoard {
    void setEvents(GameEvents events);
    /** 1 = medium, 2 = hard. */
    void setDifficulty(int level);
    /** Start (or restart) a round. */
    void restart();
    /** Sound effects (may be null). */
    void setSound(SoundFx sfx);
    /** While paused the game stands completely still (timers, animation and the computer's turn). */
    void setPaused(boolean paused);
}
