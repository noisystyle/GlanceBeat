# GlanceBeat

**An ambient music controller for your Android lock screen.**

GlanceBeat puts artwork, playback controls and your music queue in a calm interface above the lock screen. It controls the music app you already use through Android media sessions. It does not stream music, replace your music account or provide a music catalogue.

**Status: experimental beta.** [Download the latest beta](https://github.com/noisystyle/GlanceBeat/releases/tag/v0.1.0-beta04). The interface supports English and Italian, follows the system language and uses English as the fallback. On Android 13 and newer, you can also choose the app language in Android settings.

## Experience

- Dim ambient mode and an interactive mode, switched with a double tap.
- Animated artwork carousel, adjacent covers when available, and soft background transitions.
- Playback controls, hold-to-seek with haptic feedback, and provider-dependent favourites and shuffle.
- A frosted queue with artwork and track selection when the music app exposes it.
- Clock, phone battery and notification shortcuts. Android handles authentication before opening an app.
- Dynamic system colours, ambient brightness adjustment and pocket detection on compatible devices.
- A two-column queue/player layout on large displays, with a button to swap columns.
- An optional driving mode tied to a paired Bluetooth device.
- An optional Continuity accessibility service to keep the player visible during brief interruptions from other apps.

## Requirements and limits

Android 12 / API 31 or newer is required. Behaviour depends on the phone's lock screen, background restrictions, sensors and the active music app.

**This is an app-drawn overlay with the display on, not the manufacturer's hardware always-on display.** Lower frame-rate requests, dimming, static rendering and position shifts reduce work, but native AOD battery consumption and burn-in protection are not guaranteed. Pocket detection cannot guarantee that the physical display powers off; it hides the player behind a black surface.

Development has focused on a Samsung foldable and Android emulators. Broad device compatibility is still being established. Review the [compatibility notes](docs/COMPATIBILITY.md) before relying on a specific provider feature.

## Get started

Install the signed public APK from [GitHub Releases](https://github.com/noisystyle/GlanceBeat/releases/tag/v0.1.0-beta04), or build a development APK using the instructions below. Open GlanceBeat and follow the initial setup wizard. Notification access and permission to display over other apps are required: each step stays locked until Android confirms the permission. After setup, enable the player, start music in a supported app, then lock the phone. If a required permission is revoked, the next visit to settings returns to setup. Continuity and Bluetooth remain optional.

Double tap to show or dim the controls. Swipe across the artwork row to change tracks; swipe right outside that row to open the queue. In interactive mode, swipe up or tap the upward arrow to request the native lock screen. Hold briefly on the progress bar before seeking. Tap the current cover to request the music app's player screen; Android handles unlocking first. Opening the exact track screen depends on the provider's session activity or a supported track link.

Artwork swipes also work when a music app exposes next/previous commands without publishing its queue. Neighboring covers and queue entries require metadata from the music app. Upward swipes accept slow drags and a pause before release.

On some Android versions, a sideloaded app's notification or accessibility settings need to be enabled through the system's “Allow restricted settings” option first. Enable the optional Continuity service only if you want it to cover brief interruptions. Disabling the player stops its monitoring; you can also revoke permissions in Android settings.

### Unlock your phone

1. Double tap the dim player to show the interactive controls.
2. **Swipe upward or tap the upward arrow** to request Android’s native unlock screen.
3. Complete the authentication Android requires: fingerprint, face, PIN, pattern or password, depending on your device and its current security policy.

The upward swipe is disabled in ambient mode. Cancelling authentication returns to the player; successful unlocking closes it. GlanceBeat does not draw a fingerprint target or replace Android authentication.

## Build

Requirements: JDK 17, Android SDK Platform 37 and the build tools requested by the Android Gradle plugin. The Gradle wrapper is included. Set `ANDROID_HOME` or create an untracked `local.properties` containing `sdk.dir` for your SDK installation.

```sh
./gradlew :app:assemblePublicDebug
./gradlew :app:testPublicDebugUnitTest :app:lintPublicRelease
```

The installable development APK is written to:

```text
app/build/outputs/apk/public/debug/app-public-debug.apk
```

The public application ID is `it.noxia.glancebeat`. The Java namespace retains its historical name, `it.noxia.ambientplayer`.

```sh
./gradlew :app:assemblePublicRelease
```

Release builds are unsigned unless all four signing environment variables are provided: `GLANCEBEAT_KEYSTORE`, `GLANCEBEAT_KEYSTORE_PASSWORD`, `GLANCEBEAT_KEY_ALIAS` and `GLANCEBEAT_KEY_PASSWORD`. Signing applies only to the public flavour. Official release APKs use a dedicated release key; debug builds use the Android development key. Keep keys and passwords outside the repository. See [signing and verification](docs/RELEASES.md).

Public and local personal builds share the core source. A personal flavour is available only when the local personal source set exists; it keeps its original application ID and settings. Personal sources are excluded from the public source export. See [development and testing](docs/DEVELOPMENT.md).

## Permissions and privacy

| Access | Purpose |
| --- | --- |
| Notification access | Discover active media sessions and show notification icons and their launch actions. |
| Display over other apps | Allow the player to appear automatically above the lock screen. |
| Nearby devices / Bluetooth | Optional driving mode: identify the paired car device selected in settings. |
| Internet | Load queue artwork from HTTPS URLs supplied by the music session. |
| Accessibility: Continuity | Optional overlay continuity. No screen-content retrieval, key filtering or gesture capability is requested. |

There is no GlanceBeat account, analytics SDK or application backend. Artwork requests contact the hosts supplied by the music app. Permissions granted by Android are broader than the data the player uses; read the [privacy description](docs/PRIVACY.md) for the implementation's scope.

## Contributing

Bug reports should include Android version, device model, music app/version and reproduction steps. Distinguish behaviour seen in an official app from behaviour seen in a synthetic test fixture. Remove personal notification text, device addresses, account identifiers and tokens from logs before sharing them.

Use a disposable emulator for automated UI tests. The test fixtures use provider package IDs and must never be installed on a personal phone. See [development and testing](docs/DEVELOPMENT.md).

## Support GlanceBeat

If you enjoy GlanceBeat, you can [buy me a coffee](https://buymeacoffee.com/marcoprattv) to support its development.

## License and credits

Original project code is licensed under [Apache License 2.0](LICENSE). Included third-party assets retain their own licences: [Phosphor Icons](https://phosphoricons.com/) use the MIT licence. See [NOTICE](NOTICE), [third-party notices](docs/THIRD_PARTY.md) and the notices included in the app's assets.

GlanceBeat is an independent project. Music-service names identify compatibility targets; their owners do not endorse this project. Music and album artwork remain the property of their respective rights holders and are not distributed with the source.
