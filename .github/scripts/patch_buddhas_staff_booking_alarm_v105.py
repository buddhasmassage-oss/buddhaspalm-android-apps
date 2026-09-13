from pathlib import Path


def patch_staff_manifest(project: Path) -> None:
    p = project / "app/src/main/AndroidManifest.xml"
    text = p.read_text()
    perms = [
        '<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
        '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />',
    ]
    for perm in perms:
        if perm not in text:
            text = text.replace("<application", "    " + perm + "\n\n    <application", 1)

    service = '''        <service
            android:name=".BookingAlarmService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />\n'''
    if "BookingAlarmService" not in text:
        text = text.replace("</application>", service + "    </application>", 1)
    p.write_text(text)


def add_alarm_service(project: Path, pkg: str) -> None:
    java_dir = project / "app/src/main/java" / Path(pkg.replace('.', '/'))
    java_dir.mkdir(parents=True, exist_ok=True)
    p = java_dir / "BookingAlarmService.java"
    p.write_text(f'''package {pkg};

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class BookingAlarmService extends Service {{
    public static final String CHANNEL = "buddhas_staff_booking_alarm";
    public static final int NOTIFICATION_ID = 91505;
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override public void onCreate() {{
        super.onCreate();
        createChannel();
    }}

    @Override public int onStartCommand(Intent intent, int flags, int startId) {{
        String title = intent != null ? intent.getStringExtra("title") : null;
        String body = intent != null ? intent.getStringExtra("body") : null;
        String url = intent != null ? intent.getStringExtra("url") : null;
        if (title == null || title.trim().isEmpty()) title = "NEW BOOKING • Buddhas Staff";
        if (body == null || body.trim().isEmpty()) body = "A new booking is waiting. Tap to open and respond.";

        Intent open = new Intent(this, MainActivity.class);
        open.putExtra("url", url == null ? "https://staff.buddhaspalm.shop/therapist.php?page=jobs" : url);
        open.putExtra("stop_booking_alarm", true);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 91505, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);
        startForeground(NOTIFICATION_ID, n.build());
        startLoudLoop();
        return START_NOT_STICKY;
    }}

    private void createChannel() {{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel c = new NotificationChannel(CHANNEL, "New Booking Alarm", NotificationManager.IMPORTANCE_HIGH);
        c.setDescription("Continuous loud alarm for a new therapist booking until the app is touched/opened");
        c.enableVibration(true);
        c.setVibrationPattern(new long[]{{0, 500, 180, 500, 180, 850}});
        c.setSound(null, null); // looping MediaPlayer below is louder and continues until acknowledged
        nm.createNotificationChannel(c);
    }}

    private void startLoudLoop() {{
        stopLoudLoop();
        try {{
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.setVolume(1.0f, 1.0f);
            player.prepare();
            player.start();
        }} catch (Throwable ignored) {{}}
        try {{
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {{
                long[] pattern = new long[]{{0, 500, 180, 500, 180, 900}};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                else vibrator.vibrate(pattern, 0);
            }}
        }} catch (Throwable ignored) {{}}
    }}

    private void stopLoudLoop() {{
        try {{ if (player != null) {{ player.stop(); player.release(); }} }} catch (Throwable ignored) {{}}
        player = null;
        try {{ if (vibrator != null) vibrator.cancel(); }} catch (Throwable ignored) {{}}
        vibrator = null;
    }}

    @Override public void onDestroy() {{
        stopLoudLoop();
        super.onDestroy();
    }}

    @Nullable @Override public IBinder onBind(Intent intent) {{ return null; }}
}}
''')


def patch_fcm_service(project: Path) -> None:
    p = next((project / "app/src/main/java").rglob("BuddhasFirebaseMessagingService.java"))
    text = p.read_text()
    marker = '''            Intent i = new Intent(this, MainActivity.class);\n'''
    inject = '''            if ("1".equals(msg.getData().get("booking_alarm"))) {
                try {
                    Intent alarm = new Intent(this, BookingAlarmService.class);
                    alarm.putExtra("title", title);
                    alarm.putExtra("body", body);
                    alarm.putExtra("url", url);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(alarm);
                    else startService(alarm);
                    return;
                } catch (Throwable ignored) {
                    // Fall through to the normal high-priority notification if the alarm service cannot start.
                }
            }

''' + marker
    if "msg.getData().get(\"booking_alarm\")" not in text:
        if marker not in text:
            raise RuntimeError("Could not find FCM notification Intent marker")
        text = text.replace(marker, inject, 1)
    p.write_text(text)


def patch_main(project: Path) -> None:
    p = next((project / "app/src/main/java").rglob("MainActivity.java"))
    text = p.read_text()
    marker = "    private void configureWebView() {"
    helper = '''    private void stopBookingAlarm() {
        try { stopService(new Intent(this, BookingAlarmService.class)); } catch (Throwable ignored) {}
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        stopBookingAlarm();
    }

'''
    if "private void stopBookingAlarm()" not in text:
        if marker not in text:
            raise RuntimeError("Could not locate configureWebView marker")
        text = text.replace(marker, helper + marker, 1)

    # Opening/tapping the alarm notification is an acknowledgement and stops the loop immediately.
    resume = "    @Override protected void onResume() {"
    if resume in text and "stopBookingAlarm(); // v1.0.5" not in text:
        text = text.replace(resume, resume + "\n        stopBookingAlarm(); // v1.0.5 new-booking alarm acknowledgement", 1)
    elif resume not in text and "stopBookingAlarm(); // v1.0.5" not in text:
        on_new = "    @Override protected void onNewIntent(Intent intent) {"
        addition = '''    @Override protected void onResume() {
        super.onResume();
        stopBookingAlarm(); // v1.0.5 new-booking alarm acknowledgement
    }

'''
        if on_new in text: text = text.replace(on_new, addition + on_new, 1)
        else: text = text.replace(marker, addition + marker, 1)
    p.write_text(text)


def main() -> None:
    # IMPORTANT: patch ONLY the isolated generated Staff/Therapist copy. Other APK projects remain untouched.
    project = Path("BuddhasStaffNewTherapist")
    patch_staff_manifest(project)
    add_alarm_service(project, "com.buddhaspalm.staff")
    patch_fcm_service(project)
    patch_main(project)
    print("Continuous new-booking alarm v1.0.5 patched", project)


if __name__ == "__main__":
    main()
