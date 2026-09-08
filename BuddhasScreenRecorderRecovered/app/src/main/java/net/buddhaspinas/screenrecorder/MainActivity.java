package net.buddhaspinas.screenrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 9001;
    private static final int REQ_AUDIO = 9002;
    private WebView webView;
    private boolean wantMic = true;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " BuddhasScreenRecorder/1.4.0");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBridge(), "BuddhasNative");
        webView.loadUrl("https://screenrecord.buddhaspinas.com/");

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9003);
        }
        if (getIntent() != null && "ACTION_REQUEST_CAPTURE".equals(getIntent().getAction())) requestCapture(true);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if ("ACTION_REQUEST_CAPTURE".equals(intent.getAction())) requestCapture(true);
    }

    private void requestCapture(boolean mic) {
        wantMic = mic;
        if (mic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) requestCapture(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent i = new Intent(this, RecorderService.class);
            i.setAction(RecorderService.ACTION_START);
            i.putExtra("resultCode", resultCode);
            i.putExtra("resultData", data);
            i.putExtra("mic", wantMic);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            if (Settings.canDrawOverlays(this)) startService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public class NativeBridge {
        @JavascriptInterface public void startRecording() { runOnUiThread(() -> requestCapture(true)); }
        @JavascriptInterface public void startRecordingNoMic() { runOnUiThread(() -> requestCapture(false)); }
        @JavascriptInterface public void stopRecording() {
            Intent i = new Intent(MainActivity.this, RecorderService.class);
            i.setAction(RecorderService.ACTION_STOP); startService(i);
        }
        @JavascriptInterface public void showFloatingBall() {
            runOnUiThread(() -> {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } else startService(new Intent(MainActivity.this, OverlayService.class));
            });
        }
        @JavascriptInterface public void openRecordings() { startActivity(new Intent(MainActivity.this, RecordingsActivity.class)); }
    }
}
