# Mochi: Firebase Setup and Maintenance Guide

This guide explains how Firebase is used in Mochi, where the code lives, and how to test, inspect and fix things.
You do not need to know Firebase to follow it.

---

## 1. The three Firebase services and what each one does

| Service | Plain-English job | What Mochi stores there |
|---|---|---|
| **Authentication** | Checks who a player is (email + password) and gives each player a unique **UID**. | Accounts only. No game data. |
| **Cloud Firestore** | Permanent cloud storage, like a filing cabinet. Stays forever. | One profile per player: `uid`, `displayName`, `level`, `totalCoins`, `createdAt`, `lastLoginAt`. |
| **Realtime Database** | A live noticeboard for small things that change constantly and must reach everyone instantly. | Online/offline status, last seen, and the community activity feed. |

Useful words:
- **UID**: a unique, never-changing id Firebase gives every account (like `QfENZfH6O4Y4...`). It names each player's records so players can never be mixed up.
- **Collection / document / field** (Firestore): drawer / folder / a line on the folder. `players` is a collection, one player's profile is a document, `level` is a field.
- **Listener**: code that watches data and runs by itself whenever the data changes.
- **Security rules**: instructions stored on Firebase's servers that approve or refuse every read and write. They are the real protection.

### How the services work together

1. A player **registers** -> Authentication creates their UID.
2. Firestore creates their profile document `players/{UID}`.
3. On login, Realtime Database marks them **online** (and **offline** automatically when they disconnect).
4. When they earn coins or level up, Firestore updates their permanent profile.
5. A small event ("Bunny reached Level 5!") is added to Realtime Database.
6. Everyone viewing the Friends screen sees it appear by itself.

The game still plays from the phone's own storage (fast, works offline). Firebase keeps the cloud copy and the community in sync.

---

## 2. Where the Firebase code is

**All Firebase code is in one folder:** `app/src/main/java/com/example/pocketpet/firebase/`

| File | What it does | Touches |
|---|---|---|
| `FirebaseConfig.java` | Holds the Realtime Database web address. | Realtime Database |
| `FirebaseAuthManager.java` | Register, log in, log out, "who is logged in", friendly error messages. | Authentication |
| `PlayerProfileRepository.java` | Creates/loads the player profile, saves level + coins, live coin listener. | Firestore `players` |
| `PresenceManager.java` | Online/offline status, `onDisconnect()`, live player list. | Realtime Database `status` |
| `ActivityFeed.java`, `ActivityEvent.java` | Post and watch community events. | Realtime Database `activities` |
| `PlayerStatus.java` | One row of the player list (data only). | n/a |

Other files that only *call* the code above (no Firebase code of their own):

| File | Why it calls Firebase code |
|---|---|
| `PocketPetApp.java` | Tells `PresenceManager` when the app comes to the front or leaves the screen. |
| `ui/LoginActivity.java` | Login/register screen; starts profile sync and presence after login. |
| `ui/SettingsActivity.java` | Log out button (marks the player offline first). |
| `ui/CommunityActivity.java`, `CommunityTabs.java`, `PlayerStatusAdapter.java`, `ActivityAdapter.java` | The Community screen: two tabs (Mochi Friends / What is happening), like the Shop. |
| `data/PetRepository.java` | In `save()`: sends level/coins to Firestore and posts level/coin feed events. |
| `ui/MainActivity.java` | Posts the "completed a mini-game" event. |

### Where to change things

- **Player profile fields** (add or rename a field): `PlayerProfileRepository.java` (`createProfile`, `pushProgress`), then `firebase-rules/firestore.rules`, then publish the rules.
- **What triggers feed events**: level-ups in `PetRepository.save()`, mini-games in `ui/MainActivity.java` (the game-result handler). The feed sentences: `ui/ActivityAdapter.java`.
- **New kind of community event**: add a type in `ActivityEvent.java`, a `post...()` in `ActivityFeed.java`, a sentence in `ActivityAdapter.java`, and allow the type in `database.rules.json`.
- **Notification thresholds** (hungry below 40%): `system/PetWatcher.java`. **"Didn't have a nice sleep" threshold** (sleep bar below 40%): `PetRepository.POOR_SLEEP_BELOW`.
- **Match chat** (v22): messages live in Realtime Database `chats/{gameId}/{messageId}` (never inside `games/`). Code: `firebase/ChatManager.java` (send, listen, clean, delete), `ui/ChatBubbleLayer.java` (floating bubbles), wiring in `ui/MultiplayerGameActivity.java`. Rules: edit `firebase-rules/tests/gen_database_rules.py` (`rules_chats`), run it, then publish `database.rules.json`. Firestore is not used by chat.
- **Login rules** (password length, name length): `ui/LoginActivity.java` (`submit()`), and error messages in `FirebaseAuthManager.java`.
- **Look of the Community screen**: `res/layout/activity_community.xml`, `item_player.xml`, `item_activity.xml`; tab colours in `ui/CommunityTabs.java`; card colours in `res/drawable/bg_pcard_*.xml`.

