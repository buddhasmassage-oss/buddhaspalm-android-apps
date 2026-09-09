package net.buddhaspinas.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class OverlayService extends Service {
    private static final String CHANNEL_ID = "buddhas_floating_controls";
    private static final int NOTIFICATION_ID = 1310;
    private WindowManager wm;
    private LinearLayout overlay;
    private LinearLayout controls;
    private WindowManager.LayoutParams params;
    private float downX, downY;
    private int startX, startY;
    private boolean moved;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        createOverlay();
    }

    private void createOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.setPadding(dp(5), dp(5), dp(5), dp(5));

        ImageView ball = new ImageView(this);
        ball.setImageResource(net.buddhaspinas.screenrecorder.R.drawable.ic_buddha);
        GradientDrawable ballBg = new GradientDrawable();
        ballBg.setColor(Color.WHITE); ballBg.setShape(GradientDrawable.OVAL); ballBg.setStroke(dp(2), Color.rgb(212, 175, 55));
        ball.setBackground(ballBg); ball.setPadding(dp(5), dp(5), dp(5), dp(5));
        overlay.addView(ball, new LinearLayout.LayoutParams(dp(58), dp(58)));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL); controls.setVisibility(View.GONE); controls.setPadding(dp(4), dp(6), dp(4), 0);
        overlay.addView(controls, new LinearLayout.LayoutParams(dp(126), LinearLayout.LayoutParams.WRAP_CONTENT));

        Button start = button("Start");
        Button pause = button(RecorderService.isPaused ? "Resume" : "Pause");
        Button stop = button("Stop & Save");
        Button files = button("Files");
        controls.addView(start); controls.addView(pause); controls.addView(stop); controls.addView(files);

        start.setOnClickListener(v -> {
            controls.setVisibility(View.GONE);
            startActivity(new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_REQUEST_CAPTURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        });
        pause.setOnClickListener(v -> {
            if (!RecorderService.isRecording) { Toast.makeText(this, "Start recording first", Toast.LENGTH_SHORT).show(); return; }
            String action = RecorderService.isPaused ? RecorderService.ACTION_RESUME : RecorderService.ACTION_PAUSE;
            startService(new Intent(this, RecorderService.class).setAction(action));
            pause.postDelayed(() -> pause.setText(RecorderService.isPaused ? "Resume" : "Pause"), 250);
        });
        stop.setOnClickListener(v -> {
            if (RecorderService.isRecording) startService(new Intent(this, RecorderService.class).setAction(RecorderService.ACTION_STOP));
            controls.setVisibility(View.GONE);
        });
        files.setOnClickListener(v -> {
            startActivity(new Intent(this, RecordingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            controls.setVisibility(View.GONE);
        });

        ball.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY(); startX = params.x; startY = params.y; moved = false; return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downX), dy = Math.round(event.getRawY() - downY);
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                    params.x = startX + dx; params.y = startY + dy;
                    try { wm.updateViewLayout(overlay, params); } catch (Exception ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        pause.setText(RecorderService.isPaused ? "Resume" : "Pause");
                        start.setVisibility(RecorderService.isRecording ? View.GONE : View.VISIBLE);
                        pause.setVisibility(RecorderService.isRecording ? View.VISIBLE : View.GONE);
                        stop.setVisibility(RecorderService.isRecording ? View.VISIBLE : View.GONE);
                        controls.setVisibility(controls.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                    return true;
            }
            return false;
        });

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = dp(14); params.y = dp(170);
        wm.addView(overlay, params);
    }

    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setTextSize(12); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(8), dp(5), dp(8), dp(5));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(17, 24, 39)); bg.setCornerRadius(dp(10)); b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)); lp.topMargin = dp(4); b.setLayoutParams(lp);
        return b;
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(net.buddhaspinas.screenrecorder.R.drawable.ic_buddha).setContentTitle("Buddhas floating recorder")
                .setContentText("Floating screen-recording controls are active").setContentIntent(pi).setOngoing(true).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Floating recorder", NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        try { if (wm != null && overlay != null) wm.removeView(overlay); } catch (Exception ignored) { }
        overlay = null; stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
}
