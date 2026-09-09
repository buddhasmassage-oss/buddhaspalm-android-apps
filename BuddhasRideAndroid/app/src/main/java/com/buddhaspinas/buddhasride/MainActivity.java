package com.buddhaspinas.buddhasride;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String HOME_URL = "https://rider.buddhaspinas.com/";
    private static final String HOST = "rider.buddhaspinas.com";
    private static final int REQ_LOCATION = 1101;
    private static final int REQ_WEB_MEDIA = 1102;

    private WebView webView;
    private View splashOverlay;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraOutputUri;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private PermissionRequest pendingWebPermission;

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (fileCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data == null || data.getData() == null) {
                        if (cameraOutputUri != null) results = new Uri[]{cameraOutputUri};
                    } else {
                        ClipData clip = data.getClipData();
                        if (clip != null && clip.getItemCount() > 0) {
                            results = new Uri[clip.getItemCount()];
                            for (int i = 0; i < clip.getItemCount(); i++) {
                                results[i] = clip.getItemAt(i).getUri();
                            }
                        } else {
                            results = new Uri[]{data.getData()};
                        }
                    }
                }
                fileCallback.onReceiveValue(results);
                fileCallback = null;
                cameraOutputUri = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            configureSystemBars();
            setContentView(R.layout.activity_main);

            webView = findViewById(R.id.webView);
            splashOverlay = findViewById(R.id.splashOverlay);
            if (webView == null) throw new IllegalStateException("Android System WebView is unavailable");

            configureWebView();
            requestNotificationPermissionIfNeeded();

            Intent launchIntent = getIntent();
            Uri launchUri = launchIntent != null ? launchIntent.getData() : null;
            String pushUrl = launchIntent != null ? launchIntent.getStringExtra("url") : null;
            String startUrl = validRideUrl(pushUrl) ? pushUrl
                    : (launchUri != null && HOST.equalsIgnoreCase(launchUri.getHost()) ? launchUri.toString() : HOME_URL);
            webView.loadUrl(startUrl);
        } catch (Throwable startupError) {
            openBrowserFallback(startupError);
        }
    }

    private void openBrowserFallback(Throwable startupError) {
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(HOME_URL));
            startActivity(browser);
            Toast.makeText(this, "Opening Buddhas Ride in your browser.", Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
            Toast.makeText(this, "Buddhas Ride could not start. Please update Android System WebView/Chrome.", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void configureSystemBars() {
        Window w = getWindow();
        w.setStatusBarColor(ContextCompat.getColor(this, R.color.buddhas_blue));
        w.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(true);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) c.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            );
        }
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
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " BuddhasRideAndroid/1.0.3");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new RideWebViewClient());
        webView.setWebChromeClient(new RideWebChromeClient());
        webView.setDownloadListener(new RideDownloadListener());
        webView.addJavascriptInterface(new AndroidBridge(this), "BuddhasRideAndroid");
    }

    private class RideWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUri(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUri(Uri.parse(url));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (splashOverlay != null) {
                splashOverlay.animate().alpha(0f).setDuration(260).withEndAction(() -> splashOverlay.setVisibility(View.GONE)).start();
            }
            CookieManager.getInstance().flush();
            injectNativeBridgeHelpers();
            requestCurrentFcmToken();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame() && splashOverlay != null) {
                splashOverlay.setVisibility(View.GONE);
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 500) {
                Toast.makeText(MainActivity.this, "Buddhas Ride server is temporarily unavailable.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean handleUri(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        String host = uri.getHost();
        if (("http".equals(scheme) || "https".equals(scheme)) && HOST.equalsIgnoreCase(host)) return false;
        if ("http".equals(scheme) || "https".equals(scheme)) { openExternal(uri); return true; }
        if (scheme.equals("tel") || scheme.equals("mailto") || scheme.equals("sms") || scheme.equals("geo") || scheme.equals("market") || scheme.equals("intent")) { openExternal(uri); return true; }
        return false;
    }

    private class RideWebChromeClient extends WebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (!origin.startsWith("https://" + HOST)) { callback.invoke(origin, false, false); return; }
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                callback.invoke(origin, true, true);
            } else {
                geoOrigin = origin;
                geoCallback = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                boolean needsCamera = false;
                boolean asksForAudio = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) needsCamera = true;
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) asksForAudio = true;
                }
                // Buddhas Ride does not require microphone access. Deny unexpected audio capture.
                if (asksForAudio) {
                    request.deny();
                    return;
                }
                boolean cameraOk = !needsCamera || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                if (cameraOk) {
                    request.grant(request.getResources());
                } else {
                    pendingWebPermission = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_WEB_MEDIA);
                }
            });
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (pendingWebPermission == request) pendingWebPermission = null;
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> newCallback, FileChooserParams fileChooserParams) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = newCallback;

            Intent contentIntent = fileChooserParams.createIntent();
            contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);

            Intent cameraIntent = null;
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    File cameraFile = createCameraFile();
                    cameraOutputUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", cameraFile);
                    cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
                    cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (IOException ignored) { }
            }

            Intent chooser = new Intent(Intent.ACTION_CHOOSER);
            chooser.putExtra(Intent.EXTRA_INTENT, contentIntent);
            if (cameraIntent != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});

            try {
                fileChooserLauncher.launch(chooser);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback.onReceiveValue(null);
                fileCallback = null;
                Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    private File createCameraFile() throws IOException {
        File dir = new File(getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create camera cache directory");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return File.createTempFile("BR_" + stamp + "_", ".jpg", dir);
    }

    private class RideDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                req.setTitle(name); req.setDescription("Downloading from Buddhas Ride"); req.setMimeType(mimetype);
                req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                req.addRequestHeader("User-Agent", userAgent);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(req);
                Toast.makeText(MainActivity.this, "Download started", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                openExternal(Uri.parse(url));
            }
        }
    }

    private void injectNativeBridgeHelpers() {
        String js = "javascript:(function(){" +
                "window.BuddhasRideNative=true;" +
                "if(!navigator.share&&window.BuddhasRideAndroid){navigator.share=function(d){BuddhasRideAndroid.share((d&&d.title)||'',(d&&d.text)||'',(d&&d.url)||location.href);return Promise.resolve();};}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private boolean validRideUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            Uri u = Uri.parse(url);
            return "https".equalsIgnoreCase(u.getScheme()) && HOST.equalsIgnoreCase(u.getHost());
        } catch (Exception e) { return false; }
    }

    private void requestCurrentFcmToken() {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) return;
                dispatchFcmTokenToWeb(task.getResult());
            });
        } catch (Throwable ignored) {
            // Push registration must never prevent the ride app from opening.
        }
    }

    private void dispatchFcmTokenToWeb(String token) {
        if (webView == null || token == null || token.isEmpty()) return;
        runOnUiThread(() -> {
            String js = "window.dispatchEvent(new CustomEvent('br:fcm-token',{detail:{token:"
                    + JSONObject.quote(token) + ",platform:'android',appVersion:'1.0.3'}}));";
            webView.evaluateJavascript(js, null);
        });
    }

    private void openExternal(Uri uri) {
        try {
            Intent i;
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                i = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
            } else {
                i = new Intent(Intent.ACTION_VIEW, uri);
            }
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No compatible app found.", Toast.LENGTH_SHORT).show();
        }
    }

    public static class AndroidBridge {
        private final MainActivity activity;
        AndroidBridge(MainActivity activity) { this.activity = activity; }

        @JavascriptInterface
        public void share(String title, String text, String url) {
            activity.runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                String body = ((text == null ? "" : text.trim()) + "\n" + (url == null ? "" : url.trim())).trim();
                send.putExtra(Intent.EXTRA_SUBJECT, title == null ? "Buddhas Ride" : title);
                send.putExtra(Intent.EXTRA_TEXT, body);
                activity.startActivity(Intent.createChooser(send, "Share Buddhas Ride"));
            });
        }

        @JavascriptInterface
        public void openExternal(String url) {
            if (url == null) return;
            activity.runOnUiThread(() -> activity.openExternal(Uri.parse(url)));
        }

        @JavascriptInterface
        public String getPlatform() { return "android"; }

        @JavascriptInterface
        public String getAppVersion() { return "1.0.3"; }

        @JavascriptInterface
        public void requestFcmToken() { activity.requestCurrentFcmToken(); }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1103);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && geoCallback != null) {
            boolean granted = false;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            geoCallback.invoke(geoOrigin, granted, granted);
            geoCallback = null;
            geoOrigin = null;
        } else if (requestCode == REQ_WEB_MEDIA && pendingWebPermission != null) {
            boolean granted = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { granted = false; break; }
            if (granted) pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String pushUrl = intent.getStringExtra("url");
        if (webView != null && validRideUrl(pushUrl)) { webView.loadUrl(pushUrl); return; }
        Uri uri = intent.getData();
        if (webView != null && uri != null && HOST.equalsIgnoreCase(uri.getHost())) webView.loadUrl(uri.toString());
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