---

## 3. Configuration (and what must stay private)

- `app/google-services.json`: connects the app to YOUR Firebase project. It is in `.gitignore`. Get it from Firebase Console -> Project settings (gear icon) -> General -> Your apps -> **google-services.json**.
- `app/google-services.example.json`: a copy **without** real values, showing the shape.
- `FirebaseConfig.java` -> `DATABASE_URL`: the Realtime Database address (Console -> Realtime Database -> Data tab, top of the page). It is an address, not a password.
- **Never put in the app:** Admin SDK keys, service account files, or private keys. None exist in this project.

The package name `com.example.pocketpet` must match the Android app registered in Firebase.

### Your Firebase project

- Project: **Mochi Pet Game** (`mochi-pet-game`), plan **Spark** (free)
- Firestore: `(default)` database, location **asia-south1 (Mumbai)**
- Realtime Database: location **Singapore (asia-southeast1)**

Locations cannot be changed after creation.

---

## 4. Security rules

Rules live in `firebase-rules/` and must be **pasted into the Firebase Console and published**. Editing the file in the project does nothing until you do.

| File | Where to paste it |
|---|---|
| `firestore.rules` | Console -> Firestore Database -> **Rules** tab -> Publish |
| `database.rules.json` | Console -> Realtime Database -> **Rules** tab -> Publish |

To paste: click in the editor, **Ctrl+A**, **Delete**, **Ctrl+V**, **Publish**.

What they enforce:
- Only logged-in players can read anything.
- Players can only write **their own** records (checked with their UID).
- Level can only go up; coins can only rise by a limited amount per save.
- Status is only `online`/`offline`, with times set by Firebase's clock.
- Feed events can only be created (never edited or deleted), under your own UID.
- Everything not listed is closed. Never switch a database to test mode.

**Known limit:** coins and levels are earned on the phone, so a determined cheater could still inflate their own numbers in small steps, and could post fake feed messages. Fully preventing that needs a trusted backend (Cloud Functions, which needs the paid Blaze plan). Admin credentials would live only on that server, never in the app. This is a possible future upgrade.

You can try rules without the app: Rules tab -> **Rules playground**.

---

## 5. Data layout

**Firestore**
```
players (collection)
  {UID} (document)
    uid, displayName, level, totalCoins, createdAt, lastLoginAt
```

**Realtime Database**
```
status
  {UID}: { state: "online"|"offline", lastChanged: <time>, displayName }
activities
  {autoId}: { uid, type: "level"|"minigame", value, coins (mini-game events only), createdAt }
```
`level`: value = the level reached. `minigame`: value = games just played, coins = the player's coin total afterwards.
Feed events store no names. Names are looked up by UID when the screen is drawn.
(Older `coins` events from an earlier version may still exist; delete them in the Console if you like.)

---

## 6. How to test and inspect

### Authentication
1. Run the app, tap **New here? Create an account**, register.
2. Close the app completely and reopen it: you should go straight into the game (session persists).
3. Settings -> **Log out**, then log in again.
4. Try a wrong password: you should see a friendly message.
5. Inspect: Console -> **Authentication** -> **Users**. Each account has its own long **User UID**.

### Firestore
1. Console -> **Firestore Database** -> **Data** -> `players` -> click a UID.
2. Check the six fields. Earn coins in a mini-game and refresh: `totalCoins` changes.
3. Log out and in again: level and coins stay; `lastLoginAt` gets newer, `createdAt` never changes.

### Realtime Database
1. Console -> **Realtime Database** -> **Data**.
2. `status/{UID}`: `online` while the app is open; press Home for a few seconds and it becomes `offline`.
3. `activities`: one record per community event.

---

## 7. Two-device checklist (do this when you install on your phone)

Use **two different accounts**, one on the emulator and one on the phone (or two phones).

- [ ] Both accounts appear in **Friends > Mochi Friends** with a green dot while the app is open.
- [ ] Press Home on device A (wait about 3 seconds): device B shows A as "Last seen ..." automatically.
- [ ] Reopen A: B shows A **Online** again, without leaving the screen.
- [ ] Turn on airplane mode on A: within about a minute B shows A offline.
- [ ] A earns coins: B's card for A updates by itself.
- [ ] A finishes a mini-game / levels up: the event appears in B's **What is happening** tab by itself.
- [ ] Log out and in on A: progress is unchanged.

