# Signing and verification

Official public release APKs are signed with a dedicated GlanceBeat release key. The private key and credentials are never included in the repository, source archives or release assets.

## Release certificate

The SHA-256 fingerprint of the public release certificate is:

```text
01ba0c12d363f18b3d475ca36cb15e9dba5b85889e3d9a220e6c699704dbf203
```

The v0.1.0-beta03 GitHub release includes an APK and SHA256SUMS.txt. Verify the downloaded file with `shasum -a 256`, and verify its signature and certificate fingerprint using Android SDK Build Tools:

```sh
apksigner verify --verbose --print-certs GlanceBeat-0.1.0-beta03.apk
```

## Build your own signed APK

Set all four environment variables before invoking `./gradlew :app:assemblePublicRelease`:

- `GLANCEBEAT_KEYSTORE`: path to your own keystore.
- `GLANCEBEAT_KEYSTORE_PASSWORD`: keystore password.
- `GLANCEBEAT_KEY_ALIAS`: signing-key alias.
- `GLANCEBEAT_KEY_PASSWORD`: signing-key password.

If none are set, the release APK is unsigned. Partial configurations fail rather than silently creating an unsigned APK. Signing is scoped to the public flavour; local personal builds keep their existing identity.

Your own key cannot update an installation signed with the official release key. Keep your private key and credentials backed up securely if you distribute your own builds. Never commit them or attach them to a GitHub release.

## 0.1.0-beta02 — Swipe fixes

- Artwork swipes now use the music app’s standard next/previous commands when no selectable queue neighbor is available. Adjacent cover artwork still depends on metadata supplied by the music app.
- Upward unlock swipes accept slow drags and a pause before release, including gestures starting on native buttons. Android continues to handle authentication.
- Pending track changes prevent duplicate commands and recover if the provider does not confirm the change.

Validated with 23 full-window touch checks on each of two emulator screen sizes, 121 existing carousel/layout/keyguard checks, 13 signed-release smoke checks, and 123 public unit tests. No music-service login was used; these checks exercise controlled media sessions.

Install the new signed APK over the previous official beta to keep existing settings. Ambient-mode swipes remain intentionally disabled: double tap to show the interactive controls first.

## 0.1.0-beta03 — Namida and Musicolet

- Added Namida and Musicolet to the recognised music apps.
- Verified their real local-audio queues, including artwork, selecting an exact track and returning to the player. Cover swipes and pause/resume were also verified.
- Queue selection now tolerates a brief media-session disappearance, as observed with Namida, while retaining its existing timeout and rejecting a different live session.

Validation: 15 checks passed for each real app on an Android 16 / API 36 emulator using three generated local MP3s without a service login. Namida 7.1.2 and Musicolet 6.14.1 build540 were tested. All 29 swipe/session regression checks and 123 public unit tests passed; public release lint completed without errors. Online playback and provider favourite/shuffle operations were not verified in this test.

Install over the previous official beta to keep your settings. Double tap into interactive mode, then swipe right outside the artwork row to open the queue.
