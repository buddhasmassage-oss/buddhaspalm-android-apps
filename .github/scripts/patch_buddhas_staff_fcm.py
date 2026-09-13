from pathlib import Path

API_KEY = "AIzaSyBQr-1L7Znkw3m7PaynTJGGuA5TbyEd84I"
PROJECT_ID = "buddhas-ed87a"
SENDER_ID = "1020836481565"
ADMIN_APP_ID = "1:1020836481565:android:210532315a961f42bed4f4"
STAFF_APP_ID = "1:1020836481565:android:8f7b31bd364629d0bed4f4"


def ensure_import(text: str, line: str, anchor: str = "import org.json.JSONTokener;\n") -> str:
    if line in text:
        return text
    if anchor in text:
        return text.replace(anchor, line + "\n" + anchor, 1)
    idx = text.find("public class MainActivity")
    return text[:idx] + line + "\n" + text[idx:]


def add_dependency(project: Path) -> None:
    p = project / "app/build.gradle"
    text = p.read_text()
    if "firebase-messaging" not in text:
        text = text.replace("dependencies {", "dependencies {\n    implementation 'com.google.firebase:firebase-messaging:24.1.2'", 1)
    p.write_text(text)


def disable_native_onesignal(project: Path) -> None:
    for p in (project / "app/src/main/java").rglob("BuddhasPalmApplication.java"):
        text = p.read_text()
        text = text.replace(
            "        OneSignalManager.initialize(this);",
            "        // Buddhas Staff v1.0.3 uses Firebase FCM natively.\n"
            "        // Keep OneSignal out of native startup so a push SDK failure can never close the APK."
        )
        p.write_text(text)


def add_service(project: Path, pkg: str) -> None:
    java_dir = project / "app/src/main/java" / Path(pkg.replace('.', '/'))
    java_dir.mkdir(parents=True, exist_ok=True)
    service = java_dir / "BuddhasFirebaseMessagingService.java"
    service.write_text(f'''package {pkg};

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class BuddhasFirebaseMessagingService extends FirebaseMessagingService {{
    private static final String CHANNEL = "buddhas_staff_fcm";

    @Override public void onNewToken(String token) {{
        getSharedPreferences("buddhas_fcm", MODE_PRIVATE)
                .edit().putString("fcm_token", token == null ? "" : token.trim()).apply();
    }}

    @Override public void onMessageReceived(RemoteMessage msg) {{
        try {{
            String title = msg.getNotification() != null && msg.getNotification().getTitle() != null
                    ? msg.getNotification().getTitle() : "Buddhas Staff";
            String body = msg.getNotification() != null && msg.getNotification().getBody() != null
                    ? msg.getNotification().getBody() : "You have a new update.";
            String url = msg.getData().get("url");

            Intent i = new Intent(this, MainActivity.class);
            if (url != null && url.startsWith("https://staff.buddhaspalm.shop/"))
                i.setData(android.net.Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {{
                NotificationChannel c = new NotificationChannel(
                        CHANNEL, "Buddhas Staff Firebase", NotificationManager.IMPORTANCE_HIGH);
                c.enableVibration(true);
                nm.createNotificationChannel(c);
            }}
            if (nm != null) {{
                nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE),
                        new NotificationCompat.Builder(this, CHANNEL)
                                .setSmallIcon(android.R.drawable.ic_dialog_info)
                                .setContentTitle(title)
                                .setContentText(body)
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setAutoCancel(true)
                                .setContentIntent(pi)
                                .build());
            }}
        }} catch (Throwable ignored) {{}}
    }}
}}
''')

    manifest = project / "app/src/main/AndroidManifest.xml"
    m = manifest.read_text()
    if "BuddhasFirebaseMessagingService" not in m:
        m = m.replace(
            "</application>",
            '''        <service android:name=".BuddhasFirebaseMessagingService" android:exported="false">\n'''
            '''            <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>\n'''
            '''        </service>\n'''
            '''        <activity android:name=".FirebaseDeviceInfoActivity" android:exported="false" />\n'''
            '''    </application>''',
            1,
        )
    elif "FirebaseDeviceInfoActivity" not in m:
        m = m.replace("</application>", '        <activity android:name=".FirebaseDeviceInfoActivity" android:exported="false" />\n    </application>', 1)
    manifest.write_text(m)


