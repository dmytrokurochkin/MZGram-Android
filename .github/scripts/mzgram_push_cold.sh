#!/usr/bin/env bash
# A real push through Google FCM to MZGram while its process is gone, on the
# already-booted emulator (Google Play Services). Called from mzgram_tests.sh.
#
# 1. MZGramPushColdStartTest#prepare registers the built-in Google FCM
#    distributor with a test VAPID key made here and writes out the push
#    Telegram would send for a new message.
# 2. The app's process is gone (an instrumentation run ends with it, as a
#    swipe from the recent apps does; not force-stop, after which Android
#    delivers nothing at all).
# 3. This host signs with the test key and sends the push to FCM, as the
#    gateway's /fcm/ route would, then looks for the notification.
# 4. MZGramPushColdStartTest#verify checks what the app wrote down.
#
# Exit code is non-zero when the push did not end up as a notification.
set -u

APP=org.telegram.messenger.web
TEST=org.telegram.messenger.mzgram.test.MZGramPushColdStartTest
DIR=/data/local/tmp/mzgram-push

python3 -m pip install --quiet cryptography || exit 1
read -r PRIVATE PUBLIC < <(python3 .github/scripts/mzgram_fcm_push.py key)

./gradlew :TMessagesProj_AppTests:installAfatDebug :TMessagesProj_AppTests:installAfatDebugAndroidTest --console=plain || exit 1
INSTRUMENTATION=$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s/^instrumentation:\([^ ]*\) (target=$APP)\$/\1/p" | head -1)
echo "instrumentation: $INSTRUMENTATION"
for permission in POST_NOTIFICATIONS READ_CONTACTS WRITE_CONTACTS; do
    adb shell pm grant $APP android.permission.$permission
done

adb shell rm -rf $DIR
adb logcat -c
adb shell am instrument -r -w -e mzColdStart 1 -e mzVapidPublic "$PUBLIC" -e class "$TEST#prepare" "$INSTRUMENTATION" > cold-prepare.txt
grep -E "INSTRUMENTATION_STATUS: (stack|test)=|INSTRUMENTATION_CODE|OK \(|FAILURES" cold-prepare.txt | head -20
echo "===== app log while preparing ====="
adb logcat -d -s MZGramArchiveTest UP-FCMD FirebaseReceiver UnifiedPush | tail -80
TOKEN=$(adb shell cat $DIR/token.txt 2>/dev/null | tr -d '\r')
if [ -z "$TOKEN" ]; then
    echo "::error::Push cold start: the app gave no FCM endpoint for the test key (see cold-prepare.txt)."
    exit 1
fi
adb pull $DIR/body.b64 cold-body.b64 > /dev/null

# The process the push has to start, not the one that prepared it.
adb shell input keyevent KEYCODE_HOME
sleep 3
pid=$(adb shell pidof $APP | tr -d '\r')
if [ -n "$pid" ]; then
    adb shell am kill $APP
    sleep 3
    pid=$(adb shell pidof $APP | tr -d '\r')
fi
if [ -n "$pid" ]; then
    # The task of the first screen brought the process back; end it the way
    # the system ends a process it needs the memory of.
    adb root > /dev/null
    sleep 3
    adb wait-for-device
    adb shell kill -9 $pid
    sleep 3
    pid=$(adb shell pidof $APP | tr -d '\r')
fi
echo "app process before the push: '${pid}' (empty: not running)"
[ -z "$pid" ] || echo "::warning::Push cold start: the app is still running, so this is not a cold start."

echo "FCM answer: $(python3 .github/scripts/mzgram_fcm_push.py send "$PRIVATE" "$PUBLIC" "$TOKEN" cold-body.b64)"

notified=0
for i in $(seq 1 30); do
    if adb shell dumpsys notification --noredact | grep -qF "Cold start push"; then
        notified=1
        break
    fi
    sleep 2
done
echo "notification shown: $notified; app process now: '$(adb shell pidof $APP | tr -d '\r')'"
adb shell dumpsys notification --noredact | grep -A3 "pkg=$APP" | head -20

adb shell am instrument -r -w -e mzColdStart 1 -e mzNotified $notified -e class "$TEST#verify" "$INSTRUMENTATION" > cold-verify.txt
grep -E "INSTRUMENTATION_STATUS: (stack|test)=|INSTRUMENTATION_CODE|OK \(|FAILURES" cold-verify.txt | head -20
echo "===== app log after the push ====="
adb logcat -d -s MZGramArchiveTest UP-FCMD FirebaseReceiver UnifiedPush | tail -120
if grep -q "INSTRUMENTATION_CODE: -1" cold-verify.txt && ! grep -qE "AssumptionViolatedException|FAILURES" cold-verify.txt && [ $notified = 1 ]; then
    echo "Push cold start: passed"
    exit 0
fi
echo "::error::Push cold start: failed (see cold-verify.txt and the MZGramArchiveTest lines in logcat)"
exit 1
