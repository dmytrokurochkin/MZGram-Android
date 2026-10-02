#!/usr/bin/env bash
# Runs the MZGram instrumented tests on the already-booted emulator.
# Called from the "MZGram instrumented tests" CI job.
#
# 1. Every test in org.telegram.messenger.mzgram.test except the live one.
# 2. MZGramDeletedArchiveLiveTest, with mzgram_e2e_peer.py driving the
#    second Telegram test-server account from this host -- only when the
#    preflight step managed to sign in (e2e-preflight-ok exists).
#
# Exit code is non-zero if either run fails. logcat goes to logcat-full.txt.
set -u

PKG=org.telegram.messenger.mzgram.test
LIVE=$PKG.MZGramDeletedArchiveLiveTest
status=0

adb logcat -c

./gradlew :TMessagesProj_AppTests:connectedAfatDebugAndroidTest --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.package=$PKG \
    -Pandroid.testInstrumentationRunnerArguments.notClass=$LIVE || status=1

if [ ! -f e2e-preflight-ok ]; then
    echo "::warning::Live E2E test skipped: the test-server login preflight did not pass."
elif [ -n "${API_ID:-}" ] && [ -n "${API_HASH:-}" ]; then
    rm -f e2e-phones.txt
    python3 .github/scripts/mzgram_e2e_peer.py e2e-phones.txt > e2e-peer.log 2>&1 &
    peer=$!
    for i in $(seq 1 180); do
        [ -s e2e-phones.txt ] && break
        kill -0 $peer 2>/dev/null || break
        sleep 2
    done
    if [ -s e2e-phones.txt ]; then
        read -r phoneA phoneB code basicChatId channelId < e2e-phones.txt
        ./gradlew :TMessagesProj_AppTests:connectedAfatDebugAndroidTest --console=plain \
            -Pandroid.testInstrumentationRunnerArguments.class=$LIVE \
            -Pandroid.testInstrumentationRunnerArguments.mzPhoneA=$phoneA \
            -Pandroid.testInstrumentationRunnerArguments.mzPhoneB=$phoneB \
            -Pandroid.testInstrumentationRunnerArguments.mzCode=$code \
            -Pandroid.testInstrumentationRunnerArguments.mzBasicChatId=$basicChatId \
            -Pandroid.testInstrumentationRunnerArguments.mzChannelId=$channelId || status=1
    else
        echo "::error::E2E peer could not prepare test accounts"
        status=1
    fi
    kill $peer 2>/dev/null
    echo "===== e2e peer log ====="
    cat e2e-peer.log
else
    echo "::warning::No API credentials: live E2E test skipped."
fi

# Screenshots MZGramDeletedInChatTest saves of the rendered message cells.
mkdir -p mzgram-screens
for png in $(adb shell "find /sdcard/Android/data -name 'mzgram-*.png' 2>/dev/null" | tr -d '\r'); do
    adb pull "$png" mzgram-screens/ > /dev/null
done
ls -l mzgram-screens

adb logcat -d > logcat-full.txt
echo "===== MZGram logcat ====="
grep -E "MZGramArchiveTest|MZGramE2E|MZGram|TestRunner" logcat-full.txt | tail -400
exit $status
