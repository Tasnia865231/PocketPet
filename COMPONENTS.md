# Android components in PocketPet (where and why)

## Service  (`system/PetSessionService`)
* **Bound** by `MainActivity` while the app is open (Binder + listener): ticks every few seconds, applies time-based changes,
  runs `PetWatcher`, pushes the fresh pet to the screen, and does `startSleep()` / `wakeUp()` for the Sleep button.
* **Started + foreground** (`mode=SLEEP`) while Mochi sleeps with the app closed: "Mochi is sleeping" notification with an
  energy progress bar, books the wake-up alarm, stops itself when energy is full (0 -> 100 takes 30 minutes).

## Broadcast receivers
| Receiver | Registered | Purpose |
|---|---|---|
| `BootReceiver` | manifest | after reboot: book alarms, resume a sleep in progress |
| `HungerAlarmReceiver` | manifest | daily summary / hungry reminder at the hour chosen in Settings |
| `CareCheckReceiver` | manifest | every ~15 min: Mochi's mood / hunger / energy check (notifications while app is closed) |
| `WakeAlarmReceiver` | manifest | exact minute Mochi is rested: "Mochi is awake!" |
| `ActionReceiver` | manifest (not exported) | the buttons inside notifications: Feed now / Play / Sleep now |
| `PowerReceiver` | code | charger plugged in: +energy |
| `SystemEventsReceiver` | code | BATTERY_LOW (Mochi sleepy), DATE_CHANGED (daily gift) |
| app events (`PetEvents`) | code | custom broadcasts PET_WOKE, LEVEL_UP, PET_CRITICAL, PET_CHANGED |

## Content provider  (`system/PetProvider`, authority `com.example.pocketpet.provider`)
* URIs `/pet`, `/items`, `/items/#`, `/log`: query, insert (log), update (pet stats, item counts), delete (log).
* Protected by `READ_PET` (normal) and `WRITE_PET` (signature) - custom permissions defined in the manifest.
* Every repository change calls `notifyChange()`; `MainActivity`, `ShopActivity` and `InventoryActivity` use a `ContentObserver`
  to refresh live; `PetWidget` reads it through `PetClient` (a ContentResolver client) like any other app would.
* `FileProvider` shares the screenshot from the Share button.

## Notifications  (`system/NotificationHelper`)
* Channels: alerts (high), wake, milestones, session (low). Grouped under one summary.
* Custom bubble-font layouts with Mochi's face; buttons in the card fire `ActionReceiver`; tapping opens the right screen with a
  proper Back stack (`TaskStackBuilder`, parent = MainActivity). Progress-bar notification while sleeping, BigText-style daily summary.
* Messages: "Mochi is awake!", "Mochi wants to play!" (mood <= 15%), "Mochi is hungry!", "Mochi needs to sleep" (energy 0), level-ups.

## Permissions
* Runtime (asked once, with a friendly `KawaiiDialog` first, with a fallback if denied): `POST_NOTIFICATIONS`,
  `ACCESS_COARSE_LOCATION` (local weather), exact alarms (`SCHEDULE_EXACT_ALARM`, via system settings).
* Normal: `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE(_DATA_SYNC)`, `VIBRATE`.
* Defined by the app: `READ_PET`, `WRITE_PET`.

## Care loop (good for the demo)
Alarm -> `CareCheckReceiver` -> `PetWatcher` -> notification with "Feed now" -> `ActionReceiver` -> repository -> provider `notifyChange()`
-> open screen + widget update; level-up fires `LEVEL_UP` broadcast + notification.
