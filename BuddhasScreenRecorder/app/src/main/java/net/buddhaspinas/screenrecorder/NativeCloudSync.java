package net.buddhaspinas.screenrecorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class NativeCloudSync {
    static final class SyncResult {
        final boolean synced;
        final long cloudId;
        final String message;
        SyncResult(boolean synced, long cloudId, String message) {
            this.synced = synced;
            this.cloudId = cloudId;
            this.message = message;
        }
    }

    private static final String BASE = MainActivity.BASE_URL;
    private static final String UA = "BuddhasScreenRecorder/1.4.0 Android";
    private static final String PREFS = "native_cloud_sync";

    private NativeCloudSync() {}

    static SyncResult sync(Context context, Uri uri, String name) throws Exception {
        if (uri == null) throw new IllegalArgumentException("Recording URI missing");
        if (name == null || name.trim().isEmpty()) name = queryName(context, uri);
        if (name == null || name.trim().isEmpty()) name = "Buddhas_Screen_Recording.mp4";

        String cookie = CookieManager.getInstance().getCookie(BASE);
        JSONObject status = getJson(BASE + "api/status.php", cookie);
        if (!status.optBoolean("logged_in", false)) {
            return new SyncResult(false, 0, "Log in to Cloud Library first");
        }
        String csrf = status.optString("csrf", "");
        if (csrf.isEmpty()) throw new IllegalStateException("Cloud session token unavailable");

        long size = querySize(context, uri);
        String key = mappingKey(uri, size);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long cached = prefs.getLong(key, 0);
        if (cached > 0 && cloudIdExists(cookie, cached)) {
            return new SyncResult(true, cached, "Already synced");
        }

        String boundary = "----BuddhasNative" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + "api/save.php").openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(180_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setUseCaches(false);
        c.setChunkedStreamingMode(64 * 1024);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);

        try (OutputStream raw = new BufferedOutputStream(c.getOutputStream())) {
            writeField(raw, boundary, "csrf", csrf);
            raw.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            raw.write(("Content-Disposition: form-data; name=\"recording\"; filename=\"" + safeFilename(name) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            raw.write("Content-Type: video/mp4\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            try (InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(uri))) {
                if (in == null) throw new IllegalStateException("Could not open phone recording");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n > 0) raw.write(buf, 0, n);
                }
            }
            raw.write("\r\n".getBytes(StandardCharsets.UTF_8));
            raw.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            raw.flush();
        }

        int code = c.getResponseCode();
        String text = readText(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (text == null || text.trim().isEmpty()) throw new IllegalStateException("Cloud server returned no response");
        JSONObject out = new JSONObject(text);
        if (code >= 400 || !out.optBoolean("ok", false)) {
            String error = out.optString("error", "Cloud upload failed");
            throw new IllegalStateException(error);
        }
        if (!out.optBoolean("cloud", false)) {
            return new SyncResult(false, 0, "Log in to save this recording to your Cloud Library");
        }
        long id = out.optLong("id", 0);
        if (id < 1) throw new IllegalStateException("Cloud recording ID missing");
        prefs.edit().putLong(key, id).apply();
        return new SyncResult(true, id, "Saved to Cloud Library");
    }

    private static boolean cloudIdExists(String cookie, long id) {
        try {
            JSONObject j = getJson(BASE + "api/recordings.php", cookie);
            JSONArray a = j.optJSONArray("recordings");
            if (a == null) return false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject row = a.optJSONObject(i);
                if (row != null && row.optLong("id", 0) == id) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static JSONObject getJson(String url, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestMethod("GET");
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", UA);
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        int code = c.getResponseCode();
        String text = readText(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new IllegalStateException("Cloud request failed (" + code + ")");
        return new JSONObject(text == null ? "{}" : text);
    }

    private static void writeField(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String readText(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) if (n > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static String safeFilename(String s) {
        return s.replace("\\", "_").replace("/", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    static String queryName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) { }
        return null;
    }

    static long querySize(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0) return c.getLong(i);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    private static String mappingKey(Uri uri, long size) {
        return "cloud:" + uri.toString() + ":" + size;
    }
}
