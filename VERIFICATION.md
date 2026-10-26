# Verification — My Gym Log 1.2 — October 26, 2026 (Asia/Manila)

## Final result

`assembleDebug testDebugUnitTest lintDebug` completed with **BUILD SUCCESSFUL**, using Gradle 8.13, Android Studio JBR 21 and Android SDK 35.

- **33 tests passed**, 0 failures, 0 errors: 5 gym metrics, 5 running calculations / filtering, 5 backup validation, 10 Room persistence / migration, 4 GPS service cases (2 tests each on API 28 and API 35), and 4 Compose interaction tests.
- Lint: **0 errors, 13 warnings** about target SDK 35, newer AndroidX dependencies, and a String.toUri style suggestion. No running-feature lint errors are suppressed.
- Debug APK: `My-Gym-Log.apk` at the project root and `app/build/outputs/apk/debug/app-debug.apk`, copied from the verified ASCII output directory. Version **1.2**, code **3**, app ID `com.mygym.log`, min SDK 26, target SDK 35. The existing local debug signing key supports an in-place update.
- APK SHA-256: `FC312D207589E12F62323201E408502BF6DE9D1815C1F726B645A6323CBB7158`.
- Room schema **3** exported to `app/schemas/com.mygym.log.data.GymDatabase/3.json`; schema 2 remains committed. Explicit 1→2 and 2→3 migrations are registered without destructive fallback.
- APK inspection with SDK aapt confirms location, foreground service / location service, and notification permissions. There is **no INTERNET, broad storage or ACCESS_BACKGROUND_LOCATION permission**. AndroidX also declares an app-private receiver permission. Automatic cloud backup and device transfer remain disabled.

Reports copied into this project:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/TEST-*.xml`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.txt`

## What was checked

Running tests verify haversine distance and min/km pace, monotonic elapsed time, exclusion of pauses, filtering inaccurate / stale / out-of-order fixes and GPS jumps, segment breaks after gaps and resume, coordinate bounds and dateline distance, persistence after database reopen, recovery into paused state, concurrent starts returning one run, cascading route deletion, and migration retaining existing strength records.

Backup tests verify legacy schema-1 compatibility, schema-2 running data, invalid coordinates / distance / relationships, rejection of restore during recording, preserved route points, and imported active runs paused with device-specific clock anchors cleared. Existing gym calculations, resume, routine snapshots and restore regressions passed.

Service tests create a foreground notification, register a GPS listener, deliver a simulated fix through Android's LocationManager shadow, confirm a Room point was written, pause the service, confirm recording stops, and reject startup without precise permission. These run on Robolectric API 28 and 35; they are not physical GPS tests. An initial simulated-fix test exposed a test setup race; it now waits for listener registration before injection.

Compose tests verify independent set-edit / completion controls, busy guards, Home start / resume states, options menus, and the route view's empty state, accessibility description and Fit route control. They are not screenshot or visual layout verification.

## Reproduce verification

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JDK_JAVA_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon --console=plain '-PgymBuildRoot=C:/Users/Alexander San Pedro/.codex/visualizations/2026/10/25/01a173f4-a3b4-73c1-a066-a76f231c9d64/android-build'
```

The ASCII output directory works around this Windows JDK's test-classpath encoding issue with the Japanese workspace path. Sources stay in this project. The short JBR socket path avoids its loopback initialization error. On a normal ASCII path, use the standard README commands. Gradle reports plugin deprecations for a future Gradle 9 upgrade and a deprecated Robolectric GPS-listener inspection helper; neither fails this build.

## Unverified on hardware

`adb devices` completed and listed **no connected device or emulator**. No configured emulator was available. Outdoor GPS accuracy, actual permission dialogs, notification drawer actions, screen-off recording, battery consumption, OEM restrictions, physical process death / reboot, TalkBack, font scaling and real file-picker behavior remain **unverified on a device**. No hardware or screen-off test is claimed to have passed.

Before relying on recording, install as an update, allow precise location while using the app, and do a short outdoor test: acquire GPS, move a known distance, lock the screen for several minutes, pause / resume, and finish. Inspect the saved route, duration, distance, notification Pause action and history. Repeat with GPS disabled / re-enabled and after closing the activity. Export a backup before testing restore. Manufacturer power policies can interrupt foreground services.

## Scope and limits

Runs, coordinates and statistics stay in local Room tables. The map is an offline north-up projection of recorded GPS points with a reference grid, start / end markers, scale, pan and zoom. It has **no streets, tiles, navigation, elevation or route planning**. Pauses and signal gaps are not bridged with guessed distance. Pace is an estimate from active duration and recorded distance; GPS acquisition / wait time remains in active duration. No automatic pause is implemented.

The foreground service intentionally does not automatically restart after process death. The app recovers an unfinished run paused at its last checkpoint, excludes downtime and requires explicit resume; up to about five seconds of duration can be lost. GPS disablement or permission loss stops tracking safely. The gym rest timer still has no background alerts.

JSON backups include sensitive route coordinates in **unencrypted** form and are limited to 50 MiB / 100,000 records per table. Restore replaces current records; old gym-only backups contain no runs. Database replacement is transactional, while DataStore settings cannot share the SQLite transaction; the abrupt-process-kill settings interval is documented in the README. A cloud file provider can upload a manually exported backup even though this app has no internet permission; choose device storage to keep the file local.
