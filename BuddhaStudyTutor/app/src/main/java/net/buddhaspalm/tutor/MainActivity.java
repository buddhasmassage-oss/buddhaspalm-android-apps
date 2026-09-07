package net.buddhaspalm.tutor;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String HOME = "https://school.buddhaspinas.com/";
    private static final String PRIMARY_HOST = "school.buddhaspinas.com";
    private static final String API_FCM_REGISTER = HOME + "api.php?action=fcm_register_token";
    private static final int REQ_NOTIFY = 6101;
    private static final int REQ_MEDIA = 6102;
    private static final int REQ_FILES = 6103;

    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
        askNotificationPermission();
        TutorFirebaseMessagingService.ensureNotificationChannel(this);

        // google-services.json initializes Firebase automatically. The cached/admin
        // configuration remains as an optional fallback for future Firebase changes.
        if (FirebaseConfigManager.initializeFromCache(this)) obtainFcmToken();
        FirebaseConfigManager.fetchAndInitialize(this, (ready, message) -> {
            if (ready) obtainFcmToken();
            else obtainFcmToken();
        });

        webView.loadUrl(safeTutorUrl(getIntent().getStringExtra("click_url")));
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progress = new ProgressBar(this);
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " BuddhaStudyTutorAndroid/" + getAppVersion());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new NativeBridge(), "BuddhaTutorNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                injectToken();
                syncStoredFcmTokenToWebsite();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress >= 95 ? View.GONE : View.VISIBLE);
            }

            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILES);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker available", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> requestWebMediaPermission(request));
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open download", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isInternalTutorUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) || host == null) return false;
            return PRIMARY_HOST.equalsIgnoreCase(host) || ("www." + PRIMARY_HOST).equalsIgnoreCase(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (isInternalTutorUrl(url)) return false;
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("geo:")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    private void requestWebMediaPermission(PermissionRequest request) {
        List<String> needed = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.CAMERA);
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.RECORD_AUDIO);
            }
        }
        if (needed.isEmpty()) {
            request.grant(request.getResources());
        } else {
            pendingWebPermission = request;
            requestPermissions(needed.toArray(new String[0]), REQ_MEDIA);
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
    }

    private void obtainFcmToken() {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) return;
                String token = task.getResult();
                getSharedPreferences("buddhastudy_native", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
                try { FirebaseMessaging.getInstance().subscribeToTopic("buddhastudy_tutor"); } catch (Exception ignored) {}
                injectToken();
                syncFcmTokenToWebsite(token);
            });
        } catch (Exception ignored) {}
    }

    private void injectToken() {
        if (webView == null) return;
        String token = getSharedPreferences("buddhastudy_native", MODE_PRIVATE).getString("fcm_token", "");
        if (token == null || token.length() < 30) return;
        String device = Build.MANUFACTURER + " " + Build.MODEL;
        String version = getAppVersion();
        String js = "if(window.bspNativeFcmReady){window.bspNativeFcmReady(" + jsQuote(token) + "," + jsQuote(device) + "," + jsQuote(version) + ");}";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void syncStoredFcmTokenToWebsite() {
        String token = getSharedPreferences("buddhastudy_native", MODE_PRIVATE).getString("fcm_token", "");
        if (token != null && token.length() >= 30) syncFcmTokenToWebsite(token);
    }

    private void syncFcmTokenToWebsite(String token) {
        if (token == null || token.length() < 30) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String cookie = CookieManager.getInstance().getCookie(HOME);
                JSONObject payload = new JSONObject();
                payload.put("action", "fcm_register_token");
                payload.put("token", token);
                payload.put("platform", "android");
                payload.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
                payload.put("app_version", getAppVersion());

                URL endpoint = new URL(API_FCM_REGISTER + "&_=" + System.currentTimeMillis());
                conn = (HttpURLConnection) endpoint.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                if (cookie != null && !cookie.trim().isEmpty()) conn.setRequestProperty("Cookie", cookie);

                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
                int ignoredCode = conn.getResponseCode();
            } catch (Exception ignored) {
                // Token is still cached locally; the next page load/login retries sync.
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String getAppVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "1.1.0"; }
    }

    private static String jsQuote(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private String safeTutorUrl(String url) {
        if (isInternalTutorUrl(url)) return url;
        return HOME;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView != null) {
            String click = intent.getStringExtra("click_url");
            if (click == null) click = intent.getStringExtra("link");
            if (click == null) click = intent.getStringExtra("url");
            webView.loadUrl(safeTutorUrl(click));
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILES && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA && pendingWebPermission != null) {
            boolean all = true;
            for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) all = false;
            if (all) pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        }
    }

    public class NativeBridge {
        @JavascriptInterface public String getFcmToken() {
            return getSharedPreferences("buddhastudy_native", MODE_PRIVATE).getString("fcm_token", "");
        }

        @JavascriptInterface public String getDeviceName() {
            return Build.MANUFACTURER + " " + Build.MODEL;
        }

        @JavascriptInterface public String getAppVersion() {
            return MainActivity.this.getAppVersion();
        }

        @JavascriptInterface public void syncFcmToken() {
            syncStoredFcmTokenToWebsite();
        }

        @JavascriptInterface public void openNotificationSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                } catch (Exception ignored) {}
            });
        }
    }
}
