# Buddhas Ride Android v1.0.0

A new, isolated Android APK project for **https://rider.buddhaspinas.com/**.

- App name: **Buddhas Ride**
- Package/applicationId: `com.buddhaspinas.buddhasride`
- Min SDK: 23
- Target/compile SDK: 35
- Java: 17
- Does not replace or modify the existing Buddhas Palm, Tutor, School, Provider, Admin, Customer, or Hub Android projects.

## Included native capabilities

- Secure WebView for `rider.buddhaspinas.com`
- Persistent cookies/session login
- JavaScript + DOM storage
- Leaflet/Mapbox geolocation through Android location permission
- Camera/gallery file uploads for avatars, payment proofs, documents, and photos
- Microphone/camera WebView permission bridge
- Android DownloadManager support
- External `tel:`, `mailto:`, `sms:`, `geo:`, `market:`, and `intent:` links
- Android native share bridge and `navigator.share` fallback
- HTTPS deep links for `rider.buddhaspinas.com`
- Android 13+ notification permission
- Optional native OneSignal SDK integration
- Buddhas Ride blue splash screen and supplied Buddha motorcycle icon

## OneSignal

Set the native Android OneSignal App ID in:

`app/src/main/res/values/strings.xml`

```xml
<string name="onesignal_app_id">YOUR-ONESIGNAL-APP-ID</string>
```

The field is intentionally blank by default, so the app can build and run without exposing a private configuration value. Configure the Android/FCM platform in the same OneSignal app before relying on native push delivery.

## Build

With Android SDK 35, Java 17 and Gradle 8.10.2 available:

```bash
gradle assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

The GitHub Actions workflow in this branch builds only this Buddhas Ride project and uploads `Buddhas Ride v1.0.0.apk` as an artifact.
