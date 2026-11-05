# DWM Sync

Auto-syncs workouts from **Samsung Health → Health Connect → Devs Who Move** (`POST /api/activities`).

## Setup
1. Get the APK: open **Actions → Build APK → latest run**, download the `dwm-sync-debug-*` artifact and unzip it. Or open this folder in Android Studio and run it.
2. Install it on your phone. You'll need to allow installs from your browser or file manager.
3. Tap **Grant access** and allow Exercise, Distance, background access and past-data access.
4. On devswhorun.dev, go to **Your agents** and create a token with `activities:write`. Paste it into the app.
5. Tap **Save & turn on auto-sync**.

## How it works
- WorkManager runs every ~3 hours when the phone is online, plus right away when you save.
- It reads `ExerciseSessionRecord` from just before the last synced session, which defaults to the last 30 days on the first run, and adds up `DistanceRecord` over each session.
- The `Idempotency-Key` is `hc-<Health Connect record id>`, so retries and overlapping runs never create duplicates.
- Response handling: 2xx counts as sent. 409 or other 4xx responses skip that session. 401/403 stop the sync, because the token has expired (90 days) or was revoked. Network errors, 429 and 5xx retry with backoff.
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
- Samsung Health only pushes to Health Connect when Samsung Health syncs. Opening Samsung Health occasionally keeps things fresh.
- If the status shows "blocked the read", your Health Connect version doesn't allow background reads. Tap **Save & sync now** while the app is open.
- The **Only Samsung Health workouts** switch filters by `com.sec.android.app.shealth`. Turn it off if you also log workouts in other apps.
