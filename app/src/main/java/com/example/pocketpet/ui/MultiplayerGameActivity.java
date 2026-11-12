package com.example.pocketpet.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pocketpet.Bg;
import com.example.pocketpet.R;
import com.example.pocketpet.databinding.ActivityMultiplayerBinding;
import com.example.pocketpet.firebase.ChatManager;
import com.example.pocketpet.firebase.FirebaseAuthManager;
import com.example.pocketpet.firebase.FirebaseConfig;
import com.example.pocketpet.firebase.MatchManager;
import com.example.pocketpet.firebase.MatchState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import com.example.pocketpet.view.BubbleFont;

import java.util.Locale;
import java.util.Random;

/**
 * The online Tic-Tac-Toe screen. It is only a WINDOW onto the match in the Realtime Database (games/{id}):
 * a tap sends a move, and everything you see (board, turn, round, score, result) is drawn from what the database
 * says. That is why both phones always show exactly the same game, and why reopening the app restores the match.
 *
 * Three rounds are always played. Rounds are shown for a moment, then the next one starts by itself.
 * The winner of the match collects 50 coins through MatchManager.claimReward (once).
 */
public class MultiplayerGameActivity extends AppCompatActivity {
    public static final String EXTRA_GID = "gid";
    private static final long AWAY_LIMIT_MS = 120_000;      // a friend who is away longer than this ends the match (no winner)
    private static final long NEXT_ROUND_MS = 2800;

    private ActivityMultiplayerBinding b;
    private String gid, me, oppUid;
    private MatchState state;
    private boolean finalShown = false, finalScheduled = false, cancelledShown = false, leaving = false;
    private boolean connected = true, oppOnline = true;
    private long oppAwaySince = 0;
    private int rewardBalance = -1;            // >= 0 once the 50 coins were added
    private boolean rewardAlready = false;
    private boolean rewardPlayed = false;
    private boolean claimStarted = false;

    // chat (all of it lives in ChatManager + ChatBubbleLayer; the match logic above never touches it)
    private ChatManager chat;
    private boolean chatOpen = false, cancelRequested = false;
    private float dp;
    private int baseBottomPad, maxRootH = 0;
    private boolean typing = false, wasOnline = true, netUp = true;
    private ConnectivityManager cm;
    private boolean connConfirmed = false;       // Firebase has told us at least once that it is connected
    private ConnectivityManager.NetworkCallback netCb;

    private DatabaseReference connRef, oppRef;
    private ValueEventListener connListener, oppListener;
    private Runnable advance;
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateHint(); Bg.UI.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMultiplayerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        gid = getIntent().getStringExtra(EXTRA_GID);
        me = FirebaseAuthManager.uid();
        if (gid == null || me == null) { finish(); return; }
        MatchManager.setScreen(gid);
        setupChat();

