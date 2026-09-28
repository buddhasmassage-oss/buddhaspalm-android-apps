package net.buddhaspalm.tutor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

public class ScreenRecordService extends Service {
    public static final String ACTION_START = "net.buddhaspalm.tutor.action.START_SCREEN_RECORDING";
    public static final String ACTION_STOP = "net.buddhaspalm.tutor.action.STOP_SCREEN_RECORDING";
    public static final String ACTION_RECORDING_STATE = "net.buddhaspalm.tutor.action.SCREEN_RECORDING_STATE";
    public static final String ACTION_RECORDING_RESULT = "net.buddhaspalm.tutor.action.SCREEN_RECORDING_RESULT";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_RECORDING = "recording";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_ID = "buddhastudy_screen_recording";
    private static final int NOTIFICATION_ID = 9204;
    private static volatile boolean recording = false;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;
    private ParcelFileDescriptor outputPfd;
    private Uri outputUri;
    private File legacyOutputFile;
    private String outputFilename = "";
    private boolean stopping = false;

    public static boolean isRecording() { return recording; }

    public static Intent startIntent(Context context, int resultCode, Intent resultData) {
        return new Intent(context, ScreenRecordService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData);
    }

    public static Intent stopIntent(Context context) {
        return new Intent(context, ScreenRecordService.class).setAction(ACTION_STOP);
    }

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopRecording(true, null);
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        if (recording) {
            broadcastState(true);
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData;
        if (Build.VERSION.SDK_INT >= 33) resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        else resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);

        if (resultData == null) {
            broadcastResult(false, "", "", "Android did not return screen-capture permission.");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundCompat();
        try {
            startProjection(resultCode, resultData);
        } catch (Exception e) {
            stopRecording(false, e.getMessage() == null ? "Could not start screen recording." : e.getMessage());
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "BuddhaStudy Screen Recording", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shown while an administrator records the BuddhaStudy screen.");
        nm.createNotificationChannel(channel);
    }

    private Notification notification() {
        Intent open = new Intent(this, TutorWebActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("BuddhaStudy screen recording")
                .setContentText("Recording in progress • return to the app to stop")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pi)
                .build();
    }

    private void startForegroundCompat() {
        Notification n = notification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void startProjection(int resultCode, Intent resultData) throws Exception {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) throw new IllegalStateException("MediaProjection service is unavailable.");
        mediaProjection = manager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) throw new IllegalStateException("Android screen-capture permission was not granted.");

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = Math.max(2, metrics.widthPixels);
        int height = Math.max(2, metrics.heightPixels);
        width = width - (width % 2);
        height = height - (height % 2);
        int density = Math.max(1, metrics.densityDpi);

        outputFilename = "BuddhaStudy-screen-record-" +
                new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".mp4";

        mediaRecorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(Math.min(12_000_000, Math.max(4_000_000, width * height * 4)));
        mediaRecorder.setVideoSize(width, height);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, outputFilename);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BuddhaStudy Tutor");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            outputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (outputUri == null) throw new IllegalStateException("Could not create the MP4 file.");
            outputPfd = getContentResolver().openFileDescriptor(outputUri, "w");
            if (outputPfd == null) throw new IllegalStateException("Could not open the MP4 file.");
            mediaRecorder.setOutputFile(outputPfd.getFileDescriptor());
        } else {
            File root = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (root == null) throw new IllegalStateException("Movies storage is unavailable.");
            File dir = new File(root, "BuddhaStudy Tutor");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create the recording folder.");
            legacyOutputFile = new File(dir, outputFilename);
            mediaRecorder.setOutputFile(legacyOutputFile.getAbsolutePath());
        }

        mediaRecorder.prepare();
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "BuddhaStudyTutorRecorder",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                if (!stopping) stopRecording(true, null);
            }
        }, null);

        mediaRecorder.start();
        recording = true;
        broadcastState(true);
    }

    private void stopRecording(boolean userRequested, @Nullable String failure) {
        if (stopping) return;
        stopping = true;
        boolean success = failure == null && (recording || userRequested);
        String error = failure;

        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); }
            catch (RuntimeException e) {
                success = false;
                if (error == null) error = "Recording was too short or Android could not finish the MP4.";
            }
            try { mediaRecorder.reset(); } catch (Exception ignored) {}
            try { mediaRecorder.release(); } catch (Exception ignored) {}
            mediaRecorder = null;
        }
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) {}
            mediaProjection = null;
        }
        if (outputPfd != null) {
            try { outputPfd.close(); } catch (Exception ignored) {}
            outputPfd = null;
        }

        String uriText = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outputUri != null) {
            if (success) {
                try {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Video.Media.IS_PENDING, 0);
                    getContentResolver().update(outputUri, done, null, null);
                    uriText = outputUri.toString();
                } catch (Exception e) {
                    success = false;
                    error = "Could not finalize the MP4 file.";
                }
            }
            if (!success) {
                try { getContentResolver().delete(outputUri, null, null); } catch (Exception ignored) {}
            }
        } else if (legacyOutputFile != null) {
            if (success) uriText = Uri.fromFile(legacyOutputFile).toString();
            else try { legacyOutputFile.delete(); } catch (Exception ignored) {}
        }

        recording = false;
        broadcastState(false);
        broadcastResult(success, uriText, outputFilename, error == null ? "" : error);
        stopForegroundCompat();
        stopSelf();
        stopping = false;
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
        } catch (Exception ignored) {}
    }

    private void broadcastState(boolean on) {
        Intent i = new Intent(ACTION_RECORDING_STATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_RECORDING, on);
        sendBroadcast(i);
    }

    private void broadcastResult(boolean success, String uri, String filename, String error) {
        Intent i = new Intent(ACTION_RECORDING_RESULT)
                .setPackage(getPackageName())
                .putExtra(EXTRA_SUCCESS, success)
                .putExtra(EXTRA_URI, uri == null ? "" : uri)
                .putExtra(EXTRA_FILENAME, filename == null ? "" : filename)
                .putExtra(EXTRA_ERROR, error == null ? "" : error);
        sendBroadcast(i);
    }

    @Override public void onDestroy() {
        if (recording && !stopping) stopRecording(true, null);
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
