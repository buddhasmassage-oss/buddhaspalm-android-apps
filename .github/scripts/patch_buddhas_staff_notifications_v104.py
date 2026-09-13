from pathlib import Path

CHANNEL_ID = "buddhas_staff_fcm"


def ensure_import(text: str, line: str, anchor: str = "import android.app.Activity;\n") -> str:
    if line in text:
        return text
    if anchor in text:
        return text.replace(anchor, anchor + line + "\n", 1)
    return text


def patch_manifest(project: Path) -> None:
    p = project / "app/src/main/AndroidManifest.xml"
    text = p.read_text()
    permission = '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />'
    if permission not in text:
        text = text.replace("<application", "    " + permission + "\n\n    <application", 1)

    metadata = (
        '        <meta-data android:name="com.google.firebase.messaging.default_notification_channel_id" '
        'android:value="buddhas_staff_fcm" />\n'
    )
    if "com.google.firebase.messaging.default_notification_channel_id" not in text:
        text = text.replace("</application>", metadata + "    </application>", 1)
    p.write_text(text)


def patch_service(project: Path) -> None:
    p = next((project / "app/src/main/java").rglob("BuddhasFirebaseMessagingService.java"))
    text = p.read_text()
    old = '''            String title = msg.getNotification() != null && msg.getNotification().getTitle() != null
                    ? msg.getNotification().getTitle() : "Buddhas Staff";
            String body = msg.getNotification() != null && msg.getNotification().getBody() != null
                    ? msg.getNotification().getBody() : "You have a new update.";
            String url = msg.getData().get("url");
'''
    new = '''            String dataTitle = msg.getData().get("title");
            String dataBody = msg.getData().get("body");
            String title = msg.getNotification() != null && msg.getNotification().getTitle() != null
                    ? msg.getNotification().getTitle()
                    : (dataTitle != null && !dataTitle.trim().isEmpty() ? dataTitle : "Buddhas Staff");
            String body = msg.getNotification() != null && msg.getNotification().getBody() != null
                    ? msg.getNotification().getBody()
                    : (dataBody != null && !dataBody.trim().isEmpty() ? dataBody : "You have a new update.");
            String url = msg.getData().get("url");
'''
    if old in text:
        text = text.replace(old, new, 1)
    p.write_text(text)


def patch_main(project: Path) -> None:
    p = next((project / "app/src/main/java").rglob("MainActivity.java"))
    text = p.read_text()

    text = ensure_import(text, "import android.app.NotificationChannel;")
    text = ensure_import(text, "import android.app.NotificationManager;")

    if "REQ_NOTIFICATIONS" not in text:
        text = text.replace(
            "    private static final int REQ_FILE = 1201;\n",
            "    private static final int REQ_FILE = 1201;\n"
            "    private static final int REQ_NOTIFICATIONS = 4401;\n",
            1,
        )

    if "ensureFcmNotificationPermissionAndChannel();" not in text:
        text = text.replace(
            "        configureWebView();\n",
            "        ensureFcmNotificationPermissionAndChannel();\n"
            "        configureWebView();\n",
            1,
        )

    if "private void ensureFcmNotificationPermissionAndChannel()" not in text:
        marker = "    private void configureWebView() {"
        helper = '''    private void ensureFcmNotificationPermissionAndChannel() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        "buddhas_staff_fcm", "Buddhas Staff Notifications", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Bookings, service updates and staff alerts");
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            }
        } catch (Throwable ignored) {}
    }

'''
        text = text.replace(marker, helper + marker, 1)

    # Keep therapist map avatar photos at exactly 45 px inside the APK WebView.
    if "bp-apk-map-avatar-fix" not in text:
        flush = "                CookieManager.getInstance().flush();\n"
        inject = flush + '''                view.evaluateJavascript("(function(){try{var s=document.getElementById('bp-apk-map-avatar-fix');if(!s){s=document.createElement('style');s.id='bp-apk-map-avatar-fix';s.textContent='div.leaflet-marker-icon img{width:45px!important;height:45px!important;max-width:45px!important;max-height:45px!important;min-width:45px!important;min-height:45px!important;object-fit:cover!important;border-radius:50%!important;}';document.head.appendChild(s);}}catch(e){}})();", null);
'''
        text = text.replace(flush, inject, 1)

    # A background FCM notification supplies the deep-link URL as an Intent extra.
    old_load = '''    private void loadInitialUrl(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "https".equalsIgnoreCase(data.getScheme()) && getString(R.string.allowed_host).equalsIgnoreCase(data.getHost())) webView.loadUrl(data.toString());
        else webView.loadUrl(getString(R.string.start_url));
    }
'''
    new_load = '''    private void loadInitialUrl(Intent intent) {
        String pushedUrl = intent != null ? intent.getStringExtra("url") : null;
        Uri data = null;
        if (pushedUrl != null && !pushedUrl.trim().isEmpty()) {
            try { data = Uri.parse(pushedUrl.trim()); } catch (Throwable ignored) {}
        }
        if (data == null) data = intent != null ? intent.getData() : null;
        if (data != null && "https".equalsIgnoreCase(data.getScheme()) && getString(R.string.allowed_host).equalsIgnoreCase(data.getHost())) webView.loadUrl(data.toString());
        else webView.loadUrl(getString(R.string.start_url));
    }
'''
    if old_load in text:
        text = text.replace(old_load, new_load, 1)

    p.write_text(text)


def patch_project(name: str) -> None:
    project = Path(name)
    patch_manifest(project)
    patch_service(project)
    patch_main(project)
    print("Notification v1.0.4 patched", name)


patch_project("BuddhasStaffNewAdmin")
patch_project("BuddhasStaffNewTherapist")
