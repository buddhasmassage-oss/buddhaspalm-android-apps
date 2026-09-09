package com.buddhaspinas.buddhasride;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class BuddhasRideFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "buddhas_ride_updates";
    private static final String HOME_URL = "https://rider.buddhaspinas.com/";

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        getSharedPreferences("buddhas_ride_push", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
    }

    @Override public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Map<String,String> data = remoteMessage.getData();
        String title = data.get("title");
        String body = data.get("body");
        if (remoteMessage.getNotification() != null) {
            if (title == null || title.isEmpty()) title = remoteMessage.getNotification().getTitle();
            if (body == null || body.isEmpty()) body = remoteMessage.getNotification().getBody();
        }
        if (title == null || title.isEmpty()) title = "Buddhas Ride";
        if (body == null) body = "You have a new Buddhas Ride update.";
        showNotification(title, body, data.get("url"));
    }

    private void showNotification(String title, String body, String url) {
        ensureChannel();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (url != null && url.startsWith("https://rider.buddhaspinas.com/")) {
            intent.putExtra("url", url);
            intent.setData(Uri.parse(url));
        } else intent.setData(Uri.parse(HOME_URL));
        int requestCode = (int)(System.currentTimeMillis() & 0x7fffffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_onesignal_default)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0,180,120,180});
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) NotificationManagerCompat.from(this).notify(requestCode, builder.build());
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Buddhas Ride updates", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Ride arrival, food delivery, chat and account updates");
            channel.enableVibration(true);
            channel.setLightColor(Color.rgb(7,93,255));
            nm.createNotificationChannel(channel);
        }
    }
}
