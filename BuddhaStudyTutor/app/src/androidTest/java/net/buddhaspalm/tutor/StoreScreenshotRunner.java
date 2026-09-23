package net.buddhaspalm.tutor;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
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
    private boolean contains(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (contains(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}
