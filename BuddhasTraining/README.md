# Buddhas Training Android App

A new, separate Android application for **https://buddhasprovider.net/**.

This project does not replace or modify the repository's existing Admin, Provider, Customer, Tutor, School, Staff, or other Android applications.

## App identity

- App name: **Buddhas Training**
- Package: `net.buddhasprovider.app`
- Start URL: `https://buddhasprovider.net/`
- Firebase project: `buddhas-ed87a`
- FCM registration endpoint: `https://buddhasprovider.net/api/fcm-register.php`

## Push notifications

Firebase Cloud Messaging is initialized through `app/google-services.json`.
The app uploads its current FCM registration token to the website after a logged-in WebView session is available and whenever Firebase rotates the token.

The Firebase Admin SDK **service-account private key must never be committed to this repository**. Install that credential on the web server through the Notification CRUD Admin page or place it at:

`storage/private/fcm/service-account.json`

## Build

The dedicated GitHub Actions workflow builds only this project and uploads an artifact named:

`Buddhas-Training-APK`
