package com.buddhaspalm.admin;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1201;
    private static final int MAX_PUSH_REGISTRATION_ATTEMPTS = 30;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private String lastExternalId = "";
    private volatile String lastRegisteredSubscriptionId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(75, 23, 106));
        getWindow().setNavigationBarColor(Color.rgb(50, 16, 69));
        lastExternalId = getPreferences(MODE_PRIVATE).getString("onesignal_external_id", "");
        // Re-confirm the device with the server on every app launch. This does not
        // create a new OneSignal subscription; it only refreshes our server record.
        lastRegisteredSubscriptionId = "";

        RelativeLayout root = new RelativeLayout(this);
        webView = new WebView(this);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(212,175,55)));

        RelativeLayout.LayoutParams webParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        RelativeLayout.LayoutParams progParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 6);
        progParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        root.addView(webView, webParams);
        root.addView(progress, progParams);
        setContentView(root);

        configureWebView();
        OneSignalManager.setupPushSubscriptionVerification(this);
        OneSignalManager.setSubscriptionChangedListener(() -> runOnUiThread(() -> publishNativeSubscription(0)));
        restoreNativePushIdentity();
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
        s.setGeolocationEnabled(false);
        s.setUserAgentString(s.getUserAgentString() + " BuddhasPalm-Admin-Android/1.4.5");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && getString(R.string.allowed_host).equalsIgnoreCase(uri.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                syncNativeOneSignalIdentity(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try { startActivityForResult(intent, REQ_FILE); return true; }
                catch (ActivityNotFoundException e) { fileCallback = null; return false; }
            }
        });
    }

    private void restoreNativePushIdentity() {
        if (!OneSignalManager.isInitialized() || lastExternalId.isEmpty()) return;
        OneSignalManager.login(lastExternalId);
        publishNativeSubscription(0);
    }

    private void syncNativeOneSignalIdentity(WebView view, String url) {
        if (!OneSignalManager.isInitialized()) return;
        String script = "(function(){try{var n=window.BP_NATIVE_PUSH_EXTERNAL_ID||'';if(n)return String(n);var c=(window.BP_ONESIGNAL_CONFIG&&window.BP_ONESIGNAL_CONFIG.externalId)?window.BP_ONESIGNAL_CONFIG.externalId:'';if(c)return String(c);if(window.__onesignal_external_id)return String(window.__onesignal_external_id);try{var l=localStorage.getItem('hilot_os_ext_id');if(l)return String(l);}catch(e){}return '';}catch(e){return '';}})();";
        view.evaluateJavascript(script, value -> {
            String externalId = decodeJavascriptString(value);
            if (!externalId.isEmpty()) {
                lastExternalId = externalId;
                getPreferences(MODE_PRIVATE).edit().putString("onesignal_external_id", externalId).apply();
                OneSignalManager.login(externalId);
                publishNativeSubscription(0);
            } else if (isSignedOutPage(url) && !lastExternalId.isEmpty()) {
                OneSignalManager.logout();
                lastExternalId = "";
                lastRegisteredSubscriptionId = "";
                getPreferences(MODE_PRIVATE).edit().remove("onesignal_external_id").remove("onesignal_registered_subscription_id").apply();
            }
        });
    }

    private void publishNativeSubscription(int attempt) {
        if (attempt > MAX_PUSH_REGISTRATION_ATTEMPTS || lastExternalId.isEmpty()) return;
        long delay = attempt == 0 ? 400L : 1200L;
        webView.postDelayed(() -> {
            String sid = OneSignalManager.getSubscriptionId();
            if (!OneSignalManager.isRealSubscriptionId(sid)) {
                publishNativeSubscription(attempt + 1);
                return;
            }

            sid = sid.trim();
            publishSubscriptionStateToWeb(sid);

            // A server-assigned subscription ID by itself is not enough for
            // Android delivery. Do not tell the website the device is ready until
            // OneSignal also exposes the real FCM token and the subscription is opted in.
            String token = OneSignalManager.getPushToken();
            if (token == null || token.trim().isEmpty() || !OneSignalManager.isOptedIn()) {
                publishNativeSubscription(attempt + 1);
                return;
            }

            registerSubscriptionNatively(sid, attempt);
        }, delay);
    }

    private void publishSubscriptionStateToWeb(String sid) {
        String safe = sid.replace("\\", "\\\\").replace("'", "\\'");
        webView.evaluateJavascript("window.BP_NATIVE_ONESIGNAL_SUBSCRIPTION_ID='" + safe + "';window.dispatchEvent(new CustomEvent('bp:native-push-ready',{detail:{subscriptionId:'" + safe + "'}}));", null);
    }

    private String nativeRegisterUrl() {
        Uri base = Uri.parse(getString(R.string.start_url));
        return base.buildUpon().encodedPath("/api/native-push-register.php").clearQuery().fragment(null).build().toString();
    }

    private void registerSubscriptionNatively(String sid, int attempt) {
        if (sid.equals(lastRegisteredSubscriptionId)) return;
        final String endpoint = nativeRegisterUrl();
        final String cookie = CookieManager.getInstance().getCookie(endpoint);
        if (cookie == null || cookie.trim().isEmpty()) {
            if (attempt < MAX_PUSH_REGISTRATION_ATTEMPTS) publishNativeSubscription(attempt + 1);
            return;
        }
        final String externalId = lastExternalId;
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
                con.setRequestProperty("X-BP-Native-Push", "1");
                con.setRequestProperty("User-Agent", "BuddhasPalm-Admin-Android/1.4.5");
                String body = "subscription_id=" + enc(sid) + "&external_id=" + enc(externalId) + "&app_role=admin";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = con.getOutputStream()) { os.write(bytes); }
                int code = con.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? con.getInputStream() : con.getErrorStream();
                String response = readAll(stream);
                boolean ok = code >= 200 && code < 300 && response.contains("\"ok\":true");
                if (ok) {
                    lastRegisteredSubscriptionId = sid;
                    getPreferences(MODE_PRIVATE).edit().putString("onesignal_registered_subscription_id", sid).apply();
                    runOnUiThread(() -> webView.evaluateJavascript("window.BP_NATIVE_PUSH_REGISTERED=true;", null));
                } else if (attempt < MAX_PUSH_REGISTRATION_ATTEMPTS) {
                    runOnUiThread(() -> publishNativeSubscription(attempt + 1));
                }
            } catch (Exception ignored) {
                if (attempt < MAX_PUSH_REGISTRATION_ATTEMPTS) runOnUiThread(() -> publishNativeSubscription(attempt + 1));
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private String enc(String value) throws Exception { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String decodeJavascriptString(String raw) {
        if (raw == null || raw.equals("null") || raw.equals("undefined")) return "";
        try {
            Object decoded = new JSONTokener(raw).nextValue();
            return decoded instanceof String ? ((String) decoded).trim() : "";
        } catch (Exception ignored) { return ""; }
    }

    private boolean isSignedOutPage(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("/logout.php") || lower.contains("/login.php");
    }

    private void loadInitialUrl(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "https".equalsIgnoreCase(data.getScheme()) && getString(R.string.allowed_host).equalsIgnoreCase(data.getHost())) webView.loadUrl(data.toString());
        else webView.loadUrl(getString(R.string.start_url));
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); loadInitialUrl(intent); }
    @Override protected void onSaveInstanceState(Bundle outState) { webView.saveState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }
    @Override public void onBackPressed() { if (webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
