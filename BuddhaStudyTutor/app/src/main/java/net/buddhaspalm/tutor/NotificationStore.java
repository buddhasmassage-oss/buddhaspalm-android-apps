package net.buddhaspalm.tutor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class NotificationStore {
    private static final String PREF = "buddhastudy_native_inbox";
    private static final String KEY = "items";
    private static final int MAX = 40;

    static final class Item {
        final String title, body, url;
        final long time;
        Item(String title, String body, String url, long time) {
            this.title = title; this.body = body; this.url = url; this.time = time;
        }
    }

    private NotificationStore() {}

    static synchronized void add(Context c, String title, String body, String url) {
        try {
            JSONArray old = readArray(c);
            JSONArray out = new JSONArray();
            JSONObject newest = new JSONObject();
            newest.put("title", title == null ? "BuddhaStudy Tutor" : title);
            newest.put("body", body == null ? "" : body);
            newest.put("url", url == null ? "" : url);
            newest.put("time", System.currentTimeMillis());
            out.put(newest);
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
            items.add(new Item(o.optString("title", "BuddhaStudy Tutor"), o.optString("body", ""), o.optString("url", ""), o.optLong("time", 0)));
        }
        return items;
    }

    static int count(Context c) { return readArray(c).length(); }
    static void clear(Context c) { prefs(c).edit().remove(KEY).apply(); }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    private static JSONArray readArray(Context c) {
        try { return new JSONArray(prefs(c).getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
}