def add_device_info_activity(project: Path, pkg: str, role: str, app_id: str) -> None:
    java_dir = project / "app/src/main/java" / Path(pkg.replace('.', '/'))
    java_dir.mkdir(parents=True, exist_ok=True)
    f = java_dir / "FirebaseDeviceInfoActivity.java"
    f.write_text(f'''package {pkg};

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.CookieManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FirebaseDeviceInfoActivity extends Activity {{
    private static final String ROLE = "{role}";
    private static final String APP_ID = "{app_id}";
    private static final String API_KEY = "{API_KEY}";
    private static final String PROJECT_ID = "{PROJECT_ID}";
    private static final String SENDER_ID = "{SENDER_ID}";

    private TextView tokenView;
    private TextView statusView;
    private Button showButton;
    private String token = "";
    private boolean showing = false;

    @Override protected void onCreate(Bundle state) {{
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(248, 248, 250));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {{
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        }});

        TextView title = text("Firebase / Device Info", 24, true);
        root.addView(title);
        TextView subtitle = text("Native FCM diagnostics for this installed APK", 14, false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        addRow(root, "Role", ROLE.equals("admin") ? "Admin" : "Staff / Therapist");
        addRow(root, "Package Name", getPackageName());
        addRow(root, "Device", (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim());
        addRow(root, "Firebase Project", PROJECT_ID);
        addRow(root, "Mobile SDK App ID", APP_ID);

        TextView tokenLabel = text("FCM Registration Token", 13, true);
        tokenLabel.setPadding(0, dp(18), 0, dp(6));
        root.addView(tokenLabel);
        tokenView = text("No token yet", 13, false);
        tokenView.setTextIsSelectable(true);
        tokenView.setPadding(dp(12), dp(12), dp(12), dp(12));
        tokenView.setBackgroundColor(Color.WHITE);
        root.addView(tokenView, new LinearLayout.LayoutParams(-1, -2));

        showButton = button("Show FCM Token");
        Button copy = button("Copy Token");
        Button refresh = button("Refresh Token");
        Button register = button("Register Again");
        Button close = button("Close");
        root.addView(showButton); root.addView(copy); root.addView(refresh); root.addView(register); root.addView(close);

        statusView = text("Loading Firebase token...", 13, false);
        statusView.setPadding(0, dp(16), 0, 0);
        root.addView(statusView);

        token = getSharedPreferences("buddhas_fcm", MODE_PRIVATE).getString("fcm_token", "");
        renderToken();
        showButton.setOnClickListener(v -> {{ showing = !showing; renderToken(); }});
        copy.setOnClickListener(v -> copyToken());
        refresh.setOnClickListener(v -> refreshToken());
        register.setOnClickListener(v -> registerAgain());
        close.setOnClickListener(v -> finish());

        loadToken(false);
    }}

    private FirebaseApp ensureFirebase() {{
        try {{ return FirebaseApp.getInstance(); }} catch (Throwable ignored) {{}}
        try {{
            FirebaseOptions o = new FirebaseOptions.Builder()
                    .setApplicationId(APP_ID).setApiKey(API_KEY).setProjectId(PROJECT_ID).setGcmSenderId(SENDER_ID).build();
            FirebaseApp app = FirebaseApp.initializeApp(this, o);
            if (app != null) return app;
        }} catch (Throwable ignored) {{}}
        try {{ return FirebaseApp.getInstance(); }} catch (Throwable ignored) {{ return null; }}
    }}

    private void loadToken(boolean registerAfter) {{
        status("Getting Firebase token...");
        try {{
            if (ensureFirebase() == null) {{ status("Firebase initialization failed. The APK stays open; use Refresh Token to retry."); return; }}
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {{
                if (!task.isSuccessful() || task.getResult() == null || task.getResult().trim().isEmpty()) {{
                    status("Token not available yet. Check internet and tap Refresh Token.");
                    return;
                }}
                token = task.getResult().trim();
                getSharedPreferences("buddhas_fcm", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
                renderToken();
                status("FCM token ready.");
                if (registerAfter) registerAgain();
            }});
        }} catch (Throwable e) {{ status("Firebase error was contained safely. Tap Refresh Token to retry."); }}
    }}

    private void refreshToken() {{
        status("Refreshing token...");
        try {{
            if (ensureFirebase() == null) {{ status("Firebase initialization failed."); return; }}
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(task -> loadToken(true));
        }} catch (Throwable e) {{ status("Could not refresh token. The APK remains usable."); }}
    }}

    private void registerAgain() {{
        if (token == null || token.trim().isEmpty()) {{ loadToken(true); return; }}
        final String endpoint = "https://staff.buddhaspalm.shop/api/fcm-register.php";
        final String cookie = CookieManager.getInstance().getCookie(endpoint);
        if (cookie == null || cookie.trim().isEmpty()) {{
            status("Login to the app first, then return here and tap Register Again.");
            return;
        }}
        status("Registering this device...");
        new Thread(() -> {{
            HttpURLConnection con = null;
            try {{
                con = (HttpURLConnection) new URL(endpoint).openConnection();
                con.setRequestMethod("POST"); con.setConnectTimeout(10000); con.setReadTimeout(12000); con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                con.setRequestProperty("Cookie", cookie); con.setRequestProperty("X-BP-Native-FCM", "1");
                String device = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim();
                String body = "token=" + enc(token) + "&role=" + enc(ROLE) + "&package_name=" + enc(getPackageName()) + "&device_name=" + enc(device);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8); con.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = con.getOutputStream()) {{ os.write(bytes); }}
                int code = con.getResponseCode(); String response = readAll(code >= 200 && code < 400 ? con.getInputStream() : con.getErrorStream());
                boolean ok = code >= 200 && code < 300 && response.contains("\\\"ok\\\":true");
                status(ok ? "Registered successfully in Admin → Firebase FCM → Devices." : "Registration failed (HTTP " + code + "). Check the website FCM API/config.");
            }} catch (Throwable e) {{ status("Registration failed. Check internet/login and retry."); }}
            finally {{ if (con != null) con.disconnect(); }}
        }}).start();
    }}

    private void copyToken() {{
        if (token == null || token.trim().isEmpty()) {{ status("No token to copy yet."); return; }}
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("FCM Registration Token", token));
        status("FCM token copied.");
    }}

    private void renderToken() {{
        if (tokenView == null) return;
        if (token == null || token.trim().isEmpty()) {{ tokenView.setText("No token yet"); return; }}
        if (showing) tokenView.setText(token);
        else tokenView.setText(token.length() > 22 ? token.substring(0, 12) + "••••••••" + token.substring(token.length() - 8) : "••••••••••••");
        if (showButton != null) showButton.setText(showing ? "Hide FCM Token" : "Show FCM Token");
    }}

    private void status(String s) {{ runOnUiThread(() -> {{ if (statusView != null) statusView.setText(s); }}); }}
    private void addRow(LinearLayout root, String label, String value) {{
        TextView l = text(label, 12, true); l.setPadding(0, dp(9), 0, dp(2)); root.addView(l);
        TextView v = text(value, 14, false); root.addView(v);
    }}
    private TextView text(String s, int sp, boolean bold) {{
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.rgb(35,35,42));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return t;
    }}
    private Button button(String s) {{
        Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(14); b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48)); p.setMargins(0, dp(8), 0, 0); b.setLayoutParams(p); return b;
    }}
    private int dp(int v) {{ return Math.round(v * getResources().getDisplayMetrics().density); }}
    private String enc(String v) throws Exception {{ return URLEncoder.encode(v == null ? "" : v, "UTF-8"); }}
    private String readAll(InputStream in) throws Exception {{
        if (in == null) return ""; StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {{ String line; while ((line = br.readLine()) != null) sb.append(line); }}
        return sb.toString();
    }}
}}
''')


