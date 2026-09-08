package net.buddhaspinas.screenrecorder;

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

public class OverlayService extends Service {
    private WindowManager windowManager;
    private LinearLayout root;
    private LinearLayout menu;
    private WindowManager.LayoutParams params;
    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean moved;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (root == null) createOverlay();
        return START_STICKY;
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);

        ImageView bubble = new ImageView(this);
        bubble.setImageResource(net.buddhaspinas.screenrecorder.R.drawable.ic_buddha);
        bubble.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.argb(238, 255, 255, 255));
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubbleBg.setStroke(dp(2), Color.rgb(212, 175, 55));
        bubble.setBackground(bubbleBg);
        root.addView(bubble, new LinearLayout.LayoutParams(dp(64), dp(64)));

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setVisibility(View.GONE);
        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(Color.argb(246, 91, 11, 18));
        menuBg.setCornerRadius(dp(14));
        menu.setBackground(menuBg);

        menu.addView(menuButton("● Start", v -> requestNewCapture()));
        menu.addView(menuButton("Ⅱ Pause", v -> sendRecorderAction(RecorderService.ACTION_PAUSE)));
        menu.addView(menuButton("▶ Resume", v -> sendRecorderAction(RecorderService.ACTION_RESUME)));
        menu.addView(menuButton("■ Stop & Save", v -> sendRecorderAction(RecorderService.ACTION_STOP)));
        menu.addView(menuButton("Open Recorder", v -> openMainApp()));
        menu.addView(menuButton("Hide Ball", v -> stopSelf()));
        root.addView(menu, new LinearLayout.LayoutParams(dp(160), LinearLayout.LayoutParams.WRAP_CONTENT));

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(180);

        bubble.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downRawX);
                    int dy = Math.round(event.getRawY() - downRawY);
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                    params.x = Math.max(0, startX + dx);
                    params.y = Math.max(0, startY + dy);
                    try { windowManager.updateViewLayout(root, params); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) toggleMenu();
                    return true;
                default:
                    return false;
            }
        });

        try {
            windowManager.addView(root, params);
        } catch (Exception e) {
            root = null;
            stopSelf();
        }
    }

    private Button menuButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(listener);
        return button;
    }

    private void toggleMenu() {
        if (menu == null) return;
        menu.setVisibility(menu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        try { windowManager.updateViewLayout(root, params); } catch (Exception ignored) {}
    }

    private void requestNewCapture() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.ACTION_REQUEST_CAPTURE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        if (menu != null) menu.setVisibility(View.GONE);
    }

    private void openMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        if (menu != null) menu.setVisibility(View.GONE);
    }

    private void sendRecorderAction(String action) {
        Intent intent = new Intent(this, RecorderService.class);
        intent.setAction(action);
        try {
            startService(intent);
        } catch (Exception ignored) {
        }
        if (menu != null && !RecorderService.ACTION_PAUSE.equals(action)) menu.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        if (windowManager != null && root != null) {
            try { windowManager.removeView(root); } catch (Exception ignored) {}
        }
        root = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
