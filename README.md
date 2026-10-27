# Gym — Get your muscle

A native, personal strength-training and running log for Android 8.0 (API 26) and newer. Kotlin, Jetpack Compose / Material 3, Navigation Compose, ViewModel / StateFlow, Room, DataStore and coroutines. One application module and manual dependency injection. No account, subscription, ads, analytics, or backend. No INTERNET permission is requested. Android automatic backup and device transfer are disabled.

Version 1.4 unifies tab buttons and horizontal swipes in a single pager, replaces the library icon with a dumbbell, simplifies Run into compact stats with collapsed help, adds starting external weights for custom / predefined exercises and saved plans, and introduces local body-weight / height history. It includes all previous features. At that release, database schema was 4 and JSON backup schema was 3; older databases and backups are supported through explicit migrations and validated defaults.

Version 1.2 adds GPS runs with local routes, distance, active duration, average pace, pause/resume, history and backup support. The Run tab joins the existing navigation. All GPS samples stay in the app's Room database. The offline route view draws your actual recorded path, not a street map. Version 1.5.1 restores this sketch-only route view.

Version 1.1 refreshes the interface with charcoal surfaces and yellow accents, a four-tab bottom navigation bar, a dashboard with weekly / total counts, a persistent resume shortcut, searchable exercise library, and compact workout set rows. Tap a set's values to edit; tap its check button to save completion and start rest when auto-rest is enabled. Secondary actions such as reordering, notes, archiving and deletion are available from each card's labelled options menu. Progress includes accessible weekly bars with exact counts and all-time weekly totals. Dark, light and system preferences remain available.


Version **1.5.1** keeps the **Gym** name, Run / Walk sessions and local step counts, restores the route sketch, and removes the MapLibre SDK, bundled street maps, downloader and map controls. The launcher uses the supplied golden bull artwork, with adaptive square/round Android icons. Application ID `com.mygym.log`, Room schema **5** and backup schema **4** remain unchanged, so installing as an update preserves existing workouts, body measurements, runs, walks and steps.

## Routes, walking and storage

- Recorded routes use the original offline sketch, with pan/zoom, start/end markers and separate segments for pauses/GPS gaps. No streets, map downloads, external map provider or API keys are used.
- Choose **Walk**, then **Start walk**. Android 10+ requests Physical activity permission. Hardware step counter/detector readings count only during active sessions and are saved locally, including in history and JSON backups. Carry the phone. Without a sensor or permission, the app labels steps unavailable while GPS distance/time still work. This is not an all-day pedometer or stride estimate.
- The update removes the former app-private `files/maps` directory and old map preferences. Cleanup runs on the application IO scope, refuses unexpected links, retries on a later launch if deletion fails and never blocks startup with an exception. Activity databases, settings and backups are not removed. There is no database downgrade.
- Sensor batching/registration boundaries can omit steps; baselines reset after pause, interruption or reboot. Phone testing is still required for actual GPS, permission prompts, steps, screen-off behavior and launcher icon appearance.
- The golden bull artwork was supplied by the user and resized into Android launcher resources. The source artwork is not altered; adaptive padding keeps the horns inside common launcher masks.

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

The project now lives at `C:\Projects\ChatGPT\gym app`, outside OneDrive. Open that folder in Android Studio or your coding workspace. The standard `gradlew`, `gradlew.bat`, wrapper JAR, properties and optional PowerShell entry point are included. Builds use the normal project build directories; no Unicode-path override or external build-output folder is needed at this location.

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

The main pages are **Home, Plans, Run, History and Progress**. Tap their bottom-bar labels or swipe left / right across a page. Tab taps cancel any earlier page animation, and page links use the same pager state; they do not create duplicate tab back-stack entries. Secondary pages and active workout editors use Back to return to the previous tab. Data-entry dialogs do not swipe between pages, and dragging the run route pans its map rather than navigating. Search text and page scroll positions use saveable page state. The top-right **dumbbell** opens the exercise library; the gear opens Settings. Saving indicators occupy a fixed slot so toolbar buttons do not shift while saving.

