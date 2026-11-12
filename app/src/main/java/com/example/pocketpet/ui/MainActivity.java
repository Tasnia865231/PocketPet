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
import android.content.pm.PackageManager;
import com.example.pocketpet.voice.VoiceEcho;
import com.example.pocketpet.Prefs;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.databinding.ActivityMainBinding;
import com.example.pocketpet.system.LocationHelper;
import com.example.pocketpet.firebase.ActivityFeed;
import com.example.pocketpet.firebase.MatchManager;
import com.example.pocketpet.sensor.SensorHelper;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.IBinder;
import com.example.pocketpet.system.AlarmScheduler;
import com.example.pocketpet.system.DailyReward;
import com.example.pocketpet.system.ForegroundTracker;
import com.example.pocketpet.system.Haptics;
import com.example.pocketpet.system.PetEvents;
import com.example.pocketpet.system.PetProvider;
import com.example.pocketpet.system.SystemEventsReceiver;
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

    // ---- Android components used by this screen ----
    private PetSessionService engine;                 // bound service: Mochi's engine
    private PermissionFlow permissions;               // friendly runtime-permission prompts
    private ContentObserver petObserver;              // refreshes the screen whenever the content provider reports a change
    private SystemEventsReceiver sysEvents;           // BATTERY_LOW, DATE_CHANGED (registered in code)
    private BroadcastReceiver petEvents;              // the app's own broadcasts: woke, level-up, critical, changed
    private ConnectivityManager.NetworkCallback netCb;

    private final ServiceConnection engineConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder binder) {
            engine = ((PetSessionService.PetBinder) binder).getService();
            engine.setListener(pet -> render(pet));
        }
        @Override public void onServiceDisconnected(ComponentName n) { engine = null; }
    };

    // Mini-games: every finished game gives the normal Play reward (mood, XP, energy cost); coins are what the game screens showed
    private final ActivityResultLauncher<Intent> gameLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null) return;
                final int games = data.getIntExtra(GameActivity.EXTRA_GAMES, 0);
                final int coins = data.getIntExtra(GameActivity.EXTRA_COINS, 0);
                if (games <= 0) return;
                b.petView.cheer();
                mutate(p -> {
                    Pet n = p;
                    for (int i = 0; i < games; i++) n = repo.play(n);     // mood, XP and energy cost per game
                    Pet r = repo.addCoins(n, coins);                       // exactly the coins the game screens showed
                    // community feed: games played + the player's coin total AFTER this reward (a new level, if any,
                    // is posted by PetRepository.save() right after this)
                    ActivityFeed.postMiniGames(games, r.coins);
                    return r;
                });
            });

    // ---- Mochi voice: tap Mochi, he listens, then repeats what he heard in a cute voice ----
    private final VoiceEcho voice = new VoiceEcho();
    private final ActivityResultLauncher<String> micPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startVoiceEcho();
                else KawaiiToast.show(this, "Mochi needs the microphone to hear you");
            });

    /** Mochi was tapped (PetView only reports taps while he is awake, not eating and not bathing). */
    private void onMochiTapped() {
        if (!prefs.isVoiceEnabled()) return;                 // switched off in Settings: taps do nothing, like before
        if (isSleeping() || inBath || voice.isBusy()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceEcho();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startVoiceEcho() {
        voice.start(new VoiceEcho.Listener() {
            @Override public void onListening()    { b.petView.say("I'm listening!"); Haptics.pulse(MainActivity.this, 40); }
            @Override public void onSpeaking()     { b.petView.cheer(); }
            @Override public void onNothingHeard() { b.petView.say("I didn't hear anything..."); }
            @Override public void onError()        { KawaiiToast.show(MainActivity.this, "Mochi can't use the microphone right now"); }
        });
    }

    // Charging receiver must be registered in code (not manifest)
    private final PowerReceiver power = new PowerReceiver(() -> mutate(p -> {
        Pet n = p.copy();
        n.energy = Math.min(100f, p.energy + 20f);
        return n;
    }));

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
        permissions = new PermissionFlow(this);       // asks (nicely) for notifications, location and exact alarms

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
                        levelUp(p.level);
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
                KawaiiToast.show(this, "Sparkly clean!");
            });
        }));

        // PetView listener: wake from sleep (shake or auto)
        b.petView.setOnWake(() -> {
            if (currentPet == null) return;
            boolean early = currentPet.energy < PetRepository.POOR_SLEEP_BELOW;
            wakeMochi(early, null);
        });

        // Mochi voice: a tap on Mochi starts listening
        b.petView.setOnTap(this::onMochiTapped);

        // Food menu setup
        foodAdapter = new FoodMenuAdapter(item -> selectFood(item));
        b.foodRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        b.foodRecycler.setAdapter(foodAdapter);

        // ---- button listeners ----
        b.btnFeed.setOnClickListener(v -> openFoodMenu());

        b.btnPlay.setOnClickListener(v -> {
            if (isSleeping()) { showSleepToast(); return; }
            if (currentPet != null && currentPet.energy <= 0f) {          // no energy at all: no games until Mochi has slept
                KawaiiToast.show(this, "Mochi needs to sleep");
                b.petView.say("I need to sleep...");
                return;
            }
            if (currentPet != null && currentPet.energy < 10f) {
                KawaiiToast.show(this, "Too tired to play");
                return;
            }
            // Opens the game menu (Tic Tac Toe / Minesweeper). The usual Play rewards
            // are applied in gameLauncher when the player comes back.
            gameLauncher.launch(new Intent(this, GameActivity.class).putExtra(GameActivity.EXTRA_ENERGY, currentPet == null ? 100f : currentPet.energy));
        });

        b.btnSleep.setOnClickListener(v -> {
            if (isSleeping()) {
                // Wake branch
                if (currentPet == null) return;
                boolean early = currentPet.energy < PetRepository.POOR_SLEEP_BELOW;
                wakeMochi(early, null);
            } else {
                // Sleep branch
                if (inBath) { KawaiiToast.show(this, "Finish the bath first"); return; }
                sleepMochi();
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
        b.btnFriends.setOnClickListener(v  -> startActivity(new Intent(this, CommunityActivity.class)));
        b.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        b.btnShare.setOnClickListener(v    -> share());
    }

    @Override
    protected void onStart() {
        super.onStart();
        ForegroundTracker.visible = true;

        // 1) Service: bind to Mochi's engine
        bindService(new Intent(this, PetSessionService.class), engineConn, Context.BIND_AUTO_CREATE);

        // 2) Content provider: any change to Mochi's data (from a notification button, the widget, the engine) refreshes this screen
        petObserver = new ContentObserver(new Handler(getMainLooper())) {
            @Override public void onChange(boolean selfChange) { reloadFromDb(); }
        };
        getContentResolver().registerContentObserver(PetProvider.PET, true, petObserver);

        // 3) Broadcast receivers: our own events + system events (both registered in code)
        petEvents = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (PetEvents.ACTION_PET_CRITICAL.equals(a)) {
                    String kind = i.getStringExtra(PetEvents.EXTRA_KIND);
                    if ("mood".equals(kind))        b.petView.say("Please play with me!");
                    else if ("hunger".equals(kind)) b.petView.say("I'm so hungry!");
                    else if ("energy".equals(kind)) b.petView.say("I need to sleep...");
                } else if (PetEvents.ACTION_LEVEL_UP.equals(a)) {
                    Haptics.pulse(MainActivity.this, 120);
                } else if (PetEvents.ACTION_PET_WOKE.equals(a)) {
                    Haptics.pulse(MainActivity.this, 80);
                }
                reloadFromDb();
            }
        };
        IntentFilter pf = new IntentFilter();
        pf.addAction(PetEvents.ACTION_PET_WOKE);
        pf.addAction(PetEvents.ACTION_LEVEL_UP);
        pf.addAction(PetEvents.ACTION_PET_CRITICAL);
        pf.addAction(PetEvents.ACTION_PET_CHANGED);
        ContextCompat.registerReceiver(this, petEvents, pf, ContextCompat.RECEIVER_NOT_EXPORTED);

        sysEvents = new SystemEventsReceiver((action, coins) -> {
            if (Intent.ACTION_BATTERY_LOW.equals(action)) {
                KawaiiToast.show(this, "Battery is low, Mochi feels sleepy");
                b.petView.say("I'm so sleepy...");
            } else if (coins > 0) {
                KawaiiToast.show(this, "New day! Daily gift: +" + coins + " coins");
            }
        });
        ContextCompat.registerReceiver(this, sysEvents, SystemEventsReceiver.filter(), ContextCompat.RECEIVER_EXPORTED);

        // 4) Network: refresh the weather as soon as we are back online
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { refreshWeather(); }
            };
            cm.registerDefaultNetworkCallback(netCb);
        } catch (Exception ignored) { netCb = null; }

        // daily login gift
        Bg.IO.execute(() -> {
            int coins = DailyReward.claimIfNeeded(this);
            if (coins > 0) Bg.UI.post(() -> KawaiiToast.show(this, "Daily gift: +" + coins + " coins"));
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        voice.cancel();                       // stop listening / repeating when the screen closes
        ForegroundTracker.visible = false;
        if (engine != null) { engine.setListener(null); engine = null; }
        try { unbindService(engineConn); } catch (IllegalArgumentException ignored) { }
        if (petObserver != null) getContentResolver().unregisterContentObserver(petObserver);
        try { unregisterReceiver(petEvents); } catch (IllegalArgumentException ignored) { }
        try { unregisterReceiver(sysEvents); } catch (IllegalArgumentException ignored) { }
        if (netCb != null) {
            try { ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCb); } catch (Exception ignored) { }
            netCb = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        permissions.start();
        MatchManager.resumeIfAny();     // still inside an online match (app was closed)? open it again
        if (LocationHelper.hasPermission(this)
                && System.currentTimeMillis() - prefs.getLocTime() > 3 * 60 * 60 * 1000L) {
            LocationHelper.refreshAsync(this, found -> { if (found) refreshWeather(); });
        }
        sensors.tiltOn  = prefs.isTilt();
        sensors.shakeOn = prefs.isShake();
        sensors.start();
        ContextCompat.registerReceiver(this, power,
                new IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_EXPORTED);

        b.petView.setWeather(weatherRepo.cached());
        refreshWeather();
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
                    KawaiiToast.show(this, "No food in bag. Visit the Shop!");
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
            KawaiiToast.show(this, "Mochi is already full!");
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
                        KawaiiToast.show(this, "Mochi is already full!");
                        b.petView.exitEating();
                        return;
                    }
                    // Refresh UI after eating
                    Bg.IO.execute(() -> {
                        Pet p = repo.load();
                        Bg.UI.post(() -> {
                            int prevLevel = currentPet != null ? currentPet.level : 1;
                            render(p);
                            Haptics.pulse(this, 50);
                            // Mochi talks: "Yummy!", and if he is still hungry: "I want more!"
                            if (p.hunger < 90f) b.petView.say("Yummy!", "I want more!");
                            else b.petView.say("Yummy!");
                            if (p.level > prevLevel) levelUp(p.level);
                        });
                    });
                });
            });
        });
    }

    private void levelUp(int level) {
        b.petView.showLevelUp(level);
        Haptics.pulse(this, 120);
    }

    /** Falls asleep through the bound engine service (falls back to doing it directly if it is not bound yet). */
    private void sleepMochi() {
        voice.cancel();
        PetSessionService.PetListener done = result -> {
            b.petView.enterSleeping();
            setSleepingUiMode(true);
            render(result);
        };
        if (engine != null) { engine.startSleep(done); return; }
        Bg.IO.execute(() -> {
            Pet result = repo.edit(p -> repo.startSleep(p));
            Bg.UI.post(() -> { startSleepService(); done.onPet(result); });
        });
    }

    private void wakeMochi(boolean early, String message) {
        // Sleep bar below 40% when woken = not a nice sleep: Mochi says so and a toast tells the player
        final String toast = early ? "Mochi didn't have a nice sleep. He needs to sleep more!" : message;
        b.petView.setWakeLine(early ? "I didn't have a nice sleep... I need to sleep more!" : null);
        PetSessionService.PetListener done = result -> {
            b.petView.exitSleeping();                 // Mochi says "Hello! It was a nice sleep!" (or the line above)
            setSleepingUiMode(false);
            render(result);
            if (toast != null) KawaiiToast.show(this, toast);
        };
        if (engine != null) { engine.wakeUp(early, done); return; }
        Bg.IO.execute(() -> {
            Pet result = repo.edit(p -> repo.wake(p, early));
            Bg.UI.post(() -> { stopSleepService(); done.onPet(result); });
        });
    }

    /** True when the phone has a working network (ACCESS_NETWORK_STATE). */
    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    private void refreshWeather() {
        if (!isOnline()) return;
        Bg.IO.execute(() -> {
            WeatherType w = weatherRepo.fetch();
            if (w != null) Bg.UI.post(() -> b.petView.setWeather(w));
        });
    }

    private void reloadFromDb() {
        Bg.IO.execute(() -> {
            Pet p = repo.load();
            Bg.UI.post(() -> render(p));
        });
    }

    // ---- sleep helpers ----
    private boolean isSleeping() {
        return currentPet != null && currentPet.isSleeping;
    }

    private void showSleepToast() {
        KawaiiToast.show(this, "Shh, Mochi is sleeping");
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
                if (r.level > prevLevel) levelUp(r.level);
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
        b.petView.setEnergy(p.energy);
        b.petView.setStage(p.stage);
        b.petView.setHat(p.hat);
        b.petView.setShell(p.shell);
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
            KawaiiToast.show(this, "Could not create screenshot");
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
