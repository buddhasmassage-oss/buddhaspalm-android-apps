package net.buddhaspinas.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecorderService extends Service {
    public static final String ACTION_START = "net.buddhaspinas.screenrecorder.START";
    public static final String ACTION_STOP = "net.buddhaspinas.screenrecorder.STOP";
    public static final String ACTION_PAUSE = "net.buddhaspinas.screenrecorder.PAUSE";
    public static final String ACTION_RESUME = "net.buddhaspinas.screenrecorder.RESUME";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_MIC = "mic";

    private static final String CHANNEL_ID = "buddhas_screen_recording";
    private static final int NOTIFICATION_ID = 1300;

    public static volatile boolean isRecording = false;
    public static volatile boolean isPaused = false;

    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private MediaRecorder recorder;
    private VirtualDisplay virtualDisplay;
    private ParcelFileDescriptor outputPfd;
    private Uri outputUri;
    private File legacyFile;
    private String outputName;
    private boolean stopping;
    private boolean usingMic;

    @Override public void onCreate() { super.onCreate(); createChannel(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            if (isRecording) return START_STICKY;
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            usingMic = intent.getBooleanExtra(EXTRA_MIC, false);
            startCaptureForeground(false);
            if (resultCode != 0 && resultData != null) startCapture(resultCode, resultData); else stopSelf();
        } else if (ACTION_STOP.equals(action)) stopCapture(true);
        else if (ACTION_PAUSE.equals(action)) pauseCapture();
        else if (ACTION_RESUME.equals(action)) resumeCapture();
        return START_STICKY;
    }

    private void startCaptureForeground(boolean paused) {
        Notification n = buildNotification(paused);
        if (Build.VERSION.SDK_INT >= 29) {
            int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (usingMic && Build.VERSION.SDK_INT >= 30) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(NOTIFICATION_ID, n, types);
        } else startForeground(NOTIFICATION_ID, n);
    }

    private void startCapture(int resultCode, Intent data) {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(resultCode, data);
            if (projection == null) throw new IllegalStateException("MediaProjection unavailable");
            projectionCallback = new MediaProjection.Callback() { @Override public void onStop() { stopCapture(false); } };
            projection.registerCallback(projectionCallback, new Handler(Looper.getMainLooper()));

            DisplayMetrics dm = new DisplayMetrics();
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Rect b = wm.getCurrentWindowMetrics().getBounds();
                dm.widthPixels = b.width(); dm.heightPixels = b.height(); dm.densityDpi = getResources().getDisplayMetrics().densityDpi;
            } else wm.getDefaultDisplay().getRealMetrics(dm);
            int width = Math.max(2, dm.widthPixels - (dm.widthPixels % 2));
            int height = Math.max(2, dm.heightPixels - (dm.heightPixels % 2));
            int density = Math.max(1, dm.densityDpi);

            prepareOutput();
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            if (usingMic) recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoEncodingBitRate(Math.max(6_000_000, width * height * 5));
            recorder.setVideoFrameRate(30);
            recorder.setVideoSize(width, height);
            if (usingMic) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setAudioEncodingBitRate(128_000);
                recorder.setAudioSamplingRate(44_100);
            }
            if (outputPfd != null) recorder.setOutputFile(outputPfd.getFileDescriptor());
            else if (legacyFile != null) recorder.setOutputFile(legacyFile.getAbsolutePath());
            else throw new IllegalStateException("No recording output");
            recorder.prepare();
            virtualDisplay = projection.createVirtualDisplay("BuddhasScreenRecorder", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.getSurface(), null, null);
            recorder.start();
            isRecording = true; isPaused = false;
            updateNotification();
            toast("Recording started — tap Stop when finished");
        } catch (Exception e) {
            cleanupFailedOutput(); releaseEverything(); toast("Could not start recording: " + safeMessage(e)); stopSelf();
        }
    }

    private void prepareOutput() throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        outputName = "Buddhas_Screen_" + stamp + ".mp4";
        ContentResolver cr = getContentResolver();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, outputName);
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Buddhas Screen Recorder");
            cv.put(MediaStore.Video.Media.IS_PENDING, 1);
            outputUri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (outputUri == null) throw new IllegalStateException("Cannot create recording file");
            outputPfd = cr.openFileDescriptor(outputUri, "rw");
            if (outputPfd == null) throw new IllegalStateException("Cannot open recording file");
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Buddhas Screen Recorder");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create recording folder");
            legacyFile = new File(dir, outputName);
        }
    }

    private synchronized void pauseCapture() {
        if (!isRecording || isPaused || recorder == null || Build.VERSION.SDK_INT < 24) return;
        try { recorder.pause(); isPaused = true; updateNotification(); }
        catch (Exception e) { toast("Pause failed: " + safeMessage(e)); }
    }

    private synchronized void resumeCapture() {
        if (!isRecording || !isPaused || recorder == null || Build.VERSION.SDK_INT < 24) return;
        try { recorder.resume(); isPaused = false; updateNotification(); }
        catch (Exception e) { toast("Resume failed: " + safeMessage(e)); }
    }

    public synchronized void stopCapture(boolean userInitiated) {
        if (stopping) return;
        stopping = true;
        boolean saved = false;
        if (recorder != null) {
            try { recorder.stop(); saved = true; } catch (Exception ignored) { saved = false; }
        }
        if (saved) finalizeOutput(); else cleanupFailedOutput();
        releaseEverything();
        isRecording = false; isPaused = false;
        try { stopService(new Intent(this, OverlayService.class)); } catch (Exception ignored) { }
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (saved) {
            toast("MP4 saved to Movies/Buddhas Screen Recorder");
            showSavedNotification();
            startCloudSyncIfPossible();
        } else if (userInitiated) toast("Recording could not be saved");
        stopSelf();
    }

    private void finalizeOutput() {
        try { if (outputPfd != null) outputPfd.close(); } catch (Exception ignored) { }
        outputPfd = null;
        if (Build.VERSION.SDK_INT >= 29 && outputUri != null) {
            try { ContentValues cv = new ContentValues(); cv.put(MediaStore.Video.Media.IS_PENDING, 0); getContentResolver().update(outputUri, cv, null, null); }
            catch (Exception ignored) { }
        }
    }

    private void cleanupFailedOutput() {
        try { if (outputPfd != null) outputPfd.close(); } catch (Exception ignored) { }
        outputPfd = null;
        try { if (outputUri != null) getContentResolver().delete(outputUri, null, null); } catch (Exception ignored) { }
        try { if (legacyFile != null && legacyFile.exists()) legacyFile.delete(); } catch (Exception ignored) { }
    }

    private void releaseEverything() {
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) { }
        virtualDisplay = null;
        try { if (recorder != null) recorder.reset(); } catch (Exception ignored) { }
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) { }
        recorder = null;
        try { if (projection != null && projectionCallback != null) projection.unregisterCallback(projectionCallback); } catch (Exception ignored) { }
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        projection = null; projectionCallback = null;
    }

    private void startCloudSyncIfPossible() {
        try {
            Uri uri = outputUri;
            if (uri == null && legacyFile != null) uri = Uri.fromFile(legacyFile);
            if (uri == null) return;
            Intent sync = new Intent(this, CloudSyncService.class)
                    .putExtra(CloudSyncService.EXTRA_URI, uri.toString())
                    .putExtra(CloudSyncService.EXTRA_NAME, outputName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(sync); else startService(sync);
        } catch (Exception ignored) { }
    }

    private Notification buildNotification(boolean paused) {
        PendingIntent content = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, RecorderService.class).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle = PendingIntent.getService(this, 3, new Intent(this, RecorderService.class).setAction(paused ? ACTION_RESUME : ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(net.buddhaspinas.screenrecorder.R.drawable.ic_buddha)
                .setContentTitle(paused ? "Buddhas recording paused" : "Buddhas Screen Recorder")
                .setContentText(paused ? "Tap Resume or Stop & Save" : "Whole-screen recording is active")
                .setContentIntent(content).setOngoing(true).setColor(Color.rgb(212, 175, 55))
                .addAction(new Notification.Action.Builder(0, paused ? "Resume" : "Pause", toggle).build())
                .addAction(new Notification.Action.Builder(0, "Stop & Save MP4", stop).build()).build();
    }

    private void updateNotification() { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification(isPaused)); }

    private void showSavedNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 9, new Intent(this, RecordingsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(net.buddhaspinas.screenrecorder.R.drawable.ic_buddha).setContentTitle("Recording saved")
                .setContentText("Tap to open My Phone Recordings").setContentIntent(pi).setAutoCancel(true).setColor(Color.rgb(212, 175, 55)).build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID + 1, n);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Buddhas screen-recording controls");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private void toast(String message) { new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()); }
    private static String safeMessage(Throwable t) { String s = t == null ? null : t.getMessage(); return s == null || s.trim().isEmpty() ? "Unknown error" : s; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { if (isRecording && !stopping) stopCapture(false); super.onDestroy(); }
}
