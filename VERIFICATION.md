# Verification — My Gym Log 1.4 — October 26, 2026 (Asia/Manila)

## Final result

`assembleDebug testDebugUnitTest lintDebug` completed with **BUILD SUCCESSFUL**, using Gradle 8.13, Android Studio JBR 21 and Android SDK 35.

- **60 tests passed**, 0 failures, 0 errors: 5 gym metrics, 5 running calculations / filtering, 3 body-progress calculations / validation, 7 backup validation, 20 Room persistence / migration / customization, 4 GPS service cases (API 28 and 35), 4 navigation regressions and 12 Compose interaction tests.
- Lint: **0 errors, 14 warnings** about target SDK 35, newer AndroidX dependencies, and two String.toUri style suggestions. No errors were suppressed to pass verification.
- Debug APK: `My-Gym-Log.apk` at the project root and `app/build/outputs/apk/debug/app-debug.apk`, copied from the verified ASCII output directory. Version **1.4**, code **5**, app ID `com.mygym.log`, min SDK 26, target SDK 35. The existing local debug signing key supports an in-place update.
- APK SHA-256: `E73A93EFA574C80E31FBC2414C022259CA11BAE052D4833C0994B0E01B904BD7`.
- Room schema **4** exported to `app/schemas/com.mygym.log.data.GymDatabase/4.json`; schemas 2 and 3 remain committed. Explicit 1→2, 2→3 and 3→4 migrations are registered without destructive fallback.
- APK inspection with SDK aapt confirms location, foreground service / location service, and notification permissions. There is **no INTERNET, broad storage or ACCESS_BACKGROUND_LOCATION permission**. AndroidX also declares an app-private receiver permission. Automatic cloud backup and device transfer remain disabled.

Reports copied into this project:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/TEST-*.xml`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.txt`

## What was checked

Customization tests verify named workouts, inline exercises, targets, rollback without orphan records, independent plans, plan conversion without rewriting the original session, rest controls and backup round trips. Starting external loads now populate both custom and predefined exercise sets and are saved with plans. Tests confirm those loads do not count as completed progress until the appropriate sets are completed in a finished workout; warm-ups and incomplete sets stay excluded. Changing a plan's load does not modify existing session weights.

Body tests cover independent latest weight / height values, weight change, unit conversion and edit precision, invalid / non-finite / empty values, local dates and future-date validation, database reopen, editing / deletion, and backup round trips. Body mass is not included in exercise volume. The 3→4 migration preserves gym and running records and gives existing plans zero starting load. Migration tests exercise the registered chains from legacy versions 1, 2 and 3. Backup schema 3 includes body entries and plan loads, while schemas 1 and 2 restore safe defaults; missing or invalid schema-3 fields are rejected before replacement.

Navigation tests exercise the real NavHost / HorizontalPager shell with tab taps, left/right swipes, rapid taps during an unfinished animation, navigation from a page link, repeated library opening and Back, and dragging the real route component without switching pages. Main pages share one pager selection; only secondary / editor screens use back-stack entries. The distinct dumbbell library icon and fixed saving-indicator slot avoid duplicate icons and shifting toolbar controls. Run instructions, optional fields and timer explanations use collapsed disclosures; toggle behavior is tested.

Running tests verify haversine distance and min/km pace, monotonic elapsed time, exclusion of pauses, filtering inaccurate / stale / out-of-order fixes and GPS jumps, segment breaks after gaps and resume, coordinate bounds and dateline distance, persistence after database reopen, recovery into paused state, concurrent starts returning one run, cascading route deletion, and migration retaining existing strength records.

Backup tests verify legacy schema-1 compatibility, schema-2 running data, invalid coordinates / distance / relationships, rejection of restore during recording, preserved route points, and imported active runs paused with device-specific clock anchors cleared. Existing gym calculations, resume, routine snapshots and restore regressions passed.

Service tests create a foreground notification, register a GPS listener, deliver a simulated fix through Android's LocationManager shadow, confirm a Room point was written, pause the service, confirm recording stops, and reject startup without precise permission. These run on Robolectric API 28 and 35; they are not physical GPS tests. An initial simulated-fix test exposed a test setup race; it now waits for listener registration before injection.

Compose tests verify set controls and busy guards, Home states, options menus, route controls, named workouts, custom exercise entry, kg/lb starting-load entry for both exercise types, body-entry validation and preservation of full precision while editing notes. Tests use a full activity-sized host and default Robolectric display metrics. Explicit width / height qualifiers previously caused dialog TextFields to hang; the test-only override was removed for the [reported Robolectric issue](https://github.com/robolectric/robolectric/issues/8460). No tests are disabled or skipped. These are not screenshot or visual layout verification.

## Reproduce verification

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JDK_JAVA_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon --console=plain '-PgymBuildRoot=C:/Users/Alexander San Pedro/.codex/visualizations/2026/10/25/01a173f4-a3b4-73c1-a066-a76f231c9d64/android-build'
```

The ASCII output directory works around this Windows JDK's test-classpath encoding issue with the Japanese workspace path. Sources stay in this project. The short JBR socket path avoids its loopback initialization error. On a normal ASCII path, use the standard README commands. Gradle reports plugin deprecations for a future Gradle 9 upgrade and a deprecated Robolectric GPS-listener inspection helper; neither fails this build.

## Unverified on hardware

`adb devices` completed and listed **no connected device or emulator**; emulator inventory was also empty. Visual navigation smoothness, Android screen-edge gestures, font scaling, TalkBack, keyboard layout, outdoor GPS, actual permission dialogs, notification actions, screen-off recording, battery / OEM behavior, physical process death / reboot and real file-picker behavior remain **unverified on a device**. No hardware test is claimed to have passed.

On the intended phone, install as an update and try rapid tab taps, swipes, library / Back, map pan, and dialogs at the preferred font size. Add starting loads in kg and lb; verify completed-set progress. Log body weight / height, edit a note, reopen and export / restore a backup. For running, do a short outdoor and screen-off test as described in the README. Export current records before testing replacement restore. Manufacturer power policies can interrupt foreground services.

## Scope and limits

Runs, coordinates and statistics stay in local Room tables. The map is an offline north-up projection of recorded GPS points with a reference grid, start / end markers, scale, pan and zoom. It has **no streets, tiles, navigation, elevation or route planning**. Pauses and signal gaps are not bridged with guessed distance. Pace is an estimate from active duration and recorded distance; GPS acquisition / wait time remains in active duration. No automatic pause is implemented.

The foreground service intentionally does not automatically restart after process death. The app recovers an unfinished run paused at its last checkpoint, excludes downtime and requires explicit resume; up to about five seconds of duration can be lost. GPS disablement or permission loss stops tracking safely. The gym rest timer still has no background alerts.

JSON backups include sensitive route coordinates in **unencrypted** form and are limited to 50 MiB / 100,000 records per table. Restore replaces current records; old gym-only backups contain no runs. Database replacement is transactional, while DataStore settings cannot share the SQLite transaction; the abrupt-process-kill settings interval is documented in the README. A cloud file provider can upload a manually exported backup even though this app has no internet permission; choose device storage to keep the file local.
