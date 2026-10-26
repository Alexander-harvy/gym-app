# My Gym Log

A native, personal strength-training and running log for Android 8.0 (API 26) and newer. Kotlin, Jetpack Compose / Material 3, Navigation Compose, ViewModel / StateFlow, Room, DataStore and coroutines. One application module and manual dependency injection. No account, subscription, ads, analytics, backend, or INTERNET permission. Android automatic backup and device transfer are disabled.

Version 1.3 makes workout creation explicit: name your own workout, create any exercise directly from its picker, choose your own set / rep / rest targets when adding it, and save a session as a reusable workout plan. Muscle group and equipment are free text and optional, not fixed lists. It includes all 1.2 running features. Database schema 3 and JSON backup schema 2 are unchanged.

Version 1.2 adds GPS runs with local routes, distance, active duration, average pace, pause/resume, history and backup support. The Run tab joins the existing navigation. All GPS samples stay in the app's Room database. The offline route view draws your actual recorded path, not a street map. No map SDK, API key, tile downloads or external map service is used.

Version 1.1 refreshes the interface with charcoal surfaces and yellow accents, a four-tab bottom navigation bar, a dashboard with weekly / total counts, a persistent resume shortcut, searchable exercise library, and compact workout set rows. Tap a set's values to edit; tap its check button to save completion and start rest when auto-rest is enabled. Secondary actions such as reordering, notes, archiving and deletion are available from each card's labelled options menu. Progress includes accessible weekly bars with exact counts and all-time weekly totals. Dark, light and system preferences remain available.

## Open and build

