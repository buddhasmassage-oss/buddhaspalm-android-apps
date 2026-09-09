package net.buddhaspinas.screenrecorder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingsActivity extends Activity {
    private static final int REQ_MEDIA = 5101;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private TextView subtitle;

    static final class RecordingItem {
        long id; Uri uri; String name; long size; long duration; long dateAdded; long dateModified;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(17, 24, 39));
        buildUi();
        ensurePermissionThenLoad();
    }

    private void buildUi() {
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true); scroller.setBackgroundColor(Color.rgb(248, 250, 252));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroller.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(text("PHONE RECORDINGS", 12, Color.rgb(161, 98, 7), Typeface.BOLD));
        TextView title = text("My Phone Recordings", 26, Color.rgb(17, 24, 39), Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2); tlp.topMargin = dp(3); root.addView(title, tlp);
        subtitle = text("MP4 recordings saved in Movies/Buddhas Screen Recorder", 14, Color.rgb(75, 85, 99), Typeface.NORMAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2); slp.topMargin = dp(6); slp.bottomMargin = dp(14); root.addView(subtitle, slp);

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.START);
        Button back = button("← Recorder", false); Button cloud = button("Cloud Library", true);
        actions.addView(back, weightParams()); LinearLayout.LayoutParams gap = weightParams(); gap.leftMargin = dp(8); actions.addView(cloud, gap); root.addView(actions);
        back.setOnClickListener(v -> finish());
        cloud.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_START_URL, MainActivity.BASE_URL + "#recordings")));

        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(14); root.addView(list, lp);
        setContentView(scroller);
    }

    private void ensurePermissionThenLoad() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQ_MEDIA); return;
            }
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA); return;
        }
        loadRecordings();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA) loadRecordings();
    }

    @Override protected void onResume() { super.onResume(); if (list != null) loadRecordings(); }

    private void loadRecordings() {
        executor.execute(() -> { List<RecordingItem> items = queryRecordings(); runOnUiThread(() -> render(items)); });
    }

    private List<RecordingItem> queryRecordings() {
        List<RecordingItem> out = new ArrayList<>();
        Uri base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String locationColumn = Build.VERSION.SDK_INT >= 29 ? MediaStore.Video.Media.RELATIVE_PATH : MediaStore.Video.Media.DATA;
        String[] projection = new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED, locationColumn};
        String selection = locationColumn + " LIKE ?";
        String[] args = new String[]{"%Buddhas Screen Recorder%"};
        try (Cursor c = getContentResolver().query(base, projection, selection, args, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c == null) return out;
            int idI = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID), nameI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME),
                    sizeI = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE), durationI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION),
                    dateI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED), modI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
            while (c.moveToNext()) {
                RecordingItem x = new RecordingItem(); x.id = c.getLong(idI); x.uri = ContentUris.withAppendedId(base, x.id);
                x.name = c.getString(nameI); x.size = c.getLong(sizeI); x.duration = c.getLong(durationI); x.dateAdded = c.getLong(dateI); x.dateModified = c.getLong(modI); out.add(x);
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Could not read phone recordings: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
        return out;
    }

    private void render(List<RecordingItem> items) {
        list.removeAllViews();
        subtitle.setText(items.isEmpty() ? "No phone recordings yet. Tap Start Recording on the main screen, then Stop & Save."
                : items.size() + (items.size() == 1 ? " MP4 saved on this phone" : " MP4s saved on this phone"));
        if (items.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No recordings yet", 18, Color.rgb(17, 24, 39), Typeface.BOLD));
            TextView p = text("Stop & Save creates an MP4 automatically. It will appear here and, when you are logged in, the app also syncs it to Cloud Library.", 14, Color.rgb(75, 85, 99), Typeface.NORMAL);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2); pp.topMargin = dp(6); empty.addView(p, pp); list.addView(empty, cardParams()); return;
        }

        for (RecordingItem item : items) {
            LinearLayout c = card();
            TextView name = text(item.name == null ? "Recording.mp4" : item.name, 16, Color.rgb(17, 24, 39), Typeface.BOLD); name.setSingleLine(true); c.addView(name);
            String meta = formatDuration(item.duration) + " • " + formatBytes(item.size);
            if (item.dateAdded > 0) meta += " • " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.dateAdded * 1000));
            TextView m = text(meta, 12, Color.rgb(107, 114, 128), Typeface.NORMAL);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2); mlp.topMargin = dp(4); mlp.bottomMargin = dp(10); c.addView(m, mlp);

            LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
            Button edit = button("Edit", true), play = button("Play", false);
            row1.addView(edit, weightParams()); LinearLayout.LayoutParams p2 = weightParams(); p2.leftMargin = dp(7); row1.addView(play, p2); c.addView(row1);
            LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
            Button sync = button("Sync Cloud", false), save = button("Save Copy", false), delete = button("Delete", false);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2); rlp.topMargin = dp(7);
            row2.addView(sync, weightParams()); LinearLayout.LayoutParams s2 = weightParams(); s2.leftMargin = dp(6); row2.addView(save, s2);
            LinearLayout.LayoutParams s3 = weightParams(); s3.leftMargin = dp(6); row2.addView(delete, s3); c.addView(row2, rlp);

            edit.setOnClickListener(v -> editExact(item)); play.setOnClickListener(v -> play(item)); sync.setOnClickListener(v -> syncCloud(item));
            save.setOnClickListener(v -> saveCopy(item)); delete.setOnClickListener(v -> confirmDelete(item)); list.addView(c, cardParams());
        }
    }

    private void editExact(RecordingItem item) {
        final ProgressDialog dialog = ProgressDialog.show(this, "Preparing exact recording", "Checking Cloud Library…", true, false);
        executor.execute(() -> {
            try {
                NativeCloudSync.SyncResult r = NativeCloudSync.sync(this, item.uri, item.name);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    if (r.synced && r.cloudId > 0) startActivity(new Intent(this, MainActivity.class)
                            .putExtra(MainActivity.EXTRA_START_URL, MainActivity.EDITOR_URL + "?id=" + r.cloudId)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    else openExactLocal(item, r.message);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.dismiss(); openExactLocal(item, "Cloud sync unavailable — opening the exact phone recording locally"); });
            }
        });
    }

    private void openExactLocal(RecordingItem item, String message) {
        Toast.makeText(this, message == null ? "Opening exact phone recording" : message, Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_START_URL, MainActivity.EDITOR_URL)
                .putExtra(MainActivity.EXTRA_EDIT_URI, item.uri.toString()).putExtra(MainActivity.EXTRA_EDIT_NAME, item.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void syncCloud(RecordingItem item) {
        final ProgressDialog dialog = ProgressDialog.show(this, "Cloud Library", "Uploading " + item.name + "…", true, false);
        executor.execute(() -> {
            try {
                NativeCloudSync.SyncResult r = NativeCloudSync.sync(this, item.uri, item.name);
                runOnUiThread(() -> {
                    dialog.dismiss(); Toast.makeText(this, r.message, Toast.LENGTH_LONG).show();
                    if (r.synced && r.cloudId > 0) startActivity(new Intent(this, MainActivity.class)
                            .putExtra(MainActivity.EXTRA_START_URL, MainActivity.BASE_URL + "#recordings")
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.dismiss(); Toast.makeText(this, "Cloud sync failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private void play(RecordingItem item) {
        try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Play recording")); }
        catch (Exception e) { Toast.makeText(this, "No video player found", Toast.LENGTH_SHORT).show(); }
    }

    private void saveCopy(RecordingItem item) {
        executor.execute(() -> {
            try {
                ContentResolver cr = getContentResolver(); ContentValues cv = new ContentValues(); cv.put(MediaStore.Video.Media.DISPLAY_NAME, item.name); cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                if (Build.VERSION.SDK_INT >= 29) { cv.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Buddhas Screen Recorder"); cv.put(MediaStore.Video.Media.IS_PENDING, 1); }
                Uri dst = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv); if (dst == null) throw new IllegalStateException("Cannot create download copy");
                try (InputStream in = cr.openInputStream(item.uri); OutputStream out = cr.openOutputStream(dst)) {
                    if (in == null || out == null) throw new IllegalStateException("Cannot copy recording"); byte[] b = new byte[64 * 1024]; int n;
                    while ((n = in.read(b)) >= 0) if (n > 0) out.write(b, 0, n);
                }
                if (Build.VERSION.SDK_INT >= 29) { ContentValues ready = new ContentValues(); ready.put(MediaStore.Video.Media.IS_PENDING, 0); cr.update(dst, ready, null, null); }
                runOnUiThread(() -> Toast.makeText(this, "Copied to Downloads/Buddhas Screen Recorder", Toast.LENGTH_LONG).show());
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Save copy failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        });
    }

    private void confirmDelete(RecordingItem item) {
        new AlertDialog.Builder(this).setTitle("Delete recording?").setMessage((item.name == null ? "This video" : item.name) + " will be deleted from this phone.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) -> {
                    try { int n = getContentResolver().delete(item.uri, null, null); Toast.makeText(this, n > 0 ? "Recording deleted" : "Delete failed", Toast.LENGTH_SHORT).show(); }
                    catch (Exception e) { Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    loadRecordings();
                }).show();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(16)); bg.setStroke(dp(1), Color.rgb(226, 232, 240)); c.setBackground(bg); return c;
    }
    private LinearLayout.LayoutParams cardParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(10); return p; }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(8), dp(4), dp(8), dp(4));
        b.setTextColor(primary ? Color.rgb(17, 24, 39) : Color.rgb(31, 41, 55)); GradientDrawable bg = new GradientDrawable(); bg.setColor(primary ? Color.rgb(212, 175, 55) : Color.rgb(248, 250, 252)); bg.setCornerRadius(dp(11)); bg.setStroke(dp(1), primary ? Color.rgb(202, 138, 4) : Color.rgb(203, 213, 225)); b.setBackground(bg); return b;
    }
    private LinearLayout.LayoutParams weightParams() { return new LinearLayout.LayoutParams(0, dp(44), 1f); }
    private TextView text(String value, int sp, int color, int style) { TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(Typeface.DEFAULT, style); return t; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static String formatDuration(long ms) { long total = Math.max(0, ms / 1000), m = total / 60, s = total % 60; return String.format(Locale.US, "%02d:%02d", m, s); }
    private static String formatBytes(long bytes) { if (bytes < 1024) return bytes + " B"; double v = bytes; String[] units = {"KB", "MB", "GB"}; int i = -1; do { v /= 1024.0; i++; } while (v >= 1024 && i < units.length - 1); return String.format(Locale.US, "%.1f %s", v, units[i]); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
