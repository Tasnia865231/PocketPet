package com.example.pocketpet.game;

/**
 * One place that decides the Score and the Coins a game gives. The "raw" number comes from the game itself via
 * GameEvents.onScore(): treats caught (Catch), metres climbed (Jump), cells opened (Minesweeper) or tries left
 * (Hide and Seek, reported when the level is won). Tic Tac Toe only has a result.
 */
public final class GameRewards {
    private GameRewards() {}

    public static final int TTT = 0, MINE = 1, JUMP = 2, HIDE = 3, CATCH = 4;
    private static final int[] HIDE_WIN_COINS = {6, 9, 13, 18, 25};

    private static boolean hard(int game, int level) { return game != HIDE && level >= 2; }

    /** Score shown while playing. */
    public static int liveScore(int game, int level, int raw) {
        switch (game) {
            case MINE:  return raw * 5;
            case JUMP:  return raw * 2;
            case CATCH: return raw * 10;
            default:    return 0;
        }
    }

    /** Coins shown while playing (the win bonus is only added when the game ends). */
    public static int liveCoins(int game, int level, int raw) {
        switch (game) {
            case MINE:  return raw / 6;
            case JUMP:  return raw / 40;
            case CATCH: return raw / 4;
            default:    return 0;
        }
    }

    /** Final score when the game is over, whether the player won, lost or drew. */
    public static int finalScore(int game, int level, int result, int raw) {
        boolean win = result == GameEvents.RESULT_WIN;
        boolean hard = hard(game, level);
        switch (game) {
            case TTT:
                return win ? (hard ? 150 : 100) : result == GameEvents.RESULT_DRAW ? 40 : 10;
            case HIDE:
                return win ? level * 100 + raw * 50 : level * 20;
            case MINE:  return liveScore(game, level, raw) + (win ? (hard ? 200 : 150) : 0);
            case JUMP:  return liveScore(game, level, raw) + (win ? (hard ? 300 : 200) : 0);
            default:    return liveScore(game, level, raw) + (win ? (hard ? 200 : 150) : 0);
        }
    }

    /** Coins earned when the game is over. */
    public static int finalCoins(int game, int level, int result, int raw) {
        boolean win = result == GameEvents.RESULT_WIN;
        boolean hard = hard(game, level);
        switch (game) {
            case TTT:
                return win ? (hard ? 12 : 8) : result == GameEvents.RESULT_DRAW ? (hard ? 4 : 3) : 1;
            case HIDE:
                return win ? HIDE_WIN_COINS[Math.max(0, Math.min(4, level - 1))] : 1;
            case MINE:  return liveCoins(game, level, raw) + (win ? (hard ? 12 : 8) : 0);
            case JUMP:  return liveCoins(game, level, raw) + (win ? (hard ? 9 : 6) : 0);
            default:    return liveCoins(game, level, raw) + (win ? (hard ? 9 : 6) : 0);
        }
    }
}
