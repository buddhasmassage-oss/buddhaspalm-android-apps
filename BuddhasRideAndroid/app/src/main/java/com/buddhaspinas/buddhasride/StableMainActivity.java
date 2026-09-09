package com.buddhaspinas.buddhasride;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StableMainActivity extends AppCompatActivity {
    private static final String HOME_URL = "https://rider.buddhaspinas.com/";
    private static final String HOST = "rider.buddhaspinas.com";
    private static final int REQ_LOCATION = 2101;

    private WebView webView;
    private View splashOverlay;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraOutputUri;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (fileCallback == null) return;
                Uri[] out = null;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data == null || data.getData() == null) {
                        if (cameraOutputUri != null) out = new Uri[]{cameraOutputUri};
                    } else if (data.getClipData() != null) {
                        ClipData clip = data.getClipData();
                        out = new Uri[clip.getItemCount()];
                        for (int i = 0; i < clip.getItemCount(); i++) out[i] = clip.getItemAt(i).getUri();
                    } else {
                        out = new Uri[]{data.getData()};
                    }
                }
                fileCallback.onReceiveValue(out);
                fileCallback = null;
                cameraOutputUri = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureBars();
        applySafeAreaInsets();

        webView = findViewById(R.id.webView);
        splashOverlay = findViewById(R.id.splashOverlay);

        if (webView == null) {
            Toast.makeText(this, "Buddhas Ride cannot start because Android System WebView is unavailable.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            configureWebView();
            String startUrl = resolveStartUrl(getIntent());
            webView.loadUrl(startUrl);
        } catch (Throwable error) {
            showInAppError();
        }

        requestNotificationPermissionSafely();
    }

    private void configureBars() {
        Window w = getWindow();
        w.setStatusBarColor(ContextCompat.getColor(this, R.color.buddhas_blue));
        w.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 15+ enforces edge-to-edge for target SDK 35. We handle the
            // system-bar insets ourselves so the web app never touches the camera,
            // status icons, gesture area, or top edge of the display.
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }
    }

    private void applySafeAreaInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        final View root = findViewById(R.id.root);
        if (root == null) return;

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.statusBars() |
                    WindowInsets.Type.navigationBars() |
                    WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        // Match the responsive PWA viewport instead of letting Android WebView
        // auto-enlarge text or choose its own overview scale.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        webView.setInitialScale(100);

        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " BuddhasRideAndroid/1.0.5");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        try { cookies.setAcceptThirdPartyCookies(webView, true); } catch (Throwable ignored) { }

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new RideClient());
        webView.setWebChromeClient(new RideChrome());
        webView.setDownloadListener(new RideDownloadListener());
        webView.addJavascriptInterface(new AndroidBridge(), "BuddhasRideAndroid");
    }

    private class RideClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUri(request.getUrl());
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUri(Uri.parse(url));
        }
        @Override public void onPageFinished(WebView view, String url) {
            if (splashOverlay != null && splashOverlay.getVisibility() == View.VISIBLE) {
                splashOverlay.animate().alpha(0f).setDuration(220)
                        .withEndAction(() -> splashOverlay.setVisibility(View.GONE)).start();
            }
            CookieManager.getInstance().flush();
            injectDisplayNormalization();
            injectNativeHelpers();
            requestCurrentFcmToken();
        }
        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                if (splashOverlay != null) splashOverlay.setVisibility(View.GONE);
                Toast.makeText(StableMainActivity.this, "Unable to load Buddhas Ride. Check your internet connection.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private class RideChrome extends WebChromeClient {
        @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (origin == null || !origin.startsWith("https://" + HOST)) {
                callback.invoke(origin, false, false);
                return;
            }
            boolean granted = ContextCompat.checkSelfPermission(StableMainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(StableMainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                callback.invoke(origin, true, true);
            } else {
                geoOrigin = origin;
                geoCallback = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
        }

        @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;

            Intent content = params.createIntent();
            content.addCategory(Intent.CATEGORY_OPENABLE);
            content.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);

            Intent camera = null;
            if (ContextCompat.checkSelfPermission(StableMainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    File f = createCameraFile();
                    cameraOutputUri = FileProvider.getUriForFile(StableMainActivity.this, getPackageName() + ".fileprovider", f);
                    camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
                    camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (IOException ignored) { }
            }

            Intent chooser = new Intent(Intent.ACTION_CHOOSER);
            chooser.putExtra(Intent.EXTRA_INTENT, content);
            if (camera != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
            fileChooserLauncher.launch(chooser);
            return true;
        }
    }

    private File createCameraFile() throws IOException {
        File dir = new File(getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("camera cache unavailable");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return File.createTempFile("BR_" + stamp + "_", ".jpg", dir);
    }

    private class RideDownloadListener implements DownloadListener {
        @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                req.setTitle(name);
                req.setMimeType(mimetype);
                req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                req.addRequestHeader("User-Agent", userAgent);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
            } catch (Throwable ignored) {
                Toast.makeText(StableMainActivity.this, "Download could not start.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean handleUri(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        String host = uri.getHost();
        if (("https".equals(scheme) || "http".equals(scheme)) && HOST.equalsIgnoreCase(host)) return false;
        if ("https".equals(scheme) || "http".equals(scheme) || "tel".equals(scheme) || "mailto".equals(scheme)
                || "sms".equals(scheme) || "geo".equals(scheme) || "market".equals(scheme) || "intent".equals(scheme)) {
            openExternal(uri);
            return true;
        }
        return true;
    }

    private String resolveStartUrl(Intent intent) {
        if (intent == null) return HOME_URL;
        String pushed = intent.getStringExtra("url");
        if (isRideUrl(pushed)) return pushed;
        Uri data = intent.getData();
        return data != null && HOST.equalsIgnoreCase(data.getHost()) ? data.toString() : HOME_URL;
    }

    private boolean isRideUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            Uri u = Uri.parse(url);
            return "https".equalsIgnoreCase(u.getScheme()) && HOST.equalsIgnoreCase(u.getHost());
        } catch (Throwable ignored) { return false; }
    }

    private void injectDisplayNormalization() {
        if (webView == null) return;
        String js = "(function(){try{" +
                "var h=document.documentElement;if(h){h.style.setProperty('-webkit-text-size-adjust','100%','important');h.style.setProperty('text-size-adjust','100%','important');}" +
                "var b=document.body;if(b){b.style.setProperty('-webkit-text-size-adjust','100%','important');b.style.setProperty('text-size-adjust','100%','important');}" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';m.content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no';document.head&&document.head.appendChild(m);}" +
                "}catch(e){}})();";
        try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) { }
    }

    private void injectNativeHelpers() {
        if (webView == null) return;
        String js = "javascript:(function(){window.BuddhasRideNative=true;if(!navigator.share&&window.BuddhasRideAndroid){navigator.share=function(d){BuddhasRideAndroid.share((d&&d.title)||'',(d&&d.text)||'',(d&&d.url)||location.href);return Promise.resolve();};}})();";
        try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) { }
    }

    private void requestCurrentFcmToken() {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty() || webView == null) return;
                String js = "window.dispatchEvent(new CustomEvent('br:fcm-token',{detail:{token:"
                        + JSONObject.quote(task.getResult()) + ",platform:'android',appVersion:'1.0.5'}}));";
                runOnUiThread(() -> {
                    try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) { }
                });
            });
        } catch (Throwable ignored) { }
    }

    private void requestNotificationPermissionSafely() {
        try {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2102);
            }
        } catch (Throwable ignored) { }
    }

    private void showInAppError() {
        if (splashOverlay != null) splashOverlay.setVisibility(View.GONE);
        if (webView != null) {
            try {
                webView.loadDataWithBaseURL(HOME_URL,
                        "<html><body style='font-family:sans-serif;padding:32px;text-align:center'><h2 style='color:#075dff'>Buddhas Ride</h2><p>The app could not start its in-app browser.</p><p>Please update Android System WebView or Chrome, then reopen Buddhas Ride.</p></body></html>",
                        "text/html", "UTF-8", null);
            } catch (Throwable ignored) { }
        }
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = "intent".equalsIgnoreCase(uri.getScheme()) ? Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, uri);
            startActivity(i);
        } catch (Throwable ignored) {
            Toast.makeText(this, "No compatible app found.", Toast.LENGTH_SHORT).show();
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public void share(String title, String text, String url) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title == null ? "Buddhas Ride" : title);
                String body = ((text == null ? "" : text.trim()) + "\n" + (url == null ? "" : url.trim())).trim();
                send.putExtra(Intent.EXTRA_TEXT, body);
                startActivity(Intent.createChooser(send, "Share Buddhas Ride"));
            });
        }
        @JavascriptInterface public String getPlatform() { return "android"; }
        @JavascriptInterface public String getAppVersion() { return "1.0.5"; }
        @JavascriptInterface public void requestFcmToken() { requestCurrentFcmToken(); }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && geoCallback != null) {
            boolean granted = false;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            geoCallback.invoke(geoOrigin, granted, granted);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView != null) webView.loadUrl(resolveStartUrl(intent));
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
