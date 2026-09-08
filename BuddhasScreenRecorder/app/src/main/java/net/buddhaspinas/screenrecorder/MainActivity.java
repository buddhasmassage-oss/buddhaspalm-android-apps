package net.buddhaspinas.screenrecorder;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.webkit.CookieManager;
import android.webkit.URLUtil;
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
    private static final int REQ_CAPTURE = 7001;
    private static final int REQ_AUDIO = 7002;
    private static final int REQ_NOTIFICATIONS = 7003;
    private static final String SITE_URL = "https://screenrecord.buddhaspinas.com";

    private MediaProjectionManager projectionManager;
    private CheckBox micCheck;
    private TextView statusText;
    private WebView webView;
    private boolean waitingForOverlay = false;
    private boolean pendingCaptureAfterAudio = false;

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
        if (intent != null && ACTION_REQUEST_CAPTURE.equals(intent.getAction())) {
            requestCapture();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 242, 234));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        top.setBackgroundColor(Color.rgb(91, 11, 18));

        ImageView logo = new ImageView(this);
        logo.setImageResource(net.buddhaspinas.screenrecorder.R.drawable.ic_buddha);
        top.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("Buddhas Screen Recorder");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, 1);
        TextView sub = new TextView(this);
        sub.setText("screenrecord.buddhaspinas.com • v1.1.0");
        sub.setTextColor(Color.rgb(224, 194, 103));
        sub.setTextSize(11);
        titleWrap.addView(title);
        titleWrap.addView(sub);
        top.addView(titleWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(14), dp(10), dp(14), dp(10));
        controls.setBackgroundColor(Color.WHITE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button bubbleButton = actionButton("Enable Floating Ball");
        bubbleButton.setOnClickListener(v -> enableFloatingBall());
        row.addView(bubbleButton, new LinearLayout.LayoutParams(0, dp(46), 1));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));

        Button recordButton = actionButton("Start Recording");
        recordButton.setOnClickListener(v -> requestCapture());
        row.addView(recordButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        controls.addView(row);

        Button browserButton = actionButton("Open Browser / Other Website");
        LinearLayout.LayoutParams browserLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        browserLp.topMargin = dp(8);
        browserButton.setOnClickListener(v -> openDefaultBrowser());
        controls.addView(browserButton, browserLp);

        micCheck = new CheckBox(this);
        micCheck.setText("Record microphone audio");
        micCheck.setChecked(true);
        micCheck.setTextColor(Color.rgb(70, 70, 70));
        controls.addView(micCheck, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setText("Ready. Android will ask permission before every new whole-screen capture session.");
        statusText.setTextColor(Color.rgb(95, 95, 95));
        statusText.setTextSize(12);
        controls.addView(statusText);
        root.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " BuddhasScreenRecorder/1.1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && host.endsWith("buddhaspinas.com")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
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

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(91, 11, 18));
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        return b;
    }

    private void enableFloatingBall() {
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlay = true;
            Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permissionIntent);
            statusText.setText("Allow 'Display over other apps', then return here.");
            return;
        }
        startOverlayService();
    }

    private void startOverlayService() {
        try {
            Intent service = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
            statusText.setText("Floating Buddha ball enabled. You can now open Chrome or another app.");
        } catch (Exception e) {
            Toast.makeText(this, "Unable to start floating control: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestCapture() {
        if (micCheck != null && micCheck.isChecked() &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCaptureAfterAudio = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        Intent captureIntent;
        if (Build.VERSION.SDK_INT >= 34) {
            captureIntent = projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            captureIntent = projectionManager.createScreenCaptureIntent();
        }
        startActivityForResult(captureIntent, REQ_CAPTURE);
        if (statusText != null) statusText.setText("Waiting for Android whole-screen capture permission…");
    }

    private void openDefaultBrowser() {
        try {
            Intent browser = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
            startActivity(browser);
        } catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))); } catch (Exception ignored) {}
        }
        if (statusText != null) statusText.setText("Browser opened. The floating ball stays available; start or control recording from it.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && pendingCaptureAfterAudio) {
            pendingCaptureAfterAudio = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestCapture();
            } else {
                if (micCheck != null) micCheck.setChecked(false);
                Toast.makeText(this, "Microphone denied. Recording will continue without microphone audio.", Toast.LENGTH_LONG).show();
                requestCapture();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
        serviceIntent.putExtra(RecorderService.EXTRA_MIC,
                micCheck != null && micCheck.isChecked() &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
        else startService(serviceIntent);
        if (Settings.canDrawOverlays(this)) startOverlayService();
        if (statusText != null) statusText.setText("Recording started. Open any website/app; use the floating Buddha ball to pause, resume, or stop.");
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
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
