# BuddhasPalm Android Apps

Complete Android source for two separate Buddhas Palm applications using the live backend at `https://metro.buddhaspinas.com`.

- **BuddhasPalm Admin** → `https://metro.buddhaspinas.com/admin/` → package `com.buddhaspalm.admin`
- **BuddhasPalm Provider** → `https://metro.buddhaspinas.com/provider/` → package `com.buddhaspalm.provider`

The Provider app includes Android fine/coarse location permission and WebView geolocation support. Both apps include persistent cookies, HTTPS-only in-app navigation, file upload support, deep links, Android back navigation, and a loading indicator.

## APK builds

GitHub Actions builds both debug APKs on every push to `main` and on manual dispatch. Download them from the workflow run artifacts:

- `BuddhasPalmAdmin-APK`
- `BuddhasPalmProvider-APK`

Debug APKs are installable directly on Android when installation from the selected browser/files app is allowed.
