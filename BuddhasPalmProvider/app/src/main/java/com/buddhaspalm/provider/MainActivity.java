package com.buddhaspalm.provider;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
    private static final int REQ_LOCATION = 1202;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
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
        s.setGeolocationEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " BuddhasPalm-Provider-Android/1.3.1");
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
                if (origin != null && origin.startsWith("https://" + getString(R.string.allowed_host))) {
                    boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    callback.invoke(origin, granted, false);
                } else callback.invoke(origin, false, false);
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
                publishNativeSubscriptionToWeb();
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

    private void publishNativeSubscriptionToWeb() {
        webView.postDelayed(() -> {
            String id = OneSignalManager.getSubscriptionId();
            if (id == null || id.trim().isEmpty()) return;
            String safe = id.replace("\\", "\\\\").replace("'", "\\'");
            webView.evaluateJavascript("window.BP_NATIVE_ONESIGNAL_SUBSCRIPTION_ID='" + safe + "';window.dispatchEvent(new CustomEvent('bp:native-push-ready',{detail:{subscriptionId:'" + safe + "'}}));", null);
        }, 1800);
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
