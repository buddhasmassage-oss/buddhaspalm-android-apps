# Buddhas Ride Provider Android v1.1.0

Separate Android provider app for **https://rider.buddhaspinas.com/provider/** only.

- Package: `com.buddhaspinas.buddhasride.provider`
- Does not use or modify `metro.buddhaspinas.com`.
- Foreground Online receiver checks live Buddhas Ride jobs every ~4 seconds.
- Supports Smart Next-Ride Queue notifications.
- Keeps GPS updates running while Online.
- Offline-resilient trip actions: On the Way / Arrived / Start / Complete are queued locally if the network fails and synchronized later.
- Failed GPS telemetry points are queued locally and synchronized when connection returns.
- Registers a stable Android device identifier with the Buddhas Ride provider-device approval system. The PHP server stores a one-way hash, not the raw device id.
- Identity/selfie review remains server-side. True automated biometric liveness requires a separate identity verification provider.

Build with `gradle assembleDebug` (Gradle 8.10.2 + JDK 17) or the dedicated GitHub Actions workflow.
