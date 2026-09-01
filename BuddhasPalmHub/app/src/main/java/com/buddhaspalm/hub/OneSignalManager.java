package com.buddhaspalm.hub;

import android.content.Context;
import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

public final class OneSignalManager {
    private static volatile boolean initialized = false;
    private OneSignalManager() {}

    public static synchronized void initialize(Context context) {
        if (initialized) return;
        String appId = context.getString(R.string.onesignal_app_id).trim();
        if (appId.isEmpty()) return;
        OneSignal.getDebug().setLogLevel(LogLevel.INFO);
        OneSignal.initWithContext(context.getApplicationContext(), appId);
        initialized = true;
    }

    public static void requestNotificationPermission() {
        if (initialized && !OneSignal.getNotifications().getPermission()) {
            OneSignal.getNotifications().requestPermission(true, Continue.none());
        }
    }
}
