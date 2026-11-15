# MZGram for Android (exteraless base)

MZGram is an unofficial Telegram client for Android.

This branch builds MZGram on top of [exteraless](https://github.com/exteraless/exteraless),
an open source Telegram client based on Telegram for Android 13.2.2. MZGram's own features
are being moved onto this base step by step.

* Package name: `org.mzgram.messenger` (installs over the MZGram release)
* 64-bit phones only (`arm64-v8a`)

## Building

The `MZGram on exteraless` workflow in `.github/workflows/mzgram-ext.yml` builds the APK on every
push to `w-ext` and signs it with the MZGram key. Locally: put `TELEGRAM_APP_ID` and
`TELEGRAM_APP_HASH` (from https://my.telegram.org) into `local.properties`, then run
`NATIVE_TARGET=arm64-v8a ./gradlew TMessagesProj:assembleRelease`.

## License

GNU General Public License v3.0, see [LICENSE](LICENSE). Based on exteraless and on
Telegram for Android; the original copyright notices are kept in the source files.
