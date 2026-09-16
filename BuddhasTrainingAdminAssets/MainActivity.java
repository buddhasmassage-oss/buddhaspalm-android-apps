package net.buddhasprovider.admin;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1201;
    private static final int REQ_NOTIFICATIONS = 1202;
    static final String VERSION_UA = "BuddhasTrainingAdmin-Android/1.0.2";
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private String fcmToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(6, 43, 84));

        RelativeLayout root = new RelativeLayout(this);
        root.setBackgroundColor(Color.rgb(6, 43, 84));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(247, 244, 235));
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(228, 184, 78)));

        RelativeLayout.LayoutParams webParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        RelativeLayout.LayoutParams progressParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 6
        );
        progressParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);

        root.addView(webView, webParams);
        root.addView(progress, progressParams);
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                    WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        NotificationHelper.ensureChannel(this);
        configureWebView();
        requestNotificationPermission();
        loadFcmToken();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            loadInitialUrl(getIntent());
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setDefaultFontSize(16);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " " + VERSION_UA);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isAllowedAdminUri(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                injectAdminMobileEnhancements(url);
                registerCurrentToken();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
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

    private boolean isAllowedAdminUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        String allowed = getString(R.string.allowed_host);
        if (!allowed.equalsIgnoreCase(host) && !("www." + allowed).equalsIgnoreCase(host)) return false;
        String path = uri.getPath();
        return path != null && (path.equals("/admin") || path.startsWith("/admin/"));
    }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private void injectAdminMobileEnhancements(String url) {
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return;
        }
        if (!isAllowedAdminUri(uri)) return;

        String css =
                "html,body{width:100%!important;max-width:100%!important;overflow-x:hidden!important;-webkit-text-size-adjust:100%!important;text-size-adjust:100%!important;}" +
                "*,*:before,*:after{box-sizing:border-box!important;}" +
                "img,svg,video,canvas{max-width:100%;}" +
                "input,select,textarea,button{max-width:100%;}" +
                ".admin-main,.admin-content,.panel,.welcome,.stat,.sidebar,.admin-top{min-width:0!important;}" +
                "@media(max-width:900px){" +
                "body{margin:0!important;width:100%!important;overflow-x:hidden!important;}" +
                ".sidebar{position:fixed!important;left:0!important;top:0!important;bottom:0!important;width:min(86vw,310px)!important;max-width:310px!important;height:100%!important;transform:translateX(-105%)!important;transition:transform .22s ease!important;z-index:9999!important;overflow-y:auto!important;overscroll-behavior:contain!important;}" +
                "body.side-open .sidebar{transform:translateX(0)!important;}" +
                ".side-brand{padding:14px 12px!important;gap:10px!important;align-items:center!important;}" +
                ".side-brand img{width:44px!important;height:44px!important;object-fit:contain!important;border-radius:11px!important;flex:0 0 44px!important;}" +
                ".side-brand b{font-size:.9rem!important;line-height:1.15!important;}" +
                ".side-brand small{font-size:.68rem!important;line-height:1.2!important;}" +
                ".sidebar nav{padding-bottom:28px!important;}" +
                ".sidebar nav a{display:flex!important;align-items:center!important;gap:10px!important;min-height:46px!important;padding:9px 12px!important;font-size:.82rem!important;line-height:1.25!important;white-space:normal!important;}" +
                ".sidebar nav a img{width:21px!important;height:21px!important;max-width:21px!important;object-fit:contain!important;flex:0 0 21px!important;}" +
                ".nav-label{padding:13px 12px 6px!important;font-size:.65rem!important;}" +
                ".admin-main{margin-left:0!important;width:100%!important;max-width:100%!important;min-height:100%!important;display:block!important;}" +
                ".admin-top{position:sticky!important;top:0!important;z-index:1200!important;width:100%!important;max-width:100%!important;padding:10px 12px!important;gap:9px!important;align-items:stretch!important;flex-direction:column!important;}" +
                ".admin-top>div:first-child{display:flex!important;align-items:center!important;width:100%!important;min-width:0!important;gap:10px!important;}" +
                ".admin-top h1{font-size:1.02rem!important;line-height:1.2!important;margin:0!important;overflow-wrap:anywhere!important;}" +
                ".admin-top p{font-size:.7rem!important;margin:2px 0 0!important;}" +
                ".side-toggle{display:flex!important;flex-direction:column!important;justify-content:center!important;align-items:center!important;width:42px!important;height:42px!important;min-width:42px!important;padding:9px!important;border-radius:11px!important;}" +
                ".side-toggle span{width:21px!important;height:2px!important;margin:2px 0!important;}" +
                ".admin-top-actions{display:flex!important;width:100%!important;max-width:100%!important;gap:7px!important;overflow-x:auto!important;padding:1px 0 3px!important;-webkit-overflow-scrolling:touch!important;scrollbar-width:none!important;}" +
                ".admin-top-actions::-webkit-scrollbar{display:none!important;}" +
                ".admin-top-actions .admin-btn{flex:0 0 auto!important;min-height:38px!important;padding:7px 10px!important;font-size:.7rem!important;white-space:nowrap!important;}" +
                ".admin-content{width:100%!important;max-width:100%!important;min-width:0!important;padding:12px!important;overflow-x:hidden!important;}" +
                ".admin-content>*{max-width:100%!important;min-width:0!important;}" +
                ".welcome{grid-template-columns:1fr!important;gap:14px!important;padding:16px!important;}" +
                ".welcome h2{font-size:1.2rem!important;line-height:1.25!important;}" +
                ".welcome p{font-size:.84rem!important;line-height:1.55!important;}" +
                ".welcome-actions{display:flex!important;flex-wrap:wrap!important;gap:8px!important;}" +
                ".dashboard-logo{width:84px!important;height:84px!important;max-width:84px!important;object-fit:contain!important;margin:0 auto!important;}" +
                ".stat-grid,.stat-cards,.dashboard-stat-grid{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:10px!important;}" +
                ".stat,.stat-card{min-width:0!important;max-width:100%!important;padding:12px!important;gap:9px!important;overflow:hidden!important;}" +
                ".stat img,.stat-card img{width:25px!important;height:25px!important;max-width:25px!important;object-fit:contain!important;flex:0 0 25px!important;}" +
                ".stat strong,.stat-card strong{font-size:1rem!important;line-height:1.15!important;}" +
                ".stat small,.stat-card small{font-size:.7rem!important;line-height:1.25!important;white-space:normal!important;overflow-wrap:anywhere!important;}" +
                ".panel,.admin-card{width:100%!important;max-width:100%!important;padding:14px!important;overflow:hidden!important;border-radius:15px!important;}" +
                ".panel-head{display:flex!important;flex-wrap:wrap!important;gap:10px!important;align-items:flex-start!important;}" +
                ".panel-head>div{min-width:0!important;flex:1 1 220px!important;}" +
                ".panel h2,.admin-card h2{font-size:1.08rem!important;line-height:1.25!important;overflow-wrap:anywhere!important;}" +
                ".panel h3,.admin-card h3{font-size:.96rem!important;line-height:1.3!important;overflow-wrap:anywhere!important;}" +
                ".panel p,.admin-card p{font-size:.82rem!important;line-height:1.5!important;overflow-wrap:anywhere!important;}" +
                ".form-grid{grid-template-columns:1fr!important;gap:12px!important;}" +
                ".span-2{grid-column:auto!important;}" +
                "label{min-width:0!important;font-size:.8rem!important;}" +
                "input,select,textarea{width:100%!important;font-size:16px!important;min-height:43px!important;}" +
                "textarea{min-height:96px!important;}" +
                ".admin-btn,.btn,button{min-height:40px!important;max-width:100%!important;white-space:normal!important;line-height:1.2!important;}" +
                "table{width:100%!important;max-width:100%!important;font-size:.76rem!important;}" +
                "th,td{padding:7px 8px!important;overflow-wrap:anywhere!important;}" +
                ".table-wrap,.admin-table-wrap,.responsive-table{width:100%!important;max-width:100%!important;overflow-x:auto!important;-webkit-overflow-scrolling:touch!important;}" +
                ".feature-checks{grid-template-columns:1fr!important;gap:8px!important;}" +
                ".feature-checks span{min-width:0!important;font-size:.78rem!important;line-height:1.35!important;}" +
                ".feature-checks img{width:20px!important;height:20px!important;max-width:20px!important;}" +
                ".login-page{padding:12px!important;}" +
                ".login-shell{width:100%!important;max-width:100%!important;margin:0 auto!important;grid-template-columns:1fr!important;min-height:auto!important;}" +
                ".login-showcase,.login-card{width:100%!important;max-width:560px!important;margin:0 auto!important;}" +
                "}" +
                "@media(max-width:380px){.stat-grid,.stat-cards,.dashboard-stat-grid{grid-template-columns:1fr!important;}.admin-content{padding:10px!important;}.panel,.admin-card{padding:12px!important;}}";

        String js =
                "(function(){try{" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                "m.content='width=device-width,initial-scale=1,maximum-scale=1,viewport-fit=cover';" +
                "var s=document.getElementById('bpNativeAdminCss');" +
                "if(!s){s=document.createElement('style');s.id='bpNativeAdminCss';document.head.appendChild(s);}" +
                "s.textContent=" + JSONObject.quote(css) + ";" +
                "document.documentElement.classList.add('bp-native-admin-app');" +
                "document.body.classList.add('bp-native-admin-app');" +
                "}catch(e){console.warn('Admin mobile CSS',e);}})();";
        webView.evaluateJavascript(js, null);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
        if (isAllowedAdminUri(data)) webView.loadUrl(data.toString());
        else webView.loadUrl(getString(R.string.start_url));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadInitialUrl(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}

