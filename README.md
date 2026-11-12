# PocketPet (Android, Java)

PocketPet is a cozy 2D virtual pet game. You look after a small pet called Mochi: feed him, play with him, bathe him and put him to sleep. The phone's motion changes how Mochi behaves, and the window of his room shows the real weather. Players can also sign in, see which friends are online, invite them to a three-round Tic-Tac-Toe match and chat with floating bubbles while they play.

Everything is plain Java. Mochi, his room, the food, the baths and all the games are drawn on a Canvas, so the project runs without any image files.

## Features at a glance

| Area | What you get |
|---|---|
| Pet care | Feed, Play, Bath and Sleep, with four stat bars: Hunger, Mood, Energy and Cleanliness |
| Growth | XP, levels 1 to 10, coins, a daily gift, and a Growth History |
| Motion | Tilt, flip and shake change how Mochi moves and feels |
| Weather | The window shows the real weather (Open-Meteo, no API key) |
| Voice | Tap Mochi, say something, and he repeats it in a cute high voice |
| Shop and Bag | Food and Dresses, with items that unlock at higher levels |
| Mini-games | Tic Tac Toe, Minesweeper duel, Mochi Jump, Hide and Seek, Catch the Treats |
| Notifications | Care alerts, a daily summary, a sleep session card and action buttons |
| Widget | A home-screen widget with Mochi's face and bars |
| Accounts | Register and log in with Firebase Authentication |
| Community | Mochi Friends (online or offline) and a live What is happening feed |
| Online match | Invite a friend, play 3 synchronized rounds, the winner gets 50 coins |
| Match chat | Short text and emoji messages that float up from the player cards |
| Airplane mode | A pastel banner appears on any screen when airplane mode is switched on |

## Open and run

Built for **Android Studio Dolphin 2021.3.1** (AGP 7.3.1, Gradle 7.5.1, compileSdk 33, Java 8 source level).

1. Android Studio -> **Open** -> select the `PocketPet` folder.
2. Let Gradle sync (it downloads Gradle and the dependencies, so you need internet once).
3. Put your own `app/google-services.json` in place (see `app/google-services.example.json` and **FIREBASE_SETUP.md**).
4. Run on a **real phone**. Tilt can be faked on an emulator (Extended Controls -> Virtual sensors), but shake and the two-player match are much easier on devices.

> The Room database is versioned with `fallbackToDestructiveMigration()`. If you install over an old build and see odd data, uninstall the old app (or clear its data) first.

## Pet care

### Feeding
- Tap **Feed** to open a panel of food cards.
- Drag a card onto Mochi, or tap it to float the food to his mouth.
- He eats in three bites, hearts float up, hunger rises, mood +3, XP +5, and the item is used up.
- If hunger is 95 or more he turns his head away and refuses.
- Foods: Berry (lv1), Rice Ball (lv1), Apple (lv2), Milk (lv3), Cake (lv4).

### Sleep and rest
- When energy is low Mochi looks tired, yawns, and the Sleep button pulses.
- **Sleep** dims the screen and shows a bed, closed eyes and floating Zzz bubbles.
- Sleep continues when the app is closed. Energy refills from timestamps (from empty to full takes about 30 minutes).
- Feed, Play and Bath are disabled while he sleeps. Waking him too early costs mood, and a hard shake wakes him up dizzy.
- When he is fully rested the notification "Mochi is awake!" is sent.

### XP and levels
- XP to the next level is 50 times the current level. Levels go from 1 to 10.
- XP sources: Feed +5, Play +10, Bath +8, finishing a sleep +6.
- A level-up shows a sparkle burst and a banner, gives 10 coins and adds a line to the Growth History.
- The Shop greys out items above Mochi's level with "Unlocks at level N".
- Every new day Mochi brings a small daily gift (10 coins and a mood boost).

### Bath
- Cleanliness (0 to 100) falls slowly over time and drops after each play session.
- Below 30, dirt spots and stink lines appear and mood falls faster.
- **Bath** opens a tub with soap bubbles. Scrub Mochi with a finger, then he rinses, shakes dry and sparkles.
- A full bath sets cleanliness to 100, mood +8, XP +8. **Done** leaves early and keeps the partial result.