Open this directory in Android Studio. Use JDK 17 or 21 (Android Studio's bundled JBR works), install Android SDK Platform 35 and accept the SDK licences. Set the SDK location in Android Studio, `ANDROID_HOME`, or an untracked `local.properties` file. Gradle downloads build dependencies on first sync; the installed app works entirely offline.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.ps1 assembleDebug testDebugUnitTest lintDebug
```

The standard `gradlew`, `gradlew.bat`, wrapper JAR and wrapper properties are included. `gradlew.ps1` is an additional Windows entry point that preserves Unicode paths when a batch invocation cannot locate the wrapper class. The Android path check is disabled because this workspace has Japanese characters; relocate to an ASCII path if a native tool has trouble with it.

If a Windows test worker reports `ClassNotFoundException` for every test class despite successful compilation, its Java argument-file parser may be misreading the Unicode output path. Pass `-PgymBuildRoot=C:/path/to/ascii-output` to place generated files there (with `app` and `My Gym Log` subdirectories). This changes only build outputs; source files remain in this project. The output root must be writable. Omit the property to use the usual project `build` directories.

On this computer, JBR 21's Unix-domain socket initialization required a short temporary socket path. If Gradle reports `Unable to establish loopback connection` with `UnixDomainSockets.connect`, use an existing, writable short temporary directory, for example:

```powershell
$env:JDK_JAVA_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'
```

Successful builds produce `app/build/outputs/apk/debug/app-debug.apk`. Install from Android Studio, transfer the APK to the phone and allow installation from that source, or run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Keep the same application ID and signing key when updating to preserve installed data. Debug APKs use the local Android debug signing key; a different machine's key can prevent an in-place update. Export a backup before changing signing keys.

## Use

- Home opens a custom-workout form for a name and optional notes, or resumes the one unfinished workout. Starting any routine while a workout is active resumes that workout.
- Exercises contains common seeded exercises and custom exercises. Edit their name, muscle group, equipment and tracking type. Archive / unarchive hides or shows exercises in new-workout pickers while preserving references and history.
- Routines lets you create, rename, delete and reorder your own workout plans. Creating a plan opens its editor immediately. Add existing or custom exercises and choose their target sets, reps and rest seconds. Starting a plan copies its name, exercises and targets into a separate workout.
- In a workout, add / reorder / remove exercises, add sets, and tap a set to enter external weight and repetitions, warm-up / working status, and completion. Tap Save to accept and persist the edit. Invalid values leave the editor open and show an error. Add workout and exercise notes using their edit buttons. Saved changes survive restart; unfinished editor text is not an accepted change.
- Finish requires at least one completed set and confirmation. Discard, exercise removal, set deletion and history deletion require confirmation. Finished workouts can be opened in History and edited; progress recalculates from saved records.
- The rest timer has start, pause, resume, reset and skip controls. Completing a previously incomplete set starts rest when enabled in Settings. Running timers persist an epoch-millisecond end timestamp in Room, so background time and process death do not reset them. Paused timers persist their remaining duration. No background alerts, vibration, sound or notifications are implemented. A device clock adjustment changes remaining wall-clock time.
- Progress lists completed workouts by week, external weight history per exercise, the heaviest completed working set with reps/date, and external-weight volume. Archived exercises remain available for progress selection.
- Settings controls kilograms / pounds, dark / light / system theme, default rest seconds and automatic rest. The default applies to newly added exercises / newly started empty workouts; routine rest targets are copied individually.

## Create a custom workout

1. On Home, tap **Start workout**, enter any workout name (for example, My home training day), optionally add notes, and save.
2. Tap **Add exercise**. Search your library by name, muscle group or equipment, or tap **Create custom exercise**. A search with no match can become the new exercise name without leaving the workout.
3. For a new exercise, type any name. Muscle group and equipment are optional free text; blank values are stored as General and None. Choose weighted or bodyweight tracking, then enter the starting sets, reps and rest seconds. The library exercise and workout entry are saved together only after valid submission; cancelling saves neither.
4. Existing exercises also open the target form before being added. Defaults are suggestions (3 sets, 8 reps, and your default rest); you can replace them. Edit individual sets for different weights, reps, warm-ups and completion status as you train. Add or delete sets, reorder / remove exercises, and add notes. **Customize rest** in an exercise's options changes its next automatic rest duration without resetting a running timer.
5. Tap **Save as workout plan** from an active or historical session if you want to reuse it. Give the plan your own name; it appears in Routines. This copies order, per-exercise rest, working-set counts, and the first working set's reps as the target for that exercise. It excludes warm-ups, weights, notes and completion flags; exercises with no working sets default to 1 set × 8 reps. Edit the plan's targets afterwards as needed. Saving a plan does not alter the original session, and later plan changes do not alter history.

You can also create a named plan directly in **Routines → Create workout plan** and add custom exercises there. The seeded library is a starting point, not a restriction: your exercises are saved in the same editable library and available to future sessions, plans, history, progress and backups. Exercise names and tracking types in existing workouts remain historical snapshots.

Strength exercises currently track external weight and repetitions (weighted / bodyweight). Customization does not add timed sets, supersets or other new measurement types. Target ranges remain 1–50 initial sets, 1–1,000 reps and 1–3,600 rest seconds. A plan with more than 50 working sets for one exercise cannot be saved until that count is reduced. Individual live sets can still be added and edited as before.

## Running

1. Open **Run**, go outdoors, and tap **Start run**.
2. Grant **precise location while using the app**, enable your phone's GPS/location services, and allow notifications if you want controls in the notification drawer. Approximate location is insufficient for route measurement. Denial leaves the rest of the gym app usable; the Run screen links to location settings and app permissions.
3. Active duration starts immediately. Wait for an accurate GPS fix before relying on distance and pace. A cold GPS start may take time. The screen reports whether it is recording or waiting for GPS/movement.
4. **Pause run** stops GPS requests and duration accumulation, removes the tracking notification and keeps the unfinished run. **Resume run** explicitly starts tracking again and begins a separate path segment. Only one unfinished run can exist; gym workouts remain independent.
5. **Finish and save run** confirms before storing the run in **Run history**. A zero-GPS run can retain duration only; pace is unavailable. Open a history item to inspect the saved route and statistics. Run and route deletion require confirmation.

The foreground location service is started from the visible app and provides an ongoing notification with a **Pause** action and a link back to the Run screen. It is designed to keep receiving GPS with the screen off; it does not request `ACCESS_BACKGROUND_LOCATION`. Android 13+ notification denial hides normal drawer controls but does not prevent starting the foreground service. Location is requested only during a run you start/resume, not while paused or merely viewing history. No automatic tracking starts on boot, restore, or app launch.

Every accepted GPS point is saved transactionally along with distance and duration; elapsed time is checkpointed every five seconds while tracking. Active duration uses Android's monotonic elapsed-realtime clock, so changing the wall clock does not add or subtract running time. If the process is killed or the phone reboots, the unfinished run is recovered **paused** at its most recent saved checkpoint (up to about five seconds of time can be lost). Downtime is excluded, and the UI asks you to resume. Closing the activity normally does not intentionally stop the service. Android force-stop, revoked permission, disabled GPS, OEM battery restrictions, or process termination can interrupt tracking; real-device screen-off behavior still needs testing on the intended phone.

Distance is the sum of haversine distances between accepted consecutive fixes **within the same segment**, in metres, displayed in kilometres. Average pace is active duration divided by recorded distance, displayed as minutes per kilometre, and is unavailable below 10 m. Pauses are excluded from active duration; time spent waiting for GPS is included. The tracker ignores fixes with reported accuracy worse than 35 m, stale fixes older than 15 seconds, out-of-order fixes, very small drift, and obvious jumps faster than 12 m/s. Signal gaps longer than 30 seconds start new segments; unobserved distance across gaps is not guessed. This can undercount distance in poor reception. Metrics are GPS estimates, not surveyed distances; no automatic pause, elevation, turn-by-turn directions or route planning is included.

The route view is north-up, has start/current-or-end markers and a metric scale, and supports pinch zoom, pan and **Fit route**. Its background grid is a reference grid, not streets. Pauses and GPS gaps are not joined. Large routes are sampled for drawing only; stored points and distance calculations retain the accepted data. Only the selected run's points are observed by its screen, so opening the dashboard does not load every historical GPS point.

GPS routes are sensitive location data. There is no upload or location analytics, and Android automatic backup is off. User-exported JSON backups include full route coordinates and are **unencrypted**; choose device storage in the system picker if you want that file kept local too. Uninstalling or clearing app storage deletes runs just like strength records.

## Data and metric definitions

All weights are stored as **kilograms** in Room `Double` fields; 1 kg = 2.2046226218487757 lb. Display and input convert to the selected unit. Zero external weight is valid, including bodyweight exercise sets. Weight is limited to 0–10,000 kg, reps to 1–1,000, routine sets to 1–50, rest to 1–3,600 seconds, names to 120 characters and notes to 10,000 characters. Decimal input uses a period. Unchanged displayed weights retain their original precision when saving other set fields.

Progress uses only completed working sets in finished workouts. Warm-ups, incomplete sets and active workouts are excluded. External-weight volume is the sum of weight × reps for **weighted exercise snapshots** only, displayed in kg·reps or lb·reps. Bodyweight snapshots are excluded from this volume; body mass is never estimated. The heaviest set and load history report recorded external load for either tracking type. Ties for heaviest load select one tied set without an estimated-1RM calculation.

Dates are UTC epoch milliseconds, displayed and grouped in the current device timezone. Weeks begin Monday and use workout finish dates. The previous-performance section finds the latest earlier finished workout containing completed sets for that exercise, including completed warm-ups, and labels warm-ups explicitly.

The eight Room entities have stable IDs, foreign keys, indexes and explicit ordering. Workout exercise names and tracking types are snapshots. Routine or library changes cannot rewrite them. Separate nullable unique active-slot indexes permit at most one active workout and one active run. RunPoint records have a foreign key to Run and unique sequence positions per run; deletion cascades. Reordering is transactional. All repository work runs off the main thread. Compose observes ViewModel flows, and busy guards prevent repeated save and tracking-command submissions.

The database is version 3. `MIGRATION_1_2` adds a history index; `MIGRATION_2_3` adds Run and RunPoint tables without modifying strength records. Exported Room schemas live in `app/schemas` after KSP runs. Future releases must increment the database version, commit the exported schema, add explicit non-destructive migrations from supported versions, and test those paths before shipping. No destructive fallback is configured. JSON's schema version is independent of Room's database version.

## Backup and restore

1. In Settings, tap **Export JSON backup**.
2. Android's system file picker asks where to write a `.json` file. Choose device storage to keep the file local; the picker may also offer other providers installed on your phone.
3. Copy the file somewhere safe. It contains your records and preferences in readable, unencrypted JSON.
4. To restore, tap **Restore JSON backup** and select the file.
5. The app reads and validates the entire backup before presenting a replacement confirmation. Review the record counts, then confirm.

Restore **replaces every current record and preference**, including active workouts, runs and routes. Export first if you want to keep the current data. New backups use schema **2**; older schema **1** gym backups remain supported and contain no runs, so restoring one replaces existing runs with an empty run history. Maximum UTF-8 file size: **50 MiB**, with up to 100,000 records per table. Validation checks the schema, size, types, unique nonempty IDs, foreign-key relationships, ordering, names, notes, weights, repetitions, timestamps, tracking types, settings, active-session / timer invariants, coordinate bounds, GPS accuracy and segment order, and recalculated route distance. Unsupported, malformed or invalid files do not modify live records. Pause an actively recording run first; restore is rejected while the service is recording. Imported unfinished runs are always restored paused, and monotonic clock anchors from the old device are cleared. Database replacement runs in one Room transaction. SQLite and DataStore cannot participate in one shared transaction: settings are written first and restored to their prior values on an ordinary database failure; an abrupt process kill in that small interval could leave settings from the backup with the prior records. The database records themselves remain atomic.

**Uninstalling the app or clearing its app data removes all local records and preferences. They can only be recovered from a backup you exported.** Export before uninstalling, changing devices, or clearing storage. Android cloud backup will not rescue them.

## Verification

Tests in `app/src/test` cover weight conversions, invalid input, completed-set / bodyweight volume filtering, timezone weekly grouping, timestamp timers, JSON round trips, invalid / oversized backups, persistence after database reopen, routine snapshot independence, concurrent active-workout creation, migration data retention, and valid / invalid restore behavior. Running tests cover distance/pace, pause timing, GPS filtering and gaps, process interruption, concurrent starts, route deletion, schema-1 compatibility and schema-2 restore safety, plus simulated service GPS delivery and permission denial. Persistence tests run against Room with Robolectric, not a mocked DAO.

Build, lint, test and device results for this workspace are recorded in `VERIFICATION.md`. No device or emulator result should be inferred merely from successful compilation.

Focused choices: no cloud sync, nutrition, coaching, social features or rest-timer background alerts; the running foreground service has its own required notification. Labelled Move up / Move down menu items provide reordering; backups are whole-dataset replacement rather than merging. Screen content scrolls, respects system insets and uses Material text, labelled fields and 48dp minimum action targets. Android Studio Compose previews are included for the Home dashboard and set rows.
