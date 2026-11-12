package com.example.pocketpet.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pocketpet.databinding.ActivityGameBinding;
import com.example.pocketpet.game.CatchView;
import com.example.pocketpet.game.GameBoard;
import com.example.pocketpet.game.GameEvents;
import com.example.pocketpet.game.GameIconView;
import com.example.pocketpet.game.GameRewards;
import com.example.pocketpet.game.HideSeekView;
import com.example.pocketpet.game.JumpView;
import com.example.pocketpet.game.MinesweeperView;
import com.example.pocketpet.game.SoundFx;
import com.example.pocketpet.game.TicTacToeView;

/**
 * Play hub: choose a game -> choose a difficulty (Medium or Hard) -> play -> result (Retry / Home).
 * Hide and Seek skips the difficulty card: it has 5 levels that unlock one after another when you win.
 * Every game shows its Score and the Coins it is earning while you play, and again on the result card.
 * Home on the result card goes back to the games menu. "Back to Mochi" leaves the hub and reports how
 * many games were finished so MainActivity can give the usual Play rewards (mood, XP, coins).
 */
public class GameActivity extends AppCompatActivity implements GameEvents {
    public static final String EXTRA_GAMES = "games";
    public static final String EXTRA_ENERGY = "energy";    // Mochi's energy when the game screen opened
    public static final String EXTRA_COINS = "coins";      // total coins earned in this visit (shown on screen, paid by MainActivity)

    private static final int TTT = 0, MINE = 1, JUMP = 2, HIDE = 3, CATCH = 4;
    private static final String[] GAME_NAMES = {"Tic Tac Toe", "Minesweeper", "Mochi Jump", "Hide and Seek", "Catch the Treats"};
    // difficulty levels used by the games: 1 = Medium, 2 = Hard (there is no Easy)
    private static final String[] DIFF_NAMES = {"", "Medium", "Hard"};
    private static final String[] CHIP_COLORS = {"", "#FFE08A", "#FFB3C4"};

    // Hide and Seek: 5 levels (1 easy, 2 = the old Medium, 3 and 4 in between, 5 = the old Hard)
    private static final int HIDE_LEVELS = 5;
    private static final String[] HIDE_NAMES = {"", "Level 1 - Easy", "Level 2 - Medium", "Level 3", "Level 4", "Level 5 - Hard"};
    private static final String[] HIDE_COLORS = {"", "#BFE8C0", "#FFE08A", "#FFCB9A", "#FFB3C4", "#E3A8F0"};
    private static final String[] HIDE_HINTS = {"",
            "Level 1 of 5: Mochi hides in a bush. Watch him, then tap where he is.",
            "Level 2 of 5: The bushes swap places. Keep your eyes on Mochi's spot!",
            "Level 3 of 5: Six spots now. Watch closely!",
            "Level 4 of 5: Same-looking spots and sometimes two swaps at once!",
            "Level 5 of 5: Fast double swaps. Last level, you can do it!"};

    private static final String[][] DIFF_DESC = {
            {"Blocks and wins, but slips up sometimes", "Plays perfectly. The best you can do is a draw"},
            {"8x8 board, 10 mines. It avoids sure mines", "9x9 board, 15 mines. The computer uses logic"},
            {"Reach 320 m. Some clouds crumble", "Reach 420 m. Moving and crumbling clouds"},
            {"4 spots, one swap at a time. 2 tries", "6 identical spots, fast double swaps. 2 tries"},
            {"Catch 25 treats. A bit faster", "Catch 35 treats. Fast, with lots of soot sprites"}};

    private static final String[] HINTS = {
            "You are X. The computer is O.",
            "Tap to open a cell. Long-press to flag. Open a mine and you lose.",
            "Drag your finger left and right to steer Mochi.",
            "Watch Mochi's spot carefully, then tap where he is.",
            "Drag your finger left and right. Avoid the soot sprites!"};

