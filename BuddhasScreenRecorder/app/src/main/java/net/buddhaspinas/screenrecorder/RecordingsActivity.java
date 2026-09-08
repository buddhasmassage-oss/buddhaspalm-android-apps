package net.buddhaspinas.screenrecorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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

public class RecordingsActivity extends Activity {
    private LinearLayout listWrap;
    private TextView summary;
    private List<RecordingItem> currentRows = new ArrayList<>();

    private static class RecordingItem {
        long id;
        String name;
        long size;
        long dateAdded;
        long duration;
        Uri uri;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadRecordings();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 246, 241));
        applySafeInsets(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(9), dp(7), dp(9), dp(7));
        top.setBackgroundColor(Color.rgb(104, 21, 35));

        Button back = compactButton("‹ Back", false);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(66), dp(36)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(8), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("My Phone Recordings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, 1);
        summary = new TextView(this);
        summary.setTextColor(Color.rgb(231, 182, 83));
        summary.setTextSize(9);
        heading.addView(title);
        heading.addView(summary);
        top.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button refresh = compactButton("↻", false);
        refresh.setOnClickListener(v -> loadRecordings());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(36)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(dp(9), dp(7), dp(9), dp(7));
        tools.setBackgroundColor(Color.WHITE);
        Button downloadAll = compactButton("⬇ Download All", true);
        downloadAll.setOnClickListener(v -> downloadAll());
        tools.addView(downloadAll, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button editor = compactButton("✂ Video Editor", true);
        editor.setOnClickListener(v -> openEditor(null));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(38), 1);
        ep.leftMargin = dp(6);
        tools.addView(editor, ep);
        root.addView(tools, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        listWrap = new LinearLayout(this);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        listWrap.setPadding(dp(10), dp(10), dp(10), dp(20));
        scroll.addView(listWrap, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
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

    private void loadRecordings() {
        listWrap.removeAllViews();
        currentRows = queryRecordings();
        summary.setText(currentRows.size() + (currentRows.size() == 1 ? " MP4" : " MP4s") + " • Movies/Buddhas Screen Recorder");
        if (currentRows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No local recordings found yet.\n\nTap Record, approve Android whole-screen capture, then tap the floating Buddha ball → Stop & Save. The MP4 is saved automatically and will appear here.");
            empty.setTextColor(Color.rgb(95, 95, 95));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(60), dp(20), dp(30));
            listWrap.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (RecordingItem item : currentRows) listWrap.addView(card(item));
    }

    private List<RecordingItem> queryRecordings() {
        List<RecordingItem> out = new ArrayList<>();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        ArrayList<String> cols = new ArrayList<>();
        cols.add(MediaStore.Video.Media._ID);
        cols.add(MediaStore.Video.Media.DISPLAY_NAME);
        cols.add(MediaStore.Video.Media.SIZE);
        cols.add(MediaStore.Video.Media.DATE_ADDED);
        cols.add(MediaStore.Video.Media.DURATION);
        String selection;
        String[] args;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cols.add(MediaStore.Video.Media.RELATIVE_PATH);
            selection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ? AND " + MediaStore.Video.Media.DISPLAY_NAME + " LIKE ?";
            args = new String[]{"%Buddhas Screen Recorder%", "Buddhas_Screen_%"};
        } else {
            cols.add(MediaStore.Video.Media.DATA);
            selection = MediaStore.Video.Media.DATA + " LIKE ? AND " + MediaStore.Video.Media.DISPLAY_NAME + " LIKE ?";
            args = new String[]{"%BuddhasScreenRecorder%", "Buddhas_Screen_%"};
        }
        try (Cursor c = getContentResolver().query(collection, cols.toArray(new String[0]), selection, args, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c == null) return out;
            int idIx = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameIx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int sizeIx = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int dateIx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
            int durationIx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            while (c.moveToNext()) {
                RecordingItem x = new RecordingItem();
                x.id = c.getLong(idIx);
                x.name = c.getString(nameIx);
                x.size = c.getLong(sizeIx);
                x.dateAdded = c.getLong(dateIx);
                x.duration = c.getLong(durationIx);
                x.uri = ContentUris.withAppendedId(collection, x.id);
                out.add(x);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not read phone recordings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return out;
    }

    private LinearLayout card(RecordingItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawableFactory.applyCard(card, dp(16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(9);
        card.setLayoutParams(cp);

        TextView name = new TextView(this);
        name.setText("🎬 " + item.name);
        name.setTextColor(Color.rgb(45, 35, 38));
        name.setTextSize(13);
        name.setTypeface(null, 1);
        name.setSingleLine(true);
        card.addView(name);

        TextView meta = new TextView(this);
        String date = item.dateAdded > 0 ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.dateAdded * 1000L)) : "Unknown date";
        meta.setText(formatBytes(item.size) + " • " + formatDuration(item.duration) + " • " + date + "\nMP4 • already saved on this phone");
        meta.setTextColor(Color.rgb(105, 95, 97));
        meta.setTextSize(10);
        meta.setPadding(0, dp(5), 0, dp(8));
        card.addView(meta);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"▶ Play", "✂ Edit", "Share", "Delete"};
        View.OnClickListener[] ls = {
                v -> play(item), v -> openEditor(item), v -> share(item), v -> confirmDelete(item)
        };
        for (int i = 0; i < labels.length; i++) {
            Button b = compactButton(labels[i], true);
            b.setOnClickListener(ls[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(4);
            actions.addView(b, lp);
        }
        card.addView(actions);
        return card;
    }

    private void play(RecordingItem item) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(item.uri, "video/mp4");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No video player could open this recording.", Toast.LENGTH_LONG).show();
        }
    }

    private void openEditor(RecordingItem item) {
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(MainActivity.ACTION_OPEN_EDITOR);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (item != null) i.putExtra("editor_hint", item.name);
        startActivity(i);
        if (item != null) Toast.makeText(this, "Editor opened. Tap Add video and choose " + item.name, Toast.LENGTH_LONG).show();
    }

    private void share(RecordingItem item) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("video/mp4");
            i.putExtra(Intent.EXTRA_STREAM, item.uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share recording"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share this recording.", Toast.LENGTH_LONG).show();
        }
    }

    private void downloadAll() {
        if (currentRows.isEmpty()) {
            Toast.makeText(this, "No recordings to download.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "These MP4s are already downloaded in Movies/Buddhas Screen Recorder on this Android version.", Toast.LENGTH_LONG).show();
            return;
        }
        int copied = 0;
        for (RecordingItem item : currentRows) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, item.name);
                values.put(MediaStore.Downloads.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Buddhas Screen Recorder");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri dest = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (dest == null) continue;
                try (InputStream in = getContentResolver().openInputStream(item.uri); OutputStream out = getContentResolver().openOutputStream(dest)) {
                    if (in == null || out == null) throw new IllegalStateException("Unable to open file");
                    byte[] buffer = new byte[256 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    out.flush();
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(dest, values, null, null);
                copied++;
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, copied + " video" + (copied == 1 ? "" : "s") + " copied to Downloads/Buddhas Screen Recorder.", Toast.LENGTH_LONG).show();
    }

    private void confirmDelete(RecordingItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete recording?")
                .setMessage(item.name + " will be deleted from this phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> delete(item))
                .show();
    }

    private void delete(RecordingItem item) {
        try {
            int count = getContentResolver().delete(item.uri, null, null);
            Toast.makeText(this, count > 0 ? "Recording deleted." : "Recording could not be deleted.", Toast.LENGTH_LONG).show();
            loadRecordings();
        } catch (Exception e) {
            Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Button compactButton(String text, boolean wine) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(9);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(wine ? Color.rgb(104, 21, 35) : Color.argb(35, 255, 255, 255));
        b.setTextColor(Color.WHITE);
        b.setBackground(bg);
        return b;
    }

    private String formatBytes(long n) {
        if (n < 1024) return n + " B";
        double v = n;
        String[] u = {"B", "KB", "MB", "GB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024.0; i++; }
        return String.format(Locale.US, "%.1f %s", v, u[i]);
    }

    private String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static class GradientDrawableFactory {
        static void applyCard(View v, float radius) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(radius);
            bg.setStroke(1, Color.rgb(234, 223, 216));
            v.setBackground(bg);
        }
    }
}
