# BuddhaStudy Tutor 1.0.9 — Uptodown Release Build

Package: `net.buddhaspalm.tutor`  
Version code: `9`  
Version name: `1.0.9`

## What changed from the WebView-first 1.0.8 build

The launcher now opens a native Android BuddhaStudy dashboard instead of loading the website immediately.

Native functionality now visible to reviewers/users:

- Native dashboard / app home
- Dedicated Tutor learning activity with native top and bottom navigation
- Native Firebase notification inbox
- Native Android DownloadManager integration and download history
- Biometric/fingerprint sign-in using Android Keystore + BiometricPrompt
- Offline / retry screen around the Tutor learning portal
- Native Android share action
- File chooser, camera and microphone integration for LMS activities
- Deep-link handling for `https://tutor.buddhaspalm.net/...`
- Native About screen with package/version/capabilities

## IMPORTANT: use a private RELEASE signing key

Do not upload a debug-signed APK to Uptodown.

1. In Android Studio open **Build > Generate Signed App Bundle or APK**.
2. Choose **APK**.
3. Create or select your permanent private `.jks` release key.
4. Keep the `.jks`, alias and passwords backed up securely. Future updates to `net.buddhaspalm.tutor` must use the same release key.
5. Build the `release` variant.

Alternatively, copy `keystore.properties.example` to `keystore.properties`, enter your private values, and build the release variant. The real properties and key files are excluded by `.gitignore`.

## Recommended Uptodown screenshots

Show the native functionality, not only the website:

1. Native BuddhaStudy dashboard
2. Tutor Learning Portal with the native top/bottom bars visible
3. Native Notification Inbox
4. Native Course Downloads
5. Biometric/Fingerprint login prompt or security screen
6. Native offline/retry state (optional)

## Suggested reviewer note

"BuddhaStudy Tutor is an Android learning companion for the BuddhaStudy Massage Therapy LMS. The app includes a native dashboard, Firebase push notification inbox, Android DownloadManager integration, biometric authentication protected by Android Keystore, native sharing/deep links, offline recovery, file/media integration, and a dedicated secured learning portal. The embedded web content is one part of a broader native Android experience."

## Launcher icon note

The uploaded 1.0.8 source contained only a 64x64 launcher image. Version 1.0.9 preserves the same artwork and generates the standard Android density variants. For the Uptodown store listing, a genuine original 512x512 or larger logo source is still preferable if one is available; do not stretch a visibly blurry screenshot as the store icon.
