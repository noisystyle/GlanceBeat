# Signing and verification

Official public release APKs are signed with a dedicated GlanceBeat release key. The private key and credentials are never included in the repository, source archives or release assets.

## Release certificate

The SHA-256 fingerprint of the public release certificate is:

```text
01ba0c12d363f18b3d475ca36cb15e9dba5b85889e3d9a220e6c699704dbf203
```

The v0.1.0-beta01 GitHub release includes an APK and SHA256SUMS.txt. Verify the downloaded file with `shasum -a 256`, and verify its signature and certificate fingerprint using Android SDK Build Tools:

```sh
apksigner verify --verbose --print-certs GlanceBeat-0.1.0-beta01.apk
```

## Build your own signed APK

Set all four environment variables before invoking `./gradlew :app:assemblePublicRelease`:

- `GLANCEBEAT_KEYSTORE`: path to your own keystore.
- `GLANCEBEAT_KEYSTORE_PASSWORD`: keystore password.
- `GLANCEBEAT_KEY_ALIAS`: signing-key alias.
- `GLANCEBEAT_KEY_PASSWORD`: signing-key password.

If none are set, the release APK is unsigned. Partial configurations fail rather than silently creating an unsigned APK. Signing is scoped to the public flavour; local personal builds keep their existing identity.

Your own key cannot update an installation signed with the official release key. Keep your private key and credentials backed up securely if you distribute your own builds. Never commit them or attach them to a GitHub release.
