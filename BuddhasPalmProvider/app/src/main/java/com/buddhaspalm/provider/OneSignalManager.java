package com.buddhaspalm.provider;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

public final class OneSignalManager {
    public static final String BOOKING_CHANNEL_ID = "buddhas_booking_alerts";
    private static volatile boolean initialized = false;

    private OneSignalManager() {}

    public static synchronized void initialize(Context context) {
        if (initialized) return;
        String appId = context.getString(R.string.onesignal_app_id).trim();
        if (appId.isEmpty()) return;

        createBookingNotificationChannel(context.getApplicationContext());
        OneSignal.getDebug().setLogLevel(LogLevel.WARN);
        OneSignal.initWithContext(context.getApplicationContext(), appId);
        initialized = true;
    }

    private static void createBookingNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                BOOKING_CHANNEL_ID,
                "Booking & Provider Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("New bookings, booking updates and important BuddhasPalm provider alerts.");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 300, 180, 300});
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        Uri sound = Settings.System.DEFAULT_NOTIFICATION_URI;
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, audioAttributes);
        manager.createNotificationChannel(channel);
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
