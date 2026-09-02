# BuddhaStudy Tutor for iPhone

Native iOS wrapper for `https://tutor.buddhaspalm.net/`.

## Included
- WKWebView with persistent website login/cookies
- Camera, microphone, file/photo upload permissions
- Same-domain navigation inside the app; external links open in the system browser
- Firebase Cloud Messaging integration hooks
- FCM token bridge to the Tutor website (`window.bspRegisterNativeFcmToken`)
- Foreground notification banners/sounds
- Notification deep-links back into `tutor.buddhaspalm.net`
- Buddhas Training app icon

## Firebase iOS setup
Use the same Firebase project you already use for Android, but add an Apple/iOS app with bundle ID:

`net.buddhaspalm.tutor`

Download `GoogleService-Info.plist` and place it at:

`BuddhaStudyTutorIOS/GoogleService-Info.plist`

Then upload your Apple Push Notification Authentication Key in Firebase Console > Project Settings > Cloud Messaging for the iOS app.

## Apple signing
A simulator build can be created without signing. An installable iPhone IPA/TestFlight/App Store build requires your Apple Developer signing/provisioning setup.
