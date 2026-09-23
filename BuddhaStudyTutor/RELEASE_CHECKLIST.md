# Release checklist

1. Open the `BuddhaStudyTutor` folder in Android Studio.
2. Let Gradle sync complete.
3. Test the DEBUG build on a real Android phone:
   - Native dashboard opens first.
   - Continue Learning opens the Tutor portal.
   - Login/session works.
   - Biometric registration/authentication still works.
   - FCM arrives and appears in Notification Inbox.
   - A course file downloads and appears in Course Downloads.
   - Camera/microphone/file chooser work where the LMS asks for them.
   - External links open outside the app; Tutor links remain inside.
   - Turn off Wi-Fi/mobile data and confirm the native Retry screen.
4. Create/select a permanent private release keystore in Android Studio.
5. Build > Generate Signed App Bundle or APK > APK > release.
6. Do NOT upload an APK whose signer is `CN=Android Debug`.
7. Confirm the release package is `net.buddhaspalm.tutor` and version code is 9.
8. Capture real screenshots of the native dashboard, learning portal chrome, inbox, downloads and biometric prompt.
9. Submit the signed release APK plus the text in `UPTODOWN_SUBMISSION_TEXT.md`.
10. Keep the release `.jks`, alias and passwords backed up. Every future update for the same package must use the same signing key.

Optional command-line key creation (only if you understand key backup requirements):

`keytool -genkeypair -v -keystore buddhastudy-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias buddhastudy`

Never commit the `.jks` or `keystore.properties` file to a public repository.
