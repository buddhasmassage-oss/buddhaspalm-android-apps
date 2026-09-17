package net.buddhaspalm.tutor;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
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

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends FragmentActivity {
    private static final String HOME = "https://tutor.buddhaspalm.net/";
    private static final int REQ_NOTIFY = 6101;
    private static final int REQ_MEDIA = 6102;
    private static final int REQ_FILES = 6103;

    private static final String PREFS = "buddhastudy_native";
    private static final String BIO_KEY_ALIAS = "buddhastudy_tutor_biometric_v1";
    private static final String BIO_CT = "bio_ciphertext";
    private static final String BIO_IV = "bio_iv";
    private static final String BIO_CREDENTIAL_ID = "bio_credential_id";

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
        if (FirebaseConfigManager.initializeFromCache(this)) obtainFcmToken();
        FirebaseConfigManager.fetchAndInitialize(this, (ready, message) -> { if (ready) obtainFcmToken(); });
        webView.loadUrl(safeTutorUrl(getIntent().getStringExtra("click_url")));
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progress = new ProgressBar(this);

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setDefaultFontSize(16);
        s.setDefaultFixedFontSize(13);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setInitialScale(0);

        s.setUserAgentString(s.getUserAgentString() + " BuddhaStudyTutorAndroid/" + getAppVersion());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);
        webView.addJavascriptInterface(new NativeBridge(), "BuddhaTutorNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return handleUrl(request.getUrl().toString()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return handleUrl(url); }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (view.getScale() > 1.20f || view.getScale() < 0.80f) view.setInitialScale(0);
                injectToken();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) { progress.setVisibility(newProgress >= 95 ? View.GONE : View.VISIBLE); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try { startActivityForResult(params.createIntent(), REQ_FILES); }
                catch (ActivityNotFoundException e) { fileCallback = null; Toast.makeText(MainActivity.this, "No file picker available", Toast.LENGTH_SHORT).show(); }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) { runOnUiThread(() -> requestWebMediaPermission(request)); }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(this, "Unable to open download", Toast.LENGTH_SHORT).show(); }
        });
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("https://tutor.buddhaspalm.net/") || url.startsWith("https://www.tutor.buddhaspalm.net/")) return false;
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("sms:")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    private void requestWebMediaPermission(PermissionRequest request) {
        List<String> needed = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.CAMERA);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (needed.isEmpty()) request.grant(request.getResources());
        else { pendingWebPermission = request; requestPermissions(needed.toArray(new String[0]), REQ_MEDIA); }
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
                prefs().edit().putString("fcm_token", task.getResult()).apply();
                injectToken();
            });
        } catch (Exception ignored) {}
    }

    private void injectToken() {
        if (webView == null) return;
        String token = prefs().getString("fcm_token", "");
        if (token == null || token.length() < 30) return;
        String device = Build.MANUFACTURER + " " + Build.MODEL;
        String version = getAppVersion();
        String js = "if(window.bspNativeFcmReady){window.bspNativeFcmReady(" + jsQuote(token) + "," + jsQuote(device) + "," + jsQuote(version) + ");}";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private String getAppVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "1.0.6"; }
    }

    private int biometricStatus() {
        try {
            int can = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
            if (can != BiometricManager.BIOMETRIC_SUCCESS) return 0;
            String ct = prefs().getString(BIO_CT, "");
            String iv = prefs().getString(BIO_IV, "");
            String cid = prefs().getString(BIO_CREDENTIAL_ID, "");
            return (!ct.isEmpty() && !iv.isEmpty() && !cid.isEmpty()) ? 2 : 1;
        } catch (Exception e) { return 0; }
    }

    private SecretKey createBiometricKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(BIO_KEY_ALIAS)) ks.deleteEntry(BIO_KEY_ALIAS);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                BIO_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            b.setUserAuthenticationValidityDurationSeconds(-1);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) b.setInvalidatedByBiometricEnrollment(true);
        kg.init(b.build()); return kg.generateKey();
    }

    private SecretKey getBiometricKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        java.security.Key k = ks.getKey(BIO_KEY_ALIAS, null);
        if (!(k instanceof SecretKey)) throw new IllegalStateException("Fingerprint key is unavailable");
        return (SecretKey) k;
    }

    private BiometricPrompt.PromptInfo promptInfo(String title, String subtitle) {
        return new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use email & password")
                .setConfirmationRequired(false)
                .build();
    }

    private void registerBiometricInternal(String token, String credentialId) {
        if (token == null || token.length() < 20 || credentialId == null || credentialId.isEmpty()) {
            jsBiometricRegistration(false, credentialId, "Registration token is missing."); return;
        }
        if (biometricStatus() == 0) {
            jsBiometricRegistration(false, credentialId, "Strong fingerprint/biometric authentication is not available or not enrolled on this device."); return;
        }
        try {
            SecretKey key = createBiometricKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key);
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString); jsBiometricRegistration(false, credentialId, errString.toString());
                }
                @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    try {
                        Cipher c = result.getCryptoObject() != null ? result.getCryptoObject().getCipher() : null;
                        if (c == null) throw new IllegalStateException("Fingerprint crypto session is unavailable");
                        byte[] ct = c.doFinal(token.getBytes(StandardCharsets.UTF_8));
                        byte[] iv = c.getIV();
                        prefs().edit()
                                .putString(BIO_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
                                .putString(BIO_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                                .putString(BIO_CREDENTIAL_ID, credentialId)
                                .apply();
                        jsBiometricRegistration(true, credentialId, "");
                    } catch (Exception e) { clearBiometricInternal(); jsBiometricRegistration(false, credentialId, "Could not protect the fingerprint login token."); }
                }
            });
            prompt.authenticate(promptInfo("Enable Fingerprint Login", "Touch your fingerprint sensor to protect this Tutor login."), new BiometricPrompt.CryptoObject(cipher));
        } catch (Exception e) { clearBiometricInternal(); jsBiometricRegistration(false, credentialId, "Could not start fingerprint setup."); }
    }

    private void authenticateBiometricInternal() {
        if (biometricStatus() != 2) { jsBiometricLogin(false, "", "No fingerprint login is registered on this app."); return; }
        try {
            String ct64 = prefs().getString(BIO_CT, "");
            String iv64 = prefs().getString(BIO_IV, "");
            byte[] ct = Base64.decode(ct64, Base64.NO_WRAP); byte[] iv = Base64.decode(iv64, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getBiometricKey(), new GCMParameterSpec(128, iv));
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString); jsBiometricLogin(false, "", errString.toString());
                }
                @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    try {
                        Cipher c = result.getCryptoObject() != null ? result.getCryptoObject().getCipher() : null;
                        if (c == null) throw new IllegalStateException("Fingerprint crypto session is unavailable");
                        String token = new String(c.doFinal(ct), StandardCharsets.UTF_8);
                        jsBiometricLogin(true, token, "");
                    } catch (Exception e) {
                        clearBiometricInternal();
                        jsBiometricLogin(false, "", "Fingerprint credential changed or expired. Sign in with your password and enable it again.");
                    }
                }
            });
            prompt.authenticate(promptInfo("Sign in with Fingerprint", "Touch your fingerprint sensor to open BuddhaStudy Tutor."), new BiometricPrompt.CryptoObject(cipher));
        } catch (Exception e) {
            clearBiometricInternal(); jsBiometricLogin(false, "", "Fingerprint credential is no longer valid. Use email and password, then enable it again.");
        }
    }

    private void clearBiometricInternal() {
        prefs().edit().remove(BIO_CT).remove(BIO_IV).remove(BIO_CREDENTIAL_ID).apply();
        try { KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); if (ks.containsAlias(BIO_KEY_ALIAS)) ks.deleteEntry(BIO_KEY_ALIAS); } catch (Exception ignored) {}
    }

    private void jsBiometricRegistration(boolean success, String credentialId, String error) {
        try { JSONObject o=new JSONObject();o.put("success",success);o.put("credential_id",credentialId==null?"":credentialId);if(!success)o.put("error",error==null?"Fingerprint registration failed.":error);callJs("bspNativeBiometricRegistrationResult",o); } catch(Exception ignored){}
    }
    private void jsBiometricLogin(boolean success, String token, String error) {
        try { JSONObject o=new JSONObject();o.put("success",success);if(success)o.put("token",token==null?"":token);else o.put("error",error==null?"Fingerprint authentication failed.":error);callJs("bspNativeBiometricResult",o); } catch(Exception ignored){}
    }
    private void callJs(String fn, JSONObject payload) {
        if (webView == null || fn == null) return;
        String js = "if(window."+fn+"){window."+fn+"("+payload.toString()+");}";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private static String jsQuote(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private String safeTutorUrl(String url) {
        if (url != null && url.startsWith("https://tutor.buddhaspalm.net/")) return url;
        return HOME;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (webView != null) webView.loadUrl(safeTutorUrl(intent.getStringExtra("click_url")));
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILES && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA && pendingWebPermission != null) {
            boolean all = true; for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) all = false;
            if (all) pendingWebPermission.grant(pendingWebPermission.getResources()); else pendingWebPermission.deny(); pendingWebPermission = null;
        }
    }

    public class NativeBridge {
        @JavascriptInterface public String getFcmToken() { return prefs().getString("fcm_token", ""); }
        @JavascriptInterface public String getDeviceName() { return Build.MANUFACTURER + " " + Build.MODEL; }
        @JavascriptInterface public String getAppVersion() { return MainActivity.this.getAppVersion(); }
        @JavascriptInterface public int getBiometricStatus() { return biometricStatus(); }
        @JavascriptInterface public void registerBiometric(String token, String credentialId) { runOnUiThread(() -> registerBiometricInternal(token, credentialId)); }
        @JavascriptInterface public void authenticateBiometric() { runOnUiThread(MainActivity.this::authenticateBiometricInternal); }
        @JavascriptInterface public void clearBiometricCredential() { runOnUiThread(MainActivity.this::clearBiometricInternal); }
        @JavascriptInterface public void openNotificationSettings() {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
                catch (Exception ignored) {}
            });
        }
    }
}
