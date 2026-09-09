package net.buddhaspinas.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudSyncService extends Service {
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_NAME = "name";
    private static final String CHANNEL = "buddhas_cloud_sync";
    private static final int ID = 1320;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }
        String uriText = intent.getStringExtra(EXTRA_URI);
        String name = intent.getStringExtra(EXTRA_NAME);
        if (uriText == null || uriText.isEmpty()) { stopSelf(startId); return START_NOT_STICKY; }

        Notification n = notification("Syncing recording to Cloud Library…", false, 0);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else startForeground(ID, n);

        Uri uri = Uri.parse(uriText);
        executor.execute(() -> {
            try {
                NativeCloudSync.SyncResult r = NativeCloudSync.sync(this, uri, name);
                if (r.synced && r.cloudId > 0) {
                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                            .notify(ID + 1, notification("Saved to Cloud Library — tap to edit", true, r.cloudId));
                }
            } catch (Exception ignored) {
                // Phone recording is already safe locally; cloud sync can be retried from Files.
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            }
        });
        return START_NOT_STICKY;
    }

    private Notification notification(String text, boolean done, long cloudId) {
        Intent open = new Intent(this, MainActivity.class);
        if (cloudId > 0) open.putExtra(MainActivity.EXTRA_START_URL, MainActivity.EDITOR_URL + "?id=" + cloudId);
        PendingIntent pi = PendingIntent.getActivity(this, 41, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setSmallIcon(net.buddhaspinas.screenrecorder.R.drawable.ic_buddha)
                .setContentTitle("Buddhas Screen Recorder")
                .setContentText(text)
                .setContentIntent(pi)
                .setColor(Color.rgb(212, 175, 55))
                .setOngoing(!done)
                .setAutoCancel(done)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Cloud recording sync", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
