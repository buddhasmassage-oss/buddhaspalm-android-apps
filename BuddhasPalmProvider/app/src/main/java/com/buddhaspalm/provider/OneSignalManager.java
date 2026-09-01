package com.buddhaspalm.provider;

import android.app.Activity;
import android.app.AlertDialog;
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
import com.onesignal.user.subscriptions.IPushSubscriptionObserver;
import com.onesignal.user.subscriptions.PushSubscriptionChangedState;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OneSignalManager {
    public static final String BOOKING_CHANNEL_ID = "buddhas_booking_critical_v3";

    private static volatile boolean initialized = false;
    private static volatile Runnable subscriptionChangedListener;
    private static volatile WeakReference<Activity> verificationActivity = new WeakReference<>(null);
    private static final AtomicBoolean verificationDialogShown = new AtomicBoolean(false);
    private static boolean observerAttached = false;
    private static IPushSubscriptionObserver pushSubscriptionObserver;

    private OneSignalManager() {}

    public static synchronized void initialize(Context context) {
        if (initialized) return;
        String appId = context.getString(R.string.onesignal_app_id).trim();
        if (appId.isEmpty()) return;

        createBookingNotificationChannel(context.getApplicationContext());
        OneSignal.getDebug().setLogLevel(LogLevel.INFO);
        OneSignal.initWithContext(context.getApplicationContext(), appId);
        initialized = true;
        attachSubscriptionObserver();
    }

    private static synchronized void attachSubscriptionObserver() {
        if (!initialized || observerAttached) return;

        pushSubscriptionObserver = new IPushSubscriptionObserver() {
            @Override
            public void onPushSubscriptionChange(PushSubscriptionChangedState state) {
                String id = state == null || state.getCurrent() == null
                        ? ""
                        : state.getCurrent().getId();
                maybeShowIntegrationCompleteDialog(id);

                Runnable listener = subscriptionChangedListener;
                if (listener != null) listener.run();
            }
        };

        OneSignal.getUser().getPushSubscription().addObserver(pushSubscriptionObserver);
        observerAttached = true;
    }

    public static void setupPushSubscriptionVerification(Activity activity) {
        if (activity == null) return;
        verificationActivity = new WeakReference<>(activity);
        attachSubscriptionObserver();
        maybeShowIntegrationCompleteDialog(getSubscriptionId());
    }

    private static void maybeShowIntegrationCompleteDialog(String subscriptionId) {
        if (!isRealSubscriptionId(subscriptionId)) return;
        Activity activity = verificationActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!verificationDialogShown.compareAndSet(false, true)) return;

        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            new AlertDialog.Builder(activity)
                    .setTitle("Your OneSignal SDK integration is complete!")
                    .setMessage("You can now send Push Notifications & In-App Messages through OneSignal. Tap below to enable push notifications.")
                    .setPositiveButton("Got it", (dialog, which) -> requestNotificationPermission())
                    .setCancelable(false)
                    .show();
        });
    }

    public static void setSubscriptionChangedListener(Runnable listener) {
        subscriptionChangedListener = listener;
        attachSubscriptionObserver();
        if (listener != null && isRealSubscriptionId(getSubscriptionId())) listener.run();
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

    public static boolean isInitialized() {
        return initialized;
    }

    public static void login(String externalId) {
        if (!initialized || externalId == null) return;
        String clean = externalId.trim();
        if (clean.isEmpty()) return;
        OneSignal.login(clean);
    }

    public static void logout() {
        if (initialized) OneSignal.logout();
    }

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
        return id == null ? "" : id.trim();
    }

    public static String getPushToken() {
        if (!initialized) return "";
        String token = OneSignal.getUser().getPushSubscription().getToken();
        return token == null ? "" : token.trim();
    }

    public static boolean isOptedIn() {
        return initialized && OneSignal.getUser().getPushSubscription().getOptedIn();
    }

    public static boolean isRealSubscriptionId(String id) {
        if (id == null) return false;
        String clean = id.trim();
        return !clean.isEmpty() && !clean.startsWith("local-");
    }
}
