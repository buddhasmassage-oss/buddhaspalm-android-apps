package net.buddhaspinas.screenrecorder;

import android.app.*;
import android.content.*;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.WindowManager;
import java.io.FileDescriptor;

public class RecorderService extends Service {
    public static final String ACTION_START="ACTION_START", ACTION_STOP="ACTION_STOP";
    private static final String CHANNEL="buddhas_recorder";
    private MediaProjection projection;
    private MediaRecorder recorder;

    @Override public void onCreate() { super.onCreate(); createChannel(); }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) { stopRecording(); return START_NOT_STICKY; }
        if (ACTION_START.equals(intent.getAction())) startRecording(intent);
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL,"Screen recording",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification notification() {
        Intent stop = new Intent(this, RecorderService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this,CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("Buddhas Screen Recorder").setContentText("Recording your screen")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Stop",pi).build()).build();
    }

    @SuppressWarnings("deprecation")
    private void startRecording(Intent intent) {
        try {
            startForeground(77, notification());
            int code = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra("resultData");
            boolean mic = intent.getBooleanExtra("mic", true);
            MediaProjectionManager pm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=pm.getMediaProjection(code,data);
            projection.registerCallback(new MediaProjection.Callback(){ @Override public void onStop(){ stopRecording(); } }, new Handler(Looper.getMainLooper()));

            WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);
            Point p=new Point(); wm.getDefaultDisplay().getRealSize(p);
            int width=p.x, height=p.y;
            int max=1280;
            if (Math.max(width,height)>max) {
                float r=max/(float)Math.max(width,height); width=(int)(width*r); height=(int)(height*r);
                width-=width%2; height-=height%2;
            }
            int density=getResources().getDisplayMetrics().densityDpi;

            ContentValues v=new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME,"Buddhas_Screen_"+System.currentTimeMillis()+".mp4");
            v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
            if (Build.VERSION.SDK_INT>=29) v.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Buddhas Screen Recorder");
            Uri outputUri=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);
            FileDescriptor fd=getContentResolver().openFileDescriptor(outputUri,"w").getFileDescriptor();

            recorder=new MediaRecorder();
            if (mic) recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (mic) { recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setAudioEncodingBitRate(128000); recorder.setAudioSamplingRate(44100); }
            recorder.setVideoSize(width,height);
            recorder.setVideoFrameRate(30);
            recorder.setVideoEncodingBitRate(6_000_000);
            recorder.setOutputFile(fd);
            recorder.prepare();
            projection.createVirtualDisplay("BuddhasRecorder",width,height,density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,recorder.getSurface(),null,null);
            recorder.start();
        } catch (Exception e) { stopRecording(); }
    }

    private void stopRecording() {
        try { if (recorder!=null) recorder.stop(); } catch(Exception ignored) {}
        try { if (recorder!=null) recorder.release(); } catch(Exception ignored) {}
        try { if (projection!=null) projection.stop(); } catch(Exception ignored) {}
        recorder=null; projection=null;
        stopForeground(true); stopSelf();
    }
    @Override public android.os.IBinder onBind(Intent i){ return null; }
}
