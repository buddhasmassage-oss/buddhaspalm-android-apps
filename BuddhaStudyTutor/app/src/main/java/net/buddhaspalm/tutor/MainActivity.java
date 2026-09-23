package net.buddhaspalm.tutor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.biometric.BiometricManager;
import androidx.fragment.app.FragmentActivity;

public class MainActivity extends FragmentActivity {
    private static final String HOME = "https://tutor.buddhaspalm.net/";
    private static final int REQ_NOTIFY = 7101;
    private TextView pushStatus, biometricStatus, inboxCount;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(NativeUi.NAVY);
        w.setNavigationBarColor(Color.WHITE);
        buildUi();
        TutorFirebaseMessagingService.ensureNotificationChannel(this);
        askNotificationPermission();
        if (getIntent() != null && getIntent().getData() != null) {
            String deep = getIntent().getData().toString();
            if (deep.startsWith("https://tutor.buddhaspalm.net/")) openPortal(deep);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NativeUi.BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(NativeUi.dp(this, 18), NativeUi.dp(this, 18), NativeUi.dp(this, 18), NativeUi.dp(this, 30));
        scroll.addView(page, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(NativeUi.dp(this, 20), NativeUi.dp(this, 22), NativeUi.dp(this, 20), NativeUi.dp(this, 22));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{NativeUi.NAVY, Color.rgb(49,46,129), Color.rgb(37,99,235)});
        hg.setCornerRadius(NativeUi.dp(this, 24));
        hero.setBackground(hg);
        hero.setElevation(NativeUi.dp(this, 6));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(net.buddhaspalm.tutor.R.drawable.app_icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable iconBg = NativeUi.rounded(Color.WHITE, 18, this);
        icon.setBackground(iconBg);
        icon.setPadding(NativeUi.dp(this, 6), NativeUi.dp(this, 6), NativeUi.dp(this, 6), NativeUi.dp(this, 6));
        head.addView(icon, new LinearLayout.LayoutParams(NativeUi.dp(this, 66), NativeUi.dp(this, 66)));
        LinearLayout ht = new LinearLayout(this); ht.setOrientation(LinearLayout.VERTICAL); ht.setPadding(NativeUi.dp(this, 14),0,0,0);
        TextView title = NativeUi.text(this, "BuddhaStudy Tutor", 24, Color.WHITE, true);
        TextView sub = NativeUi.text(this, "Massage Therapy Learning Companion", 13, Color.rgb(219,234,254), false);
        ht.addView(title); ht.addView(sub);
        head.addView(ht, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        hero.addView(head);
        NativeUi.addSpace(hero, 18);
        TextView intro = NativeUi.text(this, "Study courses, receive class alerts, manage downloads, and use secure biometric sign-in from one Android app.", 14, Color.rgb(226,232,240), false);
        hero.addView(intro);
        NativeUi.addSpace(hero, 18);
        Button continueBtn = NativeUi.button(this, "Continue Learning", Color.WHITE, NativeUi.NAVY);
        continueBtn.setOnClickListener(v -> openPortal(HOME));
        hero.addView(continueBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        page.addView(hero);

        NativeUi.addSpace(page, 20);
        page.addView(NativeUi.text(this, "Your study tools", 18, NativeUi.TEXT, true));
        NativeUi.addSpace(page, 10);
        addFeatureCard(page, "Learning Portal", "Open your BuddhaStudy lessons, quizzes, modules and course progress.", "OPEN PORTAL", v -> openPortal(HOME));
        addFeatureCard(page, "Notification Inbox", "Keep recent BuddhaStudy push messages in a native inbox even after dismissing the system notification.", "VIEW ALERTS", v -> startActivity(new Intent(this, NotificationInboxActivity.class)));
        addFeatureCard(page, "Course Downloads", "See learning files you downloaded from the Tutor portal and open Android's Downloads manager.", "VIEW DOWNLOADS", v -> startActivity(new Intent(this, DownloadsActivity.class)));
        addFeatureCard(page, "Security & Biometrics", "Fingerprint/biometric credentials stay protected by the Android Keystore and work with your Tutor login.", "OPEN SECURITY", v -> openPortal(HOME));
        addFeatureCard(page, "About BuddhaStudy", "View app version, package information, native capabilities and official website.", "ABOUT APP", v -> startActivity(new Intent(this, AboutActivity.class)));

        NativeUi.addSpace(page, 12);
        LinearLayout status = NativeUi.card(this);
        status.addView(NativeUi.text(this, "Device status", 16, NativeUi.TEXT, true));
        NativeUi.addSpace(status, 8);
        pushStatus = NativeUi.text(this, "Push notifications: checking…", 13, NativeUi.MUTED, false);
        biometricStatus = NativeUi.text(this, "Biometric security: checking…", 13, NativeUi.MUTED, false);
        inboxCount = NativeUi.text(this, "Saved alerts: checking…", 13, NativeUi.MUTED, false);
        status.addView(pushStatus); NativeUi.addSpace(status, 5); status.addView(biometricStatus); NativeUi.addSpace(status, 5); status.addView(inboxCount);
        NativeUi.addSpace(status, 12);
        Button settings = NativeUi.button(this, "Android App Settings", Color.rgb(241,245,249), NativeUi.TEXT);
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))); }
            catch (Exception ignored) {}
        });
        status.addView(settings);
        page.addView(status);
        NativeUi.addSpace(page, 16);
        page.addView(NativeUi.text(this, "Learn with BuddhaStudy • tutor.buddhaspalm.net", 12, NativeUi.MUTED, false));

        NativeUi.applyInsets(scroll); setContentView(scroll);
    }

    private void addFeatureCard(LinearLayout parent, String title, String body, String action, View.OnClickListener listener) {
        LinearLayout card = NativeUi.card(this);
        card.addView(NativeUi.text(this, title, 16, NativeUi.TEXT, true));
        NativeUi.addSpace(card, 5);
        card.addView(NativeUi.text(this, body, 13, NativeUi.MUTED, false));
        NativeUi.addSpace(card, 12);
        Button b = NativeUi.button(this, action, Color.rgb(238,242,255), Color.rgb(55,48,163));
        b.setOnClickListener(listener);
        card.addView(b);
        parent.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        NativeUi.addSpace(parent, 10);
    }

    private void refreshStatus() {
        if (pushStatus != null) {
            boolean allowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            pushStatus.setText("Push notifications: " + (allowed ? "enabled" : "permission needed"));
        }
        if (biometricStatus != null) {
            int can = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
            biometricStatus.setText("Biometric security: " + (can == BiometricManager.BIOMETRIC_SUCCESS ? "ready on this device" : "not enrolled / unavailable"));
        }
        if (inboxCount != null) inboxCount.setText( "Saved alerts: " + NotificationStore.count(this) + " saved alert(s)");
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
    }

    private void openPortal(String url) {
        Intent i = new Intent(this, TutorWebActivity.class);
        i.putExtra("url", url);
        startActivity(i);
    }
}