### Motion
| You do | Mochi does |
|---|---|
| Tilt left or right | Slides to the low side, kicks his feet, squishes on the wall |
| Back to flat | Shakes himself off and walks back to the center |
| Turn upside down | Tumbles with wide scared eyes |
| Shake (4+ jolts in 1 second) | Dizzy: swirly eyes, orbiting stars, a wobbly walk |
| Keep shaking | Mood and energy start to drop; his face turns green at the end |

Tilt, shake and voice can each be switched off in **Settings**.

### Weather scenes
Clear day, clear night (stars and moon), cloudy, rain, thunderstorm, snow and fog. Only the window changes. The weather comes from Open-Meteo and the last answer is cached for 30 minutes and used offline. The location can come from the phone (coarse location) or from latitude and longitude typed in **Settings**.

### Mochi voice
Tap Mochi (awake, not eating or bathing). He says "I'm listening!", records what you say and repeats it in a high-pitched voice. Nothing is saved or sent, and the microphone is only open while he listens. Switch it off in Settings.

## Mini-games (Play button)

Play opens `ui/GameActivity`: choose a game, choose Medium or Hard, play, then Retry or go Home.

- Games: Tic Tac Toe, Minesweeper duel, Mochi Jump, Hide and Seek (a shell game) and Catch the Treats.
- The in-game Menu opens a pause card (Resume, Retry, Games menu).
- `game/SoundFx` creates all sound effects and music at run time (no audio files) and has an on/off toggle.
- Every board is a Canvas view that implements `GameBoard`. To add a game, write a view that implements `GameBoard`, add a card in `activity_game.xml` and a case in `GameActivity`.
- Each finished game gives the normal Play reward.

## Online features

All online features use the free Firebase Spark plan. No Cloud Functions and no push messages are used.

### Accounts and profile
Register or log in on the first screen (Firebase Authentication). A permanent player profile (name, level, coins, dates) is stored in Cloud Firestore. Log out from **Settings**.

### Community
- **Mochi Friends:** every player with an online or offline status, and how many are online. You can invite an online friend.
- **What is happening:** a live feed of events such as level-ups.
- Online status follows the app coming to the front and going to the background, and goes offline when the connection is lost.

### Online Tic-Tac-Toe
1. Invite a friend from Community. They can accept or reject.
2. Both phones open the match screen. Player 1 is X (mint) and Player 2 is O (pink).
3. A match has 3 synchronized rounds with a scoreboard and a final result screen.
4. The winner gets 50 coins, and the reward can only be claimed once.
5. If a player steps away, the other sees a waiting countdown, and the match continues when they reconnect.
6. Every finished match is saved as one record in Cloud Firestore.

### Match chat
While two players are in a match, each can send the other a short message.

- A speech-bubble button opens a compact bar with a text field, a Send button and a scrollable row of 14 emoji that send with one tap. The phone keyboard's emoji work too.
- Messages are trimmed, empty ones are not sent, control and invisible characters are removed, the maximum is 60 characters, and there is a cooldown of about 1.5 seconds between sends.
- A new message floats up from the sender's player card (X on the left, O on the right) with the sender's name, bobs gently, stays about 4 seconds and fades. At most 3 bubbles show at once, and your own messages float from your side too.
- The floating layer never takes a touch, so the board stays tappable. When the keyboard opens, the turn text and big letters hide so the board stays big enough.
- Chat works while playing, between rounds and on the final result screen.
- Only messages newer than the moment the screen opened are listened to (a limited query), so old messages never replay and the history is never downloaded.
- If the connection is lost, sending is blocked and the bar shows "Reconnecting...". Nothing is queued.
- When a player leaves a finished or cancelled match, that match's chat is deleted. The other player's final screen is not disturbed.

Code: `firebase/ChatManager.java` (send, listen, clean, delete) and `ui/ChatBubbleLayer.java` (floating bubbles). The wiring is in `ui/MultiplayerGameActivity.java`.

### Airplane mode banner
`system/AirplaneWatcher.java` is a broadcast receiver installed once when the app starts. When airplane mode is switched on, a pastel banner with a paper plane and a cloud appears on whichever screen is open. It stays about 6.5 seconds, bobs gently and never takes a tap. Switching airplane mode off removes it.

## Firebase data map

| Database | Path | Used for |
|---|---|---|
| Cloud Firestore | `players/{uid}` | Permanent player profile |
| Cloud Firestore | `matches/{gameId}` | One record per finished online match |
| Realtime Database | `status` | Online or offline status |
| Realtime Database | `activities` | The What is happening feed |
| Realtime Database | `invites`, `playing` | Invitations and who is playing |
| Realtime Database | `games/{gameId}` | The live match: moves, turns and rounds |
| Realtime Database | `claims` | The 50-coin reward (once) |
| Realtime Database | `chats/{gameId}/{messageId}` | Match chat messages |

