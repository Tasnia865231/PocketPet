# Pocket Pet (Android, Java)

A 2D Ghibli-style virtual pet. The phone's motion controls how the pet physically behaves,
and the window background changes with the real weather.

## Open and run
Built for **Android Studio Dolphin 2021.3.1** (AGP 7.3.1, Gradle 7.5.1, compileSdk 33, Java 8 source level).
1. Android Studio -> **Open** -> select this `PocketPet` folder.
2. Let Gradle sync (it downloads Gradle 7.5.1 and the dependencies, so you need internet once).
3. Run on a **real phone** (the emulator can fake tilt via Extended Controls -> Virtual sensors, but shake is easier on a device).

## What the pet does
| You do | Pet does |
|---|---|
| Tilt left/right | Slides toward the low edge, kicks its feet, squishes on the wall |
| Back to flat | Shakes itself off and walks back to the center |
| Turn upside down | Tumbles, wide scared eyes, sweat drop |
| Shake (4+ jolts in 1 s) | Dizzy: swirly eyes, orbiting stars, wobbly walk |
| Keep shaking | Dizziness hits 70+: mood and energy drop every 3 s. Face turns green at 90+ |
| Leave it | Dizziness drains slowly |

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
| RecyclerView + adapters + view holders | `ui/ItemAdapter.java`, `LogAdapter.java`, `RowHolder.java` |
| Custom view | `view/PetView.java` (room, sky, physics, pet, all on Canvas) |
| Service | `system/PetSessionService.java` (foreground "sleeping" session) |
| Broadcast receivers | `system/BootReceiver.java`, `HungerAlarmReceiver.java`, `PowerReceiver.java` (Boot + Alarm in manifest, **Power in code**) |
| Content provider | `system/PetProvider.java` |
| Permissions | POST_NOTIFICATIONS requested in `MainActivity` |
| Networking + JSON | `weather/WeatherRepository.java` |
| Database | Room: `data/*.java` |
| Sensors | `sensor/SensorHelper.java` |

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
