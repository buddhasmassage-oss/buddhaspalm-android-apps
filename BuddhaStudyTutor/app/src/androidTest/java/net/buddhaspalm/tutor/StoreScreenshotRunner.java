package net.buddhaspalm.tutor;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

/** Captures actual native app screens on a clean emulator; creates no sample user data. */
public class StoreScreenshotRunner extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            capture(MainActivity.class, "BuddhaStudy Tutor", "01-dashboard");
            captureDashboardLower();
            capture(NotificationInboxActivity.class, "No saved notifications yet", "02-notification-inbox");
            capture(DownloadsActivity.class, "No Tutor downloads recorded yet", "03-course-downloads");
            capture(AboutActivity.class, "About BuddhaStudy", "04-about");
            result.putString("stream", "STORE_SCREENSHOTS_OK: 4 native screens launched and verified\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "STORE_SCREENSHOTS_FAILED: " + error + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void capture(Class<? extends Activity> type, String expected, String filename) throws Exception {
        Intent intent = new Intent(getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = startActivitySync(intent);
        waitForIdleSync();
        final boolean[] found = {false};
        runOnMainSync(() -> found[0] = contains(activity.getWindow().getDecorView(), expected));
        if (!found[0]) throw new AssertionError("Missing screen text: " + expected);
        if (type == MainActivity.class) runOnMainSync(() -> verifyDashboard(activity.getWindow().getDecorView()));
        SystemClock.sleep(1200);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Screenshot unavailable");
        File dir = new File(getTargetContext().getExternalFilesDir(null), "store-screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new AssertionError("Cannot create screenshot directory");
        try (FileOutputStream out = new FileOutputStream(new File(dir, filename + ".png"))) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new AssertionError("PNG failed");
        }
        bitmap.recycle();
        runOnMainSync(activity::finish);
        waitForIdleSync();
    }
    private void captureDashboardLower() throws Exception {
        Activity activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        runOnMainSync(() -> {
            ScrollView scroll = findScroll(activity.getWindow().getDecorView());
            if (scroll == null) throw new AssertionError("Dashboard cannot scroll");
            scroll.fullScroll(View.FOCUS_DOWN);
        });
        waitForIdleSync();
        SystemClock.sleep(1500);
        runOnMainSync(() -> {
            TextView home = findText(activity.getWindow().getDecorView(), "Home");
            if (home == null || !home.getGlobalVisibleRect(new Rect()))
                throw new AssertionError("Fixed bottom navigation is hidden after scrolling");
        });
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Lower dashboard screenshot unavailable");
        File dir = new File(getTargetContext().getExternalFilesDir(null), "store-screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new AssertionError("Cannot create screenshot directory");
        try (FileOutputStream out = new FileOutputStream(new File(dir, "01-dashboard-lower.png"))) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new AssertionError("PNG failed");
        }
        bitmap.recycle();
        runOnMainSync(activity::finish);
        waitForIdleSync();
    }
    private ScrollView findScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ScrollView found = findScroll(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private void verifyDashboard(View root) {
        if (!contains(root, "About BuddhaStudy") || !contains(root, "Android App Settings"))
            throw new AssertionError("Dashboard footer actions missing");
        for (String label : new String[]{"Open Portal", "View Alerts", "Open Downloads", "Manage Access"}) {
            TextView text = findText(root, label);
            if (text == null) throw new AssertionError("Missing dashboard action: " + label);
            View pill = (View) text.getParent();
            View content = (View) pill.getParent();
            View shell = (View) content.getParent();
            if (content.getBottom() > shell.getHeight())
                throw new AssertionError("Card clips action: " + label);
        }
    }
    private TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }    private boolean contains(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (contains(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}
