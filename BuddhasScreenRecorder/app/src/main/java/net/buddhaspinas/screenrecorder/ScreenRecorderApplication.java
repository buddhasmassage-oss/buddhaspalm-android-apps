package net.buddhaspinas.screenrecorder;

import android.app.Activity;
import android.app.Application;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ScreenRecorderApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof MainActivity) {
                    activity.getWindow().getDecorView().post(() -> styleMainScreen(activity));
                }
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof MainActivity) {
                    activity.getWindow().getDecorView().post(() -> styleMainScreen(activity));
                }
            }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    private void styleMainScreen(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup contentGroup = (ViewGroup) content;
        if (contentGroup.getChildCount() == 0) return;
        View rootView = contentGroup.getChildAt(0);
        if (!(rootView instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) rootView;
        if (root.getChildCount() < 2 || !(root.getChildAt(0) instanceof LinearLayout)) return;

        LinearLayout toolbar = (LinearLayout) root.getChildAt(0);
        styleToolbar(activity, toolbar);

        View second = root.getChildAt(1);
        if (second instanceof WebView) {
            WebView web = (WebView) second;
            String ua = web.getSettings().getUserAgentString();
            if (ua != null && ua.contains("BuddhasScreenRecorder/1.4.1")) {
                web.getSettings().setUserAgentString(
                        ua.replace("BuddhasScreenRecorder/1.4.1", "BuddhasScreenRecorder/1.4.2"));
            }
        }
    }

    private void styleToolbar(Activity activity, LinearLayout toolbar) {
        final int side = dp(activity, 7);
        final int topSpace = dp(activity, 8);
        final int bottom = dp(activity, 8);

        activity.getWindow().setStatusBarColor(Color.rgb(11, 18, 32));
        activity.getWindow().setNavigationBarColor(Color.BLACK);
        setLightStatusBarIconsOff(activity);

        toolbar.setBackgroundColor(Color.rgb(11, 18, 32));
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setElevation(dp(activity, 5));
        toolbar.setMinimumHeight(dp(activity, 76));
        ViewGroup.LayoutParams toolbarParams = toolbar.getLayoutParams();
        if (toolbarParams != null) {
            toolbarParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            toolbar.setLayoutParams(toolbarParams);
        }

        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                styleButton(activity, (TextView) child, i);
            }
        }

        toolbar.setPadding(side, topSpace, side, bottom);
        toolbar.setOnApplyWindowInsetsListener((v, insets) -> {
            int statusTop;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                statusTop = insets.getInsets(WindowInsets.Type.statusBars()).top;
            } else {
                statusTop = insets.getSystemWindowInsetTop();
            }
            v.setPadding(side, statusTop + topSpace, side, bottom);
            v.setMinimumHeight(statusTop + dp(activity, 76));
            return insets;
        });
        toolbar.requestApplyInsets();
    }

    private void styleButton(Activity activity, TextView button, int index) {
        final boolean primary = index == 0;
        String label = String.valueOf(button.getText());

        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.rgb(248, 250, 252));
        button.setTextSize(label.length() > 8 ? 9.3f : 10.2f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setIncludeFontPadding(false);
        button.setLineSpacing(0f, 0.92f);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(activity, 3), dp(activity, 5), dp(activity, 3), dp(activity, 4));

        int iconRes;
        if (index == 0) iconRes = R.drawable.ic_nav_record;
        else if (index == 1) iconRes = R.drawable.ic_nav_floating;
        else if (index == 2) iconRes = R.drawable.ic_nav_files;
        else iconRes = R.drawable.ic_nav_editor;

        Drawable icon = activity.getDrawable(iconRes);
        if (icon != null) {
            int iconSize = dp(activity, 22);
            icon.setBounds(0, 0, iconSize, iconSize);
            button.setCompoundDrawables(null, icon, null, null);
            button.setCompoundDrawablePadding(dp(activity, 3));
        }

        GradientDrawable card = new GradientDrawable();
        card.setColor(primary ? Color.rgb(39, 47, 62) : Color.rgb(24, 34, 52));
        card.setStroke(dp(activity, 1),
                primary ? Color.rgb(244, 201, 93) : Color.rgb(61, 76, 99));
        card.setCornerRadius(dp(activity, 15));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(62, 255, 255, 255)), card, null));

        LinearLayout.LayoutParams lp;
        if (button.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            lp = (LinearLayout.LayoutParams) button.getLayoutParams();
        } else {
            lp = new LinearLayout.LayoutParams(0, dp(activity, 60), 1f);
        }
        lp.width = 0;
        lp.height = dp(activity, 60);
        lp.weight = 1f;
        lp.leftMargin = dp(activity, 3);
        lp.rightMargin = dp(activity, 3);
        lp.topMargin = 0;
        lp.bottomMargin = 0;
        button.setLayoutParams(lp);
    }

    private void setLightStatusBarIconsOff(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
