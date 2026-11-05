# DWM Sync

Auto-syncs workouts from **Samsung Health → Health Connect → Devs Who Move** (`POST /api/activities`).

## Install
1. Download `dwm-sync.apk` from **[Latest release](https://github.com/yogeshpaliyal/dwm-sync/releases/tag/latest)**. Every push to `main` rebuilds it. You can also open this folder in Android Studio and run it from there.
2. Install the APK on your phone. You'll need to allow installs from your browser or file manager. New builds install over the old one, because every build is signed with the same `debug.keystore` from this repo.

## First-run setup (shown once)
1. **Welcome**: explains what the app does.
2. **Health Connect**: grant access to Exercise and Distance, plus background and past-data access if offered.
3. **Sources**: the app lists every app that has written workouts to Health Connect in the last 90 days, with its icon, workout count and devices. Tick the ones to sync and use the arrows to set priority. Samsung Health is ticked by default.
4. **Account**: enter the site URL and an agent token (`activities:write`, created under **Your agents** on the site).
5. **Older workouts**: choose *Only new from now on*, the last 7, 30 or 90 days, the last 12 months, or **Pick dates…** for a custom range.

When you tap Finish, auto-sync is scheduled and the import you chose starts.

## Screens
- **Home**: shows the status, when the last run happened, when the next auto-sync is due, and buttons for **Sync now** and **Import older data**. Below that is the list of every workout handled, each with its source app and device, title, date, type, duration, distance, Synced/Duplicate/Skipped/Failed status and upload time (or the reason it was skipped or failed).
- **Import older data**: pick a preset or a custom date range. Workouts that were already synced are skipped.
- **Settings**: replace the URL or token, view Health Connect permissions, turn auto-sync on or off, choose the interval (1, 3, 6 or 12 h), change the workout sources and their priority, clear the history, or run setup again.

## How it works
- Auto-sync is a periodic WorkManager job. It needs a network connection and backs off exponentially on errors.
- Each auto-sync reads `ExerciseSessionRecord` starting one day before the last synced session. It never looks earlier than the time setup finished, because older data comes in through imports. Distance is the sum of `DistanceRecord` over each session.
- Imports are a separate one-off job for the window you picked. They don't move the auto-sync position.
- **Duplicates**: two workouts count as the same if they have the same sport and overlap by at least half of the shorter one. If a matching workout from another app is already uploaded, or a higher-priority copy is in the same batch, the workout is marked *Duplicate of "…" from App* and not sent.
- The history is kept in `files/sync_log.json`, up to 2,000 entries. Workouts already marked Synced aren't sent again.
- The `Idempotency-Key` is `hc-<Health Connect record id>`, so retries and overlapping runs never create duplicates on the server.
- Responses are handled like this:
  - 2xx: marked Synced.
  - 409: marked Skipped (the workout changed after it was uploaded).
  - Other 4xx: marked Failed, with the error shown.
  - 401/403: the sync stops and asks for a new token.
  - Network errors, 429 and 5xx: retried later.
- The token is stored in EncryptedSharedPreferences, and backups are disabled.

## Sport mapping
| Health Connect | Devs Who Move |
|---|---|
| Running, Treadmill | Running |
| Biking, Stationary bike | Cycling |
| Tennis | Tennis |
| Walking | Walking |
| Pool / Open-water swim | Swimming |
| Strength training, Weightlifting | Strength |
| Everything else (hike, yoga, badminton, cricket…) | Other, with the type kept in the notes |

## Notes
- Samsung Health only writes to Health Connect when Samsung Health itself syncs. Opening it now and then keeps the data fresh.
- Device names come from each app's Health Connect metadata. Many apps leave them blank, so only the app name is shown.
- Without past-data access, Health Connect only shares about the last 30 days, so older imports come back empty.
- If the status says "blocked the read", your Health Connect version doesn't allow background reads. Tap **Sync now** while the app is open.
