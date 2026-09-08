package net.buddhaspinas.screenrecorder;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String ACTION_REQUEST_CAPTURE = "net.buddhaspinas.screenrecorder.REQUEST_CAPTURE";
    public static final String ACTION_OPEN_EDITOR = "net.buddhaspinas.screenrecorder.OPEN_EDITOR";
    private static final int REQ_CAPTURE = 7001;
    private static final int REQ_AUDIO = 7002;
    private static final int REQ_NOTIFICATIONS = 7003;
    private static final int REQ_FILE_CHOOSER = 7004;
    private static final String SITE_URL = "https://screenrecord.buddhaspinas.com";

    private MediaProjectionManager projectionManager;
    private CheckBox micCheck;
    private TextView statusText;
    private WebView webView;
    private boolean waitingForOverlay = false;
    private boolean pendingCaptureAfterAudio = false;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        buildUi();
        requestNotificationPermissionIfNeeded();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (ACTION_REQUEST_CAPTURE.equals(intent.getAction())) requestCapture();
        if (ACTION_OPEN_EDITOR.equals(intent.getAction()) && webView != null) webView.loadUrl(SITE_URL + "/editor/");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 246, 241));
        applySafeInsets(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(7), dp(10), dp(7));
        top.setBackgroundColor(Color.rgb(104, 21, 35));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_buddha);
        top.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setPadding(dp(8), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("Buddhas Screen Recorder");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, 1);
        TextView sub = new TextView(this);
        sub.setText("screenrecord.buddhaspinas.com • v1.3.0");
        sub.setTextColor(Color.rgb(231, 182, 84));
        sub.setTextSize(9);
        titleWrap.addView(title);
        titleWrap.addView(sub);
        top.addView(titleWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(8), dp(7), dp(8), dp(7));
        controls.setBackgroundColor(Color.WHITE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels = {"● Record", "◉ Ball", "▣ Files", "✂ Editor"};
        View.OnClickListener[] listeners = {
                v -> requestCapture(),
                v -> enableFloatingBall(),
                v -> openPhoneRecordings(),
                v -> openVideoEditor()
        };
        for (int i = 0; i < labels.length; i++) {
            Button b = compactButton(labels[i]);
            b.setOnClickListener(listeners[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(5);
            row.addView(b, lp);
        }
        controls.addView(row);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(4), 0, 0);
        micCheck = new CheckBox(this);
        micCheck.setText("Mic");
        micCheck.setChecked(true);
        micCheck.setTextColor(Color.rgb(80, 70, 72));
        micCheck.setTextSize(11);
        statusRow.addView(micCheck, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        statusText = new TextView(this);
        statusText.setText("Native recorder ready • MP4 saves to Movies");
        statusText.setTextColor(Color.rgb(105, 95, 97));
        statusText.setTextSize(10);
        statusText.setSingleLine(true);
        statusText.setPadding(dp(6), 0, 0, 0);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        controls.addView(statusRow);
        root.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setTextZoom(100);
        settings.setUserAgentString(settings.getUserAgentString() + " BuddhasScreenRecorder/1.3.0");
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new NativeBridge(), "BuddhasNative");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = filePathCallback;
                try {
                    Intent chooser = fileChooserParams.createIntent();
                    chooser.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(chooser, REQ_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "Unable to open file picker.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && host.endsWith("buddhaspinas.com")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectNativeFallback();
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(fileName);
                request.setDescription("Buddhas Screen Recorder download");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this, "Download started: " + fileName, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
            }
        });
        webView.loadUrl(SITE_URL);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void applySafeInsets(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }

    private void injectNativeFallback() {
        if (webView == null) return;
        String js = "(function(){window.__BUDDHAS_NATIVE_RECORDER__=true;" +
                "if(!window.__BUDDHAS_NATIVE_CLICK_BOUND__){window.__BUDDHAS_NATIVE_CLICK_BOUND__=true;document.addEventListener('click',function(e){var t=e.target;var b=t&&t.closest?t.closest('#startBtn'):null;if(b&&window.BuddhasNative&&typeof window.BuddhasNative.startRecording==='function'){e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();window.BuddhasNative.startRecording();}},true);}" +
                "var b=document.getElementById('startBtn');if(b)b.disabled=false;" +
                "var l=document.getElementById('secureLabel');if(l)l.textContent='Native Android recorder ready';" +
                "var s=document.getElementById('supportText');if(s)s.textContent='Start Recording uses Android whole-screen capture. MP4 saves on this phone.';" +
                "var p=document.getElementById('phoneRecordingsBtn');if(p)p.classList.remove('hidden');})();";
        webView.evaluateJavascript(js, null);
    }

    private Button compactButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(104, 21, 35));
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
    }

    private void enableFloatingBall() {
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlay = true;
            Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(permissionIntent);
            statusText.setText("Allow Display over other apps, then return.");
            return;
        }
        startOverlayService();
    }

    private void startOverlayService() {
        try {
            Intent service = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
            statusText.setText("Floating ball enabled • open any app/site");
        } catch (Exception e) {
            Toast.makeText(this, "Unable to start floating control: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestCapture() {
        if (micCheck != null && micCheck.isChecked() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCaptureAfterAudio = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        Intent captureIntent;
        if (Build.VERSION.SDK_INT >= 34) captureIntent = projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        else captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQ_CAPTURE);
        if (statusText != null) statusText.setText("Waiting for Android screen-capture permission…");
    }

    private void openPhoneRecordings() { startActivity(new Intent(this, RecordingsActivity.class)); }
    private void openVideoEditor() { if (webView != null) webView.loadUrl(SITE_URL + "/editor/"); }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && pendingCaptureAfterAudio) {
            pendingCaptureAfterAudio = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) requestCapture();
            else {
                if (micCheck != null) micCheck.setChecked(false);
                Toast.makeText(this, "Microphone denied. Recording will continue without microphone audio.", Toast.LENGTH_LONG).show();
                requestCapture();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (fileChooserCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                fileChooserCallback.onReceiveValue(results);
                fileChooserCallback = null;
            }
            return;
        }
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            if (statusText != null) statusText.setText("Screen recording permission was cancelled.");
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        Intent serviceIntent = new Intent(this, RecorderService.class);
        serviceIntent.setAction(RecorderService.ACTION_START);
        serviceIntent.putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode);
        serviceIntent.putExtra(RecorderService.EXTRA_RESULT_DATA, data);
        serviceIntent.putExtra(RecorderService.EXTRA_WIDTH, metrics.widthPixels);
        serviceIntent.putExtra(RecorderService.EXTRA_HEIGHT, metrics.heightPixels);
        serviceIntent.putExtra(RecorderService.EXTRA_DENSITY, metrics.densityDpi);
        serviceIntent.putExtra(RecorderService.EXTRA_MIC, micCheck != null && micCheck.isChecked() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent); else startService(serviceIntent);
        if (Settings.canDrawOverlays(this)) startOverlayService();
        if (statusText != null) statusText.setText("RECORDING • Stop & Save creates MP4 automatically");
        Toast.makeText(this, "Whole-screen MP4 recording started", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForOverlay && Settings.canDrawOverlays(this)) {
            waitingForOverlay = false;
            startOverlayService();
        }
        injectNativeFallback();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private class NativeBridge {
        @JavascriptInterface public void startRecording() { runOnUiThread(() -> requestCapture()); }
        @JavascriptInterface public void openPhoneRecordings() { runOnUiThread(() -> openPhoneRecordings()); }
        @JavascriptInterface public void enableFloatingBall() { runOnUiThread(() -> enableFloatingBall()); }
        @JavascriptInterface public void openVideoEditor() { runOnUiThread(() -> openVideoEditor()); }
        @JavascriptInterface public String getVersion() { return "1.3.0"; }
    }
}
