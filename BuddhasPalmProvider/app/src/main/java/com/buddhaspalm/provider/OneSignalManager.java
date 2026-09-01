package com.buddhaspalm.provider;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

public final class OneSignalManager {
    public static final String BOOKING_CHANNEL_ID = "buddhas_booking_critical_v3";
    private static volatile boolean initialized = false;

    private OneSignalManager() {}

    public static synchronized void initialize(Context context) {
        if (initialized) return;
        String appId = context.getString(R.string.onesignal_app_id).trim();
        if (appId.isEmpty()) return;

        createBookingNotificationChannel(context.getApplicationContext());
        OneSignal.getDebug().setLogLevel(LogLevel.INFO);
        OneSignal.initWithContext(context.getApplicationContext(), appId);
        initialized = true;
    }

    public static void createBookingNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        Uri sound = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.buddhas_booking_alarm);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel channel = new NotificationChannel(
                BOOKING_CHANNEL_ID,
                "Buddhas Palm Booking Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Urgent new-booking alerts that require acknowledgement.");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 700, 250, 700, 250, 1200, 500});
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setSound(sound, audioAttributes);
        manager.createNotificationChannel(channel);
    }

    public static boolean isInitialized() { return initialized; }

    public static void login(String externalId) {
        if (!initialized || externalId == null) return;
        String clean = externalId.trim();
        if (clean.isEmpty()) return;
        OneSignal.login(clean);
        OneSignal.getUser().getPushSubscription().optIn();
    }

    public static void logout() { if (initialized) OneSignal.logout(); }

    public static boolean hasNotificationPermission() {
        return initialized && OneSignal.getNotifications().getPermission();
    }

    public static void requestNotificationPermission() {
        if (initialized && !OneSignal.getNotifications().getPermission()) {
            OneSignal.getNotifications().requestPermission(true, Continue.none());
        }
    }

    public static String getSubscriptionId() {
        if (!initialized) return "";
        String id = OneSignal.getUser().getPushSubscription().getId();
        return id == null ? "" : id;
    }
}
