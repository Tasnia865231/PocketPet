package com.example.pocketpet.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.databinding.ActivitySettingsBinding;
import com.example.pocketpet.system.AlarmScheduler;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivitySettingsBinding b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        final Prefs p = new Prefs(this);
        b.switchTilt.setChecked(p.isTilt());
        b.switchShake.setChecked(p.isShake());
        b.editHour.setText(String.valueOf(p.getNotifHour()));
        b.editLat.setText(String.valueOf(p.getLat()));
        b.editLon.setText(String.valueOf(p.getLon()));
        b.btnSave.setOnClickListener(v -> {
            p.setTilt(b.switchTilt.isChecked());
            p.setShake(b.switchShake.isChecked());
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
