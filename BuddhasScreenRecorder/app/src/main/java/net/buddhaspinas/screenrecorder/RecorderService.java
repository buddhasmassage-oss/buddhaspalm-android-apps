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
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecorderService extends Service {
    public static final String ACTION_START = "net.buddhaspinas.screenrecorder.START";
    public static final String ACTION_PAUSE = "net.buddhaspinas.screenrecorder.PAUSE";
    public static final String ACTION_RESUME = "net.buddhaspinas.screenrecorder.RESUME";
    public static final String ACTION_STOP = "net.buddhaspinas.screenrecorder.STOP";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_DENSITY = "density";
    public static final String EXTRA_MIC = "microphone";

    private static final String CHANNEL_ID = "buddhas_recorder_capture";
    private static final int NOTIFICATION_ID = 2411;

    private MediaProjection projection;
    private MediaRecorder recorder;
    private VirtualDisplay virtualDisplay;
    private File tempFile;
    private boolean recording = false;
    private boolean paused = false;
    private boolean stopping = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            startCapture(intent);
        } else if (ACTION_PAUSE.equals(action)) {
            pauseCapture();
        } else if (ACTION_RESUME.equals(action)) {
            resumeCapture();
        } else if (ACTION_STOP.equals(action)) {
            stopCapture(true);
        }
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void startCapture(Intent intent) {
        if (recording) return;
        createNotificationChannel();
        boolean withMic = intent.getBooleanExtra(EXTRA_MIC, true);
        startCaptureForeground(withMic, "Recording your screen");

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        int sourceWidth = intent.getIntExtra(EXTRA_WIDTH, 1080);
        int sourceHeight = intent.getIntExtra(EXTRA_HEIGHT, 1920);
        int density = intent.getIntExtra(EXTRA_DENSITY, 420);

        if (resultData == null) {
            Toast.makeText(this, "Screen capture permission data is missing.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    if (!stopping) stopCapture(true);
                }
            }, null);

            int[] size = fitSize(sourceWidth, sourceHeight);
            int width = size[0];
            int height = size[1];

            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "BuddhasScreenRecorder");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Cannot create recording folder");
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            tempFile = new File(dir, "Buddhas_Screen_" + stamp + ".mp4");

            recorder = new MediaRecorder();
            if (withMic) recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(tempFile.getAbsolutePath());
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(width, height);
            recorder.setVideoFrameRate(30);
            recorder.setVideoEncodingBitRate(calculateBitrate(width, height));
            if (withMic) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setAudioEncodingBitRate(128000);
                recorder.setAudioSamplingRate(44100);
            }
            recorder.prepare();

            virtualDisplay = projection.createVirtualDisplay(
                    "BuddhasScreenRecorder",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    recorder.getSurface(),
                    null,
                    null
            );
            recorder.start();
            recording = true;
            paused = false;
            updateNotification("Recording • tap Stop when finished");
        } catch (Exception e) {
            cleanupRecorder();
            Toast.makeText(this, "Unable to start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void pauseCapture() {
        if (!recording || paused || recorder == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder.pause();
                paused = true;
                updateNotification("Recording paused");
            } catch (Exception ignored) {
            }
        }
    }

    private void resumeCapture() {
        if (!recording || !paused || recorder == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder.resume();
                paused = false;
                updateNotification("Recording resumed");
            } catch (Exception ignored) {
            }
        }
    }

    private void stopCapture(boolean publish) {
        if (stopping) return;
        stopping = true;
        boolean hadRecording = recording;
        recording = false;
        paused = false;

        if (recorder != null) {
            try {
                if (hadRecording) recorder.stop();
            } catch (Exception ignored) {
            }
        }
        cleanupRecorder();

        File completed = tempFile;
        tempFile = null;
        if (publish && completed != null && completed.exists() && completed.length() > 0) {
            publishVideo(completed);
        }
        stopForeground(true);
        stopSelf();
        stopping = false;
    }

    private void cleanupRecorder() {
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
        if (projection != null) {
            try { projection.stop(); } catch (Exception ignored) {}
            projection = null;
        }
    }

    private void publishVideo(File source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, source.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Buddhas Screen Recorder");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (FileInputStream in = new FileInputStream(source);
                     OutputStream out = resolver.openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("Cannot open media destination");
                    byte[] buffer = new byte[1024 * 256];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    out.flush();
                    values.clear();
                    values.put(MediaStore.Video.Media.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                    source.delete();
                    Toast.makeText(this, "MP4 saved to Movies/Buddhas Screen Recorder", Toast.LENGTH_LONG).show();
                    return;
                } catch (Exception e) {
                    resolver.delete(uri, null, null);
                }
            }
        }

        MediaScannerConnection.scanFile(this,
                new String[]{source.getAbsolutePath()},
                new String[]{"video/mp4"},
                null);
        Toast.makeText(this, "MP4 saved: " + source.getAbsolutePath(), Toast.LENGTH_LONG).show();
    }

    private int[] fitSize(int width, int height) {
        int maxSide = Math.max(width, height);
        if (maxSide > 1920) {
            float scale = 1920f / maxSide;
            width = Math.round(width * scale);
            height = Math.round(height * scale);
        }
        width = Math.max(2, width - (width % 2));
        height = Math.max(2, height - (height % 2));
        return new int[]{width, height};
    }

    private int calculateBitrate(int width, int height) {
        long pixels = (long) width * (long) height;
        if (pixels >= 1920L * 1080L) return 8_000_000;
        if (pixels >= 1280L * 720L) return 5_000_000;
        return 3_000_000;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen recording",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows while Buddhas Screen Recorder is capturing your screen");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, RecorderService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 44, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 45, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(net.buddhaspinas.screenrecorder.R.drawable.ic_buddha)
                .setContentTitle("Buddhas Screen Recorder")
                .setContentText(text)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "Stop", stopPending).build())
                .build();
    }

    private void startCaptureForeground(boolean withMic, String text) {
        Notification notification = buildNotification(text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (withMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NOTIFICATION_ID, notification, types);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        if (recording && !stopping) stopCapture(true);
        super.onDestroy();
    }
}
