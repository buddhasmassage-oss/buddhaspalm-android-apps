# BuddhaStudy Tutor 1.0.9

- Changed launcher from immediate WebView loading to a native Android dashboard.
- Moved Tutor LMS WebView functionality into `TutorWebActivity`.
- Preserved the existing biometric login and Android Keystore implementation.
- Preserved Firebase configuration/token injection and push messaging.
- Added a native push-notification inbox with local history.
- Added Android DownloadManager handling with native download history.
- Added native top/bottom navigation around the Tutor learning portal.
- Added native share support and safe Tutor deep-link handling.
- Added a native offline/retry state for main-frame network/server failures.
- Added a native About screen.
- Added correct Android launcher density resources based on the existing brand icon.
- Changed version code from 8 to 9 and version name from 1.0.8 to 1.0.9.
- Added optional private release-keystore configuration; no signing password or private key is bundled.
- Disabled Android backup for the application because biometric-protected credentials are device-bound.
