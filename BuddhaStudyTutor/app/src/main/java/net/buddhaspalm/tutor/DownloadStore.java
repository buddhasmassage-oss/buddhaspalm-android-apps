package net.buddhaspalm.tutor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class DownloadStore {
    private static final String PREF = "buddhastudy_native_downloads";
    private static final String KEY = "items";
    private static final int MAX = 40;

    static final class Item {
        final String title, url;
        final long time, downloadId;
        Item(String title, String url, long time, long downloadId) {
            this.title = title; this.url = url; this.time = time; this.downloadId = downloadId;
        }
    }

    private DownloadStore() {}

    static synchronized void add(Context c, String title, String url, long id) {
        try {
            JSONArray old = readArray(c), out = new JSONArray();
            JSONObject n = new JSONObject();
            n.put("title", title == null ? "Course download" : title);
            n.put("url", url == null ? "" : url);
            n.put("time", System.currentTimeMillis());
            n.put("id", id);
            out.put(n);
            for (int i = 0; i < old.length() && out.length() < MAX; i++) out.put(old.optJSONObject(i));
            prefs(c).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    static synchronized List<Item> list(Context c) {
        List<Item> items = new ArrayList<>();
        JSONArray a = readArray(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            items.add(new Item(o.optString("title", "Course download"), o.optString("url", ""), o.optLong("time", 0), o.optLong("id", -1)));
        }
        return items;
    }

    static void clear(Context c) { prefs(c).edit().remove(KEY).apply(); }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    private static JSONArray readArray(Context c) {
        try { return new JSONArray(prefs(c).getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
}
