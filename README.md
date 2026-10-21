# Pocket Pet (Android, Java)

A 2D Ghibli-style virtual pet. The phone's motion controls how the pet physically behaves,
and the window background changes with the real weather.

## Open and run
Built for **Android Studio Dolphin 2021.3.1** (AGP 7.3.1, Gradle 7.5.1, compileSdk 33, Java 8 source level).
1. Android Studio -> **Open** -> select this `PocketPet` folder.
2. Let Gradle sync (it downloads Gradle 7.5.1 and the dependencies, so you need internet once).
3. Run on a **real phone** (the emulator can fake tilt via Extended Controls -> Virtual sensors, but shake is easier on a device).

> **First run after Stage 1:** The Room database was bumped to version 2 with `fallbackToDestructiveMigration()`.
> Uninstall the old APK (or clear app data) before installing this build, or Room will wipe the data automatically.

## What the pet does (original)
| You do | Pet does |
|---|---|
| Tilt left/right | Slides toward the low edge, kicks its feet, squishes on the wall |
| Back to flat | Shakes itself off and walks back to the center |
| Turn upside down | Tumbles, wide scared eyes, sweat drop |
| Shake (4+ jolts in 1 s) | Dizzy: swirly eyes, orbiting stars, wobbly walk |
| Keep shaking | Dizziness hits 70+: mood and energy drop every 3 s. Face turns green at 90+ |
| Leave it | Dizziness drains slowly |

## Stage 1: Core Loop additions

### A) Interactive feeding
- Tap **Feed** to open a food-panel overlay (horizontal RecyclerView of food cards).
- Drag a food card onto the pet or tap it to auto-float it to the pet's mouth.
- 3-bite eating animation (Milk: glass level drops). Hearts float up after eating.
- After the last bite: hunger up, mood +3, XP +5, item consumed.
- If hunger >= 95 the pet turns its head away and refuses (nothing consumed).
- Tilt and shake are paused during eating.
- Foods: Berry (lv1), Rice Ball (lv1), Apple (lv2), Milk (lv3), Cake (lv4).

### B) Sleep and rest
- When energy < 20 the pet shows half-closed tired eyes and yawns every few seconds. The Sleep button pulses.
- Tap **Sleep** to dim the screen, show a Canvas bed, closed eyes, floating Zzz bubbles.
- Sleep persists across app close (`isSleeping` + `sleepStart` stored in Room). Energy refills ~12/hr from timestamps.
- Feed, Play, Bath are disabled while sleeping (Toast: "Shh, Mochi is sleeping").
- Sleep button becomes **Wake**. Waking before energy 50 costs mood -5.
- Hard shake wakes the pet and applies dizziness.
- `PetSessionService` shows "Mochi is sleeping..." notification and ticks energy while the app is open.

### C) XP and levels (1-10)
- XP to next level = 50 * current level. Stage derived: lv1-2 = stage 0, lv3-5 = stage 1, lv6+ = stage 2.
- XP sources: feed +5, play +10, bath +8, sleep finish +6.
- Level badge and XP bar shown under the title on the main screen.
- Level-up: sparkle burst + "Level Up!" banner in PetView, +10 coins, entry in Growth History.
- Shop locks items above the pet's level ("Unlocks at level N").

### D) Hygiene / bath
- Cleanliness stat (0-100). Decays 2.5/hr; drops 5 per play session.
- Below 30: dirt spots + stink lines appear; mood decays 1.5x faster.
- Tap **Bath** (was Clean) to enter bath mode: Canvas tub with soap bubbles.
- Scrub by dragging a finger across the pet. Dirt fades with progress.
- Rinse animation -> pet shakes dry -> sparkles. Cleanliness = 100, mood +8, XP +8.
- Tap **Done** to exit early keeping partial cleanliness.

## Weather scenes (Open-Meteo, no API key)
Clear day, clear night (stars + moon), cloudy, rain, thunderstorm (flashes), snow, fog.
Only the window/sky changes; the room stays the same. The last result is cached for 30 min and
used offline. Set the weather latitude/longitude in **Settings**.

