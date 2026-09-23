package net.buddhaspalm.tutor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
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
        final int l = root.getPaddingLeft(), t = root.getPaddingTop();
        final int r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(l + insets.getSystemWindowInsetLeft(),
                    t + insets.getSystemWindowInsetTop(),
                    r + insets.getSystemWindowInsetRight(),
                    b + insets.getSystemWindowInsetBottom());
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
