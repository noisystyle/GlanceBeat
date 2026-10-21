# Development and testing

## Editions

`public` is the default edition and uses application ID `it.noxia.glancebeat`. Its accessory implementation is an inert adapter: no observer, battery parser, panel or special notification filter is created.

The local `personal` edition exists only when `app/src/personal/java/it/noxia/ambientplayer/AccessorySupport.java` is present. It retains application ID `it.noxia.ambientplayer`. Its sources, assets and variant-specific tests are ignored by Git and excluded by the source-export tool. Back up those local files separately before moving or cleaning a checkout.

Shared behaviour belongs in `app/src/main`. Edition adapters live in `app/src/public` and the local personal source set. Do not introduce a runtime toggle that would ship personal integration code in the public APK.

The main player activity is internal in release builds. Debug builds export it for ADB probes; probe, keep-awake and timed-finish test overrides are gated by `BuildConfig.DEBUG`.

## Unit checks and builds

```sh
./gradlew :app:testPublicDebugUnitTest :app:lintPublicRelease
./gradlew :app:assemblePublicDebug :app:assemblePublicDebugAndroidTest
./gradlew :app:assemblePublicRelease
```

Where local personal sources exist:

```sh
./gradlew :app:testPersonalDebugUnitTest :app:lintPersonalRelease
./gradlew :app:assemblePersonalDebug :app:assemblePersonalDebugAndroidTest
```

The public and personal editions can coexist, but enable only one player's automatic monitoring at a time. Building either edition does not change an installed app. A personal update must retain both the original application ID and its signing certificate.

## Emulator integration

Use a **disposable emulator**. The automation changes its lock settings and installs silent synthetic apps with music-provider package IDs. Never run it against a personal phone or an emulator containing accounts or data you need.

Build the required silent fixtures:

```sh
./gradlew -p tools/music-fixture assembleDebug
./gradlew -p tools/stability-fixture assembleDebug
./gradlew -p tools/bitchord-fixture assembleDebug
./gradlew -p tools/catalog-fixture assembleDebug
```

Run tests after selecting the serial of that disposable emulator:

```sh
python3 tools/probe_player_reliability_emulator.py --serial emulator-5554 --edition public --mode accessory --mode columns --mode open_layout
```

The `setup` suite exercises the required-permission wizard with real Android grants, denials, revocations, activity recreation and completion gating. Accessibility and Bluetooth are optional and do not block setup.

The `localization` suite renders settings and player/queue accessibility in English and Italian, checks plural forms and the English fallback, and verifies the optional local edition independently.

Other suite modes include `stability`, `continuity`, `carousel_commit`, `catalog`, `music_launch`, `bitchord` and `youtube_shuffle`. The last two require the Bitchord fixture; `catalog` and `music_launch` require the catalogue fixtures. Pass `--edition personal` to check the personal build when available.

Reports stay under ignored `work/`. Official authenticated services require separate testing; fixtures do not demonstrate a real account mutation.

## Prepare a public source snapshot

```sh
python3 tools/export_public_source.py --output work/public-source/GlanceBeat
```

This creates a new local directory from an explicit source allowlist plus a SHA-256 manifest. It excludes personal source sets, development history, diagnostic output, APKs, signing files, SDK paths and Git metadata. It never publishes, creates a remote, commits, pushes or installs anything. The output path must not already exist.

Build that snapshot independently before publishing it. Release APKs are unsigned until a release signing configuration is provided. No release key is included.

Swipe regressions use Android input injection through the complete player window (including native controls), rather than calling the gesture handler directly:

```bash
python3 tools/probe_player_reliability_emulator.py --serial emulator-5554 --edition public --mode swipes
```

This covers queued and queue-less transport, slow swipes, release after a pause, cancellation, stale track identities, unsupported commands, pending request recovery, and AOD gesture suppression.

### Real local music providers

Install the official Namida/Musicolet application in a disposable emulator and import three generated audio files named `GlanceBeat Sample 1`, `GlanceBeat Sample 2` and `GlanceBeat Sample 3`, with embedded artwork. Start playback in the real app before running:

```bash
./gradlew :app:assemblePublicDebug :app:assemblePublicDebugAndroidTest
python3 tools/probe_local_providers_emulator.py --serial emulator-5554 --provider namida
python3 tools/probe_local_providers_emulator.py --serial emulator-5554 --provider musicolet
```

Run one provider at a time and start its playback before each command. These checks use the installed application's real media session and native touch input: they create no replacement media session and require no music-service account. Reports and screenshots are written under ignored `work/local-providers/`. The suite also records brief session gaps and verifies queue selection, return to the player, adjacent artwork, cover swipes and pause/resume. It temporarily sets and removes the emulator PIN `1234`; never run it on a personal device.
