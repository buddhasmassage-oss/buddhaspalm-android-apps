package net.buddhaspalm.tutor;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

public class MainActivity extends FragmentActivity {
    private static final String HOME = "https://tutor.buddhaspalm.net/";
    private static final int REQ_NOTIFY = 7101;

    private ScrollView dashboardScroll;
    private View alertDot;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        refreshAlertIndicator();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(NativeUi.gradient(
                new int[]{Color.rgb(4, 18, 52), Color.rgb(3, 26, 76), Color.rgb(6, 18, 50)},
                GradientDrawable.Orientation.TL_BR, 0, 0, 0, this));

        dashboardScroll = new ScrollView(this);
        dashboardScroll.setFillViewport(true);
        dashboardScroll.setClipToPadding(false);
        dashboardScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(NativeUi.dp(this, 14), NativeUi.dp(this, 12), NativeUi.dp(this, 14), NativeUi.dp(this, 18));
        dashboardScroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        page.addView(buildHero(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 286)));

        NativeUi.addSpace(page, 18);
        page.addView(buildSectionHeader());
        NativeUi.addSpace(page, 10);

        LinearLayout firstRow = featureRow();
        firstRow.addView(featureCard(
                "Learning Portal",
                "Open your BuddhaStudy lessons, quizzes, modules and course progress.",
                "Open Portal",
                R.drawable.ic_school,
                new int[]{Color.rgb(21, 104, 244), Color.rgb(43, 197, 255)},
                new int[]{Color.rgb(16, 62, 137), Color.rgb(12, 76, 155)},
                v -> openPortal(HOME),
                false), featureParams(true));
        firstRow.addView(featureCard(
                "Notification Inbox",
                "Keep recent BuddhaStudy push messages in a native inbox even after dismissing the system notification.",
                "View Alerts",
                R.drawable.ic_bell_outline,
                new int[]{Color.rgb(117, 43, 255), Color.rgb(168, 59, 255)},
                new int[]{Color.rgb(59, 31, 126), Color.rgb(70, 26, 132)},
                v -> startActivity(new Intent(this, NotificationInboxActivity.class)),
                true), featureParams(false));
        page.addView(firstRow);

        NativeUi.addSpace(page, 10);
        LinearLayout secondRow = featureRow();
        secondRow.addView(featureCard(
                "Course Downloads",
                "See learning files you downloaded from the Tutor portal and keep them for offline study.",
                "Open Downloads",
                R.drawable.ic_download_cloud,
                new int[]{Color.rgb(18, 193, 176), Color.rgb(63, 225, 226)},
                new int[]{Color.rgb(10, 92, 119), Color.rgb(9, 109, 127)},
                v -> startActivity(new Intent(this, DownloadsActivity.class)),
                false), featureParams(true));
        secondRow.addView(featureCard(
                "Biometric Access",
                "Use secure biometric sign-in to quickly and safely access your BuddhaStudy account.",
                "Manage Access",
                R.drawable.ic_fingerprint_outline,
                new int[]{Color.rgb(238, 161, 41), Color.rgb(249, 197, 83)},
                new int[]{Color.rgb(101, 72, 58), Color.rgb(85, 70, 82)},
                v -> openPortal(HOME),
                false), featureParams(false));
        page.addView(secondRow);

        dashboardScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(dashboardScroll);
        root.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 78)));

        NativeUi.applyDarkInsets(root);
        setContentView(root);
    }

    private FrameLayout buildHero() {
        FrameLayout hero = new FrameLayout(this);
        hero.setClipToOutline(true);
        hero.setBackground(NativeUi.roundedBorder(Color.argb(35, 38, 92, 190), Color.rgb(61, 140, 255), 24, this));
        hero.setElevation(NativeUi.dp(this, 6));

        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.dashboard_hero_bg);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setAlpha(0.98f);
        hero.addView(art, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View shade = new View(this);
        shade.setBackground(NativeUi.gradient(
                new int[]{Color.argb(244, 3, 31, 92), Color.argb(220, 10, 43, 128), Color.argb(66, 30, 72, 196)},
                GradientDrawable.Orientation.LEFT_RIGHT, 24, 0, 0, this));
        hero.addView(shade, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(NativeUi.dp(this, 18), NativeUi.dp(this, 18), NativeUi.dp(this, 18), NativeUi.dp(this, 12));
        hero.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout logoFrame = new FrameLayout(this);
        logoFrame.setPadding(NativeUi.dp(this, 2), NativeUi.dp(this, 2), NativeUi.dp(this, 2), NativeUi.dp(this, 2));
        logoFrame.setBackground(NativeUi.roundedBorder(Color.argb(42, 0, 23, 79), Color.argb(220, 109, 180, 255), 18, this));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.dashboard_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoFrame.addView(logo, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        head.addView(logoFrame, new LinearLayout.LayoutParams(NativeUi.dp(this, 74), NativeUi.dp(this, 74)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(NativeUi.dp(this, 13), 0, 0, 0);

        SpannableString brand = new SpannableString("BuddhaStudy Tutor");
        int tutorStart = "BuddhaStudy ".length();
        brand.setSpan(new ForegroundColorSpan(Color.rgb(89, 218, 255)), tutorStart, brand.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView title = NativeUi.text(this, "", 25, Color.WHITE, true);
        title.setText(brand);
        title.setLetterSpacing(-0.02f);
        titleBox.addView(title);

        TextView subtitle = NativeUi.text(this, "Massage Therapy Learning Companion", 13, Color.rgb(219, 234, 254), false);
        subtitle.setLetterSpacing(0.04f);
        titleBox.addView(subtitle);
        head.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        content.addView(head);

        NativeUi.addSpace(content, 14);
        TextView intro = NativeUi.text(this,
                "Study courses, receive class alerts, manage downloads, and use secure biometric sign-in from one Android app.",
                14, Color.rgb(236, 244, 255), false);
        intro.setLineSpacing(NativeUi.dp(this, 1), 1.08f);
        content.addView(intro);

        NativeUi.addSpace(content, 14);
        LinearLayout cta = actionPill("Continue Learning", R.drawable.ic_book_outline,
                new int[]{Color.rgb(248, 252, 255), Color.rgb(224, 243, 255)},
                Color.rgb(19, 72, 205), true);
        cta.setOnClickListener(v -> openPortal(HOME));
        content.addView(cta, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 58)));

        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        content.addView(dots, dotsParams);
        addDot(dots, 30, true);
        addDot(dots, 8, false);
        addDot(dots, 8, false);

        animateHero(art, logoFrame, title, cta);
        return hero;
    }

    private View buildSectionHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View accent = new View(this);
        accent.setBackground(NativeUi.gradient(
                new int[]{Color.rgb(81, 116, 255), Color.rgb(37, 215, 255)},
                GradientDrawable.Orientation.TOP_BOTTOM, 8, 0, 0, this));
        row.addView(accent, new LinearLayout.LayoutParams(NativeUi.dp(this, 6), NativeUi.dp(this, 34)));

        TextView title = NativeUi.text(this, "Your study tools", 22, Color.WHITE, true);
        title.setPadding(NativeUi.dp(this, 12), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView small = NativeUi.text(this, "ALL YOU NEED IN ONE PLACE", 9, Color.rgb(178, 196, 241), false);
        small.setLetterSpacing(0.30f);
        small.setGravity(Gravity.END);
        row.addView(small);
        return row;
    }

    private LinearLayout featureRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        return row;
    }

    private LinearLayout.LayoutParams featureParams(boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, NativeUi.dp(this, 232), 1);
        if (left) p.setMargins(0, 0, NativeUi.dp(this, 5), 0);
        else p.setMargins(NativeUi.dp(this, 5), 0, 0, 0);
        return p;
    }

    private FrameLayout featureCard(String title, String body, String action, int iconRes,
                                    int[] actionColors, int[] cardColors,
                                    View.OnClickListener listener, boolean showDot) {
        FrameLayout shell = new FrameLayout(this);
        shell.setBackground(NativeUi.gradient(cardColors, GradientDrawable.Orientation.TL_BR, 20,
                Color.argb(160, 84, 139, 231), 1, this));
        shell.setElevation(NativeUi.dp(this, 4));
        shell.setClipToOutline(true);
        shell.setOnClickListener(listener);

        ImageView watermark = new ImageView(this);
        watermark.setImageResource(iconRes);
        watermark.setColorFilter(Color.rgb(121, 163, 255));
        watermark.setAlpha(0.16f);
        FrameLayout.LayoutParams wm = new FrameLayout.LayoutParams(NativeUi.dp(this, 70), NativeUi.dp(this, 70), Gravity.TOP | Gravity.END);
        wm.setMargins(0, NativeUi.dp(this, 15), NativeUi.dp(this, 10), 0);
        shell.addView(watermark, wm);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(NativeUi.dp(this, 14), NativeUi.dp(this, 14), NativeUi.dp(this, 12), NativeUi.dp(this, 12));
        shell.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(NativeUi.gradient(actionColors, GradientDrawable.Orientation.TL_BR, 13,
                Color.argb(180, 224, 239, 255), 1, this));
        iconTile.setElevation(NativeUi.dp(this, 5));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(NativeUi.dp(this, 11), NativeUi.dp(this, 11), NativeUi.dp(this, 11), NativeUi.dp(this, 11));
        iconTile.addView(icon, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (showDot) {
            View dot = new View(this);
            dot.setBackground(NativeUi.rounded(Color.rgb(255, 68, 92), 8, this));
            FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(NativeUi.dp(this, 10), NativeUi.dp(this, 10), Gravity.TOP | Gravity.END);
            dp.setMargins(0, NativeUi.dp(this, 4), NativeUi.dp(this, 4), 0);
            iconTile.addView(dot, dp);
        }
        content.addView(iconTile, new LinearLayout.LayoutParams(NativeUi.dp(this, 52), NativeUi.dp(this, 52)));

        NativeUi.addSpace(content, 10);
        TextView heading = NativeUi.text(this, title, 16, Color.WHITE, true);
        content.addView(heading);
        NativeUi.addSpace(content, 5);
        TextView desc = NativeUi.text(this, body, 12.5f, Color.rgb(220, 231, 250), false);
        desc.setMaxLines(4);
        content.addView(desc);

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        LinearLayout button = actionPill(action, 0, actionColors, Color.WHITE, false);
        button.setOnClickListener(listener);
        content.addView(button, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 44)));
        return shell;
    }

    private LinearLayout actionPill(String label, int iconRes, int[] colors, int textColor, boolean lightStyle) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(NativeUi.dp(this, 12), 0, NativeUi.dp(this, 7), 0);
        int stroke = lightStyle ? Color.rgb(178, 224, 255) : Color.argb(210, 224, 239, 255);
        pill.setBackground(NativeUi.gradient(colors, GradientDrawable.Orientation.LEFT_RIGHT, 28, stroke, 1, this));
        pill.setElevation(NativeUi.dp(this, 4));
        pill.setClickable(true);
        pill.setFocusable(true);

        if (iconRes != 0) {
            ImageView left = new ImageView(this);
            left.setImageResource(iconRes);
            left.setColorFilter(lightStyle ? Color.rgb(31, 94, 223) : Color.WHITE);
            left.setPadding(NativeUi.dp(this, 5), NativeUi.dp(this, 5), NativeUi.dp(this, 5), NativeUi.dp(this, 5));
            pill.addView(left, new LinearLayout.LayoutParams(NativeUi.dp(this, 34), NativeUi.dp(this, 34)));
        }

        TextView text = NativeUi.text(this, label, lightStyle ? 16 : 13, textColor, true);
        text.setGravity(Gravity.CENTER);
        pill.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = NativeUi.text(this, "›", lightStyle ? 27 : 24, Color.WHITE, false);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(NativeUi.rounded(Color.argb(lightStyle ? 210 : 125, 18, 72, 212), 22, this));
        pill.addView(arrow, new LinearLayout.LayoutParams(NativeUi.dp(this, 38), NativeUi.dp(this, 38)));
        return pill;
    }

    private LinearLayout buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(NativeUi.dp(this, 5), NativeUi.dp(this, 5), NativeUi.dp(this, 5), NativeUi.dp(this, 4));
        nav.setBackground(NativeUi.gradient(
                new int[]{Color.rgb(5, 25, 68), Color.rgb(8, 31, 77), Color.rgb(10, 24, 58)},
                GradientDrawable.Orientation.LEFT_RIGHT, 22, Color.rgb(30, 65, 126), 1, this));
        nav.setElevation(NativeUi.dp(this, 12));

        addNavItem(nav, "Home", R.drawable.ic_home_outline, true, v -> dashboardScroll.smoothScrollTo(0, 0), false);
        addNavItem(nav, "Learn", R.drawable.ic_book_outline, false, v -> openPortal(HOME), false);
        addNavItem(nav, "Downloads", R.drawable.ic_download_outline, false, v -> startActivity(new Intent(this, DownloadsActivity.class)), false);
        addNavItem(nav, "Alerts", R.drawable.ic_bell_outline, false, v -> startActivity(new Intent(this, NotificationInboxActivity.class)), true);
        addNavItem(nav, "Profile", R.drawable.ic_person_outline, false, v -> openPortal(HOME), false);
        return nav;
    }

    private void addNavItem(LinearLayout parent, String label, int iconRes, boolean active,
                            View.OnClickListener click, boolean trackAlertDot) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(click);

        FrameLayout iconFrame = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(active ? Color.rgb(79, 139, 255) : Color.rgb(155, 177, 231));
        icon.setPadding(NativeUi.dp(this, 2), NativeUi.dp(this, 2), NativeUi.dp(this, 2), NativeUi.dp(this, 2));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(NativeUi.dp(this, 27), NativeUi.dp(this, 27), Gravity.CENTER);
        iconFrame.addView(icon, ip);

        if (trackAlertDot) {
            alertDot = new View(this);
            alertDot.setBackground(NativeUi.rounded(Color.rgb(255, 72, 94), 8, this));
            FrameLayout.LayoutParams dot = new FrameLayout.LayoutParams(NativeUi.dp(this, 9), NativeUi.dp(this, 9), Gravity.TOP | Gravity.END);
            dot.setMargins(0, NativeUi.dp(this, 1), NativeUi.dp(this, 10), 0);
            iconFrame.addView(alertDot, dot);
        }
        item.addView(iconFrame, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 31)));

        TextView txt = NativeUi.text(this, label, 10.5f, active ? Color.WHITE : Color.rgb(170, 189, 234), active);
        txt.setGravity(Gravity.CENTER);
        item.addView(txt);

        View indicator = new View(this);
        indicator.setBackground(NativeUi.rounded(active ? Color.rgb(216, 234, 255) : Color.TRANSPARENT, 4, this));
        LinearLayout.LayoutParams ind = new LinearLayout.LayoutParams(active ? NativeUi.dp(this, 30) : NativeUi.dp(this, 2), NativeUi.dp(this, 3));
        ind.topMargin = NativeUi.dp(this, 4);
        item.addView(indicator, ind);
        parent.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private void addDot(LinearLayout parent, int widthDp, boolean active) {
        View dot = new View(this);
        dot.setBackground(NativeUi.rounded(active ? Color.rgb(223, 240, 255) : Color.rgb(63, 103, 184), 5, this));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(NativeUi.dp(this, widthDp), NativeUi.dp(this, 5));
        p.setMargins(NativeUi.dp(this, 4), 0, NativeUi.dp(this, 4), 0);
        parent.addView(dot, p);
    }

    private void animateHero(View art, View logo, View title, View cta) {
        ObjectAnimator artX = ObjectAnimator.ofFloat(art, View.SCALE_X, 1.02f, 1.08f);
        ObjectAnimator artY = ObjectAnimator.ofFloat(art, View.SCALE_Y, 1.02f, 1.08f);
        artX.setDuration(6500); artY.setDuration(6500);
        artX.setRepeatCount(ObjectAnimator.INFINITE); artY.setRepeatCount(ObjectAnimator.INFINITE);
        artX.setRepeatMode(ObjectAnimator.REVERSE); artY.setRepeatMode(ObjectAnimator.REVERSE);
        artX.start(); artY.start();

        ObjectAnimator floatLogo = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -NativeUi.dp(this, 4));
        floatLogo.setDuration(1900);
        floatLogo.setRepeatCount(ObjectAnimator.INFINITE);
        floatLogo.setRepeatMode(ObjectAnimator.REVERSE);
        floatLogo.start();

        title.setAlpha(0f);
        title.setTranslationY(NativeUi.dp(this, 8));
        AnimatorSet titleIn = new AnimatorSet();
        titleIn.playTogether(
                ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, NativeUi.dp(this, 8), 0f));
        titleIn.setDuration(700);
        titleIn.setInterpolator(new DecelerateInterpolator());
        titleIn.start();

        ObjectAnimator pulseX = ObjectAnimator.ofFloat(cta, View.SCALE_X, 1f, 1.015f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(cta, View.SCALE_Y, 1f, 1.015f);
        pulseX.setDuration(1800); pulseY.setDuration(1800);
        pulseX.setRepeatCount(ObjectAnimator.INFINITE); pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseX.setRepeatMode(ObjectAnimator.REVERSE); pulseY.setRepeatMode(ObjectAnimator.REVERSE);
        pulseX.start(); pulseY.start();
    }

    private void refreshAlertIndicator() {
        if (alertDot != null) alertDot.setVisibility(NotificationStore.count(this) > 0 ? View.VISIBLE : View.GONE);
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
