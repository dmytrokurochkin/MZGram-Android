# Notes from the upstream README

These notes come from the README of [Telegram for Android](https://github.com/DrKLO/Telegram). They are kept here for reference. The MZGram build steps are in the main [README](../README.md).

## Telegram's requirements for developers

Telegram asks all developers who use its API and source code to:

1. [Obtain their own api_id](https://core.telegram.org/api/obtaining_api_id) for their application.
2. Not use the name Telegram for their app, or make sure users understand that it is unofficial.
3. Not use the standard Telegram logo (white paper plane in a blue circle) as their app's logo.
4. Study the [security guidelines](https://core.telegram.org/mtproto/security_guidelines) and take good care of their users' data and privacy.
5. Publish their code too, to comply with the licenses.

## API and protocol documentation

- Telegram API manuals: https://core.telegram.org/api
- MTProto protocol manuals: https://core.telegram.org/mtproto

## Reproducible builds and dummy files

To support [reproducible builds](https://core.telegram.org/reproducible-builds), the upstream repository contains a dummy `release.keystore`, dummy `google-services.json` files and filled values in `BuildVars.java`. Replace all of them with your own before publishing your own APKs.

## BuildVars.java

`TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` holds the values a build needs, such as `APP_ID` and `APP_HASH`. The file has a link next to each value that shows where to get it. Fill them in your working copy and do not commit your own values.

## Upstream compilation guide (summary)

Upstream builds with Android Studio 2025.1.4, Android NDK 27.2.12479018 and Android SDK 36:

1. Clone the source code with its submodules. If you forgot `--recursive`, run `git submodule init && git submodule update --init --recursive --depth=1`.
2. Copy your `release.keystore` into `TMessagesProj/config`.
3. Fill `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_STORE_PASSWORD` in `gradle.properties`.
4. In the [Firebase console](https://console.firebase.google.com/), create Android apps for your application ids, turn on Firebase messaging and download `google-services.json`.
5. Open the project in Android Studio (open it, do not import it).
6. Fill the values in `BuildVars.java`.

## Localization

Telegram's own translations are managed at https://translations.telegram.org/en/android/. MZGram's own texts (English and Ukrainian) are in `TMessagesProj/src/main/res/values/mzgram_strings.xml` and `TMessagesProj/src/main/res/values-uk/mzgram_strings.xml`.