- Home opens a custom-workout form for a name and optional notes, or resumes the one unfinished workout. Starting any routine while a workout is active resumes that workout.
- Exercises contains common seeded exercises and custom exercises. Edit their name, muscle group, equipment and tracking type. Archive / unarchive hides or shows exercises in new-workout pickers while preserving references and history.
- Plans lets you create, rename, delete and reorder your own workout plans. Creating a plan opens its editor immediately. Add existing or custom exercises and choose their target sets, reps and rest seconds. Starting a plan copies its name, exercises and targets into a separate workout.
- In a workout, add / reorder / remove exercises, add sets, and tap a set to enter external weight and repetitions, warm-up / working status, and completion. Tap Save to accept and persist the edit. Invalid values leave the editor open and show an error. Add workout and exercise notes using their edit buttons. Saved changes survive restart; unfinished editor text is not an accepted change.
- Finish requires at least one completed set and confirmation. Discard, exercise removal, set deletion and history deletion require confirmation. Finished workouts can be opened in History and edited; progress recalculates from saved records.
- The rest timer has start, pause, resume, reset and skip controls. Completing a previously incomplete set starts rest when enabled in Settings. Running timers persist an epoch-millisecond end timestamp in Room, so background time and process death do not reset them. Paused timers persist their remaining duration. No background alerts, vibration, sound or notifications are implemented. A device clock adjustment changes remaining wall-clock time.
- Progress lists completed workouts by week, external weight history per exercise, the heaviest completed working set with reps/date, and external-weight volume. Archived exercises remain available for progress selection.
- Settings controls kilograms / pounds, dark / light / system theme, default rest seconds and automatic rest. The default applies to newly added exercises / newly started empty workouts; routine rest targets are copied individually.

## Create a custom workout

1. On Home, tap **Start workout**, enter any workout name (for example, My home training day), optionally add notes, and save.
2. Tap **Add exercise**. Search your library by name, muscle group or equipment, or tap **Create custom exercise**. A search with no match can become the new exercise name without leaving the workout.
3. For a new exercise, type any name. Muscle group and equipment are optional free text under an expandable section; blank values are stored as General and None. Choose weighted or bodyweight tracking, then enter starting external weight, sets, reps and rest seconds. The library exercise and workout entry are saved together only after valid submission; cancelling saves neither.
4. Existing exercises open the same target form, including **Starting weight** in kg or lb according to Settings. Defaults are suggestions (zero external weight, 3 sets, 8 reps, and your default rest). Starting weight populates every initial set; edit individual sets for different loads, reps, warm-ups and completion status as you train. Bodyweight exercises can use zero added load. No body mass is added automatically. Add or delete sets, reorder / remove exercises, and add notes. **Customize rest** changes the next automatic rest without resetting a running timer.
5. Tap **Save as workout plan** from an active or historical session if you want to reuse it. Give it your own name; it appears in Plans. It copies order, rest, working-set counts, and the first working set's reps **and external weight** as that exercise's starting targets. It excludes warm-ups, notes and completion flags; exercises with no working sets default to 1 set × 8 reps at zero external weight. Edit plan targets afterwards. Starting a plan copies its loads into independent, incomplete sets. Merely creating a plan or adding weights does not count as completed lifting progress. Saving / editing plans cannot rewrite existing workout history.

You can also create a named plan directly in **Plans → Create workout plan** and add custom exercises there. The seeded library is a starting point, not a restriction: your exercises are saved in the same editable library and available to future sessions, plans, history, progress and backups. Exercise names and tracking types in existing workouts remain historical snapshots.

Strength exercises currently track external weight and repetitions (weighted / bodyweight). Customization does not add timed sets, supersets or other new measurement types. Target ranges remain 1–50 initial sets, 1–1,000 reps and 1–3,600 rest seconds. A plan with more than 50 working sets for one exercise cannot be saved until that count is reduced. Individual live sets can still be added and edited as before.

## Running

Run tips, permission links, pace definitions and route instructions are collapsed under **Run tips & permissions**. They can be expanded and hidden again; errors still show direct settings links. Only three recent runs are shown initially, with a Show all option. Timer help and optional exercise metadata use the same disclosure control elsewhere.

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

The nine Room entities have stable IDs, foreign keys, indexes and explicit ordering. Workout exercise names and tracking types are snapshots. Routine or library changes cannot rewrite them. Separate nullable unique active-slot indexes permit at most one active workout and one active run. RunPoint records have a foreign key to Run and unique sequence positions per run; deletion cascades. BodyMeasurement records store body weight in kg and height in cm independently from WorkoutSet loads. Reordering is transactional. All database/file work runs off the main thread. Compose observes ViewModel flows, and busy guards prevent repeated save and tracking-command submissions.

