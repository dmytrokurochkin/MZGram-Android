#!/usr/bin/env bash
# The "Ask AI" window test on a newer emulator, whose WebView has profiles
# (androidx.webkit MULTI_PROFILE). The API 34 image of the main run has
# WebView 113 without them: there MZGramAiWindowTest only checks that the
# window does not open the site. Here it must find profiles
# (mzAiProfiles=required) and put the text into each site's field.
# Called from the "MZGram instrumented tests" CI job, after mzgram_tests.sh.
set -u

PKG=org.telegram.messenger.mzgram.test
REPORT=TMessagesProj_AppTests/build/reports/androidTests/connected
status=0

# This run replaces the report of the main run: that one is kept beside it
if [ -d "$REPORT" ]; then
    rm -rf "$REPORT-main"
    cp -r "$REPORT" "$REPORT-main"
fi

adb logcat -c
./gradlew :TMessagesProj_AppTests:connectedAfatDebugAndroidTest --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.class=$PKG.MZGramAiWindowTest \
    -Pandroid.testInstrumentationRunnerArguments.mzAiProfiles=required || status=1

mkdir -p mzgram-screens-ai
timeout 120 adb pull /data/local/tmp/mzgram-screens/. mzgram-screens-ai/ > /dev/null 2>&1
ls -l mzgram-screens-ai
timeout 120 adb logcat -d > logcat-ai.txt
echo "===== MZGram AI logcat ====="
grep -E "MZGram AI test|TestRunner" logcat-ai.txt | tail -100
exit $status
