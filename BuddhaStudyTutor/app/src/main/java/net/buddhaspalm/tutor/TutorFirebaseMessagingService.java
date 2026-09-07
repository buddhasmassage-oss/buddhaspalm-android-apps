package net.buddhaspalm.tutor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class TutorFirebaseMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "buddhastudy_admin_alerts";
    private static final String HOME = "https://school.buddhaspinas.com/";
    private static final String PRIMARY_HOST = "school.buddhaspinas.com";

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        getSharedPreferences("buddhastudy_native", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token == null ? "" : token)
                .apply();
    }

    @Override public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        ensureNotificationChannel(this);

        String title = "BuddhaStudy Tutor";
        String body = "You have a new notification.";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null && !remoteMessage.getNotification().getTitle().trim().isEmpty()) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null && !remoteMessage.getNotification().getBody().trim().isEmpty()) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        Map<String, String> data = remoteMessage.getData();
        if (data != null) {
            String dataTitle = firstNonEmpty(data.get("title"), data.get("heading"), data.get("subject"));
            String dataBody = firstNonEmpty(data.get("body"), data.get("message"), data.get("text"));
            if (dataTitle != null) title = dataTitle;
            if (dataBody != null) body = dataBody;
        }

        String clickUrl = HOME;
        if (data != null) {
            String candidate = firstNonEmpty(data.get("click_url"), data.get("link"), data.get("url"));
            if (isSafeTutorUrl(candidate)) clickUrl = candidate;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("click_url", clickUrl);
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        PendingIntent pi = PendingIntent.getActivity(
                this,
                (int) (System.currentTimeMillis() & 0x7fffffff),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            if (nm != null) nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), b.build());
        } catch (SecurityException ignored) {}
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static boolean isSafeTutorUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) || host == null) return false;
            return PRIMARY_HOST.equalsIgnoreCase(host) || ("www." + PRIMARY_HOST).equalsIgnoreCase(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "BuddhaStudy Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Quiz, plan, chat, homework and account notifications from BuddhaStudy Tutor");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }
}