def patch_main(project: Path, is_admin: bool) -> None:
    p = next((project / "app/src/main/java").rglob("MainActivity.java"))
    text = p.read_text()
    pkg = text.splitlines()[0].replace("package ", "").replace(";", "").strip()
    role = "admin" if is_admin else "staff"
    app_id = ADMIN_APP_ID if is_admin else STAFF_APP_ID

    for imp in [
        "import android.Manifest;",
        "import android.content.pm.PackageManager;",
        "import android.os.Build;",
        "import android.widget.Button;",
        "import com.google.firebase.FirebaseApp;",
        "import com.google.firebase.FirebaseOptions;",
        "import com.google.firebase.messaging.FirebaseMessaging;",
        "import org.json.JSONObject;",
    ]:
        text = ensure_import(text, imp)

    if "private String fcmToken" not in text:
        marker = "    private WebView webView;\n"
        fields = marker + "    private String fcmToken = \"\";\n    private volatile String lastRegisteredFcmToken = \"\";\n"
        text = text.replace(marker, fields, 1)

    if "addFirebaseDeviceInfoButton(root);" not in text:
        text = text.replace("        setContentView(root);\n", "        setContentView(root);\n        addFirebaseDeviceInfoButton(root);\n", 1)

    if "webView.postDelayed(this::ensureFirebaseReadyAndRegister" not in text:
        text = text.replace(
            "        configureWebView();\n",
            "        configureWebView();\n"
            "        fcmToken = getSharedPreferences(\"buddhas_fcm\", MODE_PRIVATE).getString(\"fcm_token\", \"\");\n"
            "        publishFcmTokenToPage();\n"
            "        webView.postDelayed(this::ensureFirebaseReadyAndRegister, 900L);\n",
            1,
        )

    page_marker = "                CookieManager.getInstance().flush();\n"
    if page_marker in text and "registerFcmTokenWithServer();" not in text.split(page_marker, 1)[1][:400]:
        text = text.replace(
            page_marker,
            page_marker + "                publishFcmTokenToPage();\n                ensureFirebaseReadyAndRegister();\n                registerFcmTokenWithServer();\n",
            1,
        )

    if "private void addFirebaseDeviceInfoButton" not in text:
        anchor = "    private void restoreNativePushIdentity() {" if "    private void restoreNativePushIdentity() {" in text else "    private void loadInitialUrl(Intent intent) {"
        helper = f'''    private void addFirebaseDeviceInfoButton(RelativeLayout root) {{
        try {{
            Button b = new Button(this);
            b.setText("FCM"); b.setAllCaps(false); b.setTextSize(11f); b.setPadding(0,0,0,0);
            b.setContentDescription("Firebase / Device Info");
            b.setOnClickListener(v -> startActivity(new Intent(this, FirebaseDeviceInfoActivity.class)));
            RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(dp(54), dp(42));
            p.addRule(RelativeLayout.ALIGN_PARENT_END); p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            p.setMargins(dp(8), dp(8), dp(10), dp(10)); root.addView(b, p);
        }} catch (Throwable ignored) {{}}
    }}

    private void publishFcmTokenToPage() {{
        try {{
            if (webView == null || fcmToken == null || fcmToken.isEmpty()) return;
            String a = JSONObject.quote(fcmToken), b = JSONObject.quote("{role}"), c = JSONObject.quote(getPackageName());
            webView.post(() -> webView.evaluateJavascript(
                    "window.BP_NATIVE_FCM_TOKEN=" + a + ";window.dispatchEvent(new CustomEvent('bp:native-fcm-token',{{detail:{{token:" + a + ",role:" + b + ",packageName:" + c + "}}}}));", null));
        }} catch (Throwable ignored) {{}}
    }}

    private FirebaseApp ensureFirebaseAppSafe() {{
        try {{ return FirebaseApp.getInstance(); }} catch (Throwable ignored) {{}}
        try {{
            FirebaseOptions o = new FirebaseOptions.Builder()
                    .setApplicationId("{app_id}").setApiKey("{API_KEY}").setProjectId("{PROJECT_ID}").setGcmSenderId("{SENDER_ID}").build();
            FirebaseApp app = FirebaseApp.initializeApp(this, o);
            if (app != null) return app;
        }} catch (Throwable ignored) {{}}
        try {{ return FirebaseApp.getInstance(); }} catch (Throwable ignored) {{ return null; }}
    }}

    private void ensureFirebaseReadyAndRegister() {{
        try {{
            if (ensureFirebaseAppSafe() == null) return;
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {{
                try {{
                    if (!task.isSuccessful() || task.getResult() == null) return;
                    String x = task.getResult().trim(); if (x.isEmpty()) return;
                    fcmToken = x;
                    getSharedPreferences("buddhas_fcm", MODE_PRIVATE).edit().putString("fcm_token", x).apply();
                    publishFcmTokenToPage(); registerFcmTokenWithServer();
                }} catch (Throwable ignored) {{}}
            }});
        }} catch (Throwable ignored) {{}}
    }}

    private void registerFcmTokenWithServer() {{
        final String token = fcmToken == null ? "" : fcmToken.trim();
        if (token.isEmpty() || token.equals(lastRegisteredFcmToken)) return;
        final String endpoint = "https://staff.buddhaspalm.shop/api/fcm-register.php";
        final String cookie = CookieManager.getInstance().getCookie(endpoint);
        if (cookie == null || cookie.trim().isEmpty()) return;
        new Thread(() -> {{
            HttpURLConnection con = null;
            try {{
                con = (HttpURLConnection) new URL(endpoint).openConnection(); con.setRequestMethod("POST");
                con.setConnectTimeout(10000); con.setReadTimeout(12000); con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                con.setRequestProperty("Cookie", cookie); con.setRequestProperty("X-BP-Native-FCM", "1");
                String device = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim();
                String body = "token=" + enc(token) + "&role=" + enc("{role}") + "&package_name=" + enc(getPackageName()) + "&device_name=" + enc(device);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8); con.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = con.getOutputStream()) {{ os.write(bytes); }}
                int code = con.getResponseCode(); String response = readAll(code >= 200 && code < 400 ? con.getInputStream() : con.getErrorStream());
                if (code >= 200 && code < 300 && response.contains("\\\"ok\\\":true")) lastRegisteredFcmToken = token;
            }} catch (Throwable ignored) {{}} finally {{ if (con != null) con.disconnect(); }}
        }}).start();
    }}

'''
        text = text.replace(anchor, helper + anchor, 1)

    text = text.replace("BuddhasPalm-Admin-Android/1.5.0", "BuddhasPalm-Admin-Android/1.0.3")
    text = text.replace("BuddhasPalm-Provider-Android/1.5.0", "BuddhasPalm-Provider-Android/1.0.3")
    p.write_text(text)

    add_dependency(project)
    disable_native_onesignal(project)
    add_service(project, pkg)
    add_device_info_activity(project, pkg, role, app_id)
    print("FCM v1.0.3 patched", p, pkg)


patch_main(Path("BuddhasStaffNewAdmin"), True)
patch_main(Path("BuddhasStaffNewTherapist"), False)
