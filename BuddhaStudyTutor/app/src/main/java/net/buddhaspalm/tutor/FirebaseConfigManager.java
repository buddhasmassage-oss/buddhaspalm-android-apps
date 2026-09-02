package net.buddhaspalm.tutor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class FirebaseConfigManager {
    private static final String TAG = "TutorFirebaseConfig";
    private static final String PREF = "buddhastudy_firebase";
    private static final String URL_CONFIG = "https://tutor.buddhaspalm.net/api.php?action=fcm_public_config";

    private FirebaseConfigManager() {}

    public interface Callback { void onComplete(boolean ready, String message); }

    public static boolean initializeFromCache(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return initialize(context,
                p.getString("application_id", ""),
                p.getString("api_key", ""),
                p.getString("project_id", ""),
                p.getString("sender_id", ""));
    }

    public static void fetchAndInitialize(Context context, Callback callback) {
        new Thread(() -> {
            boolean ok = false;
            String message = "Firebase configuration unavailable";
            HttpURLConnection c = null;
            try {
                URL u = new URL(URL_CONFIG + "&_=" + System.currentTimeMillis());
                c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                if (code >= 200 && code < 300) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                        String line; while ((line = br.readLine()) != null) sb.append(line);
                    }
                    JSONObject j = new JSONObject(sb.toString());
                    if (!j.optBoolean("enabled", false)) {
                        message = "FCM is disabled in Tutor admin";
                    } else {
                        String appId = j.optString("application_id", "");
                        String apiKey = j.optString("api_key", "");
                        String projectId = j.optString("project_id", "");
                        String senderId = j.optString("sender_id", "");
                        if (!appId.isEmpty() && !apiKey.isEmpty() && !projectId.isEmpty() && !senderId.isEmpty()) {
                            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                                    .putString("application_id", appId)
                                    .putString("api_key", apiKey)
                                    .putString("project_id", projectId)
                                    .putString("sender_id", senderId)
                                    .apply();
                            ok = initialize(context, appId, apiKey, projectId, senderId);
                            message = ok ? "Firebase ready" : "Firebase initialization failed";
                        } else {
                            message = "Import google-services.json in Tutor admin first";
                        }
                    }
                } else {
                    message = "Firebase config HTTP " + code;
                }
            } catch (Exception e) {
                message = e.getMessage() == null ? "Firebase config error" : e.getMessage();
                Log.w(TAG, "fetch config", e);
            } finally {
                if (c != null) c.disconnect();
            }
            final boolean ready = ok;
            final String status = message;
            new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(ready, status));
        }).start();
    }

    public static synchronized boolean initialize(Context context, String appId, String apiKey, String projectId, String senderId) {
        try {
            try { FirebaseApp.getInstance(); return true; } catch (IllegalStateException ignored) {}
            if (appId == null || appId.isEmpty() || apiKey == null || apiKey.isEmpty() || projectId == null || projectId.isEmpty() || senderId == null || senderId.isEmpty()) return false;
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setGcmSenderId(senderId)
                    .build();
            return FirebaseApp.initializeApp(context, options) != null;
        } catch (Exception e) {
            Log.e(TAG, "initialize", e);
            return false;
        }
    }
}
