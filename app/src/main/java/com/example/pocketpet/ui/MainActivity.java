package com.example.pocketpet.ui;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.example.pocketpet.Bg;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.databinding.ActivityMainBinding;
import com.example.pocketpet.sensor.SensorHelper;
import com.example.pocketpet.system.AlarmScheduler;
import com.example.pocketpet.system.NotificationHelper;
import com.example.pocketpet.system.PetSessionService;
import com.example.pocketpet.system.PowerReceiver;
import com.example.pocketpet.weather.WeatherRepository;
import com.example.pocketpet.weather.WeatherType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.UnaryOperator;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding b;
    private PetRepository repo;
    private Prefs prefs;
    private WeatherRepository weatherRepo;
    private SensorHelper sensors;

    // Charging receiver MUST be registered in code (power broadcasts can't be declared in the manifest).
    private final PowerReceiver power = new PowerReceiver(() -> mutate(p -> {
        Pet n = p.copy();
        n.energy = Math.min(100f, p.energy + 20f);
        return n;
    }));

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    /** Reloads the pet every 2 seconds while the screen is visible (stats decay, service updates). */
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            Bg.IO.execute(() -> {
                Pet p = repo.load();
                Bg.UI.post(() -> render(p));
            });
            Bg.UI.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        repo = new PetRepository(this);
        prefs = new Prefs(this);
        weatherRepo = new WeatherRepository(this);
        NotificationHelper.ensureChannels(this);
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);

        sensors = new SensorHelper(this, new SensorHelper.Listener() {
            @Override public void onTilt(float x, float y, float z) { b.petView.setGravity(x, y); }
            @Override public void onShake() { b.petView.addShake(); }
        });
        b.petView.setOnDizzyTick(() -> mutate(p -> {
            Pet n = p.copy();
            n.mood = Math.max(0f, p.mood - 5f);
            n.energy = Math.max(0f, p.energy - 3f);
            return n;
        }));

        b.btnFeed.setOnClickListener(v -> mutate(repo::feedFromBag));
        b.btnPlay.setOnClickListener(v -> { b.petView.cheer(); mutate(repo::play); });
        b.btnSleep.setOnClickListener(v -> ContextCompat.startForegroundService(
                this, new Intent(this, PetSessionService.class).putExtra("mode", "SLEEP")));
        b.btnClean.setOnClickListener(v -> mutate(repo::clean));
        b.btnShop.setOnClickListener(v -> startActivity(new Intent(this, ShopActivity.class)));
        b.btnBag.setOnClickListener(v -> startActivity(new Intent(this, InventoryActivity.class)));
        b.btnStats.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
        b.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        b.btnShare.setOnClickListener(v -> share());
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensors.tiltOn = prefs.isTilt();
        sensors.shakeOn = prefs.isShake();
        sensors.start();                       // register in onResume ...
        ContextCompat.registerReceiver(this, power, new IntentFilter(Intent.ACTION_POWER_CONNECTED),
                ContextCompat.RECEIVER_EXPORTED);

        b.petView.setWeather(weatherRepo.cached());
        Bg.IO.execute(() -> {
            WeatherType w = weatherRepo.fetch();
            if (w != null) Bg.UI.post(() -> b.petView.setWeather(w));
        });
        Bg.UI.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensors.stop();                        // ... unregister in onPause to save battery
        unregisterReceiver(power);
        Bg.UI.removeCallbacks(poll);
        AlarmScheduler.schedule(this);
    }

    private void mutate(UnaryOperator<Pet> f) {
        Bg.IO.execute(() -> {
            Pet r = repo.edit(f);
            Bg.UI.post(() -> render(r));
        });
    }

    private void render(Pet p) {
        String[] names = {"Baby", "Kid", "Grown"};
        String stageName = names[Math.min(Math.max(p.stage, 0), names.length - 1)];
        b.title.setText(p.name + " | " + stageName);
        b.coins.setText("Coins: " + p.coins);
        b.barHunger.setProgress((int) p.hunger);
        b.barEnergy.setProgress((int) p.energy);
        b.barMood.setProgress((int) p.mood);
        b.petView.setMood(p.mood);
        b.petView.setStage(p.stage);
        b.petView.setHat(p.hat);
    }

    /** Implicit intent: share a screenshot of the pet. */
    private void share() {
        File dir = new File(getCacheDir(), "shared");
        dir.mkdirs();
        File file = new File(dir, "pet.png");
        try (FileOutputStream os = new FileOutputStream(file)) {
            Bitmap bm = b.petView.snapshot();
            bm.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (IOException e) {
            Toast.makeText(this, "Could not create screenshot", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/png");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TEXT, "Look at my Pocket Pet!");
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share your pet"));
    }
}
