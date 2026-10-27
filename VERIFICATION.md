# Verification — Gym 1.5.1 — October 27, 2026 (Asia/Manila)

## Changes and data compatibility

This release rolls back only street-map functionality. The original local route sketch is restored. MapLibre SDK/native libraries, map packs, download helpers, download controls and INTERNET permission are removed. Walking mode, hardware session steps, GPS distance/time/pace, run/walk history, custom workouts and body progress are retained.

Version is **1.5.1 / code 7**, with installed name **Gym**. Application ID stays `com.mygym.log`, database schema stays **5** and backup schema stays **4**. There is no database downgrade or destructive migration. The new APK's signing certificate was checked against the previous APK and matches, permitting an in-place update.

The supplied golden bull image is resized to a 512 px Android resource with its colors/content retained. Square and round launcher entries use adaptive icons with padding to keep the horns inside launcher masks. Minimum supported Android version is 8.0, so redundant pre-adaptive density icons are omitted. The raster resource was visually inspected; actual launcher appearance still needs phone verification.

## Validation

- `clean assembleDebug testDebugUnitTest lintDebug` completed successfully: **65 tests**, 0 failures/errors. After icon resource cleanup, `assembleDebug lintDebug` was repeated for the final APK.
- Existing Room/migration/backup, walking step baselines, service permissions/GPS, navigation, Compose and body/workout tests remain passing. Only tests specific to the removed map implementation were deleted.
- A new Robolectric cleanup regression creates retired map files plus unrelated personal-file/database sentinels, removes maps, confirms the unrelated data is unchanged, confirms map preferences are cleared, and checks a second cleanup is safe.
- Startup cleanup targets only app-private `files/maps`, refuses unexpected links, runs on the application IO scope and catches failures so startup/tracking can proceed. Failed cleanup is retried next launch. Room databases and user backups are untouched.
- APK inspection verifies no `assets/maps/` files, MapLibre native library, or `org/maplibre/` DEX references remain. The packaged bull image is compared pixel-for-pixel against the prepared Android asset, and both adaptive launcher entries are checked.
- Android SDK aapt confirms the Gym name, version code 7 and adaptive launcher icon. INTERNET permission is absent. Location/activity recognition and the associated foreground-service permissions are retained for tracking.
- Previous APK: **96,790,148 bytes**. Final APK size, SHA-256 and lint result are below.

## Reproduce

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JDK_JAVA_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'
Set-Location -LiteralPath 'C:\Projects\ChatGPT\gym app'
.\gradlew.bat clean assembleDebug testDebugUnitTest lintDebug --no-daemon --console=plain
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest/TEST-*.xml`, and `app/build/reports/lint-results-debug.html`.

## Phone checks still needed

Install **Gym.apk as an update**, keeping app data. Check the golden bull launcher icon, open an existing saved run/walk and pan/zoom its sketch, record a short GPS session, and check walking steps with Physical activity permission granted/denied. Existing workouts, body measurements and step totals should remain. Previously downloaded map storage is removed on startup.

Real GPS, real sensor accuracy, Android launcher masks/caching, permission prompts and screen-off/OEM power behavior have not been tested on a physical phone for this release. No hardware test success is claimed. Step counts remain session totals, with sensor batching/registration limitations; no sensor/permission means unavailable steps, not an estimate. All records remain local. Exported JSON backups remain unencrypted; a cloud file-picker destination can upload a user-exported backup even though the app itself has no INTERNET permission.

## Final APK

`Gym.apk`, with the old `My-Gym-Log.apk` filename updated to the same binary for compatibility.

- Size: 12298556 bytes.
- SHA-256: 2C6163DE0F3F14704523601BD5A335695CA8F890C5667E5F65E1D57518C72DFC
- Lint: 0 errors, 19 warnings (dependency/target SDK suggestions, two optional themed-icon monochrome suggestions, and Kotlin style suggestions).
