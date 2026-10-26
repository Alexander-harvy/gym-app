# Verification — October 26, 2026 (Asia/Manila)

## Final result

`assembleDebug testDebugUnitTest lintDebug` completed with **BUILD SUCCESSFUL** using the included Windows Gradle wrapper, Gradle 8.13, Android Studio JBR 21, and Android SDK 35.

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (copied from the verified ASCII build-output directory).
- **13 tests passed**, 0 failures, 0 errors: 5 domain / metrics tests, 3 backup validator tests, 5 Room / Robolectric persistence tests.
- Lint: **0 errors, 11 warnings**. Warnings concern target SDK 35 and availability of newer stable AndroidX dependencies. The Android 12 backup configuration warning was fixed with explicit extraction exclusions and rechecked.
- Room exported the version-2 database schema to `app/schemas/com.mygym.log.data.GymDatabase/2.json`.
- APK manifest inspection with SDK `aapt` confirms application ID `com.mygym.log`, min SDK 26, target SDK 35, and no INTERNET or storage permission. The only uses-permission is AndroidX's app-private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

Reports copied into this project:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/TEST-*.xml`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.txt`

## Reproduce this environment's verification

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JDK_JAVA_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon --console=plain '-PgymBuildRoot=C:/Users/Alexander San Pedro/.codex/visualizations/2026/10/25/01a173f4-a3b4-73c1-a066-a76f231c9d64/android-build'
```

The writable ASCII output location works around this Windows JDK's failure to load test classes from an argument file containing the Japanese workspace path. Source files were built in their original project location. Initial attempts exposed native-service restrictions, the JBR socket-path error, Android's non-ASCII path check and test-worker class-loading failures; all were addressed for the final successful run. The class-loading failures happened before test assertions, not as failing persistence assertions. On a normal ASCII project path, the standard commands in the README suffice.

The Gradle build also reports deprecated plugin features for a future Gradle 9 upgrade and two packaged native libraries that could not be stripped; those do not fail the current Gradle 8.13 debug build. Keep the included wrapper version until build plugins are deliberately upgraded.

## Practical limits

`adb devices` completed successfully and listed **no connected devices or emulators**. Installation, visual layout, TalkBack, large-font behavior, keyboard interaction, actual system file-picker behavior, physical-device process death and background / foreground timer behavior have **not** been device-tested. Persistence and timer logic were verified by automated tests, including closing and reopening the Room database.

Background alerts are not implemented. Backup database replacement is transactional, but DataStore preferences cannot share a SQLite transaction; the abrupt-process-kill settings interval is documented in the README. Backups are unencrypted and limited to 10 MiB. The dependencies are pinned compatible stable releases rather than the latest available versions.