## Where each syllabus topic lives
| Topic | File |
|---|---|
| Activities, lifecycle, view binding | `ui/*.java` (sensor register/unregister in `MainActivity.onResume/onPause`) |
| Explicit intents | `MainActivity` -> Shop / Bag / Stats / Settings |
| Implicit intent | `MainActivity.share()` (screenshot via FileProvider) |
| RecyclerView + adapters + view holders | `ui/ItemAdapter.java`, `LogAdapter.java`, `RowHolder.java`, `ui/FoodMenuAdapter.java` |
| Custom view | `view/PetView.java` (room, sky, physics, pet, all on Canvas) |
| Custom drawable/view for food | `ui/FoodSpriteView.java` (Canvas-drawn food sprites in the food menu) |
| Service | `system/PetSessionService.java` (foreground sleep session, runs until pet wakes) |
| Broadcast receivers | `system/BootReceiver.java`, `HungerAlarmReceiver.java`, `PowerReceiver.java` (Boot + Alarm in manifest, Power in code) |
| Content provider | `system/PetProvider.java` (exposes name, hunger, energy, mood, coins, stage, level, cleanliness, isSleeping) |
| Permissions | POST_NOTIFICATIONS requested in `MainActivity` |
| Networking + JSON | `weather/WeatherRepository.java` |
| Database | Room v2: `data/*.java` (Pet entity + level/cleanliness/isSleeping/sleepStart; Item + minLevel) |
| Sensors | `sensor/SensorHelper.java` |
| State machine | `view/PetView.java` PetState enum: IDLE, TIRED, EATING, SLEEPING, BATHING |
| XP / level system | `data/PetRepository.java` addXp(), stageForLevel() |
| Decay from timestamps | `data/PetRepository.java` decay() — no always-running service needed |

Everything is plain Java (no Kotlin): Room + annotation processor, an ExecutorService (`Bg.java`) for database/network work, and a Handler to return to the UI thread.

Design note: no always-running service for the pet clock. Stats decay is computed from `lastUpdated`
in `PetRepository.decay()` whenever the pet is loaded. Alarms only fire notifications.

## Still to do
- **Firebase** (Auth + Firestore backup): create a Firebase project, add `google-services.json` to `app/`,
  add the Google Services plugin and `firebase-auth` / `firebase-firestore`, then replace the stub in
  `LoginActivity.java` and upload `Pet` + `Item` rows from `PetRepository`. Left out so the project builds without a Firebase account.
- **Your artwork:** the pet is drawn with Canvas shapes so the project runs with zero assets.
  To use your own sprites, put layer PNGs (body, shell, eyes, arms, feet, hats) in `res/drawable-nodpi/`
  and replace the body of `drawPet()` with `canvas.drawBitmap(...)` calls inside the same translate/rotate/scale block,
  so the physics keeps working.
- The default pet is inspired by a Pokemon character. Fine for coursework, but redraw it as your own creature if you ever publish.
- Content provider is exported without a permission (demo). Add a `readPermission` for production.

## Test checklist (emulator)

| What to test | How |
|---|---|
| **Tired state** | In emulator, run `adb shell am broadcast -a com.example.pocketpet.DEBUG_SET_ENERGY --ei value 15 com.example.pocketpet` or wait for energy to drop. Pet shows half-closed eyes and yawns. Sleep button pulses. |
| **Sleep persistence** | Tap Sleep. Press Home (or kill task). Re-open the app. Pet should still be in the bed with Zzz bubbles and energy should have refilled slightly. |
| **Early wake penalty** | Put pet to sleep. Wait until energy is between 1 and 49. Tap Wake. Check that mood decreased by 5. |
| **Shake-to-wake** | While sleeping, use Extended Controls -> Virtual sensors -> Move device. Set rapid accelerometer spikes (hold X max, release quickly 4+ times in 1 s). Pet should wake dizzy. |
| **Feeding** | Tap Feed -> select Berry. Drag it to the pet or just tap it. Watch 3-bite animation, hearts, hunger bar rising. |
| **Full refusal** | Manually raise hunger close to 100 (feed multiple times), then try to feed again. Pet should turn its head. |
| **Bath** | Tap Bath. Scrub finger across pet. Watch dirt fade, progress bar fill. Let it complete for full bonus or tap Done early. |
| **Dirty appearance** | Wait until cleanliness drops below 30, or use DB inspector to set it low. Brown spots and stink lines should appear. |
| **Level-up** | Feed, play, and bath repeatedly. XP bar fills under title. On level-up: sparkle burst + banner appears, coins + 10. Check Growth History for the log entry. |
| **Shop locks** | At level 1, open Shop. Milk (lv3), Cake (lv4), Leaf Hat (lv5) should be greyed out with "Unlocks at level N". |
| **Back buttons** | Open Shop, Bag, Stats. Each has a Back button at the top. Tapping it should return to the main screen. |
| **Tilt** | Extended Controls -> Virtual sensors -> Accelerometer. Drag the device model left/right to simulate tilt. Pet slides to the low edge. |
| **Dizzy** | Rapidly toggle X accelerometer from max to min 4+ times in 1 s. Swirly eyes and orbiting stars appear. |
