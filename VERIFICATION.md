# Verification — Gym 1.5 — October 27, 2026 (Asia/Manila)

## Result

`assembleDebug testDebugUnitTest lintDebug` passed. The suite contains **66 tests**, with 0 failures/errors. Final APK details and lint summary are recorded below after packaging.

The installed app is **Gym** (Get your muscle), version 1.5 / code 6. Application ID remains `com.mygym.log`; the existing debug signing key is retained so this installs as an update. Room schema is **5**, JSON backup schema is **4**. Explicit migrations preserve prior workouts, runs and body measurements. Legacy runs default to RUN with zero session steps. Backup schemas 1–3 remain supported.

## New verification

- Both actual map packs were downloaded from OpenFreeMap: **Manila City, 55 tiles / 15,027,393 bytes**, and **Metro Manila, 146 tiles / 11,717,436 bytes** (tile bytes; fonts and metadata are additional). Source snapshot: `20261025_080012_pt`; downloaded October 27, 2026. Each download was sequential, identified the app, and remained below provider limits. An initial larger high-detail Metro download stopped at its size limit; final Metro coverage uses zooms 10–13, with zooms 10–14 for Manila City.
- `tools/verify_map_pack.py` parses every tile as MVT, confirms expected street/name/water/place layers, checks the label font, tile counts and size limits, and compares every bundled map file byte-for-byte against the APK. Both packs passed.
- An Android/Robolectric integration test installs both packs into app-private storage, verifies the style references only local `file:///` tile/font URLs, removes the City pack and restores it from bundled assets without a network download. This does **not** test native OpenGL map rendering.
- Coverage tests include Manila locations and outside-area rejection. The UI chooses a pack containing the entire recorded route, otherwise uses the existing sketch. Separate route segments remain separate on the map.
- Step tests verify initial baselines, pause/resume baselines, device-counter resets and invalid readings. Room tests verify walking steps survive reopen, paused steps are ignored, resumed totals accumulate and walking data round-trips through backups. Invalid negative step totals are rejected.
- The 4→5 upgrade is tested using a real legacy-column database, alongside the prior migration tests. Existing gym/running data are retained.
- Service tests on API 28 and 35 verify GPS/foreground notification behavior, location-permission denial, and walking with unavailable/denied step access continuing GPS tracking. Sensor listeners follow the foreground session lifecycle. A test setup race was fixed by awaiting the step availability status before asserting it.
- Previous metrics, custom workout, body measurements, backup validation, navigation, Compose and simulated GPS regression tests pass.

## Reproduce

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JDK_JAVA_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'
Set-Location -LiteralPath 'C:\Projects\ChatGPT\gym app'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon --console=plain
python tools\verify_map_pack.py app\build\outputs\apk\debug\app-debug.apk
python tools\verify_map_pack.py --metro app\build\outputs\apk\debug\app-debug.apk
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest/TEST-*.xml`, and `app/build/reports/lint-results-debug.html`.

## Limits and phone checks

No connected phone or emulator was available (`adb devices` was empty; no AVDs were configured). Native MapLibre/OpenGL rendering, Latin street-label readability, map/pager gesture interaction, real sensor events, screen-off tracking, actual permission prompts and OEM battery behavior remain **unverified on hardware**. No hardware success is claimed.

Install `Gym.apk` over the existing app. In airplane mode, open the Run page, inspect both included map areas, pan/zoom, view a saved route and verify an uncovered route uses the sketch. Remove and restore an included area without internet. While online, download an additional area, test cancellation/retry and then reopen it offline. In Walk mode, grant Physical activity access, carry the phone for a short walk, pause/resume, finish, reopen history and export/restore a backup. Repeat with the permission denied; distance/time should remain available and steps should be labelled unavailable.

Steps are session totals, not an all-day pedometer. Sensor batching and registration boundaries can omit some steps; paused or interrupted time is excluded. Without hardware support or activity permission, steps are not estimated. GPS waiting time counts toward active duration. Existing process interruption recovery pauses an unfinished activity and requires explicit resume.

Map tiles, fonts and style are local when viewing. Only user-requested area downloads contact OpenFreeMap and reveal the requested area/IP address; run coordinates, steps and other personal records are never uploaded. Download jobs run while the app process is alive, not as guaranteed background workers. Incomplete packs are cleaned on next initialization and can be retried. Downloads need free storage and have tile/byte limits. Maps are snapshots; no navigation, satellite imagery or automatic updates are included. Panning outside a pack shows no new street data. Non-Latin-only labels may be absent because the bundled font covers the Latin range.

JSON backups contain activity/body records and settings, not offline map files. Included map packs can be restored locally; additional areas can be downloaded again. Exported backups are unencrypted and may contain sensitive routes/body data; choose a device-local destination if you want the export kept local. Android automatic cloud backup/device transfer remain disabled.

## APK

Final artifact: `Gym.apk` (also copied to the old `My-Gym-Log.apk` filename for compatibility).
SHA-256: 0F1980960F84F790CF414C8DC6815FCC93A54BA333F8F753D6DAFAFC69665DAB
Lint: 0 errors, 20 warnings (dependency updates, target SDK, platform/storage and Kotlin style suggestions; no lint errors suppressed).