final class FcmRegistrar {
    private FcmRegistrar() {}

    static void register(Context context, String token) {
        if (context == null || token == null || token.trim().isEmpty()) return;
        final String clean = token.trim();
        final String endpoint = context.getString(R.string.fcm_register_url);
        final String cookie;
        try {
            cookie = CookieManager.getInstance().getCookie(endpoint);
        } catch (Throwable ignored) {
            return;
        }
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
                con.setRequestProperty("User-Agent", MainActivity.VERSION_UA);
                String device = Build.MANUFACTURER + " " + Build.MODEL;
                String body =
                        "token=" + enc(clean) +
                        "&package_name=" + enc(context.getPackageName()) +
                        "&device_name=" + enc(device) +
                        "&platform=android";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(bytes);
                }
                int code = con.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? con.getInputStream() : con.getErrorStream();
                readAll(stream);
            } catch (Exception ignored) {
            } finally {
                if (con != null) con.disconnect();
            }
        }, "BuddhasTrainingAdmin-FCM-Register").start();
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

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
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setLightColor(Color.rgb(228, 184, 78));
        manager.createNotificationChannel(channel);
    }

    static void show(Context context, String title, String body, String url) {
        ensureChannel(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (url != null && !url.trim().isEmpty()) {
            try {
                String clean = url.trim();
                if (clean.startsWith("/admin/")) clean = "https://buddhasprovider.net" + clean;
                else if (clean.startsWith("admin/")) clean = "https://buddhasprovider.net/" + clean;
                intent.setData(Uri.parse(clean));
            } catch (Exception ignored) {
            }
        }

        int request = (int) (System.currentTimeMillis() & 0x7fffffff);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, request, intent, flags);

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context);
            builder.setPriority(android.app.Notification.PRIORITY_HIGH);
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.buddhas_training_admin_logo))
                .setContentTitle(title == null || title.isEmpty() ? context.getString(R.string.app_name) : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(request, builder.build());
    }
}
