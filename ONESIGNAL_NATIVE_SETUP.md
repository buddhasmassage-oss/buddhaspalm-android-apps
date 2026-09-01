# Native OneSignal for BuddhasPalm Android APKs

Both Android projects now use OneSignal Android SDK **5.9.2**.

## Apps

- Provider app: `com.buddhaspalm.provider`
- Buddhas Admin: `com.buddhaspalm.admin`
- OneSignal App ID: the same BuddhasPalm OneSignal App ID used by the website.

The Android apps call `OneSignal.login("bp-user-ID")` after the WebView confirms the logged-in BuddhasPalm user. This matches the PHP backend targeting format, so one booking notification can target the same user across Web/PWA and native Android subscriptions.

## Required OneSignal dashboard setup

Native Android push requires Google Android (FCM) to be configured in the OneSignal app:

1. Open OneSignal Dashboard.
2. Select the BuddhasPalm OneSignal app.
3. Open **Settings → Push & In-App → Google Android (FCM)**.
4. Complete the Firebase/FCM setup using the Firebase service-account credentials requested by OneSignal.
5. Do **not** add the OneSignal REST API key to the Android source or APK.

OneSignal's Android SDK handles FCM registration; this project does not require `google-services.json` just for OneSignal.

## First run on each phone

1. Install the newly built APK over the old app when possible.
2. Open the app and log in.
3. The native app shows **Enable booking notifications**.
4. Tap **Enable** and allow Android notification permission.
5. The app links the device to the logged-in account with the existing `bp-user-ID` External ID.

After this, OneSignal can deliver native notifications while the app is in the background or closed, subject to Android/FCM delivery and device battery restrictions.

## Build artifacts

The repository workflow builds both apps and uploads:

- `Buddhas Admin.apk`
- `Provider app.apk`
