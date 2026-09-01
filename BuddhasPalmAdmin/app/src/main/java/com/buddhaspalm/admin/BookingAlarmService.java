package com.buddhaspalm.admin;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class BookingAlarmService extends Service {
    public static final String ACTION_START="com.buddhaspalm.admin.BOOKING_ALARM_START";
    public static final String ACTION_STOP="com.buddhaspalm.admin.BOOKING_ALARM_STOP";
    private static final int NOTIFICATION_ID=62170;
    private MediaPlayer player; private Vibrator vibrator; private AudioManager audioManager; private AudioFocusRequest focusRequest;

    public static void start(Context c,String title,String body,int bookingId){
        Intent i=new Intent(c,BookingAlarmService.class).setAction(ACTION_START)
                .putExtra("title",title==null?"New Booking Request":title)
                .putExtra("body",body==null?"A booking needs your response.":body)
                .putExtra("booking_id",bookingId);
        c.startForegroundService(i);
    }
    public static void stop(Context c){ c.stopService(new Intent(c,BookingAlarmService.class)); }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        OneSignalManager.createBookingNotificationChannel(this);
        String title=intent==null?null:intent.getStringExtra("title");
        String body=intent==null?null:intent.getStringExtra("body");
        int bookingId=intent==null?0:intent.getIntExtra("booking_id",0);
        if(title==null||title.trim().isEmpty()) title="New Booking Request";
        if(body==null||body.trim().isEmpty()) body="A booking needs your response.";
        startForeground(NOTIFICATION_ID,buildNotification(title,body,bookingId));
        startAlarm();
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String title,String body,int bookingId){
        Intent open=new Intent(this,BookingAlarmOpenActivity.class)
                .setData(Uri.parse("https://metro.buddhaspinas.com/notifications.php"))
                .putExtra("booking_id",bookingId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi=PendingIntent.getActivity(this,1001,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent ack=new Intent(this,BookingAlarmReceiver.class).setAction(ACTION_STOP);
        PendingIntent ackPi=PendingIntent.getBroadcast(this,1002,ack,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,OneSignalManager.BOOKING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.buddhas_palm_exact_v133))
                .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX).setOngoing(true).setAutoCancel(false)
                .setContentIntent(openPi).addAction(0,"ACKNOWLEDGE",ackPi).setNumber(1).build();
    }

    private void startAlarm(){
        if(player!=null&&player.isPlaying()) return;
        try{
            AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
            if(audioManager!=null){ focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build(); audioManager.requestAudioFocus(focusRequest); }
            player=MediaPlayer.create(this,R.raw.buddhas_booking_alarm,attrs,0);
            if(player!=null){ player.setLooping(true); player.setVolume(1f,1f); player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK); player.start(); }
        }catch(Throwable ignored){}
        try{
            vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(vibrator!=null&&vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,700,250,700,250,1200,500},0));
        }catch(Throwable ignored){}
    }

    @Override public void onDestroy(){
        try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Throwable ignored){}
        try{if(player!=null){if(player.isPlaying())player.stop();player.release();}}catch(Throwable ignored){}
        try{if(vibrator!=null)vibrator.cancel();}catch(Throwable ignored){}
        try{if(audioManager!=null&&focusRequest!=null)audioManager.abandonAudioFocusRequest(focusRequest);}catch(Throwable ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
