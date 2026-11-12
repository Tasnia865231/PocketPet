package com.example.pocketpet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.databinding.ActivityLoginBinding;
import com.example.pocketpet.firebase.FirebaseAuthManager;
import com.example.pocketpet.firebase.MatchManager;
import com.example.pocketpet.firebase.PlayerProfileRepository;
import com.example.pocketpet.firebase.PresenceManager;
import com.google.firebase.auth.FirebaseUser;

/**
 * Login / Register screen, powered by Firebase Authentication (see firebase/FirebaseAuthManager).
 *
 * Session: Firebase remembers the logged-in player on the phone. So if someone is already logged in when the app
 * opens, we skip this screen and go straight to the game.
 */
public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding b;
    private boolean registerMode = false;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuthManager.isLoggedIn()) {      // saved session -> no need to log in again
            PlayerProfileRepository.syncAfterLogin(this, () -> { PresenceManager.enable(); MatchManager.enable(); });   // refresh lastLoginAt + cloud level/coins in the background
            openHome();
            return;
        }
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.btnMain.setOnClickListener(v -> submit());
        b.btnToggle.setOnClickListener(v -> { if (!busy) setMode(!registerMode); });
        setMode(false);
    }

    /** Switches the screen between "Log in" and "Create an account". */
    private void setMode(boolean register) {
        registerMode = register;
        b.editName.setVisibility(register ? View.VISIBLE : View.GONE);
        b.textTitle.setText(register ? "Join Mochi!" : "Welcome back!");
        b.textSubtitle.setText(register ? "Make your own cozy account" : "Mochi missed you");
        b.btnMain.setText(register ? "Create account" : "Log in");
        b.btnToggle.setText(register ? "Already have one? Log in" : "New here? Create an account");
    }

    private void setBusy(boolean on) {
        busy = on;
        b.btnMain.setEnabled(!on);
        b.btnToggle.setEnabled(!on);
        if (on) b.btnMain.setText("Please wait...");
        else setMode(registerMode);
    }

    private void submit() {
        if (busy) return;
        final String name = b.editName.getText().toString().trim();
        final String email = b.editEmail.getText().toString().trim();
        final String pass = b.editPass.getText().toString();

        // quick checks first, so we only call Firebase with sensible input
        if (registerMode && name.length() < 2) { KawaiiToast.show(this, "Pick a player name (2+ letters)"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { KawaiiToast.show(this, "That email does not look right"); return; }
        if (pass.length() < 6) { KawaiiToast.show(this, "Password needs 6+ characters"); return; }

        setBusy(true);
        FirebaseAuthManager.Callback done = new FirebaseAuthManager.Callback() {
            @Override public void onSuccess(@NonNull FirebaseUser user) {
                new Prefs(LoginActivity.this).setUser(FirebaseAuthManager.displayName());   // kept for older code
                b.btnMain.setText("Loading your Mochi...");
                // Firestore: create the profile (new player) or load it (returning player), THEN open the game
                PlayerProfileRepository.syncAfterLogin(LoginActivity.this, () -> {
                    PresenceManager.enable();       // Realtime Database: show this player as Online
                    MatchManager.enable();          // Realtime Database: invitations + online matches
                    openHome();
                });
            }
            @Override public void onError(@NonNull String message) {
                setBusy(false);
                KawaiiToast.show(LoginActivity.this, message);
            }
        };
        if (registerMode) FirebaseAuthManager.register(email, pass, name, done);
        else FirebaseAuthManager.login(email, pass, done);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