        b.board.setOnCellTap(i -> { if (state != null && connected) MatchManager.playMove(state, i); });
        b.btnLeave.setOnClickListener(v -> askLeave());
        b.btnDone.setOnClickListener(v -> leaveForGood());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (chatOpen) closeChat(); else askLeave(); }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (gid == null) return;
        MatchManager.watchGame(gid, this::onState);      // live: every move of either player arrives here
        connRef = FirebaseConfig.rtdb().getReference(".info/connected");
        connListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                connected = Boolean.TRUE.equals(s.getValue(Boolean.class));
                if (connected) connConfirmed = true;
                refreshChatOnline();
                if (state != null) render(state, state);
                updateHint();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        connRef.addValueEventListener(connListener);
        attachOpponent();
        Bg.UI.post(tick);
        startChat();
    }

    @Override
    protected void onStop() {
        stopChat();
        MatchManager.stopWatchingGame();
        if (connRef != null && connListener != null) connRef.removeEventListener(connListener);
        if (oppRef != null && oppListener != null) oppRef.removeEventListener(oppListener);
        connRef = null; connListener = null; oppRef = null; oppListener = null;
        Bg.UI.removeCallbacks(tick);
        if (advance != null) Bg.UI.removeCallbacks(advance);
        super.onStop();
    }


    // ------------------------------------------------------------------ chat

    private void setupChat() {
        dp = getResources().getDisplayMetrics().density;
        baseBottomPad = b.mainColumn.getPaddingBottom();
        // the emoji chips are written in the layout; a tap sends that emoji
        for (int i = 0; i < b.chatEmojiList.getChildCount(); i++) {
            View chip = b.chatEmojiList.getChildAt(i);
            final String e = ((TextView) chip).getText().toString();
            chip.setOnClickListener(v -> sendChat(e));
        }
        b.btnChat.setOnClickListener(v -> { if (chatOpen) closeChat(); else openChat(); });
        b.btnChatSend.setOnClickListener(v -> sendTyped());
        b.etChat.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendTyped(); return true; }
            return false;
        });
        // keep the board above the input bar, whatever size the bar has (emoji row shown or hidden, keyboard open or closed)
        b.chatBar.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> applyChatPadding());
        // keyboard open (the screen got much shorter): "typing mode" makes room so the board stays big enough
        b.getRoot().addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int h = b.getRoot().getHeight();
            if (h > maxRootH) maxRootH = h;
            boolean kb = maxRootH - h > 140 * dp;
            if (kb != typing) { typing = kb; b.getRoot().post(() -> setTypingMode(kb)); }
        });
        updateChatEnabled();
    }

    /** While the keyboard is open, hide the turn text, the hint and the big X / O letters so the board keeps its size. */
    private void setTypingMode(boolean on) {
        typing = on;
        int vis = on ? View.GONE : View.VISIBLE;
        b.tvTurn.setVisibility(vis);
        b.tvHint.setVisibility(vis);
        b.tvXLetter.setVisibility(vis);
        b.tvOLetter.setVisibility(vis);
        b.mainColumn.requestLayout();
    }

    private boolean hasNetwork() {
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** Online = the phone has a network AND Firebase says it is connected. */
    private boolean isOnline() { return netUp && connected; }

    /** Call whenever the connection may have changed: updates the bar and tells the player once when it drops. */
    private boolean airplaneOn() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void toastOffline() {
        if (airplaneOn()) return;                       // AirplaneWatcher already shows its own banner for that
        KawaiiToast.show(this, "No internet. Chat is paused.");
    }

    private void refreshChatOnline() {
        boolean online = isOnline();
        if (wasOnline && !online && !isFinishing() && (connConfirmed || !netUp)) toastOffline();
        wasOnline = online;
        updateChatEnabled();
    }

    private void startChat() {
        stopChat();
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        netUp = hasNetwork();
        wasOnline = isOnline();
        netCb = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull Network n) { Bg.UI.post(() -> { netUp = hasNetwork(); refreshChatOnline(); }); }
            @Override public void onLost(@NonNull Network n) { Bg.UI.postDelayed(() -> { netUp = hasNetwork(); refreshChatOnline(); }, 400); }
        };
        try { if (cm != null) cm.registerDefaultNetworkCallback(netCb); } catch (Exception ignored) { netCb = null; }
        chat = new ChatManager(gid, me, this::onChatMessage);
        chat.start();                                   // only messages from now on
        updateChatEnabled();
    }

    private void stopChat() {
        if (cm != null && netCb != null) { try { cm.unregisterNetworkCallback(netCb); } catch (Exception ignored) { } }
        netCb = null;
        if (chat != null) { chat.stop(); chat = null; }
        b.bubbleLayer.clear();
        hideKeyboard();
    }

    /** A message arrived (mine too): float a bubble from the sender's card. */
    private void onChatMessage(String uid, String text) {
        MatchState s = state;
        if (s == null || isFinishing()) return;
        boolean isX = uid.equals(s.x);
        if (!isX && !uid.equals(s.o)) return;
        b.bubbleLayer.show(isX ? b.cardX : b.cardO, isX, s.nameOf(isX ? "X" : "O"), text);
    }

    private void sendTyped() {
        if (sendChat(b.etChat.getText().toString())) b.etChat.setText("");
    }

    /** Returns true if the message was sent. */
    private boolean sendChat(String raw) {
        if (chat == null || state == null) return false;
        netUp = hasNetwork();                           // airplane mode is noticed right away
        ChatManager.Result r = chat.send(raw, isOnline(), () -> KawaiiToast.show(this, "Could not send"));
        switch (r) {
            case SENT: return true;
            case COOLDOWN: KawaiiToast.show(this, "Not so fast, little one!"); return false;
            case OFFLINE: KawaiiToast.show(this, airplaneOn() ? "Airplane mode is on. Your message was not sent." : "No internet. Your message was not sent."); updateChatEnabled(); return false;
            default: return false;                      // EMPTY: nothing to send
        }
    }

    /** No connection: sending is disabled and "Reconnecting..." is shown. Nothing is queued. */
    private void updateChatEnabled() {
        boolean on = isOnline();
        b.btnChatSend.setAlpha(on ? 1f : 0.5f);
        b.chatEmojiScroll.setAlpha(on ? 1f : 0.5f);
        b.tvChatHint.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private void openChat() {
        chatOpen = true;
        b.chatBar.animate().cancel();
        b.chatBar.setVisibility(View.VISIBLE);
        b.chatBar.setAlpha(0f);
        b.chatBar.setTranslationY(40 * dp);
        b.chatBar.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    private void closeChat() {
        chatOpen = false;
        hideKeyboard();
        applyChatPadding();
        b.chatBar.animate().cancel();
        b.chatBar.animate().alpha(0f).translationY(40 * dp).setDuration(160)
                .withEndAction(() -> { if (!chatOpen) b.chatBar.setVisibility(View.GONE); }).start();
    }

    /** The board column leaves room for the bar while it is open, so the bar never covers the board. */
    private void applyChatPadding() {
        int extra = (chatOpen && b.chatBar.getVisibility() == View.VISIBLE) ? b.chatBar.getHeight() : 0;
        int want = baseBottomPad + extra;
        if (b.mainColumn.getPaddingBottom() != want) {
            b.mainColumn.post(() -> {                   // after the current layout pass, so the board really re-measures
                if (b.mainColumn.getPaddingBottom() != want) b.mainColumn.setPadding(b.mainColumn.getPaddingLeft(),
                        b.mainColumn.getPaddingTop(), b.mainColumn.getPaddingRight(), want);
            });
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.etChat.getWindowToken(), 0);
        b.etChat.clearFocus();
    }

    // ------------------------------------------------------------------ leaving

    private void askLeave() {
        if (finalShown || state == null || state.isOver()) { leaveForGood(); return; }
        KawaiiDialog.show(this, 0, "Leave the match?",
                "If you leave, the match ends and nobody gets coins.",
                "STAY", null,
                "LEAVE", () -> { if (state != null) { MatchManager.cancelMatch(state); cancelRequested = true; } leaveForGood(); });
    }

    private void leaveForGood() {
        if (leaving) return;
        leaving = true;
        hideKeyboard();
        // the match is over (or I just ended it): remove its chat. The other player's final screen is not disturbed.
        if (gid != null && (state == null || state.isOver() || cancelRequested)) ChatManager.deleteChat(gid);
        MatchManager.leaveScreen();
        finish();
    }

    // ------------------------------------------------------------------ new state from the database

    private void onState(@Nullable MatchState s) {
        if (isFinishing() || leaving) return;
        if (s == null || s.symbolOf(me) == null) {
            KawaiiToast.show(this, "Could not open this match");
            leaveForGood();
            return;
        }
        MatchState prev = state;
        state = s;
        if (oppUid == null) { oppUid = s.opponentUid(me); attachOpponent(); }
        render(s, prev);
    }

    private void render(MatchState s, @Nullable MatchState prev) {
        String mySym = s.symbolOf(me);
        b.tvXName.setText(s.xName + ("X".equals(mySym) ? " (you)" : ""));
        b.tvOName.setText(s.oName + ("O".equals(mySym) ? " (you)" : ""));
        b.tvXScore.setText(String.valueOf(s.sx));
        b.tvOScore.setText(String.valueOf(s.so));
        b.tvRound.setText("finished".equals(s.phase) ? "Final" : "Round " + s.round + "/3");
        b.board.setCells(s.cells);
        boolean myTurn = "playing".equals(s.phase) && mySym.equals(s.turn) && connected;
        b.board.setMyTurn(myTurn);

        boolean realChange = prev != null && prev != s && (!s.phase.equals(prev.phase) || s.round != prev.round);

        switch (s.phase) {
            case "playing":
                b.tvTurn.setText(myTurn ? "Your turn! Place your " + mySym
                        : "Waiting for " + s.nameOf(s.turn) + "...");
                if (realChange) hideBanner();
                break;
            case "roundEnd":
                b.tvTurn.setText("Round " + s.round + " complete!");
                if (prev == null || realChange) { showRoundBanner(s, mySym); bumpScore(s); }
                scheduleAdvance(s, mySym);
                break;
            case "finished":
                b.tvTurn.setText("Match over!");
                if (prev == null || realChange) { if (prev != null) { showRoundBanner(s, mySym); bumpScore(s); } }
                if (!claimStarted) startFinish(s, prev == null);
                break;
            case "cancelled":
                b.tvTurn.setText("Match ended");
                if (!cancelledShown) {
                    cancelledShown = true;
                    String opp = s.nameOf("X".equals(mySym) ? "O" : "X");
                    String msg = me.equals(s.cancelledBy)
                            ? "The match ended, so nobody gets coins this time."
                            : opp + " left the match. Nobody gets coins this time.";
                    KawaiiDialog.show(this, 0, "Match ended", msg, "OK", this::leaveForGood, null, null);
                }
                break;
        }
        updateHint();
    }

    // ------------------------------------------------------------------ rounds

    private String roundText(MatchState s, String mySym) {
        String r = s.results[Math.max(0, Math.min(2, s.round - 1))];
        if (r == null) return "Round " + s.round;
        if ("D".equals(r)) return "Round " + s.round + ": it's a draw!";
        if (r.equals(mySym)) return "Round " + s.round + ": you win!";
        return "Round " + s.round + ": " + s.nameOf(r) + " wins!";
    }

    private void showRoundBanner(MatchState s, String mySym) {
        TextView t = b.tvBanner;
        t.animate().cancel();
        t.setText(roundText(s, mySym) + ("roundEnd".equals(s.phase) ? "\nNext round soon..." : ""));
        t.setVisibility(View.VISIBLE);
        t.setAlpha(0f);
        t.setScaleX(0.6f); t.setScaleY(0.6f);
        t.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    private void hideBanner() {
        final TextView t = b.tvBanner;
        if (t.getVisibility() != View.VISIBLE) return;
        t.animate().cancel();
        t.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(260).withEndAction(() -> t.setVisibility(View.GONE)).start();
    }

    private void bumpScore(MatchState s) {
        String r = s.results[Math.max(0, Math.min(2, s.round - 1))];
        View target = "X".equals(r) ? b.tvXScore : "O".equals(r) ? b.tvOScore : b.tvRound;
        target.animate().cancel();
        target.setScaleX(1f); target.setScaleY(1f);
        target.animate().scaleX(1.5f).scaleY(1.5f).setDuration(220)
                .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new OvershootInterpolator(2f)).start()).start();
    }

    /** After the result was shown for a moment, start the next round. Both phones try; the first one wins, the other is ignored. */
    private void scheduleAdvance(final MatchState s, String mySym) {
        if (advance != null) Bg.UI.removeCallbacks(advance);
        long wait = s.endedAt > 0 ? Math.max(0, s.endedAt + NEXT_ROUND_MS - MatchManager.serverNow()) : NEXT_ROUND_MS;
        if ("O".equals(mySym)) wait += 400;
        advance = new Runnable() {
            @Override public void run() {
                MatchState st = state;
                if (st != null && "roundEnd".equals(st.phase) && st.round == s.round && !isFinishing()) {
                    MatchManager.advanceRound(st);
                    Bg.UI.postDelayed(this, 2000);       // try again if it was too early (clock differences)
                }
            }
        };
        Bg.UI.postDelayed(advance, wait);
    }

    // ------------------------------------------------------------------ end of the match

    private void startFinish(final MatchState s, boolean immediate) {
        claimStarted = true;
        MatchManager.writeMatchRecord(s);                                        // Firestore: permanent record
        if (s.wonBy(me)) {
            MatchManager.claimReward(this, s, new MatchManager.RewardCallback() {
                @Override public void onClaimed(int newBalance) { rewardBalance = newBalance; if (finalShown) playReward(); }
                @Override public void onAlreadyClaimed() { rewardAlready = true; if (finalShown) playReward(); }
            });
        }
        if (finalScheduled) return;
        finalScheduled = true;
        Bg.UI.postDelayed(() -> { if (!isFinishing() && state != null) showFinal(state); }, immediate ? 200 : 1800);
    }

    private void showFinal(MatchState s) {
        if (finalShown) return;
        finalShown = true;
        b.tvBanner.animate().cancel();
        b.tvBanner.setVisibility(View.GONE);
        boolean won = s.wonBy(me);
        boolean draw = "D".equals(s.winner);
        String mySym = s.symbolOf(me);
        int mine = "X".equals(mySym) ? s.sx : s.so, theirs = "X".equals(mySym) ? s.so : s.sx;
        String oppName = s.nameOf("X".equals(mySym) ? "O" : "X");
        b.tvFinalTitle.setText(won ? "You won the match!" : draw ? "It's a tie!" : "Good game!");
        b.tvFinalSub.setText("Final score: " + mine + " - " + theirs
                + (won ? "" : draw ? "\nNo winner, so no coins this time." : "\n" + oppName + " won this one."));
        b.rewardRow.setVisibility(View.GONE);
        b.tvBalance.setText("");
        b.overlay.setAlpha(0f);
        b.overlay.setVisibility(View.VISIBLE);
        b.overlay.animate().alpha(1f).setDuration(240).start();
        b.finalCard.setScaleX(0.7f); b.finalCard.setScaleY(0.7f);
        b.finalCard.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(new OvershootInterpolator(1.4f)).start();
        if (won && (rewardBalance >= 0 || rewardAlready)) playReward();
    }

    /** The 50-coin celebration: the coin pops, the number counts up, little coins fly out. */
    private void playReward() {
        if (rewardPlayed) return;
        rewardPlayed = true;
        if (rewardBalance < 0) {                                   // collected earlier (e.g. before the app was closed)
            b.tvBalance.setText("Your reward was already collected");
            return;
        }
        b.rewardRow.setVisibility(View.VISIBLE);
        b.imgCoin.setScaleX(0f); b.imgCoin.setScaleY(0f);
        b.imgCoin.animate().scaleX(1f).scaleY(1f).rotationBy(360f).setDuration(650).setInterpolator(new OvershootInterpolator(2.2f)).start();
        ValueAnimator count = ValueAnimator.ofInt(0, MatchManager.WIN_COINS);
        count.setDuration(900);
        count.addUpdateListener(a -> b.tvReward.setText("+" + a.getAnimatedValue()));
        count.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { b.tvBalance.setText("Your coins: " + rewardBalance); }
        });
        count.start();
        burstCoins();
    }

    private void burstCoins() {
        final FrameLayout layer = b.coinLayer;
        layer.post(() -> {
            float dp = getResources().getDisplayMetrics().density;
            Random rnd = new Random();
            float cx = layer.getWidth() / 2f, cy = layer.getHeight() * 0.48f;
            for (int i = 0; i < 12; i++) {
                final ImageView iv = new ImageView(this);
                iv.setImageResource(R.drawable.ic_coin_cute);
                int size = (int) ((22 + rnd.nextInt(14)) * dp);
                layer.addView(iv, new FrameLayout.LayoutParams(size, size));
                iv.setX(cx - size / 2f); iv.setY(cy - size / 2f);
                iv.setAlpha(0f);
                float dx = (rnd.nextFloat() - 0.5f) * 300 * dp;
                float dy = -(80 + rnd.nextFloat() * 220) * dp;
                iv.animate().alpha(1f).translationXBy(dx).translationYBy(dy).rotationBy(rnd.nextInt(540) - 270)
                        .setStartDelay(i * 55L).setDuration(900).setInterpolator(new OvershootInterpolator(0.8f))
                        .withEndAction(() -> iv.animate().alpha(0f).translationYBy(40 * dp).setDuration(450)
                                .withEndAction(() -> layer.removeView(iv)).start()).start();
            }
        });
    }

    // ------------------------------------------------------------------ connection + friend presence

    private void attachOpponent() {
        if (oppUid == null || oppRef != null) return;
        oppRef = FirebaseConfig.rtdb().getReference("status").child(oppUid);
        oppListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                boolean online = "online".equals(s.child("state").getValue(String.class));
                Long when = s.child("lastChanged").getValue(Long.class);
                if (!online && oppOnline) oppAwaySince = (when != null && when > 0) ? when : MatchManager.serverNow();
                oppOnline = online;
                updateHint();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        oppRef.addValueEventListener(oppListener);
    }

    /**
     * A bad connection never loses the match: the board stays in the database and comes back when the phone does.
     * Only a friend who has been away for 2 whole minutes ends the match (nobody wins, nobody gets coins).
     */
    private void updateHint() {
        MatchState s = state;
        if (s == null || s.isOver() || finalShown) { b.tvHint.setText(""); return; }
        if (!connected) { b.tvHint.setText("Reconnecting... your match is safe"); return; }
        if (oppOnline) { b.tvHint.setText(""); return; }
        String mySym = s.symbolOf(me);
        String opp = s.nameOf("X".equals(mySym) ? "O" : "X");
        long away = Math.max(0, MatchManager.serverNow() - oppAwaySince);
        long left = Math.max(0, (AWAY_LIMIT_MS - away) / 1000);
        b.tvHint.setText(opp + " stepped away... waiting " + (left / 60) + ":" + String.format(Locale.US, "%02d", left % 60));
        if (away >= AWAY_LIMIT_MS) MatchManager.cancelMatch(s);
    }
}
