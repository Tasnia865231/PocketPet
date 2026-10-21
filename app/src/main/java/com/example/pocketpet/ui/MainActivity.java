package com.example.pocketpet.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.pocketpet.Bg;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.databinding.ActivityMainBinding;
import com.example.pocketpet.sensor.SensorHelper;
import com.example.pocketpet.system.AlarmScheduler;
import com.example.pocketpet.system.NotificationHelper;
import com.example.pocketpet.system.PetSessionService;
import com.example.pocketpet.system.PowerReceiver;
import com.example.pocketpet.view.PetView;
import com.example.pocketpet.weather.WeatherRepository;
import com.example.pocketpet.weather.WeatherType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.UnaryOperator;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding b;
    private PetRepository repo;
    private Prefs prefs;
    private WeatherRepository weatherRepo;
    private SensorHelper sensors;
    private FoodMenuAdapter foodAdapter;

    // Current pet snapshot (updated by render)
    private Pet currentPet;
    // Tracks whether we are currently in bath mode at the Activity level
    private boolean inBath = false;

    // Sleep button pulse animation
    private ObjectAnimator sleepPulse;

    // Charging receiver must be registered in code (not manifest)
    private final PowerReceiver power = new PowerReceiver(() -> mutate(p -> {
        Pet n = p.copy();
        n.energy = Math.min(100f, p.energy + 20f);
        return n;
    }));

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    /** Polls pet state every 2 seconds while visible. */
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

        repo        = new PetRepository(this);
        prefs       = new Prefs(this);
        weatherRepo = new WeatherRepository(this);

        NotificationHelper.ensureChannels(this);
        if (Build.VERSION.SDK_INT >= 33)
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);

        // Sensor setup
        sensors = new SensorHelper(this, new SensorHelper.Listener() {
            @Override public void onTilt(float x, float y, float z) { b.petView.setGravity(x, y); }
            @Override public void onShake() { b.petView.addShake(); }
        });

        // Dizziness -> stat penalty
        b.petView.setOnDizzyTick(() -> mutate(p -> {
            Pet n = p.copy();
            n.mood   = Math.max(0f, p.mood   - 5f);
            n.energy = Math.max(0f, p.energy - 3f);
            return n;
        }));

        // PetView listener: food eaten
        b.petView.setOnFoodEaten(itemId -> {
            // itemId was consumed inside feedItem; just refresh UI
            Bg.IO.execute(() -> {
                Pet p = repo.load();
                Bg.UI.post(() -> {
                    render(p);
                    // Check level-up (render handles showing badge; also trigger visual celebration)
                    if (currentPet != null && p.level > currentPet.level) {
                        b.petView.showLevelUp(p.level);
                    }
                });
            });
        });

        // PetView listener: bath finished (full scrub done)
        b.petView.setOnBathFinished(() -> Bg.IO.execute(() -> {
            Pet result = repo.edit(p -> repo.bath(p, 100f));
            Bg.UI.post(() -> {
                inBath = false;
                b.btnBathDone.setVisibility(View.GONE);
                b.btnFeed.setEnabled(true);
                b.btnPlay.setEnabled(true);
                b.btnClean.setText("Bath");
                sensors.tiltOn  = prefs.isTilt();
                sensors.shakeOn = prefs.isShake();
                render(result);
                Toast.makeText(this, "Sparkly clean!", Toast.LENGTH_SHORT).show();
            });
        }));

        // PetView listener: wake from sleep (shake or auto)
        b.petView.setOnWake(() -> {
            if (currentPet == null) return;
            boolean early = currentPet.energy < 50f;
            Bg.IO.execute(() -> {
                Pet result = repo.edit(p -> repo.wake(p, early));
                Bg.UI.post(() -> {
                    b.petView.exitSleeping();
                    stopSleepService();
                    setSleepingUiMode(false);
                    render(result);
                });
            });
        });

        // Food menu setup
        foodAdapter = new FoodMenuAdapter(item -> selectFood(item));
        b.foodRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        b.foodRecycler.setAdapter(foodAdapter);

        // ---- button listeners ----
        b.btnFeed.setOnClickListener(v -> openFoodMenu());

        b.btnPlay.setOnClickListener(v -> {
            if (isSleeping()) { showSleepToast(); return; }
            b.petView.cheer();
            mutate(p -> {
                if (p.energy < 10f) {
                    Bg.UI.post(() -> Toast.makeText(this, "Too tired to play", Toast.LENGTH_SHORT).show());
                    return p;
                }
                return repo.play(p);
            });
        });

        b.btnSleep.setOnClickListener(v -> {
            if (isSleeping()) {
                // Wake branch
                if (currentPet == null) return;
                boolean early = currentPet.energy < 50f;
                Bg.IO.execute(() -> {
                    Pet result = repo.edit(p -> repo.wake(p, early));
                    Bg.UI.post(() -> {
                        b.petView.exitSleeping();
                        stopSleepService();
                        setSleepingUiMode(false);
                        render(result);
                        if (early) Toast.makeText(this, "Mochi woke up early", Toast.LENGTH_SHORT).show();
                    });
                });
            } else {
                // Sleep branch
                if (inBath) { Toast.makeText(this, "Finish the bath first", Toast.LENGTH_SHORT).show(); return; }
                Bg.IO.execute(() -> {
                    Pet result = repo.edit(p -> repo.startSleep(p));
                    Bg.UI.post(() -> {
                        b.petView.enterSleeping();
                        startSleepService();
                        setSleepingUiMode(true);
                        render(result);
                    });
                });
            }
        });

        b.btnClean.setOnClickListener(v -> {
            if (isSleeping()) { showSleepToast(); return; }
            if (inBath) return; // already in bath
            inBath = true;
            b.petView.enterBathing();
            b.btnBathDone.setVisibility(View.VISIBLE);
            b.btnFeed.setEnabled(false);
            b.btnPlay.setEnabled(false);
            b.btnClean.setText("Bathing...");
            sensors.tiltOn  = false;
            sensors.shakeOn = false;
        });

        b.btnBathDone.setOnClickListener(v -> {
            float prog = b.petView.exitBathEarly();
            inBath = false;
            b.btnBathDone.setVisibility(View.GONE);
            b.btnFeed.setEnabled(true);
            b.btnPlay.setEnabled(true);
            b.btnClean.setText("Bath");
            sensors.tiltOn  = prefs.isTilt();
            sensors.shakeOn = prefs.isShake();
            final float cleanGained = prog * 100f;
            Bg.IO.execute(() -> {
                Pet result = repo.edit(p -> repo.bath(p, cleanGained));
                Bg.UI.post(() -> render(result));
            });
        });

        b.btnCloseFoodPanel.setOnClickListener(v -> closeFoodMenu());

        b.btnShop.setOnClickListener(v     -> startActivity(new Intent(this, ShopActivity.class)));
        b.btnBag.setOnClickListener(v      -> startActivity(new Intent(this, InventoryActivity.class)));
        b.btnStats.setOnClickListener(v    -> startActivity(new Intent(this, StatsActivity.class)));
        b.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        b.btnShare.setOnClickListener(v    -> share());
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensors.tiltOn  = prefs.isTilt();
        sensors.shakeOn = prefs.isShake();
        sensors.start();
        ContextCompat.registerReceiver(this, power,
                new IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_EXPORTED);

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
        sensors.stop();
        try { unregisterReceiver(power); } catch (IllegalArgumentException ignored) {}
        Bg.UI.removeCallbacks(poll);
        AlarmScheduler.schedule(this);
    }

    @Override
    public void onBackPressed() {
        if (b.foodPanel.getVisibility() == View.VISIBLE) {
            closeFoodMenu();
            return;
        }
        super.onBackPressed();
    }

    // ---- food menu ----
    private void openFoodMenu() {
        if (isSleeping()) { showSleepToast(); return; }
        if (inBath) return;
        // Load owned food items then show panel
        Bg.IO.execute(() -> {
            List<Item> items = repo.ownedItems();
            Bg.UI.post(() -> {
                foodAdapter.setItems(items);
                if (foodAdapter.getItemCount() == 0) {
                    Toast.makeText(this, "No food in bag. Visit the Shop!", Toast.LENGTH_SHORT).show();
                    return;
                }
                b.foodPanel.setVisibility(View.VISIBLE);
            });
        });
    }

    private void closeFoodMenu() {
        b.foodPanel.setVisibility(View.GONE);
        if (b.petView.getState() == PetView.PetState.EATING) {
            b.petView.exitEating();
        }
    }

    private void selectFood(Item item) {
        if (currentPet != null && currentPet.hunger >= 95f) {
            Toast.makeText(this, "Mochi is already full!", Toast.LENGTH_SHORT).show();
            return;
        }
        b.foodPanel.setVisibility(View.GONE);
        boolean isMilk = "Milk".equals(item.name);
        // Spawn the food sprite and enter eating mode
        b.petView.enterEating(item.id, item.name, isMilk);
        // The actual stat update happens when PetView calls onFoodEaten after the animation.
        // We pre-consume the item in the DB at that point via feedItem.
        b.petView.setOnFoodEaten(itemId -> {
            Bg.IO.execute(() -> {
                String result = repo.feedItem(itemId);
                Bg.UI.post(() -> {
                    if ("FULL".equals(result)) {
                        Toast.makeText(this, "Mochi is already full!", Toast.LENGTH_SHORT).show();
                        b.petView.exitEating();
                        return;
                    }
                    // Refresh UI after eating
                    Bg.IO.execute(() -> {
                        Pet p = repo.load();
                        Bg.UI.post(() -> {
                            int prevLevel = currentPet != null ? currentPet.level : 1;
                            render(p);
                            if (p.level > prevLevel) b.petView.showLevelUp(p.level);
                        });
                    });
                });
            });
        });
    }

    // ---- sleep helpers ----
    private boolean isSleeping() {
        return currentPet != null && currentPet.isSleeping;
    }

    private void showSleepToast() {
        Toast.makeText(this, "Shh, Mochi is sleeping", Toast.LENGTH_SHORT).show();
    }

    private void setSleepingUiMode(boolean sleeping) {
        b.btnFeed.setEnabled(!sleeping);
        b.btnPlay.setEnabled(!sleeping);
        b.btnClean.setEnabled(!sleeping);
        b.btnSleep.setText(sleeping ? "Wake" : "Sleep");
        // Toggle nightstand lamp: OFF when Mochi is sleeping, ON when awake
        b.petView.setLampOn(!sleeping);
        if (sleeping) {
            if (sleepPulse != null) sleepPulse.cancel();
        } else {
            if (sleepPulse != null) sleepPulse.cancel();
        }
    }

    private void startSleepService() {
        Intent svc = new Intent(this, PetSessionService.class).putExtra("mode", "SLEEP");
        ContextCompat.startForegroundService(this, svc);
    }

    private void stopSleepService() {
        stopService(new Intent(this, PetSessionService.class));
    }

    // ---- mutate / render ----
    private void mutate(UnaryOperator<Pet> f) {
        Bg.IO.execute(() -> {
            Pet r = repo.edit(f);
            Bg.UI.post(() -> {
                int prevLevel = currentPet != null ? currentPet.level : r.level;
                render(r);
                if (r.level > prevLevel) b.petView.showLevelUp(r.level);
            });
        });
    }

    private void render(Pet p) {
        currentPet = p;

        // Title: name + stage name
        String[] stages = {"Baby", "Kid", "Grown"};
        String stageName = stages[Math.min(Math.max(p.stage, 0), stages.length - 1)];
        b.title.setText(p.name + " | " + stageName);
        b.coins.setText("Coins: " + p.coins);

        // Level badge + XP bar
        int xpNeeded = 50 * p.level;
        b.levelBadge.setText("Lv." + p.level);
        b.barXp.setMax(xpNeeded);
        b.barXp.setProgress(Math.min(p.xp, xpNeeded));

        // Stat bars
        b.barHunger.setProgress((int) p.hunger);
        b.barEnergy.setProgress((int) p.energy);
        b.barMood.setProgress((int) p.mood);
        b.barClean.setProgress((int) p.cleanliness);

        // Push state to PetView
        b.petView.setMood(p.mood);
        b.petView.setStage(p.stage);
        b.petView.setHat(p.hat);
        b.petView.setCleanliness(p.cleanliness);

        // Tired state
        boolean tired = p.energy < 20f && !p.isSleeping;
        b.petView.setTired(tired);

        // Sleep pulse on Sleep button when tired
        if (tired && !p.isSleeping) {
            if (sleepPulse == null || !sleepPulse.isRunning()) {
                sleepPulse = ObjectAnimator.ofFloat(b.btnSleep, "alpha", 1f, 0.4f);
                sleepPulse.setDuration(700);
                sleepPulse.setRepeatCount(ObjectAnimator.INFINITE);
                sleepPulse.setRepeatMode(ObjectAnimator.REVERSE);
                sleepPulse.start();
            }
        } else {
            if (sleepPulse != null) { sleepPulse.cancel(); b.btnSleep.setAlpha(1f); }
        }

        // Sleeping UI mode
        setSleepingUiMode(p.isSleeping);
        if (p.isSleeping && b.petView.getState() != PetView.PetState.SLEEPING) {
            b.petView.enterSleeping();
        }
        if (!p.isSleeping && b.petView.getState() == PetView.PetState.SLEEPING) {
            b.petView.exitSleeping();
        }
    }

    // ---- share screenshot ----
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
