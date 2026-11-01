# Camille Android Updater

A shared Android library for apps distributed through https://download.camille.studio. Native update checks and verified installation require Android 8 or later. Version 1.0.4 can also be integrated into apps supporting Android 5–7: manual checks open the app’s public release page on those devices, and automatic checks remain disabled. Runtime code has no external dependencies.

## Integration

Pin `camille-updater-1.0.0.aar` from this repository's private GitHub release in the application's `libs` directory, alongside its SHA-256 file. Commit both files so builds need no GitHub credentials. The source and fixes live here; do not copy Java sources into consuming applications.

```kotlin
android {
    defaultConfig {
        resValue("string", "camille_update_app_id", "groceries")
    }
}
dependencies {
    implementation(files("libs/camille-updater-1.0.0.aar"))
}
```

Use catalogue IDs `groceries`, `vervetwodo`, or `podcini`. For Podcini, each product flavor additionally sets `resValue("string", "camille_update_edition", "free")` or `"play"`. Initialization and the private APK provider are merged automatically from the AAR manifest. No activity code is needed. Include `app/libs/**` in Android build freshness inputs.

Update the AAR and checksum together when adopting a new library version. Download with `gh release download v1.0.0 --repo CamilleHbp/android-updater --pattern 'camille-updater-1.0.0.aar*'`, then run `shasum -a 256 -c camille-updater-1.0.0.aar.sha256`.

## Behavior

The launcher activity checks on foreground entry, at most every six hours after a successful check and every fifteen minutes after a network failure. Nothing runs while the app is closed. A new build shows a localized prompt. Later defers prompts for 24 hours. Accepting opens a download screen with progress and retry. Returning to the app after Android's unknown-source permission screen requires another explicit Install update tap. Android retains installation approval; the library never silently installs.

Activity recreation preserves the active transfer. If Android kills the process, reopening the update screen verifies a completed cached file or restarts an incomplete transfer. Cache lives in the app's private cache directory. Failed downloads never replace the installed application.

The updater reads the promoted Android release, independently of desktop releases, and preserves the installed debug/release channel and configured edition. ARM64 devices prefer ARM64 assets, with universal fallback. Numeric release IDs must be `<versionName>-<versionCode>`; the numeric suffix must equal the APK's Android version code. Same-name releases with a higher code are updates; downgrades and reinstalls are rejected.

Downloads are restricted to immutable HTTPS URLs on `download.camille.studio`, without redirects. Before installation, the library verifies byte size, SHA-256, package ID, version name/code, minimum Android version, and the installed signing certificate. Signing-key rotation is rejected until the library explicitly supports a verified rotation lineage. Android also enforces its own package compatibility and signatures.

English and French resources are included. The prompt follows the host activity's locale, and the download activity carries that locale through Android's installation-permission flow.

## Build and tests

Use JDK 25, the Android 35 SDK, and the Gradle wrapper:

```sh
./gradlew :updater:testDebugUnitTest :updater:assembleRelease
./gradlew :updater:connectedDebugAndroidTest
```

Unit tests cover catalogue promotion, channel/edition/ABI selection, version comparisons, endpoint restrictions, checksums, and transfer bounds. Instrumentation tests cover prompts, snoozing, localization, retry, explicit installation, and package rejection. Run the instrumentation suite on phone and tablet emulators.

For a real installer test, use the isolated `studio.camille.updater.fixture` package:

```sh
python3 scripts/installer-fixture.py prepare emulator-5554 --locale en
# Tap Install update, grant the Android permission, return, and approve Update.
python3 scripts/installer-fixture.py verify emulator-5554
```

Repeat on a tablet with `--locale fr`. The fixture uses two APKs signed with the same test key and verifies that the version advances while a saved preference survives. It exercises the verified-cache and installation path without publishing a synthetic release. Remove only the fixture package between repeated runs; Android correctly rejects downgrading its updated installation. The fixture runner refuses physical-device serials.
