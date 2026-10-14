#!/usr/bin/env bash
# Appearance across restarts of the app, on the already-booted emulator with
# the test APKs installed (mzgram_push_cold.sh installs them). Called from
# mzgram_tests.sh.
#
# Each step of MZGramAppearanceRestartTest is its own instrumentation run,
# and the app is stopped between them, so every step starts a new process
# that reads the saved theme the way a phone does after a restart:
#   chooseThemeAndAccent -> afterRestart
#   leaveTheStateOfAnEarlierBuild -> afterTheUpdate
#
# Exit code is non-zero when a step fails.
set -u

APP=org.telegram.messenger.web
TEST=org.telegram.messenger.mzgram.test.MZGramAppearanceRestartTest
INSTRUMENTATION=$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s/^instrumentation:\([^ ]*\) (target=$APP)\$/\1/p" | head -1)
echo "instrumentation: $INSTRUMENTATION"
status=0

for step in chooseThemeAndAccent afterRestart leaveTheStateOfAnEarlierBuild afterTheUpdate; do
    adb shell am force-stop $APP
    sleep 2
    adb shell am instrument -r -w -e mzAppearanceRestart 1 -e class "$TEST#$step" "$INSTRUMENTATION" > "appearance-$step.txt"
    if grep -q "INSTRUMENTATION_CODE: -1" "appearance-$step.txt" && ! grep -qE "FAILURES|AssumptionViolatedException|Process crashed" "appearance-$step.txt"; then
        echo "Appearance restart: $step passed"
    else
        echo "::error::Appearance restart: $step failed"
        grep -E "INSTRUMENTATION_STATUS: (stack|test)=|INSTRUMENTATION_CODE|FAILURES|Error|at org.telegram" "appearance-$step.txt" | head -20
        status=1
    fi
done
adb shell am force-stop $APP
exit $status