    private ActivityGameBinding b;
    private SoundFx sfx;
    private int game = TTT;
    private int difficulty = 1;
    private int gamesFinished = 0;
    private View gameView;
    private GameBoard board;
    private float energyLeft = 100f;     // each finished game costs 10 energy; at 0 Mochi has to sleep before more games
    private int rawScore = 0;          // progress number reported by the running game
    private int coinsEarned = 0;        // coins earned in finished games during this visit
    private int nextAction = 0;         // what the result card's first button does: 0 retry, 1 next hide level, 2 play again from level 1

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        energyLeft = getIntent().getFloatExtra(EXTRA_ENERGY, 100f);
        b = ActivityGameBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        sfx = new SoundFx(this);
        sfx.startMusic(SoundFx.MUSIC_MENU);
        updateSoundButtons();

        b.iconTtt.setType(GameIconView.TIC_TAC_TOE);
        b.iconMine.setType(GameIconView.MINESWEEPER);
        b.iconJump.setType(GameIconView.JUMP);
        b.iconHide.setType(GameIconView.HIDE_SEEK);
        b.iconCatch.setType(GameIconView.CATCH);

        b.cardTtt.setOnClickListener(v -> chooseGame(TTT));
        b.cardMine.setOnClickListener(v -> chooseGame(MINE));
        b.cardJump.setOnClickListener(v -> chooseGame(JUMP));
        b.cardHide.setOnClickListener(v -> chooseGame(HIDE));
        b.cardCatch.setOnClickListener(v -> chooseGame(CATCH));
        b.btnMenuBack.setOnClickListener(v -> { tap(); finishWithResult(); });

        b.cardMedium.setOnClickListener(v -> startGame(1));
        b.cardHard.setOnClickListener(v -> startGame(2));
        b.btnDiffBack.setOnClickListener(v -> { tap(); showPanel(b.panelMenu); });

        // in-game "Menu" opens the pause card
        b.btnGameMenu.setOnClickListener(v -> { tap(); showPause(); });
        b.btnResume.setOnClickListener(v -> { tap(); resumeGame(); });
        b.btnPauseRetry.setOnClickListener(v -> {
            tap();
            b.panelPause.setVisibility(View.GONE);
            launchGame();
        });
        b.btnPauseMenu.setOnClickListener(v -> {
            tap();
            b.panelPause.setVisibility(View.GONE);
            leaveGame();
        });

        // result card
        b.btnRetry.setOnClickListener(v -> {
            tap();
            b.panelResult.setVisibility(View.GONE);
            b.confetti.stop();
            if (nextAction == 1) difficulty = Math.min(HIDE_LEVELS, difficulty + 1);   // won: next Hide and Seek level
            else if (nextAction == 2) difficulty = 1;                                    // beat level 5: play again from level 1
            launchGame();
        });
        b.btnHome.setOnClickListener(v -> {
            tap();
            b.panelResult.setVisibility(View.GONE);
            b.confetti.stop();
            leaveGame();                      // back to the games menu
        });

