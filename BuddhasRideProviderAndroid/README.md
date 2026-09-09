# Buddhas Ride Provider Android v1.0.0

Separate Android provider application for `https://rider.buddhaspinas.com/provider/`.

Package: `com.buddhaspinas.buddhasride.provider`

This project does not replace or modify the existing BuddhasPalmProvider APK. It is a separate Buddhas Ride provider app.

## Ride reception
When the provider turns **Online**, the website calls the native `BuddhasRideProviderAndroid.setDuty(true)` bridge. A foreground service keeps GPS fresh and polls `api/provider-live-jobs.php` every few seconds. New ride/parcel/food jobs produce a high-priority Android notification and open the Provider Jobs page for one-tap acceptance. After acceptance, `provider/ride-job.php` provides On the Way → Arrived → Start → Complete/Cancel status controls.

The server must first be upgraded to Buddhas Ride v1.5.1 provider dispatch.

This initial Provider build does not bundle a Firebase `google-services.json`, because the existing Firebase Android client is registered for the separate customer package `com.buddhaspinas.buddhasride`. Native foreground job polling works without Firebase. If native FCM is later desired for this provider package, register `com.buddhaspinas.buddhasride.provider` in Firebase and add its matching `google-services.json`.
