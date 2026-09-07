package net.buddhaspalm.tutor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class TutorFirebaseMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "buddhastudy_admin_alerts";

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        getSharedPreferences("buddhastudy_native", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
    }

    @Override public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        ensureNotificationChannel(this);
        String title = "BuddhaStudy Tutor";
        String body = "You have a new notification.";
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) title = remoteMessage.getNotification().getTitle();
            if (remoteMessage.getNotification().getBody() != null) body = remoteMessage.getNotification().getBody();
        }
        Map<String,String> data = remoteMessage.getData();
        String clickUrl = data.get("click_url");
        if (clickUrl == null || !clickUrl.startsWith("https://tutor.buddhaspalm.net/")) clickUrl = "https://tutor.buddhaspalm.net/";

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("click_url", clickUrl);
        PendingIntent pi = PendingIntent.getActivity(this, (int)(System.currentTimeMillis() & 0x7fffffff), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setContentIntent(pi);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try { if (nm != null) nm.notify((int)(System.currentTimeMillis() & 0x7fffffff), b.build()); } catch (SecurityException ignored) {}
    }

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "BuddhaStudy Admin Alerts", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Quiz, homework, requirement and chat notifications from BuddhaStudy Tutor");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }
}