The database is version 5. `MIGRATION_4_5` adds activity type, session steps and step availability to saved activities; existing runs retain their data and default to RUN / 0 steps. `MIGRATION_1_2` adds a history index; `MIGRATION_2_3` adds Run and RunPoint; `MIGRATION_3_4` adds body measurements and a starting-load field on routine exercises. Existing plan loads default to 0 kg; recorded workout set weights remain unchanged. Exported schemas are committed in `app/schemas`. Future releases must add explicit non-destructive migrations and test them before shipping. No destructive fallback is configured. JSON's schema version is independent of Room's version.

## Body weight and height

Open **Progress → Body weight & height** (also available in Settings). Tap **Log measurement** and enter body weight, height, or both. Weight follows the kg/lb preference and is stored in kg; height is labelled and stored in cm. The date defaults to today in the device timezone and accepts YYYY-MM-DD dates up to today. Notes are optional. Measurement history supports editing and confirmed deletion. Latest weight and latest height are found independently, so a weight-only entry does not erase a previously recorded height.

The page shows change since the first weight entry, a trend for the last 30 weight entries, and exact dated values. This is a measurement log, not a BMI / health assessment. Body mass is **never** added to exercise-set weights or lifting volume. Height-only entries do not change the weight trend. Unchanged values retain their full canonical precision when editing other fields. Data stays in local Room storage and is included in manually exported JSON backups.

## Backup and restore

1. In Settings, tap **Export JSON backup**.
2. Android's system file picker asks where to write a `.json` file. Choose device storage to keep the file local; the picker may also offer other providers installed on your phone.
3. Copy the file somewhere safe. It contains your records and preferences in readable, unencrypted JSON.
4. To restore, tap **Restore JSON backup** and select the file.
5. The app reads and validates the entire backup before presenting a replacement confirmation. Review the record counts, then confirm.

Restore **replaces every current record and preference**, including workouts, runs, routes and body history. Export first to retain current data. New backups use schema **4**, including walking mode and step counts; schemas **1–3** remain supported with RUN / 0-step defaults. Older files have no body measurements or starting plan loads: body history becomes empty and plan loads default to 0 kg. Schema 1 also has no runs. Maximum size is **50 MiB**, up to 100,000 records per table. Validation checks IDs, relationships, ordering, names, notes, units, finite weights/heights, dates, active-session/timer invariants, GPS coordinates / segments and recalculated route distance before changes. Invalid or unsupported files leave live records untouched. Pause a recording run before restore. Imported unfinished runs are paused with old device clock anchors cleared. Database replacement is transactional. DataStore settings cannot share a SQLite transaction: settings roll back on ordinary database failure, but abrupt process death in that interval can leave imported settings with old records. Database records remain atomic. Backups are unencrypted and include sensitive route and body data; choose a local file provider if you want the exported file kept on the device too.

**Uninstalling the app or clearing its app data removes all local records and preferences. They can only be recovered from a backup you exported.** Export before uninstalling, changing devices, or clearing storage. Android cloud backup will not rescue them.

## Verification

Tests in `app/src/test` cover weight conversions, invalid input, completed-set / bodyweight volume filtering, timezone weekly grouping, timestamp timers, JSON round trips, invalid / oversized backups, persistence after database reopen, routine snapshot independence, concurrent active-workout creation, migration data retention, and valid / invalid restore behavior. Running tests cover distance/pace, pause timing, GPS filtering and gaps, process interruption, concurrent starts, route deletion, schema-1 compatibility and schema-2 restore safety, plus simulated service GPS delivery and permission denial. Persistence tests run against Room with Robolectric, not a mocked DAO.

Build, lint, test and device results for this workspace are recorded in `VERIFICATION.md`. No device or emulator result should be inferred merely from successful compilation.

Focused choices: no cloud sync, nutrition, coaching, social features or rest-timer background alerts; the running foreground service has its own required notification. Labelled Move up / Move down menu items provide reordering; backups are whole-dataset replacement rather than merging. Screen content scrolls, respects system insets and uses Material text, labelled fields and 48dp minimum action targets. Android Studio Compose previews are included for the Home dashboard and set rows.
