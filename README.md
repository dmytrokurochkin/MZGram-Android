# MZGram for Android

MZGram is an unofficial Telegram client for Android, based on Telegram for Android 13.2.2.

* Package name: `org.mzgram.messenger`
* 64-bit phones only (`arm64-v8a`)

## Building

The `MZGram` workflow in `.github/workflows/mzgram-ext.yml` builds the APK on every
push to `w-ext` and signs it with the MZGram key. Locally: put `TELEGRAM_APP_ID` and
`TELEGRAM_APP_HASH` (from https://my.telegram.org) into `local.properties`, then run
`./gradlew TMessagesProj:assembleRelease`. The build is for arm64-v8a only.

## License

GNU General Public License v3.0, see [LICENSE](LICENSE). The copyright notices in the
source files are kept, as the license requires.
