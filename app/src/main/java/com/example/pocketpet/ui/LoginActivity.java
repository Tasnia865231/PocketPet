package com.example.pocketpet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.databinding.ActivityLoginBinding;

/** Local stub login. Swap for Firebase Authentication (see README). */
public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Prefs prefs = new Prefs(this);
        if (!prefs.getUser().isEmpty()) {
            openHome();
            return;
        }
        final ActivityLoginBinding b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.btnEnter.setOnClickListener(v -> {
            String u = b.editUser.getText().toString().trim();
            if (u.isEmpty() || b.editPass.getText().length() == 0) {
                Toast.makeText(this, "Enter a username and password", Toast.LENGTH_SHORT).show();
            } else {
                prefs.setUser(u);
                openHome();
            }
        });
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