---

## 8. Troubleshooting

Open **Logcat** in Android Studio and search for the tag in the table.

| Problem | Likely cause and fix |
|---|---|
| Build fails: "google-services.json is missing" | Put your file at `app/google-services.json` (see section 3). |
| "No internet" on login | Check the connection. Auth needs internet for login. |
| Wrong email/password message | Check spelling. Firebase gives the same message for a wrong password and an unknown email on purpose. |
| "Email already has an account" | That email is registered. Use **Log in**. |
| Profile not created / coins not saving (Logcat `PlayerProfile`, "PERMISSION_DENIED") | Firestore rules not published, or changed. Re-paste and publish `firestore.rules`. |
| Player stays "offline" or list is empty (Logcat `Presence`) | Realtime Database rules not published, or wrong `DATABASE_URL` in `FirebaseConfig.java`. |
| Feed empty (Logcat `ActivityFeed`) | Same as above: publish `database.rules.json` (it includes the `activities` branch). |
| Card shows "Total Coins: —" | That player's profile has no coin value yet (they have not opened this version) or you cannot read it: check Firestore rules. |
| Everything worked, then stopped | In the Console, check the rules tabs were not reset; check **Usage** for quota messages (Spark plan is free but limited). |
| Different account sees old pet | When a different player logs in on the same phone, the local game is cleared on purpose; their level and coins come from Firestore. |

---

## 9. Safe habits

- Change rules and code together: a new field needs both.
- Keep all Firebase code inside the `firebase/` folder; call it from elsewhere.
- Never commit `google-services.json` to a public repository.
- Never paste admin keys or service-account files into the app.

---

## 10. Online Tic-Tac-Toe (multiplayer)

Added in v21. It uses only services you already have (Authentication, Realtime Database, Firestore) and stays on the
free Spark plan: **no Cloud Functions, no push notifications (FCM), nothing new to enable.**

### The one thing you must do: publish the new rules
1. Firebase Console > **Realtime Database > Rules**: replace everything with `firebase-rules/database.rules.json` > **Publish**.
2. Firebase Console > **Firestore Database > Rules**: replace everything with `firebase-rules/firestore.rules` > **Publish**.

Until both are published, invitations and matches are refused (the game itself keeps working normally).

### Where the data lives
| Where | Path | What |
|---|---|---|
| Realtime Database | `invites/{toUid}/{fromUid}` | one invitation per pair: pending / accepted / rejected, expires after 30 s |
| Realtime Database | `playing/{uid}` | the match this player is in (blocks new invitations, lets the player rejoin) |
| Realtime Database | `games/{gameId}` | the live match: board, turn, round 1-3, scores, phase |
| Realtime Database | `claims/{gameId}/{uid}` | written once by the winner when the 50 coins are collected |
| Firestore | `matches/{gameId}` | permanent record of a finished match |
| Firestore | `players/{uid}.totalCoins` | the normal coin balance (the reward is added through the existing coin code) |

### How it works
* **Invite**: the Invite button (Community > Mochi Friends) appears only beside OTHER players who are online.
  The invited phone shows "NAME wants to play Tic-Tac-Toe with you!" with ACCEPT / REJECT, wherever the player is in the app.
* **Accept**: the invited player (O) creates the match; both phones open the same screen. The inviter is X and plays first.
* **Reject / no answer**: the inviter is told "NAME doesn't want to play with you right now." / "NAME didn't answer".
* **Three rounds**, always. The rules in `database.rules.json` check every move (turn, empty cell, real three-in-a-row,
  score, round order), so a changed app cannot cheat the board.
* **Reward**: the verified overall winner gets 50 coins, once (`claims/` can be written once, only by the real winner). A draw pays nobody.
* **Connection loss**: the match lives in the database. Closing the app or losing internet never loses it: the screen
  reopens with the same board. A friend who is away for 2 minutes ends the match (no winner, no coins).

### Honest limits of the free version
* The coins are added by the **winner's phone**, not by a trusted server. The rules make it impossible to claim a win
  that did not happen, or to claim twice, but a modified app could still raise its own coins through the older coin sync
  (the Firestore rules allow +300 per save). Moving the reward into a Cloud Function (Blaze plan) is the upgrade path.
* The `matches` record is written by a player's phone (the rules allow creating it once, never editing it).

### Testing the rules offline
`firebase-rules/tests/` contains 92 checks (cheating attempts included) that run without Firebase:
`cd firebase-rules/tests && npm install && node rules.test.js`.
