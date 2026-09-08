package net.buddhaspinas.screenrecorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RecordingsActivity extends Activity {
    private LinearLayout listWrap;
    private TextView summary;

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
        root.setBackgroundColor(Color.rgb(246, 242, 234));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(12), dp(14), dp(12));
        top.setBackgroundColor(Color.rgb(91, 11, 18));

        Button back = button("‹ Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(78), dp(42)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("My Phone Recordings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, 1);
        summary = new TextView(this);
        summary.setTextColor(Color.rgb(231, 182, 83));
        summary.setTextSize(11);
        heading.addView(title);
        heading.addView(summary);
        top.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button refresh = button("Refresh");
        refresh.setOnClickListener(v -> loadRecordings());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(88), dp(42)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        listWrap = new LinearLayout(this);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        listWrap.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(listWrap, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void loadRecordings() {
        listWrap.removeAllViews();
        List<RecordingItem> rows = queryRecordings();
        summary.setText(rows.size() + (rows.size() == 1 ? " MP4 recording" : " MP4 recordings") + " • Movies/Buddhas Screen Recorder");
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No local recordings found yet.\n\nTap Start Recording, approve Android whole-screen capture, then use the floating Buddha ball and choose Stop & Save. The MP4 will appear here.");
            empty.setTextColor(Color.rgb(95, 95, 95));
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(22), dp(70), dp(22), dp(30));
            listWrap.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (RecordingItem item : rows) listWrap.addView(card(item));
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
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(10);
        card.setLayoutParams(cp);

        TextView name = new TextView(this);
        name.setText("🎬 " + item.name);
        name.setTextColor(Color.rgb(45, 35, 38));
        name.setTextSize(14);
        name.setTypeface(null, 1);
        card.addView(name);

        TextView meta = new TextView(this);
        String date = item.dateAdded > 0 ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.dateAdded * 1000L)) : "Unknown date";
        meta.setText(formatBytes(item.size) + " • " + formatDuration(item.duration) + " • " + date + "\nSaved on this phone as MP4");
        meta.setTextColor(Color.rgb(105, 95, 97));
        meta.setTextSize(11);
        meta.setPadding(0, dp(6), 0, dp(10));
        card.addView(meta);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button play = button("▶ Play");
        play.setOnClickListener(v -> play(item));
        actions.addView(play, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button share = button("Share");
        share.setOnClickListener(v -> share(item));
        actions.addView(share, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button delete = button("Delete");
        delete.setOnClickListener(v -> confirmDelete(item));
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(42), 1));
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

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11);
        return b;
    }

    private String formatBytes(long n) {
        if (n < 1024) return n + " B";
        double v = n;
        String[] u = {"B", "KB", "MB", "GB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024.0; i++; }
        return String.format(java.util.Locale.US, "%.1f %s", v, u[i]);
    }

    private String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
