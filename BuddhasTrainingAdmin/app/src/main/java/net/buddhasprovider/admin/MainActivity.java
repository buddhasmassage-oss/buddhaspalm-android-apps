package net.buddhasprovider.admin;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.BiometricPrompt;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1201;
    private static final int REQ_NOTIFICATIONS = 1202;
    private static final String BASE_URL = "https://buddhasprovider.net";
    private static final String ADMIN_URL = BASE_URL + "/admin/";
    private static final String PREF_NAME = "bp_admin_native_biometric";
    private static final String KEY_ALIAS = "bp_admin_native_biometric_key_v104";

    private WebView webView;
    private ProgressBar progress;
    private Button biometricButton;
    private ValueCallback<Uri[]> fileCallback;
    private SharedPreferences biometricPrefs;
    private String fcmToken = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        biometricPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(6,43,84));
        getWindow().setNavigationBarColor(Color.rgb(6,43,84));

        RelativeLayout root = new RelativeLayout(this);
        root.setBackgroundColor(Color.rgb(6,43,84));

        webView = new WebView(this);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(228,184,78)));

        RelativeLayout.LayoutParams wp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        RelativeLayout.LayoutParams pp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 6);
        pp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        root.addView(webView, wp);
        root.addView(progress, pp);

        biometricButton = new Button(this);
        biometricButton.setAllCaps(false);
        biometricButton.setTextSize(13f);
        biometricButton.setTextColor(Color.WHITE);
        biometricButton.setBackgroundColor(Color.rgb(24,105,72));
        biometricButton.setPadding(24, 10, 24, 10);
        biometricButton.setVisibility(View.GONE);
        RelativeLayout.LayoutParams bp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        bp.addRule(RelativeLayout.ALIGN_PARENT_END);
        bp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        bp.setMargins(18,18,18,24);
        root.addView(biometricButton, bp);
        biometricButton.setOnClickListener(v -> handleBiometricButton());

        setContentView(root);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        NotificationHelper.ensureChannel(this);
        configureWebView();
        requestNotificationPermission();
        loadFcmToken();

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else loadInitialUrl(getIntent());
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " BuddhasTrainingAdmin-Android/1.0.4 NativeFingerprint");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.addJavascriptInterface(new NativeBiometricBridge(), "BuddhasNativeBio");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                String scheme = uri.getScheme(), host = uri.getHost();
                if ("https".equalsIgnoreCase(scheme) && getString(R.string.allowed_host).equalsIgnoreCase(host)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                registerCurrentToken();
                updateBiometricButton(url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, REQ_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
            }
        });
    }

    private void handleBiometricButton() {
        String url = webView.getUrl();
        if (isBiometricRegistered() && isAdminLoginPage(url)) {
            authenticateAndLogin();
        } else if (!isBiometricRegistered() && isLoggedInAdminPage(url)) {
            registerFingerprint();
        } else if (isBiometricRegistered() && isLoggedInAdminPage(url)) {
            new AlertDialog.Builder(this)
                    .setTitle("Fingerprint login is active")
                    .setMessage("This Android device is already registered for native biometric Admin login. You can replace the registration if needed.")
                    .setPositiveButton("Register Again", (d,w) -> registerFingerprint())
                    .setNegativeButton("Keep Current", null)
                    .show();
        } else {
            toast("Sign in with your Admin password first, then register this device fingerprint.");
        }
    }

    private boolean isOwnSite(String url) {
        return url != null && (url.equals(BASE_URL) || url.startsWith(BASE_URL + "/"));
    }

    private boolean isAdminLoginPage(String url) {
        if (url == null) return false;
        return url.contains("/admin/login.php");
    }

    private boolean isLoggedInAdminPage(String url) {
        return isOwnSite(url) && url.contains("/admin/") && !isAdminLoginPage(url);
    }

    private void updateBiometricButton(String url) {
        if (!isOwnSite(url)) {
            biometricButton.setVisibility(View.GONE);
            return;
        }
        if (isAdminLoginPage(url) && isBiometricRegistered()) {
            biometricButton.setText("Fingerprint Login");
            biometricButton.setVisibility(View.VISIBLE);
            return;
        }
        if (isLoggedInAdminPage(url) && !isBiometricRegistered()) {
            biometricButton.setText("Register Fingerprint");
            biometricButton.setVisibility(View.VISIBLE);
            return;
        }
        if (isLoggedInAdminPage(url) && isBiometricRegistered()) {
            biometricButton.setText("Fingerprint Active");
            biometricButton.setVisibility(View.VISIBLE);
            return;
        }
        biometricButton.setVisibility(View.GONE);
    }

    private boolean isBiometricRegistered() {
        return biometricPrefs.getBoolean("registered", false)
                && biometricPrefs.contains("ciphertext")
                && biometricPrefs.contains("iv")
                && biometricPrefs.contains("device_id");
    }

    private int biometricStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return -100;
        BiometricManager bm = getSystemService(BiometricManager.class);
        if (bm == null) return BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE;
        return bm.canAuthenticate();
    }

    private void registerFingerprint() {
        if (!isLoggedInAdminPage(webView.getUrl())) {
            toast("Sign in with your Admin password first.");
            webView.loadUrl(ADMIN_URL + "login.php");
            return;
        }
        int status = biometricStatus();
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricSetupMessage(status);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast("Native fingerprint login requires Android 9 or newer in this APK build.");
            return;
        }

        CancellationSignal signal = new CancellationSignal();
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Register Fingerprint")
                .setSubtitle("Buddhas Training Admin")
                .setDescription("Touch the fingerprint sensor. Your fingerprint stays securely on this Android device.")
                .setNegativeButton("Cancel", getMainExecutor(), (dialog, which) -> {})
                .build();
        prompt.authenticate(signal, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                requestNativeRegistrationToken();
            }
            @Override public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                toast("Fingerprint not recognized. Try again.");
            }
            @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                toast(errString == null ? "Fingerprint registration cancelled." : errString.toString());
            }
        });
    }

    private void authenticateAndLogin() {
        int status = biometricStatus();
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricSetupMessage(status);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast("Native fingerprint login requires Android 9 or newer in this APK build.");
            return;
        }

        CancellationSignal signal = new CancellationSignal();
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Fingerprint Login")
                .setSubtitle("Buddhas Training Admin")
                .setDescription("Touch the fingerprint sensor to open the Admin dashboard.")
                .setNegativeButton("Use Password", getMainExecutor(), (dialog, which) -> webView.loadUrl(ADMIN_URL + "login.php"))
                .build();
        prompt.authenticate(signal, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                try {
                    performNativeLogin(decryptSavedToken());
                } catch (Exception e) {
                    toast("Fingerprint credential could not be unlocked. Sign in with password and register fingerprint again.");
                }
            }
            @Override public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                toast("Fingerprint not recognized. Try again or use password.");
            }
            @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON) {
                    toast(errString == null ? "Fingerprint login cancelled." : errString.toString());
                }
            }
        });
    }

    private void showBiometricSetupMessage(int status) {
        String message;
        if (status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            message = "No fingerprint is enrolled on this Android phone. Add a fingerprint in Android Settings first, then return to the APK.";
        } else if (status == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            message = "This Android device does not have supported biometric hardware.";
        } else if (status == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            message = "The fingerprint sensor is currently unavailable. Try again or restart the phone.";
        } else if (status == -100) {
            message = "This APK's native fingerprint login requires Android 9 or newer.";
        } else {
            message = "Fingerprint authentication is not ready on this device. Check Android biometric/security settings.";
        }
        new AlertDialog.Builder(this)
                .setTitle("Fingerprint not ready")
                .setMessage(message)
                .setPositiveButton("Open Android Settings", (d,w) -> openBiometricSettings())
                .setNegativeButton("Use Password", (d,w) -> webView.loadUrl(ADMIN_URL + "login.php"))
                .show();
    }

    private void openBiometricSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent i = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                i.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
                startActivity(i);
            } else {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    private void requestNativeRegistrationToken() {
        String deviceId = biometricPrefs.getString("device_id", "");
        if (deviceId.isEmpty()) {
            deviceId = "admin-android-" + UUID.randomUUID();
            biometricPrefs.edit().putString("device_id", deviceId).apply();
        }
        String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        String js = "(async()=>{try{" +
                "let c=await fetch('/native-biometric-api.php?action=challenge',{credentials:'include',cache:'no-store'}).then(r=>r.json());" +
                "if(!c.ok){BuddhasNativeBio.onRegister(JSON.stringify(c));return;}" +
                "let r=await fetch('/native-biometric-api.php?action=register',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'},body:JSON.stringify({nonce:c.nonce,device_id:" + JSONObject.quote(deviceId) + ",device_name:" + JSONObject.quote(deviceName) + "})}).then(r=>r.json());" +
                "BuddhasNativeBio.onRegister(JSON.stringify(r));" +
                "}catch(e){BuddhasNativeBio.onRegister(JSON.stringify({ok:false,error:String(e)}));}})();";
        webView.evaluateJavascript(js, null);
    }

    private void performNativeLogin(String token) {
        if (token == null || token.isEmpty()) {
            toast("No fingerprint login credential is stored on this device.");
            return;
        }
        String deviceId = biometricPrefs.getString("device_id", "");
        String js = "fetch('/native-biometric-api.php?action=login',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'},body:JSON.stringify({device_id:" + JSONObject.quote(deviceId) + ",token:" + JSONObject.quote(token) + "})})" +
                ".then(r=>r.json()).then(x=>BuddhasNativeBio.onLogin(JSON.stringify(x)))" +
                ".catch(e=>BuddhasNativeBio.onLogin(JSON.stringify({ok:false,error:String(e)})));";
        if (webView.getUrl() == null || !isOwnSite(webView.getUrl())) webView.loadUrl(ADMIN_URL + "login.php");
        webView.postDelayed(() -> webView.evaluateJavascript(js, null), 250);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(60, KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(60);
        }
        generator.init(builder.build());
        return generator.generateKey();
    }

    private void encryptAndSaveToken(String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        biometricPrefs.edit()
                .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putBoolean("registered", true)
                .apply();
    }

    private String decryptSavedToken() throws Exception {
        byte[] encrypted = Base64.decode(biometricPrefs.getString("ciphertext", ""), Base64.NO_WRAP);
        byte[] iv = Base64.decode(biometricPrefs.getString("iv", ""), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    public class NativeBiometricBridge {
        @JavascriptInterface public void onRegister(String json) {
            runOnUiThread(() -> {
                try {
                    JSONObject response = new JSONObject(json);
                    if (!response.optBoolean("ok")) {
                        toast(response.optString("error", "Fingerprint registration failed."));
                        return;
                    }
                    encryptAndSaveToken(response.getString("token"));
                    biometricButton.setText("Fingerprint Active");
                    toast("Fingerprint login is now registered on this Android device.");
                } catch (Exception e) {
                    toast("Could not securely save the fingerprint login credential.");
                }
            });
        }

        @JavascriptInterface public void onLogin(String json) {
            runOnUiThread(() -> {
                try {
                    JSONObject response = new JSONObject(json);
                    if (response.optBoolean("ok")) {
                        webView.loadUrl(BASE_URL + response.optString("redirect", "/admin/"));
                    } else {
                        toast(response.optString("error", "Fingerprint login failed."));
                    }
                } catch (Exception e) {
                    toast("Fingerprint login response could not be processed.");
                }
            });
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void loadFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;
            fcmToken = task.getResult();
            registerCurrentToken();
        });
    }

    private void registerCurrentToken() {
        if (fcmToken == null || fcmToken.trim().isEmpty()) return;
        FcmRegistrar.register(getApplicationContext(), fcmToken);
    }

    private void loadInitialUrl(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "https".equalsIgnoreCase(data.getScheme()) && getString(R.string.allowed_host).equalsIgnoreCase(data.getHost())) {
            webView.loadUrl(data.toString());
        } else {
            webView.loadUrl(getString(R.string.start_url));
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadInitialUrl(intent);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}

final class FcmRegistrar {
    private FcmRegistrar() {}
    static void register(Context context, String token) {
        if (context == null || token == null || token.trim().isEmpty()) return;
        final String clean = token.trim(), endpoint = context.getString(R.string.fcm_register_url);
        final String cookie;
        try { cookie = CookieManager.getInstance().getCookie(endpoint); } catch (Throwable ignored) { return; }
        if (cookie == null || cookie.trim().isEmpty()) return;
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(endpoint).openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                con.setRequestProperty("Cookie", cookie);
                con.setRequestProperty("X-BP-FCM", "1");
                con.setRequestProperty("User-Agent", "BuddhasTrainingAdmin-Android/1.0.4 NativeFingerprint");
                String device = Build.MANUFACTURER + " " + Build.MODEL;
                String body = "token=" + enc(clean) + "&package_name=" + enc(context.getPackageName()) + "&device_name=" + enc(device) + "&platform=android";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = con.getOutputStream()) { os.write(bytes); }
                int code = con.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? con.getInputStream() : con.getErrorStream();
                readAll(stream);
            } catch (Exception ignored) {
            } finally {
                if (con != null) con.disconnect();
            }
        }, "BuddhasTrainingAdmin-FCM-Register").start();
    }
    private static String enc(String value) throws Exception { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}

final class NotificationHelper {
    static final String CHANNEL_ID = "buddhas_training_admin_updates";
    private NotificationHelper() {}
    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager m = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (m == null || m.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel c = new NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
        c.setDescription(context.getString(R.string.notification_channel_description));
        c.enableVibration(true);
        c.enableLights(true);
        c.setLightColor(Color.rgb(228,184,78));
        m.createNotificationChannel(c);
    }
    static void show(Context context, String title, String body, String url) {
        ensureChannel(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (url != null && !url.trim().isEmpty()) try { intent.setData(Uri.parse(url.trim())); } catch (Exception ignored) {}
        int request = (int) (System.currentTimeMillis() & 0x7fffffff), flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, request, intent, flags);
        android.app.Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b = new android.app.Notification.Builder(context, CHANNEL_ID);
        else { b = new android.app.Notification.Builder(context); b.setPriority(android.app.Notification.PRIORITY_HIGH); }
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title == null || title.isEmpty() ? context.getString(R.string.app_name) : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setAutoCancel(true)
                .setContentIntent(pi);
        NotificationManager m = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (m != null) m.notify(request, b.build());
    }
}
