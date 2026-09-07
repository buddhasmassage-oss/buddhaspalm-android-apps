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

## iOS 1.0.1 layout parity fix
- The WKWebView is constrained to the iPhone safe area on all four edges so the Tutor logo and notification bell stay below the status bar / Dynamic Island and the bottom navigation stays above the home indicator.
- The wrapper explicitly requests mobile webpage rendering and exposes `window.BuddhaTutorNative`, allowing Tutor PWA-aware frontend behavior to recognize the native iOS wrapper.
- A native-only layout bridge keeps the Tutor `.app-wrapper` at the same responsive width used by the PWA, prevents horizontal overflow, and normalizes iOS text autosizing.
- Form controls use a minimum 16 px text size on small screens to prevent Safari/WKWebView focus zoom from making the whole page suddenly look oversized.
- The initial Tutor page is revalidated instead of blindly reusing old wrapper HTML, helping new frontend design updates appear after rebuilding the app.
- Tutor navigation is intentionally restricted to `tutor.buddhaspalm.net`; unrelated websites open outside the app.

## Firebase iOS setup
Use the same Firebase project you already use for Android, but add an Apple/iOS app with bundle ID:

`net.buddhaspalm.tutor`

Download `GoogleService-Info.plist` and place it at:

`BuddhaStudyTutorIOS/GoogleService-Info.plist`

Then upload your Apple Push Notification Authentication Key in Firebase Console > Project Settings > Cloud Messaging for the iOS app.

## Apple signing
A simulator build can be created without signing. An installable iPhone IPA/TestFlight/App Store build requires your Apple Developer signing/provisioning setup.
