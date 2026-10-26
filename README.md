# My Gym Log

A native, personal strength-training log for Android 8.0 (API 26) and newer. Kotlin, Jetpack Compose / Material 3, Navigation Compose, ViewModel / StateFlow, Room, DataStore and coroutines. One application module and manual dependency injection. No account, subscription, ads, analytics, backend, or INTERNET permission. Android automatic backup is disabled.

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

- Home starts an empty workout or resumes the one unfinished workout. Starting any routine while a workout is active resumes that workout.
- Exercises contains common seeded exercises and custom exercises. Edit their name, muscle group, equipment and tracking type. Archive / unarchive hides or shows exercises in new-workout pickers while preserving references and history.
- Routines lets you create, rename, delete and reorder plans. Open a plan to add, remove and reorder exercises and edit target sets, reps and rest seconds. Starting a plan copies its name, exercises and targets into a separate workout.
- In a workout, add / reorder / remove exercises, add sets, and tap a set to enter external weight and repetitions, warm-up / working status, and completion. Tap Save to accept and persist the edit. Invalid values leave the editor open and show an error. Add workout and exercise notes using their edit buttons. Saved changes survive restart; unfinished editor text is not an accepted change.
- Finish requires at least one completed set and confirmation. Discard, exercise removal, set deletion and history deletion require confirmation. Finished workouts can be opened in History and edited; progress recalculates from saved records.
- The rest timer has start, pause, resume, reset and skip controls. Completing a previously incomplete set starts rest when enabled in Settings. Running timers persist an epoch-millisecond end timestamp in Room, so background time and process death do not reset them. Paused timers persist their remaining duration. No background alerts, vibration, sound or notifications are implemented. A device clock adjustment changes remaining wall-clock time.
- Progress lists completed workouts by week, external weight history per exercise, the heaviest completed working set with reps/date, and external-weight volume. Archived exercises remain available for progress selection.
- Settings controls kilograms / pounds, dark / light / system theme, default rest seconds and automatic rest. The default applies to newly added exercises / newly started empty workouts; routine rest targets are copied individually.

## Data and metric definitions

All weights are stored as **kilograms** in Room `Double` fields; 1 kg = 2.2046226218487757 lb. Display and input convert to the selected unit. Zero external weight is valid, including bodyweight exercise sets. Weight is limited to 0–10,000 kg, reps to 1–1,000, routine sets to 1–50, rest to 1–3,600 seconds, names to 120 characters and notes to 10,000 characters. Decimal input uses a period. Unchanged displayed weights retain their original precision when saving other set fields.

Progress uses only completed working sets in finished workouts. Warm-ups, incomplete sets and active workouts are excluded. External-weight volume is the sum of weight × reps for **weighted exercise snapshots** only, displayed in kg·reps or lb·reps. Bodyweight snapshots are excluded from this volume; body mass is never estimated. The heaviest set and load history report recorded external load for either tracking type. Ties for heaviest load select one tied set without an estimated-1RM calculation.

Dates are UTC epoch milliseconds, displayed and grouped in the current device timezone. Weeks begin Monday and use workout finish dates. The previous-performance section finds the latest earlier finished workout containing completed sets for that exercise, including completed warm-ups, and labels warm-ups explicitly.

The six Room entities have stable IDs, foreign keys, indexes and explicit ordering. Workout exercise names and tracking types are snapshots. Routine or library changes cannot rewrite them. A nullable unique active-slot index permits at most one active workout. Reordering is transactional. All repository work runs off the main thread. Compose observes ViewModel flows, and a busy guard prevents repeated save submissions.

The database is version 2. `MIGRATION_1_2` adds a history index while retaining records. Exported Room schemas live in `app/schemas` after KSP runs. Future releases must increment the database version, commit the exported schema, add explicit non-destructive migrations from supported versions, and test those paths before shipping. No destructive fallback is configured. JSON's schema version is independent of Room's database version.

## Backup and restore

1. In Settings, tap **Export JSON backup**.
2. Android's system file picker asks where to write a `.json` file. Choose device storage to keep the file local; the picker may also offer other providers installed on your phone.
3. Copy the file somewhere safe. It contains your records and preferences in readable, unencrypted JSON.
4. To restore, tap **Restore JSON backup** and select the file.
5. The app reads and validates the entire backup before presenting a replacement confirmation. Review the record counts, then confirm.

Restore **replaces every current record and preference**, including the active workout. Export first if you want to keep the current data. Supported backup schema: 1; maximum UTF-8 file size: 10 MiB. Validation checks the schema, size, types, unique nonempty IDs, foreign-key relationships, ordering, names, notes, weights, repetitions, timestamps, tracking types, settings and single-active-workout / timer invariants. Unsupported, malformed or invalid files do not modify live records. Database replacement runs in one Room transaction. SQLite and DataStore cannot participate in one shared transaction: settings are written first and restored to their prior values on an ordinary database failure; an abrupt process kill in that small interval could leave settings from the backup with the prior records. The database records themselves remain atomic.

**Uninstalling the app or clearing its app data removes all local records and preferences. They can only be recovered from a backup you exported.** Export before uninstalling, changing devices, or clearing storage. Android cloud backup will not rescue them.

## Verification

Tests in `app/src/test` cover weight conversions, invalid input, completed-set / bodyweight volume filtering, timezone weekly grouping, timestamp timers, JSON round trips, invalid / oversized backups, persistence after database reopen, routine snapshot independence, concurrent active-workout creation, migration data retention, and valid / invalid restore behavior including all six tables and preferences. Persistence tests run against Room with Robolectric, not a mocked DAO.

Build, lint, test and device results for this workspace are recorded in `VERIFICATION.md`. No device or emulator result should be inferred merely from successful compilation.

Focused choices: no cloud sync, nutrition, coaching, social features or background alerts; labelled Move up / Move down menu items provide reordering; backups are whole-dataset replacement rather than merging. Screen content scrolls, respects system insets and uses Material text, labelled fields and 48dp minimum action targets. Android Studio Compose previews are included for the Home dashboard and set rows.
