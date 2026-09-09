package com.buddhaspinas.buddhasride.provider;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProviderDutyService extends Service implements LocationListener {
    public static final String ACTION_START="com.buddhaspinas.buddhasride.provider.START_DUTY";
    public static final String ACTION_STOP="com.buddhaspinas.buddhasride.provider.STOP_DUTY";
    public static final String EXTRA_COOKIE="cookie";
    public static final String CHANNEL_DUTY="br_provider_duty";
    public static final String CHANNEL_JOBS="br_provider_jobs";
    public static final int DUTY_NOTIFICATION_ID=9101;
    public static final int JOB_NOTIFICATION_ID=9102;
    private static final String PREFS="br_provider_native";
    private static final String HOME="https://rider.buddhaspinas.com/provider/";
    private static final String LIVE="https://rider.buddhaspinas.com/api/provider-live-jobs.php";
    private static final String LOCATION="https://rider.buddhaspinas.com/api/provider-location.php";

    private ScheduledExecutorService scheduler;
    private LocationManager locationManager;
    private volatile Location lastLocation;
    private volatile long lastLocationPost=0;
    private volatile boolean polling=false;
    private String lastAlertKey="";

    @Override public void onCreate(){super.onCreate();ensureChannels(this);lastAlertKey=getSharedPreferences(PREFS,MODE_PRIVATE).getString("last_alert","");}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopDuty();return START_NOT_STICKY;}
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);String cookie=intent!=null?intent.getStringExtra(EXTRA_COOKIE):null;
        if(cookie!=null&&!cookie.trim().isEmpty())p.edit().putString("cookie",cookie).apply();
        p.edit().putBoolean("duty",true).apply();
        startForeground(DUTY_NOTIFICATION_ID,dutyNotification("Online • Waiting for ride requests",HOME));
        startLocationUpdates();startPolling();return START_STICKY;
    }

    private void startPolling(){if(scheduler!=null)return;scheduler=Executors.newSingleThreadScheduledExecutor();scheduler.scheduleWithFixedDelay(this::poll,0,4,TimeUnit.SECONDS);}
    private void poll(){if(polling)return;polling=true;try{
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);if(!p.getBoolean("duty",false))return;String cookie=p.getString("cookie","");if(cookie.isEmpty())return;
        Location loc=lastLocation;if(loc!=null&&System.currentTimeMillis()-lastLocationPost>5000){postLocation(cookie,loc);lastLocationPost=System.currentTimeMillis();}
        String raw=request("GET",LIVE,null,cookie);if(raw==null||raw.isEmpty())return;JSONObject j=new JSONObject(raw);if(!j.optBoolean("ok"))return;
        if(!j.optBoolean("online",true)){p.edit().putBoolean("duty",false).apply();stopSelf();return;}
        JSONObject active=j.optJSONObject("active_ride");if(active!=null){String url=HOME+"ride-job.php?id="+active.optInt("id");updateDuty("Active ride • "+human(active.optString("status")),url);}else{
            JSONObject af=j.optJSONObject("active_food");if(af!=null)updateDuty("Active food delivery • "+human(af.optString("status")),HOME+"food-job.php?id="+af.optInt("id"));else updateDuty("Online • Waiting for ride requests",HOME+"jobs.php");
        }
        JSONArray rides=j.optJSONArray("rides");if(rides!=null&&rides.length()>0){JSONObject r=rides.optJSONObject(0);if(r!=null)alertRide(r);}else{JSONArray food=j.optJSONArray("food");if(food!=null&&food.length()>0){JSONObject o=food.optJSONObject(0);if(o!=null)alertFood(o);}}
    }catch(Exception ignored){}finally{polling=false;}}

    private void postLocation(String cookie,Location l){try{if(l.getLatitude()<4.40||l.getLatitude()>21.30||l.getLongitude()<116.80||l.getLongitude()>126.80)return;String body="lat="+enc(String.valueOf(l.getLatitude()))+"&lng="+enc(String.valueOf(l.getLongitude()))+"&heading="+enc(String.valueOf(l.hasBearing()?l.getBearing():0))+"&speed="+enc(String.valueOf(l.hasSpeed()?l.getSpeed():0));request("POST",LOCATION,body,cookie);}catch(Exception ignored){}}

    private void alertRide(JSONObject r){String key="ride:"+r.optInt("id");if(key.equals(lastAlertKey))return;lastAlertKey=key;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last_alert",key).apply();String title="New Buddhas Ride request";String route=r.optString("pickup_name",r.optString("pickup_address","Pickup"))+" → "+r.optString("dropoff_address","Drop-off");String detail=route+" • ₱"+String.format(java.util.Locale.US,"%.2f",r.optDouble("fare",0))+" • "+String.format(java.util.Locale.US,"%.1f",r.optDouble("dispatch_distance",0))+" km";showJob(title,detail,HOME+"jobs.php");}
    private void alertFood(JSONObject o){String key="food:"+o.optInt("id");if(key.equals(lastAlertKey))return;lastAlertKey=key;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last_alert",key).apply();showJob("New food delivery",o.optString("restaurant_name","Restaurant")+" → "+o.optString("delivery_address","Delivery address"),HOME+"jobs.php");}

    private void showJob(String title,String message,String url){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);Notification.Builder b=builder(CHANNEL_JOBS);b.setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(openIntent(url,JOB_NOTIFICATION_ID)).setAutoCancel(true).setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_MESSAGE).setVibrate(new long[]{0,300,120,300,120,500});if(Build.VERSION.SDK_INT<26)b.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS);nm.notify(JOB_NOTIFICATION_ID,b.build());}
    private Notification dutyNotification(String text,String url){Notification.Builder b=builder(CHANNEL_DUTY);return b.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Buddhas Ride Provider • Online").setContentText(text).setContentIntent(openIntent(url,DUTY_NOTIFICATION_ID)).setOngoing(true).setOnlyAlertOnce(true).setPriority(Notification.PRIORITY_LOW).build();}
    private void updateDuty(String text,String url){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(DUTY_NOTIFICATION_ID,dutyNotification(text,url));}
    private Notification.Builder builder(String channel){return Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);}
    private PendingIntent openIntent(String url,int code){Intent i=new Intent(this,MainActivity.class);i.setData(Uri.parse(url));i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);return PendingIntent.getActivity(this,code,i,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));}

    private void startLocationUpdates(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        try{locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,3000,3f,this,Looper.getMainLooper());if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000,5f,this,Looper.getMainLooper());Location g=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),n=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);lastLocation=g!=null?g:n;}catch(Exception ignored){}
    }
    @Override public void onLocationChanged(Location location){if(location!=null)lastLocation=location;}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    private String request(String method,String target,String body,String cookie){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(target).openConnection();c.setRequestMethod(method);c.setConnectTimeout(9000);c.setReadTimeout(9000);c.setRequestProperty("Cookie",cookie);c.setRequestProperty("User-Agent","BuddhasRideProviderAndroid/1.0.0");c.setRequestProperty("Accept","application/json");c.setUseCaches(false);if(body!=null){c.setDoOutput(true);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream os=c.getOutputStream()){os.write(bytes);}}int code=c.getResponseCode();InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();if(in==null)return "";StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}return sb.toString();}catch(Exception e){return "";}finally{if(c!=null)c.disconnect();}}
    private String enc(String v)throws Exception{return URLEncoder.encode(v==null?"":v,"UTF-8");}
    private String human(String s){return s==null?"":s.replace('_',' ');}

    private void stopDuty(){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("duty",false).apply();if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(Exception ignored){}if(scheduler!=null){scheduler.shutdownNow();scheduler=null;}stopForeground(true);stopSelf();}
    @Override public void onDestroy(){if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(Exception ignored){}if(scheduler!=null){scheduler.shutdownNow();scheduler=null;}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}

    public static void ensureChannels(Context c){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel duty=new NotificationChannel(CHANNEL_DUTY,"Provider Online",NotificationManager.IMPORTANCE_LOW);duty.setDescription("Keeps Buddhas Ride Provider online and receiving requests.");nm.createNotificationChannel(duty);NotificationChannel jobs=new NotificationChannel(CHANNEL_JOBS,"New Ride Requests",NotificationManager.IMPORTANCE_HIGH);jobs.setDescription("Immediate ride and delivery request alerts.");jobs.enableVibration(true);jobs.setVibrationPattern(new long[]{0,300,120,300,120,500});nm.createNotificationChannel(jobs);}
}
