# Compatibility

GlanceBeat uses the active Android media session. A package being recognised does not guarantee that its session publishes a queue, rating, shuffle control, adjacent artwork or a player-screen launch action.

## Providers

| Provider | Evidence and limits |
| --- | --- |
| YouTube Music, original | Queue selection, next/previous and shuffle were exercised with local silent files in the official Android app 8.09.50. Rating was advertised but not acknowledged for those local files. Online, authenticated playback remains unverified in this test set. |
| YouTube Music, Morphe variant | Used during development on the personal device. Provider/version-specific media-session behaviour can differ from the original app. |
| Bitchord | Personal-device session diagnostics plus synthetic protocol tests. Favourite decoding supports the heart and star command-button formats, including the 1.8.1 format. Logged-in server-side favourite synchronisation was not exercised by the emulator fixtures. |
| Spotify | Official 9.1.84.2231 reached its sign-in requirement. Authenticated queue, favourite and shuffle behaviour is not verified. Synthetic session-contract tests pass. |
| Apple Music | Official authenticated playback not tested. Integration is based on Android session capabilities and synthetic tests. |
| Tidal | An older official build reached a login error; authenticated playback not tested. Synthetic session-contract tests pass. |
| Deezer | An older official build required an update; authenticated playback not tested. Synthetic session-contract tests pass. |
| Amazon Music | No suitable phone build was exercised successfully in the emulator. Synthetic session-contract tests pass. |

The tests never create accounts or bypass service authentication. Synthetic fixtures verify GlanceBeat's behaviour; they do not certify an official service's implementation.

## Controls and navigation

- Unavailable feedback and shuffle controls stay unavailable. The player follows provider callbacks instead of assuming that a command succeeded.
- Queue and artwork availability depend on the session's queue entries and metadata.
- Bitchord exposes a specific full-player launch intent. Other providers use their session activity or a validated track link where available. The final screen is controlled by the music app.
- Notification actions and music-app navigation go through native Android authentication. The player does not replace the lock screen's PIN, pattern or biometric checks.

## Unlocking on different phones

An interactive-mode upward swipe and the upward-arrow button both request native keyguard dismissal. Ambient-mode upward swipes remain disabled. This path does not depend on a side-mounted fingerprint reader.

Android chooses the available authentication UI and may require a PIN, pattern or password. GlanceBeat does not draw a fake fingerprint target or route touches to a hidden sensor. In-display fingerprint and face-unlock behaviour must be checked on physical devices; emulator PIN checks do not establish biometric compatibility. The optional Continuity overlay is dismissed before requesting authentication. Cancelled authentication returns to the player; successful authentication closes it.

[Android keyguard API](https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,%20android.app.KeyguardManager.KeyguardDismissCallback)).

## Devices

The main physical development device is a Samsung SM-F971B foldable running Android 17. Emulator checks also cover API 36, narrow cover-display proportions and large two-column layouts. This is a development test set, not a certification of all Samsung or foldable devices.

Sensor placement, pocket detection, low refresh-rate behaviour and automatic launch must be checked on each device. OEM background restrictions and lock-screen changes can affect continuity. The public app has no dependency on Shizuku, root or ADB for normal use.
