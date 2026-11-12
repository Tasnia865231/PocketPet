package com.example.pocketpet.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.databinding.ActivitySettingsBinding;
import com.example.pocketpet.firebase.FirebaseAuthManager;
import com.example.pocketpet.firebase.MatchManager;
import com.example.pocketpet.firebase.PresenceManager;
import com.example.pocketpet.system.AlarmScheduler;
import com.example.pocketpet.system.LocationHelper;

public class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding b;
    private Prefs p;

    // Asks for the location permission when "Use my location" is tapped without it
    private final ActivityResultLauncher<String> locPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) useMyLocation();
                else Toast.makeText(this, "Location is off. Type the coordinates instead.", Toast.LENGTH_SHORT).show();
            });

    private void useMyLocation() {
        if (!LocationHelper.hasPermission(this)) { locPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION); return; }
        Toast.makeText(this, "Finding your location...", Toast.LENGTH_SHORT).show();
        LocationHelper.refreshAsync(this, found -> {
            if (isFinishing() || isDestroyed()) return;
            if (found) {
                b.editLat.setText(String.valueOf(p.getLat()));
                b.editLon.setText(String.valueOf(p.getLon()));
                Toast.makeText(this, "Location updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Could not get your location. Turn Location on in your phone, or type it.", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        p = new Prefs(this);
        b.switchTilt.setChecked(p.isTilt());
        b.switchShake.setChecked(p.isShake());
        b.switchVoice.setChecked(p.isVoiceEnabled());
        b.switchVoice.setOnCheckedChangeListener((v, on) -> p.setVoiceEnabled(on));      // applies immediately
        b.editHour.setText(String.valueOf(p.getNotifHour()));
        b.editLat.setText(String.valueOf(p.getLat()));
        b.editLon.setText(String.valueOf(p.getLon()));
        b.btnUseLocation.setOnClickListener(v -> useMyLocation());
        b.btnLogout.setOnClickListener(v -> {
            // Realtime Database: mark this player Offline FIRST (needs the login), then really log out
            MatchManager.disable();
            PresenceManager.disableThen(() -> {
                FirebaseAuthManager.logout();
                p.setUser("");
                // clear the back stack so Back cannot return to the game after logging out
                startActivity(new Intent(this, LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            });
        });
        b.btnSave.setOnClickListener(v -> {
            p.setTilt(b.switchTilt.isChecked());
            p.setShake(b.switchShake.isChecked());
            p.setVoiceEnabled(b.switchVoice.isChecked());
            try {
                int hour = Integer.parseInt(b.editHour.getText().toString().trim());
                p.setNotifHour(Math.max(0, Math.min(23, hour)));
            } catch (NumberFormatException ignored) { }
            try {
                p.setLat(Float.parseFloat(b.editLat.getText().toString().trim()));
            } catch (NumberFormatException ignored) { }
            try {
                p.setLon(Float.parseFloat(b.editLon.getText().toString().trim()));
            } catch (NumberFormatException ignored) { }
            p.setWeatherTime(0L);                    // force a fresh weather fetch
            AlarmScheduler.schedule(this);
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
