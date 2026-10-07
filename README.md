# MZGram for Android

MZGram is an unofficial Telegram client for Android that keeps deleted and edited messages, adds a ghost mode and many small extras.

> **Disclaimer.** MZGram is an unofficial client. It is not affiliated with, endorsed by or supported by Telegram. It is a fork of [Telegram for Android](https://github.com/DrKLO/Telegram) (the desktop version is a fork of [Telegram Desktop](https://github.com/telegramdesktop/tdesktop): [MZGram Desktop](https://github.com/dmytrokurochkin/MZGram-Desktop)). MZGram uses its own name and its own `api_id`. The Telegram name and logo are trademarks of Telegram, and MZGram does not use the Telegram logo as its own; the app icon is still the one inherited from upstream and is to be replaced.

## Features

All MZGram features are in **Settings > MZGram**, grouped by topic. Every MZGram text is available in English and Ukrainian.

### Archive of deleted and edited messages

- Keeps a local copy of another person's message before it is deleted, in every private chat, group, channel and secret chat. On by default.
- Keeps every earlier revision of an edited message; the message history screen shows them, tap a revision to copy its text.
- Deleted messages and removed media stay in the chat, also in topics, threads and secret chats.
- View-once photos and videos are kept with their file, also unopened ones.
- Separate switches for media, formatting, reactions and chats with bots.
- Media of saved messages is copied to `Downloads/MZGram/Saved Attachments`, with no size limit and no total quota.
- Your own messages are never saved.
- Editable marks before the time of a deleted or edited message; deleted messages are drawn at 75% opacity (switchable).
- Clear the archive, export it to a file and import it back.
- Clear Telegram's local database without touching the archive.

### Protected content

- In chats and channels that restrict saving content: forward, save and copy messages and media, and take screenshots. Such messages are sent as new messages without the original sender.

### Ghost mode

- Others do not see that you read their messages or that you are typing.
- Delay sending outgoing messages, so sending right away does not show you online.
- Send every message without sound while ghost mode is on.
- Offer to turn ghost mode on before opening a story.

### Message menu

- Copy Photo, Delete Downloaded File, Save Message (to Saved Messages), Set a reminder, Repeat, Open in... (videos), Forward without sender.
- Details: ids, sender, dates, forward source, file, media and data center of a message. For your own messages it shows when the other side read them.
- QR Code: if a downloaded photo holds a QR code, it shows the link or text. Scanning runs on the device.

### Media and calls

- Ask before calling.
- Disable the instant camera in the attach menu.
- Prefer original video quality.
- Auto pause video when the app goes to the background.
- Confirm voice and round video messages before sending.
- Media preview on chat avatar long-press.

### Interface

- Folder tabs at the bottom of the chat list.
- Hide the bottom navigation bar.
- Hide Stories.
- Open Archive on pull down.
- Exact numbers instead of rounded ones (4777 instead of 4.8K).
- Message times with seconds.
- Disable the greeting sticker in empty chats.
- Hide the bottom button in channels where you cannot post.
- Switches for the predictive back animation and the "gooey" avatar animation.
- For people who hide their last seen, an approximate last seen from what this device saw.

### Themes

- The color theme list in Chat settings always shows the built-in themes (Classic, Day, Night, Tinted) and the emoji themes.

### Ads and filters

- Disable sponsored messages in channels and the promo banner in the chat list.
- Zalgo filter: removes stacked combining marks from names, chat titles and message text.

### Notifications through UnifiedPush

- Background notifications through a [UnifiedPush](https://unifiedpush.org) distributor: an app such as ntfy or Sunup, or the built-in Google FCM distributor (Google Play Services or microG).
- Notifications go through a gateway that you can change; the keys stay on the device, so the gateway cannot read them.
- Notification diagnostics with a test push.

## Download

Releases will be published on the [Releases](https://github.com/dmytrokurochkin/MZGram-Android/releases) page. There are no releases yet.

## Build

The working branch is `mzgram`.

Requirements: JDK 21, Android SDK 36, Android NDK 27.2.12479018 (exact version), CMake 3.22.1. Android Studio is optional.

1. Clone the `mzgram` branch with its submodules:
   ```bash
   git clone --recursive --shallow-submodules -b mzgram https://github.com/dmytrokurochkin/MZGram-Android.git
   ```
2. Get your own `api_id` and `api_hash` at https://my.telegram.org/apps and put them into `APP_ID` and `APP_HASH` in `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`. Do not commit them.
3. Signing: the repo contains a dummy `TMessagesProj/config/release.keystore`. For your own builds, create your own keystore, put it there and set `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_STORE_PASSWORD` in `gradle.properties`. Do not commit them.
4. Firebase: the build applies the Google Services plugin, so each app module needs a `google-services.json` whose `package_name` matches the application id. The repo contains dummy files for the default ids. If you change `APP_PACKAGE` in `gradle.properties`, create a project in the [Firebase console](https://console.firebase.google.com/) for your ids and replace the files. Background notifications in MZGram go through UnifiedPush, not through Telegram's Firebase push.
5. Build:
   ```bash
   ./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone   # signed, minified
   ./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug        # for development
   ```
   The APK is under `TMessagesProj_AppStandalone/build`. `assembleAfatRelease` gives an unsigned APK that does not install.

CI builds the APK and runs the instrumented tests on every push to `mzgram` (`.github/workflows/mzgram-android.yml`). It reads the credentials from the repository secrets `TELEGRAM_API_ID` and `TELEGRAM_API_HASH`.

Notes from the upstream README (API documentation, BuildVars, localization) are in [docs/upstream-notes.md](docs/upstream-notes.md).

## Upstream

MZGram for Android is based on Telegram for Android **12.10.1 (7038)** from [DrKLO/Telegram](https://github.com/DrKLO/Telegram). Upstream updates are taken from that repository: the `mzgram` branch is rebased onto the newer upstream release, so the fork stays a readable set of patches on top of it.

Every fork commit has the `[mzgram]` prefix. To list all changes against upstream:

```bash
git log --grep='^\[mzgram\]'
```

## License

MZGram for Android is free software under the [GNU General Public License v2](LICENSE), inherited from Telegram for Android. If you distribute a modified build, you must publish its source code under the same license.

MZGram Desktop is licensed under GPLv3 with the OpenSSL exception, see its [repository](https://github.com/dmytrokurochkin/MZGram-Desktop).

## Credits

- [Telegram](https://telegram.org) and the authors of [Telegram for Android](https://github.com/DrKLO/Telegram), on whose code MZGram is built.
- The [UnifiedPush](https://unifiedpush.org) project for its Android connector and embedded FCM distributor libraries.