### Security rules
- `firebase-rules/firestore.rules` is the Cloud Firestore rules file.
- `firebase-rules/database.rules.json` is the Realtime Database rules file. **Do not edit it by hand.** It is generated by `firebase-rules/tests/gen_database_rules.py`: edit the generator, run it, then publish the new JSON.
- To publish: Firebase Console -> Build -> Realtime Database -> Rules -> paste the file -> Publish. Do the same under Firestore Database -> Rules for `firestore.rules`.
- Chat rules: only the two players of a match can read or write its chat, messages can be created but never edited, the sender must be the signed-in user, text is 1 to 60 characters, `createdAt` must be the server time, no other fields are allowed, no chat for a missing or cancelled game, and either player can delete the chat.
- Test the rules offline (no Firebase needed):

```
cd firebase-rules/tests
npm install
python gen_database_rules.py
node rules.test.js
```

All tests must pass before you publish. See **FIREBASE_SETUP.md** for the full file map, setup steps and troubleshooting.

## Android components

### Service (`system/PetSessionService`)
- **Bound:** `MainActivity` binds to it while the app is open. It ticks every few seconds, applies time-based changes, runs `PetWatcher` and pushes the fresh pet to the screen.
- **Started and foreground:** while Mochi sleeps with the app closed, it shows a "Mochi is sleeping" notification with an energy bar, books the wake-up alarm and stops itself when he is rested.
- There is no always-running background service. Stats decay is computed from timestamps in `PetRepository.decay()`, and alarms plus receivers do the periodic checks.

### Broadcast receivers
| Receiver | Registered | Purpose |
|---|---|---|
| `BootReceiver` | manifest | After reboot: book alarms, resume a sleep in progress |
| `HungerAlarmReceiver` | manifest | Daily summary or hungry reminder at the hour chosen in Settings (default 6 PM) |
| `CareCheckReceiver` | manifest | About every 15 minutes: mood, hunger and energy check |
| `WakeAlarmReceiver` | manifest | The exact minute Mochi is rested: "Mochi is awake!" |
| `ActionReceiver` | manifest (not exported) | The buttons in notifications: Feed now, Play, Sleep now |
| `PowerReceiver` | code | Charger plugged in: more energy |
| `SystemEventsReceiver` | code | Battery low (Mochi sleepy), new day (daily gift) |
| `AirplaneWatcher` | code, app wide | Airplane mode on or off: the kawaii banner |
| App events (`PetEvents`) | code | PET_WOKE, LEVEL_UP, PET_CRITICAL, PET_CHANGED |

### Content provider (`system/PetProvider`, authority `com.example.pocketpet.provider`)
- URIs `/pet`, `/items`, `/items/#` and `/log`.
- Reading needs `READ_PET`, and changing data needs `WRITE_PET` (a signature permission), both defined in the manifest.
- Every repository change calls `notifyChange()`, so the main screen, Shop and Bag refresh live through a `ContentObserver`. `PetWidget` reads it through `PetClient`.
- `FileProvider` (authority `com.example.pocketpet.files`) is a separate provider that only shares the screenshot from the Share button.

### Notifications (`system/NotificationHelper`)
Channels for alerts, wake-ups, milestones and the sleep session, grouped under one summary. They use custom bubble-font layouts with Mochi's face, and the buttons fire `ActionReceiver`. Tapping a notification opens the right screen with a proper Back stack. Messages include "Mochi is hungry!", "Mochi wants to play!", "Mochi needs to sleep", "Mochi is awake!" and level-ups.

### Widget (`system/PetWidget`)
A resizable home-screen widget that shows Mochi's face (sleeping, sad, hungry or happy), his name, level and bars. Tapping it opens the game. Android refreshes it about every 30 minutes, and the app refreshes it when Mochi changes.

