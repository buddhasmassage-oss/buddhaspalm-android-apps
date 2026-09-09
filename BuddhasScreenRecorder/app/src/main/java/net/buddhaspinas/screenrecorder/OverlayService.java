package net.buddhaspinas.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class OverlayService extends Service {
    private static final String TAG = "BuddhasOverlay";
    private static final String CHANNEL_ID = "buddhas_floating_controls";
    private static final int NOTIFICATION_ID = 1310;

    private WindowManager wm;
    private LinearLayout overlay;
    private LinearLayout controls;
    private WindowManager.LayoutParams params;
    private float downX, downY;
    private int startX, startY;
    private boolean moved;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        createChannel();
        if (!enterForegroundSafely()) {
            stopSelf();
            return;
        }
        ensureOverlayVisible();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureOverlayVisible();
        return START_STICKY;
    }

    private boolean enterForegroundSafely() {
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Unable to enter foreground mode", t);
            Toast.makeText(this,
                    "Floating ball could not start on this Android version.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void ensureOverlayVisible() {
        if (overlay != null) return;
        try {
            createOverlay();
        } catch (Throwable t) {
            Log.e(TAG, "Unable to create floating overlay", t);
            Toast.makeText(this,
                    "Floating ball could not be displayed. Check Display over other apps permission.",
                    Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void createOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) throw new IllegalStateException("WindowManager unavailable");

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.HORIZONTAL);
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        overlay.setPadding(dp(4), dp(4), dp(4), dp(4));

        ImageView ball = new ImageView(this);
        ball.setImageResource(R.drawable.app_logo);
        ball.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ball.setContentDescription("Buddhas floating recorder");

        GradientDrawable ballBg = new GradientDrawable();
        ballBg.setColor(Color.argb(245, 255, 255, 255));
        ballBg.setShape(GradientDrawable.OVAL);
        ballBg.setStroke(dp(2), Color.rgb(212, 175, 55));
        ball.setBackground(ballBg);
        ball.setClipToOutline(true);
        ball.setPadding(dp(2), dp(2), dp(2), dp(2));
        overlay.addView(ball, new LinearLayout.LayoutParams(dp(64), dp(64)));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setVisibility(View.GONE);
        controls.setPadding(dp(7), dp(7), dp(7), dp(7));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.argb(248, 17, 24, 39));
        panelBg.setStroke(dp(1), Color.rgb(75, 85, 99));
        panelBg.setCornerRadius(dp(14));
        controls.setBackground(panelBg);
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                dp(200), LinearLayout.LayoutParams.WRAP_CONTENT);
        panelParams.leftMargin = dp(6);
        overlay.addView(controls, panelParams);

        Button start = button("Start Whole Screen");
        Button pause = button("Pause");
        Button resume = button("Resume");
        Button stop = button("Stop & Save MP4");
        Button files = button("Phone Files");
        Button editor = button("Video Editor");
        Button browser = button("Open Browser");
        Button recorder = button("Open Recorder");
        Button hide = button("Hide Floating Ball");

        controls.addView(start);
        controls.addView(pause);
        controls.addView(resume);
        controls.addView(stop);
        controls.addView(files);
        controls.addView(editor);
        controls.addView(browser);
        controls.addView(recorder);
        controls.addView(hide);

        start.setOnClickListener(v -> requestNewCapture());
        pause.setOnClickListener(v -> sendRecorderAction(RecorderService.ACTION_PAUSE));
        resume.setOnClickListener(v -> sendRecorderAction(RecorderService.ACTION_RESUME));
        stop.setOnClickListener(v -> sendRecorderAction(RecorderService.ACTION_STOP));
        files.setOnClickListener(v -> openFiles());
        editor.setOnClickListener(v -> openEditor());
        browser.setOnClickListener(v -> openBrowser());
        recorder.setOnClickListener(v -> openRecorder());
        hide.setOnClickListener(v -> stopSelf());

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(160);

        ball.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downX);
                    int dy = Math.round(event.getRawY() - downY);
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                    params.x = Math.max(0, startX + dx);
                    params.y = Math.max(0, startY + dy);
                    try {
                        wm.updateViewLayout(overlay, params);
                    } catch (Exception e) {
                        Log.w(TAG, "Overlay move ignored", e);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) toggleMenu();
                    return true;
                default:
                    return false;
            }
        });

        wm.addView(overlay, params);
    }

    private void toggleMenu() {
        if (controls == null) return;
        controls.setVisibility(controls.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE);
        try {
            if (wm != null && overlay != null && params != null) {
                wm.updateViewLayout(overlay, params);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resize floating menu", e);
        }
    }

    private void closeMenu() {
        if (controls != null) controls.setVisibility(View.GONE);
    }

    private void requestNewCapture() {
        if (RecorderService.isRecording) {
            Toast.makeText(this, "A recording is already running", Toast.LENGTH_SHORT).show();
            return;
        }
        closeMenu();
        try {
            Intent i = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_REQUEST_CAPTURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open recording permission screen", e);
            Toast.makeText(this, "Could not open recorder", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendRecorderAction(String action) {
        if (!RecorderService.isRecording) {
            Toast.makeText(this, "Start recording first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (RecorderService.ACTION_PAUSE.equals(action) && RecorderService.isPaused) {
            Toast.makeText(this, "Recording is already paused", Toast.LENGTH_SHORT).show();
            return;
        }
        if (RecorderService.ACTION_RESUME.equals(action) && !RecorderService.isPaused) {
            Toast.makeText(this, "Recording is already running", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startService(new Intent(this, RecorderService.class).setAction(action));
            if (RecorderService.ACTION_STOP.equals(action) ||
                    RecorderService.ACTION_RESUME.equals(action)) {
                closeMenu();
            }
        } catch (Exception e) {
            Log.e(TAG, "Recorder action failed: " + action, e);
            Toast.makeText(this, "Recording control failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openFiles() {
        closeMenu();
        try {
            startActivity(new Intent(this, RecordingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.e(TAG, "Unable to open recordings", e);
            Toast.makeText(this, "Could not open phone recordings", Toast.LENGTH_SHORT).show();
        }
    }

    private void openEditor() {
        closeMenu();
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_OPEN_EDITOR)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception e) {
            Log.e(TAG, "Unable to open editor", e);
            Toast.makeText(this, "Could not open editor", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBrowser() {
        closeMenu();
        try {
            Intent browser = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
            browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browser);
        } catch (Exception first) {
            try {
                Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(web);
            } catch (Exception second) {
                Log.e(TAG, "Unable to open browser", second);
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openRecorder() {
        closeMenu();
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception e) {
            Log.e(TAG, "Unable to open main recorder", e);
            Toast.makeText(this, "Could not open recorder", Toast.LENGTH_SHORT).show();
        }
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(11), 0, dp(8), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(31, 41, 55));
        bg.setStroke(dp(1), Color.rgb(75, 85, 99));
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36));
        lp.topMargin = dp(3);
        b.setLayoutParams(lp);
        return b;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_stat_recorder)
                .setContentTitle("Buddhas floating recorder")
                .setContentText("Floating recording controls are active")
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating recorder",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps the draggable screen-recording controls active over other apps.");
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            if (wm != null && overlay != null) wm.removeView(overlay);
        } catch (Exception e) {
            Log.w(TAG, "Overlay already removed", e);
        }
        overlay = null;
        controls = null;
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
