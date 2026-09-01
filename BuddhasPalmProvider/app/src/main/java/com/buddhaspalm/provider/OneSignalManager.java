package com.buddhaspalm.provider;

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

        OneSignal.getDebug().setLogLevel(LogLevel.WARN);
        OneSignal.initWithContext(context.getApplicationContext(), appId);
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void login(String externalId) {
        if (!initialized || externalId == null) return;
        String clean = externalId.trim();
        if (clean.isEmpty()) return;
        OneSignal.login(clean);
        OneSignal.getUser().getPushSubscription().optIn();
    }

    public static void logout() {
        if (initialized) OneSignal.logout();
    }

    public static boolean hasNotificationPermission() {
        return initialized && OneSignal.getNotifications().getPermission();
    }

    public static void requestNotificationPermission() {
        if (initialized) OneSignal.getNotifications().requestPermission(true, Continue.none());
    }

    public static String getSubscriptionId() {
        if (!initialized) return "";
        String id = OneSignal.getUser().getPushSubscription().getId();
        return id == null ? "" : id;
    }
}