### Permissions
- Asked at run time with a friendly dialog first, and the app still works if denied: `POST_NOTIFICATIONS`, `ACCESS_COARSE_LOCATION`, exact alarms and `RECORD_AUDIO`.
- Normal: `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `VIBRATE`.
- Defined by the app: `READ_PET` and `WRITE_PET`.

### Care loop
Alarm -> `CareCheckReceiver` -> `PetWatcher` -> notification with "Feed now" -> `ActionReceiver` -> repository -> provider `notifyChange()` -> open screens and the widget update. A level-up sends the `LEVEL_UP` broadcast and a notification.

## Where each topic lives

| Topic | File |
|---|---|
| Activities, lifecycle, View Binding | `ui/*.java` (sensors register and unregister in `MainActivity.onResume/onPause`) |
| Explicit intents | `MainActivity` -> Shop, Bag, Stats, Settings, Games, Community; Community -> match screen |
| Implicit intent | `MainActivity.share()` (screenshot sent with `ACTION_SEND` through `FileProvider`) |
| RecyclerView, adapters, view holders | `ItemAdapter`, `StoreAdapter`, `FoodMenuAdapter`, `LogAdapter`, `PlayerStatusAdapter`, `ActivityAdapter`, `RowHolder` |
| Custom views | `view/PetView.java`, `ui/FoodSpriteView.java`, `game/*View.java`, `game/MatchBoardView.java`, `ui/ChatBubbleLayer.java` |
| Service | `system/PetSessionService.java` |
| Broadcast receivers | `system/*Receiver.java`, `system/AirplaneWatcher.java` |
| Content provider | `system/PetProvider.java`, client in `system/PetClient.java` |
| Widget | `system/PetWidget.java`, `res/xml/pet_widget_info.xml` |
| Alarms | `system/AlarmScheduler.java` |
| Permissions | `ui/PermissionFlow.java` |
| Networking and JSON | `weather/WeatherRepository.java` |
| Database | Room (version 4): `data/*.java` (Pet, Item, GrowthLog) |
| Sensors | `sensor/SensorHelper.java` |
| State machine | `view/PetView.java` (IDLE, TIRED, EATING, SLEEPING, BATHING) |
| XP and levels, decay | `data/PetRepository.java` (`addXp()`, `decay()`) |
| Firebase accounts and profile | `firebase/FirebaseAuthManager.java`, `firebase/PlayerProfileRepository.java` |
| Presence and feed | `firebase/PresenceManager.java`, `firebase/ActivityFeed.java` |
| Online match | `firebase/MatchManager.java`, `firebase/MatchState.java`, `game/MatchLogic.java`, `ui/MultiplayerGameActivity.java` |
| Chat | `firebase/ChatManager.java`, `ui/ChatBubbleLayer.java` |
| Background work | `Bg.java` (an ExecutorService for database and network work, and a Handler to return to the UI thread) |

## Test checklist

### Single phone
| What to test | How |
|---|---|
| Tired state | Wait for energy to drop, or on an emulator run `adb shell am broadcast -a com.example.pocketpet.DEBUG_SET_ENERGY --ei value 15 com.example.pocketpet`. Mochi yawns and Sleep pulses. |
| Sleep persistence | Tap Sleep, press Home or kill the app, reopen. Mochi is still in bed and energy has risen. |
| Feeding and refusal | Feed Berry (drag or tap). When hunger is almost full, feeding again makes him turn away. |
| Bath | Scrub until the dirt fades, or tap Done early. |
| Level-up | Feed, play and bathe repeatedly. Check the banner, +10 coins and the Growth History line. |
| Shop locks | At level 1, Milk, Cake and Leaf Hat are greyed out with "Unlocks at level N". |
| Tilt and shake | Tilt the phone, then shake it 4 or more times in a second. |
| Airplane banner | With the app open, switch airplane mode on. The banner stays about 6 seconds. |
| Notifications and widget | Use the buttons in a notification, and add the widget to the home screen. |

### Two phones (online)
| What to test | How |
|---|---|
| Invitation | Send an invite from Community, then accept it on the other phone. |
| Match | Moves appear instantly on both phones, 3 rounds, the winner gets 50 coins once. |
| Chat | Messages and emoji arrive instantly in both directions, and bubbles float from the correct side. |
| Bubble limits | At most 3 bubbles show, each fades after about 4 seconds, and the board stays tappable. |
| Keyboard | With the keyboard open, the board is still visible and usable. |
| No replay | Leave and re-enter the match. Old messages do not appear again. |
| Offline | With airplane mode on, sending is blocked with a message, and nothing is queued. |
| Rules | A third account cannot read the chat (also covered by the offline rule tests). |
| Cleanup | After both players leave, the match's `chats` branch is gone in the Realtime Database. |

