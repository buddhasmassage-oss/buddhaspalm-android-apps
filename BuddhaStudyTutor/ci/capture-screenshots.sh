#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1080x1920
adb shell wm density 420
adb install -r BuddhaStudyTutor/app/build/outputs/apk/debug/app-debug.apk
adb install -r BuddhaStudyTutor/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant net.buddhaspalm.tutor android.permission.POST_NOTIFICATIONS
adb shell am instrument -w net.buddhaspalm.tutor.test/net.buddhaspalm.tutor.StoreScreenshotRunner | tee screenshot-test.txt
grep -q 'STORE_SCREENSHOTS_OK' screenshot-test.txt
mkdir -p store-screenshots
adb pull /sdcard/Android/data/net.buddhaspalm.tutor/files/store-screenshots/. store-screenshots/
