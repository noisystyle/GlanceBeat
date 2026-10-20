# Privacy and permissions

This describes the public edition's current implementation. It is not a statement about the separate music apps or the hosts from which artwork is downloaded.

## On-device processing

GlanceBeat obtains media-session metadata, playback state and supported controls through Android's notification-listener access. It uses notification package/icon metadata and launch actions to draw notification shortcuts. It does not display or parse message titles or bodies in the public edition.

Notification and session objects, launch actions, decoded artwork and the visible queue are held in memory. Settings are stored in the app's private preferences. Optional driving mode stores the selected paired Bluetooth device's name and address locally. No active Bluetooth discovery or accessory battery scanning is performed by the public edition.

The app has no account system, advertising SDK, analytics SDK or GlanceBeat server. It does not upload notification data or playback history. Android backup is disabled in the application manifest.

## Network access

The queue can request artwork from HTTPS URLs supplied by a music session. Those hosts receive the network request and associated connection information, such as the device's public IP address. Requests have connection/read timeouts, size limits and limited HTTPS redirects. The app does not proxy these requests through a GlanceBeat service.

## Support link

The optional support button opens `https://buymeacoffee.com/marcoprattv` in an external browser only when tapped. No donation or payment SDK is embedded in GlanceBeat. The external site and browser have their own privacy policies.

## Lock screen and Continuity

The optional accessibility service receives window-state events and temporarily draws a snapshot of GlanceBeat's own player to bridge interruptions. Its configuration requests no screen-content retrieval, key filtering or gesture capability. It does not capture other apps' screen contents.

Android performs unlocking before the player forwards a notification action or opens a music app. An overlay does not change the device's authentication policy.

## Diagnostics and removal

Development builds can write technical state to Android logs and a bounded local visibility-event history. Emulator tests also save local reports and screenshots inside their test app's data. Detailed player-state tracing and its visibility-history writes are disabled in release builds. Do not share raw diagnostics without reviewing them for personal information.

Disable the player to stop monitoring, revoke notification/overlay/accessibility/Bluetooth access through Android settings, or clear app data to remove saved preferences. Uninstalling removes the app's private data. Music services and artwork hosts have their own privacy policies.
