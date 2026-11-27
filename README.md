# Camille Android Updater

A shared Android library for apps distributed through https://download.camille.studio. Native update checks and verified installation require Android 8 or later. Version 1.0.5 can also be integrated into apps supporting Android 5–7: manual checks open the app’s public release page on those devices, and automatic checks remain disabled. Runtime code has no external dependencies.

The [1.0.5 source](https://github.com/CamilleHbp/android-updater/tree/v1.0.5) builds the library in Tasks, Keyboard and Media. [Version 1.0.6](https://github.com/CamilleHbp/android-updater/tree/v1.0.6) also lets Maps choose its update-prompt activity.

## Integration

Pin a verified `camille-updater-1.0.5.aar` build in the application's `libs` directory, alongside its SHA-256 file. Commit both files so builds need no GitHub credentials. The source and fixes live here; do not copy Java sources into consuming applications.

```kotlin
android {
    defaultConfig {
        resValue("string", "camille_update_app_id", "groceries")
    }
}
dependencies {
    implementation(files("libs/camille-updater-1.0.5.aar"))
}
```

Use catalogue IDs `groceries`, `vervetwodo`, or `podcini`. For Podcini, each product flavor additionally sets `resValue("string", "camille_update_edition", "free")` or `"play"`. Initialization and the private APK provider are merged automatically from the AAR manifest. No activity code is needed. Include `app/libs/**` in Android build freshness inputs.

Update the AAR and checksum together when adopting a new library version. Use the source-build commands below for 1.0.5; a local build does not imply that a matching GitHub release has been published. For published versions, fetch both files from the matching private GitHub release and verify the adjacent checksum.

## Behavior

The launcher activity checks the server on every foreground entry without a time gate. Concurrent checks are coalesced. Nothing runs while the app is closed. An explicit `CamilleUpdater.checkNow(activity)` call checks immediately, with localized checking, up-to-date, and network-error feedback. Wire this to a Check for updates setting. A new build shows a localized prompt. Later dismisses only the current prompt; the next foreground entry checks again. Old saved deadlines are discarded. Accepting opens a download screen with progress and retry. Once the download is verified, Android’s installer opens automatically. Granting unknown-source permission continues installation on return, without another in-app tap. Cancelling Android’s prompt or denying permission leaves an explicit retry action; activity recreation does not reopen a cancelled prompt. Android retains installation approval; the library never silently installs.

Activity recreation preserves the active transfer. If Android kills the process, reopening the update screen verifies a completed cached file or restarts an incomplete transfer. Cache lives in the app's private cache directory. Failed downloads never replace the installed application.

The updater reads the promoted Android release, independently of desktop releases, and preserves the installed debug/release channel and configured edition. All apps prefer ARM64 or x64 assets according to the device ABI order, with universal fallback. Keep original unqualified Android URLs universal for older updater versions. Numeric release IDs must be `<versionName>-<versionCode>`; the numeric suffix must equal the APK's Android version code. Same-name releases with a higher code are updates; downgrades and reinstalls are rejected.

Downloads are restricted to immutable HTTPS URLs on `download.camille.studio`, without redirects. Before installation, the library verifies byte size, SHA-256, package ID, version name/code, minimum Android version, and the installed signing certificate. Signing-key rotation is rejected until the library explicitly supports a verified rotation lineage. Android also enforces its own package compatibility and signatures.

English and French resources are included. The prompt follows the host activity's locale, and the download activity carries that locale through Android's installation-permission flow.

## Build and tests

Use JDK 25, the Android 35 SDK, and the Gradle wrapper:

```sh
./gradlew :updater:testDebugUnitTest :updater:assembleRelease
./gradlew :updater:connectedDebugAndroidTest
```

To export the architecture-aware library after the unit tests and release build succeed:

```sh
mkdir -p build/releases/1.0.5
cp updater/build/outputs/aar/updater-release.aar build/releases/1.0.5/camille-updater-1.0.5.aar
(cd build/releases/1.0.5 && shasum -a 256 camille-updater-1.0.5.aar > camille-updater-1.0.5.aar.sha256)
```

Copy the AAR and checksum together into each consuming app and update its pinned dependency. Rebuild and verify each affected app before distributing its APKs.

Unit tests cover catalogue promotion, channel/edition/ABI selection, version comparisons, endpoint restrictions, checksums, and transfer bounds. Instrumentation tests cover prompts, dismissal without snoozing, localization, retry, automatic installer handoff without repeated prompts, and package rejection. Run the instrumentation suite on phone and tablet emulators.

For a real installer test, use the isolated `studio.camille.updater.fixture` package:

```sh
python3 scripts/installer-fixture.py prepare emulator-5554 --locale en
# Grant the Android permission, return, and approve Update.
python3 scripts/installer-fixture.py verify emulator-5554
```

Repeat on a tablet with `--locale fr`. The fixture uses two APKs signed with the same test key and verifies that the version advances while a saved preference survives. It exercises the verified-cache and installation path without publishing a synthetic release. Remove only the fixture package between repeated runs; Android correctly rejects downgrading its updated installation. The fixture runner refuses physical-device serials.

## Launch regression test

Build the isolated fixture with `-PupdaterFixture=true -PupdaterCatalogue=groceries :installerFixture:assembleOldDebug`, then run `python3 scripts/test-launch.py emulator-5554 fixtures/installer/build/outputs/apk/old/debug/installerFixture-old-debug.apk`. This verifies a cold launch, immediate foreground re-entry after Later, and migration from persisted future deadlines against the live catalogue. Repeat with `-PupdaterCatalogue=vervetwodo` or `-PupdaterCatalogue=podcini -PupdaterEdition=free`. The fixture cannot install these apps because its package identity differs. Use `-PupdaterAar=/absolute/path/to/older.aar` for comparisons with an older library.

## License

The original updater code, including earlier revisions, is available under the [Apache License 2.0](LICENSE). The Gradle wrapper retains its Apache 2.0 license and upstream notices.