        View.OnClickListener toggleSound = v -> {
            sfx.setEnabled(!sfx.isEnabled());
            updateSoundButtons();
            sfx.play(SoundFx.TAP);
        };
        b.btnSound.setOnClickListener(toggleSound);
        b.btnSoundMenu.setOnClickListener(toggleSound);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (b.panelResult.getVisibility() == View.VISIBLE) {
                    b.panelResult.setVisibility(View.GONE);
                    b.confetti.stop();
                    leaveGame();
                } else if (b.panelPause.getVisibility() == View.VISIBLE) resumeGame();
                else if (b.panelGame.getVisibility() == View.VISIBLE) showPause();
                else if (b.panelDifficulty.getVisibility() == View.VISIBLE) showPanel(b.panelMenu);
                else finishWithResult();
            }
        });
    }

    @Override protected void onPause() {
        super.onPause();
        sfx.pauseMusic();
        // leaving the app mid-game pauses the game
        if (gameView != null && b.panelGame.getVisibility() == View.VISIBLE
                && b.panelResult.getVisibility() != View.VISIBLE) showPause();
    }

    @Override protected void onResume() {
        super.onResume();
        sfx.resumeMusic();
    }

    @Override protected void onDestroy() {
        sfx.release();
        super.onDestroy();
    }

    private void tap() { sfx.play(SoundFx.TAP); }

    private void updateSoundButtons() {
        boolean on = sfx.isEnabled();
        b.btnSound.setText(on ? "\uD83D\uDD0A" : "\uD83D\uDD07");
        b.btnSoundMenu.setText(on ? "Sound: On" : "Sound: Off");
    }

    // ------------------------------------------------------------------ navigation

    private void chooseGame(int g) {
        tap();
        game = g;
        if (g == HIDE) {                       // Hide and Seek: straight into level 1 (no Medium / Hard choice)
            difficulty = 1;
            launchGame();
            return;
        }
        b.tvDiffGame.setText(GAME_NAMES[g]);
        b.tvMediumDesc.setText(DIFF_DESC[g][0]);
        b.tvHardDesc.setText(DIFF_DESC[g][1]);
        showPanel(b.panelDifficulty);
    }

    private void startGame(int level) {
        tap();
        difficulty = level;
        launchGame();
    }

    /** (Re)build the board for the current game + difficulty and show it. */
    private void launchGame() {
        if (energyLeft <= 0f) {                          // Mochi is out of energy: no more games
            KawaiiToast.show(this, "Mochi needs to sleep");
            return;
        }
        if (gameView != null) b.gameHolder.removeView(gameView);

        switch (game) {
            case TTT:   board = new TicTacToeView(this); break;
            case MINE:  board = new MinesweeperView(this); break;
            case JUMP:  board = new JumpView(this); break;
            case HIDE:  board = new HideSeekView(this); break;
            default:    board = new CatchView(this); break;
        }
        board.setEvents(this);
        board.setSound(sfx);
        board.setDifficulty(difficulty);
        gameView = (View) board;
        b.gameHolder.addView(gameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        b.tvGameTitle.setText(GAME_NAMES[game]);
        b.tvStatus.setText("");
        if (game == HIDE) {
            b.tvHint.setText(HIDE_HINTS[difficulty]);
            b.tvDiffChip.setText(HIDE_NAMES[difficulty]);
            b.tvDiffChip.getBackground().mutate().setTint(Color.parseColor(HIDE_COLORS[difficulty]));
        } else {
            b.tvHint.setText(HINTS[game]);
            b.tvDiffChip.setText(DIFF_NAMES[difficulty]);
            b.tvDiffChip.getBackground().mutate().setTint(Color.parseColor(CHIP_COLORS[difficulty]));
        }
        rawScore = 0;
        updateLive();

        sfx.startMusic(SoundFx.MUSIC_TTT + game);
        showPanel(b.panelGame);
        // start once the view has been laid out
        final GameBoard gb = board;
        gameView.post(gb::restart);
    }

    /** Close the current game and go back to the list of games. */
    private void leaveGame() {
        if (gameView != null) { b.gameHolder.removeView(gameView); gameView = null; board = null; }
        sfx.startMusic(SoundFx.MUSIC_MENU);
        showPanel(b.panelMenu);
    }

    private void showPause() {
        if (board == null || b.panelPause.getVisibility() == View.VISIBLE
                || b.panelResult.getVisibility() == View.VISIBLE) return;
        board.setPaused(true);
        b.panelPause.setAlpha(0f);
        b.panelPause.setVisibility(View.VISIBLE);
        b.panelPause.animate().alpha(1f).setDuration(160).start();
    }

    private void resumeGame() {
        b.panelPause.setVisibility(View.GONE);
        if (board != null) board.setPaused(false);
    }

    private void showPanel(View target) {
        View[] all = {b.panelMenu, b.panelDifficulty, b.panelGame};
        for (View v : all) if (v != target) v.setVisibility(View.GONE);
        target.setVisibility(View.VISIBLE);
        target.setAlpha(0f);
        target.setTranslationY(24f * getResources().getDisplayMetrics().density);
        target.animate().alpha(1f).translationY(0f).setDuration(220).start();
    }

    /** Score and coins shown at the top of the game while playing. */
    private void updateLive() {
        b.tvScore.setText("Score: " + GameRewards.liveScore(game, difficulty, rawScore));
        b.tvCoins.setText("Coins: +" + GameRewards.liveCoins(game, difficulty, rawScore));
    }

    private void finishWithResult() {
        if (gamesFinished > 0) {
            Intent data = new Intent();
            data.putExtra(EXTRA_GAMES, gamesFinished);
            data.putExtra(EXTRA_COINS, coinsEarned);
            setResult(RESULT_OK, data);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    // ------------------------------------------------------------------ GameEvents

    @Override public void onStatus(String text) {
        b.tvStatus.setText(text);
    }

    @Override public void onScore(int raw) {
        rawScore = raw;
        updateLive();
    }

    @Override public void onGameOver(int result) {
        gamesFinished++;
        energyLeft = Math.max(0f, energyLeft - 10f);
        int score = GameRewards.finalScore(game, difficulty, result, rawScore);
        int coins = GameRewards.finalCoins(game, difficulty, result, rawScore);
        coinsEarned += coins;
        b.tvScore.setText("Score: " + score);
        b.tvCoins.setText("Coins: +" + coins);
        b.tvResultScore.setText(String.valueOf(score));
        b.tvResultCoins.setText("+" + coins);
        nextAction = 0;
        b.btnRetry.setText("Retry");
        if (result == RESULT_WIN) {
            sfx.play(SoundFx.WIN);
            b.tvResultEmoji.setText("\uD83C\uDF89");
            b.tvResultTitle.setText("Congratulations!");
            b.tvResultMsg.setText("You won! Mochi is so proud of you!");
            if (game == HIDE && difficulty < HIDE_LEVELS) {
                b.tvResultTitle.setText("Level " + difficulty + " complete!");
                b.tvResultMsg.setText("Great eyes! Level " + (difficulty + 1) + " is unlocked.");
                b.btnRetry.setText("Next Level");
                nextAction = 1;
            } else if (game == HIDE) {
                b.tvResultTitle.setText("All 5 levels done!");
                b.tvResultMsg.setText("You are the Hide and Seek champion! Mochi can't hide from you.");
                b.btnRetry.setText("Play Again");
                nextAction = 2;
            }
        } else if (result == RESULT_LOSE) {
            sfx.play(SoundFx.LOSE);
            b.tvResultEmoji.setText("\uD83D\uDE22");
            b.tvResultTitle.setText("Sorry!");
            b.tvResultMsg.setText("You lost this one. Want to try again?");
            if (game == HIDE) b.tvResultMsg.setText("Not this time. Try level " + difficulty + " again?");
        } else {
            sfx.play(SoundFx.DRAW);
            b.tvResultEmoji.setText("\uD83E\uDD1D");
            b.tvResultTitle.setText("It's a draw!");
            b.tvResultMsg.setText("Nobody won. Nicely played! Try again?");
        }
        b.panelResult.setAlpha(0f);
        b.panelResult.setVisibility(View.VISIBLE);
        b.panelResult.animate().alpha(1f).setDuration(200).start();
        b.resultCard.setScaleX(0.8f);
        b.resultCard.setScaleY(0.8f);
        b.resultCard.animate().scaleX(1f).scaleY(1f).setDuration(260).start();
        if (result == RESULT_WIN) b.confetti.post(() -> b.confetti.start());
        else b.confetti.stop();
    }
}
