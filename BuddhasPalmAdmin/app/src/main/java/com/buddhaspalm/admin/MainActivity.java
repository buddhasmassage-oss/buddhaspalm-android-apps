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

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1201;
    private static final int MAX_PUSH_REGISTRATION_ATTEMPTS = 25;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private String lastExternalId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(75, 23, 106));
        getWindow().setNavigationBarColor(Color.rgb(50, 16, 69));
        lastExternalId = getPreferences(MODE_PRIVATE).getString("onesignal_external_id", "");

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
        s.setUserAgentString(s.getUserAgentString() + " BuddhasPalm-Admin-Android/1.4.2");
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
        requestNativePushPermission();
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
                requestNativePushPermission();
                publishNativeSubscriptionToWeb(0);
            } else if (isSignedOutPage(url) && !lastExternalId.isEmpty()) {
                OneSignalManager.logout();
                lastExternalId = "";
                getPreferences(MODE_PRIVATE).edit().remove("onesignal_external_id").apply();
            }
        });
    }

    private void requestNativePushPermission() {
        if (!OneSignalManager.isInitialized() || OneSignalManager.hasNotificationPermission()) return;
        OneSignalManager.requestNotificationPermission();
    }

    private void publishNativeSubscriptionToWeb(int attempt) {
        if (attempt > MAX_PUSH_REGISTRATION_ATTEMPTS) return;
        long delay = attempt == 0 ? 500L : 1200L;
        webView.postDelayed(() -> {
            String id = OneSignalManager.getSubscriptionId();
            if (id == null || id.trim().isEmpty()) {
                publishNativeSubscriptionToWeb(attempt + 1);
                return;
            }
            String safe = id.trim().replace("\\", "\\\\").replace("'", "\\'");
            String js = "(function(){try{var sid='" + safe + "';window.BP_NATIVE_ONESIGNAL_SUBSCRIPTION_ID=sid;window.dispatchEvent(new CustomEvent('bp:native-push-ready',{detail:{subscriptionId:sid}}));var b=new URLSearchParams();b.set('csrf',window.CSRF||'');b.set('subscription_id',sid);b.set('external_id',window.BP_NATIVE_PUSH_EXTERNAL_ID||'');b.set('app_role','admin');fetch((window.APP_BASE||'/')+'api/native-push-register.php',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},body:b.toString()}).then(function(r){return r.json();}).then(function(x){window.BP_NATIVE_PUSH_REGISTERED=!!(x&&x.ok);window.BP_NATIVE_PUSH_STATUS=x||{};}).catch(function(){});}catch(e){}})();";
            webView.evaluateJavascript(js, null);
        }, delay);
    }

    private String decodeJavascriptString(String raw) {
        if (raw == null || raw.equals("null") || raw.equals("undefined")) return "";
        try {
            Object decoded = new JSONTokener(raw).nextValue();
            return decoded instanceof String ? ((String) decoded).trim() : "";
        } catch (Exception ignored) {
            return "";
        }
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
