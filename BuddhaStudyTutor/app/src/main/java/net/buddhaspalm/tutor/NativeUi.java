package net.buddhaspalm.tutor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class NativeUi {
    static final int NAVY = Color.rgb(15, 23, 42);
    static final int INDIGO = Color.rgb(79, 70, 229);
    static final int BLUE = Color.rgb(37, 99, 235);
    static final int TEXT = Color.rgb(30, 41, 59);
    static final int MUTED = Color.rgb(100, 116, 139);
    static final int SURFACE = Color.WHITE;
    static final int BG = Color.rgb(248, 250, 252);
    static final int BORDER = Color.rgb(226, 232, 240);

    private NativeUi() {}

    static void applyInsets(View root) {
        if (root.getContext() instanceof android.app.Activity) {
            Window window = ((android.app.Activity) root.getContext()).getWindow();
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(BG);
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
        applyInsetPadding(root);
    }

    static void applyDarkInsets(View root) {
        if (root.getContext() instanceof android.app.Activity) {
            Window window = ((android.app.Activity) root.getContext()).getWindow();
            int darkBlue = Color.rgb(4, 18, 52);
            window.setStatusBarColor(darkBlue);
            window.setNavigationBarColor(darkBlue);
            window.getDecorView().setSystemUiVisibility(0);
        }
        applyInsetPadding(root);
    }

    private static void applyInsetPadding(View root) {
        final int l = root.getPaddingLeft(), t = root.getPaddingTop();
        final int r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l + left, t + top, r + right, b + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int color, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable roundedBorder(int color, int stroke, int radiusDp, Context c) {
        GradientDrawable d = rounded(color, radiusDp, c);
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    static GradientDrawable gradient(int[] colors, GradientDrawable.Orientation orientation,
                                     int radiusDp, int strokeColor, int strokeWidthDp, Context c) {
        GradientDrawable d = new GradientDrawable(orientation, colors);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeWidthDp > 0) d.setStroke(dp(c, strokeWidthDp), strokeColor);
        return d;
    }

    static TextView text(Context c, String value, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setLineSpacing(0, 1.08f);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    static Button button(Context c, String label, int background, int foreground) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextColor(foreground);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));
        b.setBackground(rounded(background, 13, c));
        return b;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackground(roundedBorder(SURFACE, BORDER, 18, c));
        l.setElevation(dp(c, 2));
        return l;
    }

    static void addSpace(LinearLayout parent, int heightDp) {
        View v = new View(parent.getContext());
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(parent.getContext(), heightDp)));
    }
}
