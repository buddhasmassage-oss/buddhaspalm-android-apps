package net.buddhaspinas.screenrecorder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String BASE_URL = "https://screenrecord.buddhaspinas.com/";
    public static final String EDITOR_URL = BASE_URL + "editor/";
    public static final String EXTRA_START_URL = "start_url";
    public static final String EXTRA_EDIT_URI = "edit_uri";
    public static final String EXTRA_EDIT_NAME = "edit_name";
    public static final String ACTION_REQUEST_CAPTURE = "net.buddhaspinas.screenrecorder.REQUEST_CAPTURE";
    public static final String ACTION_OPEN_EDITOR = "net.buddhaspinas.screenrecorder.OPEN_EDITOR";

    private static final int REQ_CAPTURE = 4101;
    private static final int REQ_FILE = 4102;
    private static final int REQ_OVERLAY = 4103;
    private static final int REQ_MIC = 4104;
    private static final int REQ_NOTIFICATIONS = 4105;

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private boolean pendingMic;
    private Uri pendingEditorUri;
    private String pendingEditorName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(17, 24, 39));
        buildWebView();
        handleIntent(getIntent(), savedInstanceState == null);
    }

    private void buildWebView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setTextZoom(100);
        String ua = ws.getUserAgentString();
        if (ua == null) ua = "";
        if (!ua.contains("BuddhasScreenRecorder/")) ws.setUserAgentString(ua + " BuddhasScreenRecorder/1.4.0");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new NativeBridge(), "BuddhasNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri == null ? "" : String.valueOf(uri.getScheme());
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectNativeFallback();
                if (pendingEditorUri != null && url != null && url.contains("/editor")) injectPendingEditorRecording();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                if (pendingEditorUri != null && isVideoChooser(params)) {
                    Uri exact = pendingEditorUri;
                    pendingEditorUri = null;
                    callback.onReceiveValue(new Uri[]{exact});
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "Selected recording loaded into editor", Toast.LENGTH_SHORT).show();
                    return true;
                }
                Intent intent;
                try { intent = params.createIntent(); }
                catch (Exception e) { intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"); }
                try { startActivityForResult(intent, REQ_FILE); }
                catch (Exception e) {
                    fileChooserCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Could not open file picker", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    req.setTitle(name);
                    req.setDescription("Buddhas Screen Recorder download");
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Buddhas Screen Recorder/" + name);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null && !cookies.isEmpty()) req.addRequestHeader("Cookie", cookies);
                    if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
                    ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                    Toast.makeText(MainActivity.this, "Download started: " + name, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
                }
            }
        });
    }

    private boolean isVideoChooser(WebChromeClient.FileChooserParams params) {
        if (params == null) return true;
        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) return true;
        for (String t : types) {
            if (t == null || t.isEmpty() || t.equals("*/*") || t.startsWith("video/") || t.contains(".mp4") || t.contains(".webm")) return true;
        }
        return false;
    }

    private void handleIntent(Intent intent, boolean initial) {
        if (intent == null) { if (initial) webView.loadUrl(BASE_URL); return; }
        String editUri = intent.getStringExtra(EXTRA_EDIT_URI);
        if (editUri != null && !editUri.isEmpty()) {
            pendingEditorUri = Uri.parse(editUri);
            pendingEditorName = intent.getStringExtra(EXTRA_EDIT_NAME);
        }
        String action = intent.getAction();
        if (ACTION_REQUEST_CAPTURE.equals(action)) {
            webView.loadUrl(BASE_URL);
            webView.postDelayed(this::requestCapture, 350);
            return;
        }
        if (ACTION_OPEN_EDITOR.equals(action)) { webView.loadUrl(EDITOR_URL); return; }
        String url = intent.getStringExtra(EXTRA_START_URL);
        if (url == null || url.isEmpty()) url = pendingEditorUri != null ? EDITOR_URL : BASE_URL;
        webView.loadUrl(url);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, false);
    }

    private void injectNativeFallback() {
        if (webView == null) return;
        String js = "(function(){window.__BUDDHAS_NATIVE_RECORDER__=true;if(!window.__BUDDHAS_NATIVE_CLICK_BOUND__){window.__BUDDHAS_NATIVE_CLICK_BOUND__=true;document.addEventListener('click',function(e){var t=e.target;var b=t&&t.closest?t.closest('#startBtn'):null;if(b&&window.BuddhasNative&&typeof window.BuddhasNative.startRecording==='function'){e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();window.BuddhasNative.startRecording();}},true);}var b=document.getElementById('startBtn');if(b)b.disabled=false;var l=document.getElementById('secureLabel');if(l)l.textContent='Native Android recorder ready';var s=document.getElementById('supportText');if(s)s.textContent='Start Recording uses Android whole-screen capture. MP4 saves on this phone.';var p=document.getElementById('phoneRecordingsBtn');if(p)p.classList.remove('hidden');})();";
        webView.evaluateJavascript(js, null);
    }

    private void injectPendingEditorRecording() {
        if (webView == null || pendingEditorUri == null) return;
        String safeName = jsString(pendingEditorName == null ? "Selected phone recording" : pendingEditorName);
        String js = "(function(){if(window.__BUDDHAS_EXACT_EDIT_UI__)return;window.__BUDDHAS_EXACT_EDIT_UI__=true;var f=document.getElementById('videoFiles');if(!f)return;var n=" + safeName + ";var box=document.createElement('div');box.id='nativeExactEditBar';box.style.cssText='position:fixed;left:12px;right:12px;top:max(12px,env(safe-area-inset-top));z-index:2147483646;padding:12px 14px;border-radius:14px;background:#111827;color:white;box-shadow:0 12px 35px rgba(0,0,0,.28);font:600 14px/1.35 system-ui,sans-serif;display:flex;gap:10px;align-items:center;justify-content:space-between';var txt=document.createElement('span');txt.textContent='Ready to edit: '+n;txt.style.cssText='min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap';var btn=document.createElement('button');btn.type='button';btn.textContent='Load recording';btn.style.cssText='border:0;border-radius:10px;padding:9px 12px;background:#d4af37;color:#111827;font-weight:800;white-space:nowrap';btn.onclick=function(){f.click();};box.appendChild(txt);box.appendChild(btn);document.body.appendChild(box);f.addEventListener('change',function(){if(f.files&&f.files.length){box.remove();}}, {once:true});setTimeout(function(){try{f.click();}catch(e){}},450);})();";
        webView.evaluateJavascript(js, null);
    }

    private static String jsString(String value) {
        if (value == null) return "''";
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n") + "'";
    }

    public void requestCapture() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            final CheckBox mic = new CheckBox(MainActivity.this);
            mic.setText("Record microphone audio");
            mic.setPadding(36, 16, 36, 8);
            mic.setChecked(true);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Start whole-screen recording")
                    .setMessage("Choose whether to include microphone audio. Android will ask which screen/app to share next.")
                    .setView(mic)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue", (d, w) -> {
                        pendingMic = mic.isChecked();
                        if (pendingMic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                        else requestProjectionPermission();
                    }).show();
        });
    }

    private void requestProjectionPermission() {
        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try { startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAPTURE); }
        catch (Exception e) { Toast.makeText(this, "Screen capture is not available on this device", Toast.LENGTH_LONG).show(); }
    }

    private void startRecorder(int resultCode, Intent data) {
        Intent i = new Intent(this, RecorderService.class).setAction(RecorderService.ACTION_START)
                .putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(RecorderService.EXTRA_RESULT_DATA, data)
                .putExtra(RecorderService.EXTRA_MIC, pendingMic);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        if (Settings.canDrawOverlays(this)) startOverlayService();
        else new AlertDialog.Builder(this)
                .setTitle("Floating recording control")
                .setMessage("Allow Display over other apps to use the floating Pause/Stop control while recording. You can skip this and stop from the recording notification.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Allow", (d, w) -> startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), REQ_OVERLAY)).show();
    }

    private void startOverlayService() {
        if (!Settings.canDrawOverlays(this)) return;
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
    }

    public void openPhoneRecordings() { runOnUiThread(() -> startActivity(new Intent(MainActivity.this, RecordingsActivity.class))); }
    public void openVideoEditor() { runOnUiThread(() -> webView.loadUrl(EDITOR_URL)); }
    public void enableFloatingBall() {
        runOnUiThread(() -> {
            if (Settings.canDrawOverlays(MainActivity.this)) { startOverlayService(); Toast.makeText(MainActivity.this, "Floating ball enabled", Toast.LENGTH_SHORT).show(); }
            else startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) startRecorder(resultCode, data);
            else Toast.makeText(this, "Screen recording permission was cancelled", Toast.LENGTH_SHORT).show();
            return;
        }
        if (requestCode == REQ_OVERLAY) { if (Settings.canDrawOverlays(this)) startOverlayService(); return; }
        if (requestCode == REQ_FILE && fileChooserCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) pendingMic = false;
            requestProjectionPermission();
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onResume() { super.onResume(); if (webView != null) injectNativeFallback(); }

    public final class NativeBridge {
        @JavascriptInterface public void startRecording() { requestCapture(); }
        @JavascriptInterface public void openPhoneRecordings() { MainActivity.this.openPhoneRecordings(); }
        @JavascriptInterface public void openVideoEditor() { MainActivity.this.openVideoEditor(); }
        @JavascriptInterface public void enableFloatingBall() { MainActivity.this.enableFloatingBall(); }
    }
}
