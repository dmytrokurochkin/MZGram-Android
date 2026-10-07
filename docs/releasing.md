# Releasing MZGram for Android

## Versions

A release is a git tag on the `mzgram` branch:

- `vX.Y.Z`: stable release, for example `v1.0.0`;
- `vX.Y.Z-beta.N`: beta, published as a GitHub prerelease, N from 1 to 98.

MINOR and PATCH go up to 99. The tag gives the Android `versionCode`:

```
base = MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + (N for a beta, 99 for stable)
versionCode = base * 10 + 9      (the last digit is the ABI set: 9 = all four ABIs)
```

So `v1.3.0-beta.2` is below `v1.3.0`, which is below `v1.3.1-beta.1`. The release workflow refuses a tag whose code is not above every existing tag and above `APP_VERSION_CODE` in `gradle.properties` (7038, the code of the builds before the first release), so `versionCode` never goes down. The first release can be `v0.1.0` or higher; `v1.0.0` is the usual choice.

`versionName` is `X.Y.Z (upstream version)`, for example `1.0.0 (12.10.1)`.

Push builds of `mzgram` get the `versionCode` of the newest tag and the name `X.Y.Z-dev (upstream version)`, so a push build installs over the last release, and the next release installs over a push build.

## Signing key

Builds before the MZGram key were signed with `TMessagesProj/config/release.keystore`, a public development key from the upstream repository. Anyone can sign with it, so releases use a private MZGram key that lives only in GitHub Secrets.

The switch keeps all data: the APK is signed with key rotation (APK Signature Scheme v3, `.github/scripts/mzgram_sign.sh`). Its signing lineage says "the development key hands over to the MZGram key".

- Android 9 and newer: the first rotated APK installs as a normal update over a development-key build and keeps everything. From then on the installed app trusts only the MZGram key: APKs signed with the development key cannot update it any more.
- Android 5 to 8 has no key rotation and keeps checking the development key.

The CI job "MZGram instrumented tests" checks this on an emulator on every push (`.github/scripts/mzgram_rotation_check.sh`, with a throwaway key): a development-key install with data, the MZGram key alone refused, the rotated APK accepted with the data kept, then the MZGram key alone accepted and the development key refused.

Without the secrets, every build stays on the development key: the push artifact is named `MZGram-Android-debugkey` and a tag gives a prerelease named "debug key", never a stable release.

### Create the key (once)

On Windows in PowerShell, with the JDK that builds the app. Keep the folder outside any git repository.

```powershell
$dir = "$env:USERPROFILE\MZGram-signing"
New-Item -ItemType Directory -Force $dir | Out-Null
$keytool = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\keytool.exe"

# Asks for a password (twice). Use a long random one and save it in a password manager.
& $keytool -genkeypair -v -keystore "$dir\mzgram-release.jks" -storetype PKCS12 `
    -alias mzgram -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=MZGram"

# The certificate SHA-256 (asks for the password); releases show the same value in their notes
& $keytool -list -v -keystore "$dir\mzgram-release.jks" -alias mzgram | Select-String "SHA256:"
```

### Put it into GitHub Secrets

With the [GitHub CLI](https://cli.github.com/) logged in as the repository owner:

```powershell
$repo = "dmytrokurochkin/MZGram-Android"

# The keystore file, as base64 through stdin (it never shows on screen)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$dir\mzgram-release.jks")) | gh secret set ANDROID_KEYSTORE_B64 -R $repo

# These two ask for the value: paste the keystore password (a PKCS12 key has the same password)
gh secret set ANDROID_KEYSTORE_PASSWORD -R $repo
gh secret set ANDROID_KEY_PASSWORD -R $repo

gh secret set ANDROID_KEY_ALIAS -R $repo --body mzgram

gh secret list -R $repo
```

The next push to `mzgram` then gives the artifact `MZGram-Android` signed with the MZGram key (the "Sign with the MZGram key" step prints the certificate SHA-256).

### Back up the key

Without the key and its password no update can ever be signed again: users would have to uninstall MZGram and lose their data. Keep `mzgram-release.jks` and the password in at least two places outside GitHub (a password manager entry with the file attached, an offline USB drive). Never commit the keystore: `.gitignore` excludes `*.jks` and `*.b64`.

## Make a release

```bash
git fetch origin
git tag -a v1.0.0 -m "MZGram Android 1.0.0" origin/mzgram
git push origin v1.0.0
```

The workflow "MZGram Android release":

1. checks the tag (format, `versionCode` above everything before, commit on `mzgram`);
2. builds `assembleAfatStandalone` with that `versionCode` and `versionName`;
3. signs with the MZGram key and checks which key each Android version sees;
4. checks package name, `versionCode` and `versionName` in the APK;
5. creates a draft release with `MZGram-Android-X.Y.Z.apk`, `MZGram-Android-X.Y.Z-rotation.lineage`, `SHA256SUMS` and the `[mzgram]` commits since the previous release as the notes (a stable release lists the changes since the previous stable one, a beta since the previous tag);
6. publishes it once every file is uploaded.

The release bot ([mzgram-release-bot](https://github.com/dmytrokurochkin/mzgram-release-bot)) posts published stable releases to the Telegram channel.

To build and sign a version without publishing (an artifact only): run the workflow by hand from the Actions tab with the version, for example `v1.0.0`.
