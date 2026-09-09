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
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProviderDutyService extends Service implements LocationListener {
    public static final String ACTION_START="com.buddhaspinas.buddhasride.provider.START_DUTY";
    public static final String ACTION_STOP="com.buddhaspinas.buddhasride.provider.STOP_DUTY";
    public static final String EXTRA_COOKIE="cookie";
    public static final String CHANNEL_DUTY="br_provider_duty";
    public static final String CHANNEL_JOBS="br_provider_jobs";
    public static final String CHANNEL_RIDES="br_provider_rides";
    public static final String CHANNEL_FOOD="br_provider_food";
    public static final int DUTY_NOTIFICATION_ID=9101;
    public static final int JOB_NOTIFICATION_ID=9102;
    private static final String PREFS="br_provider_native";
    private static final String HOME="https://rider.buddhaspinas.com/provider/";
    private static final String LIVE="https://rider.buddhaspinas.com/api/provider-live-jobs.php";
    private static final String LOCATION="https://rider.buddhaspinas.com/api/provider-location.php";
    private static final String ACTIONS="https://rider.buddhaspinas.com/api/provider-action.php";
    private static final int MAX_OFFLINE_ITEMS=120;

    private ScheduledExecutorService scheduler;
    private LocationManager locationManager;
    private volatile Location lastLocation;
    private volatile long lastLocationPost=0;
    private volatile boolean polling=false;
    private volatile int activeBookingId=0;
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

    public static void enqueueAction(Context c,String kind,int id,String action,String pin){
        if(c==null||id<=0||action==null||action.trim().isEmpty())return;
        try{SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray a=new JSONArray(p.getString("offline_actions","[]"));JSONObject x=new JSONObject();x.put("kind",kind==null?"ride":kind);x.put("id",id);x.put("action",action);x.put("pin",pin==null?"":pin);x.put("queued_at",System.currentTimeMillis());a.put(x);a=trimQueue(a);p.edit().putString("offline_actions",a.toString()).apply();}catch(Exception ignored){}
    }
    public static int offlineQueueCount(Context c){try{SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);return new JSONArray(p.getString("offline_actions","[]")).length()+new JSONArray(p.getString("offline_locations","[]")).length();}catch(Exception e){return 0;}}
    private static JSONArray trimQueue(JSONArray a){if(a.length()<=MAX_OFFLINE_ITEMS)return a;JSONArray out=new JSONArray();for(int i=Math.max(0,a.length()-MAX_OFFLINE_ITEMS);i<a.length();i++)out.put(a.opt(i));return out;}
    private void enqueueLocation(Location l){try{SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);JSONArray a=new JSONArray(p.getString("offline_locations","[]"));JSONObject x=new JSONObject();x.put("lat",l.getLatitude());x.put("lng",l.getLongitude());x.put("heading",l.hasBearing()?l.getBearing():0);x.put("speed",l.hasSpeed()?l.getSpeed():0);x.put("accuracy",l.hasAccuracy()?l.getAccuracy():0);x.put("booking_id",activeBookingId);x.put("queued_at",System.currentTimeMillis());a.put(x);p.edit().putString("offline_locations",trimQueue(a).toString()).apply();}catch(Exception ignored){}
    }

    private void startPolling(){if(scheduler!=null)return;scheduler=Executors.newSingleThreadScheduledExecutor();scheduler.scheduleWithFixedDelay(this::poll,0,4,TimeUnit.SECONDS);}
    private void poll(){if(polling)return;polling=true;try{
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);if(!p.getBoolean("duty",false))return;String cookie=p.getString("cookie","");if(cookie.isEmpty())return;
        int synced=flushOffline(cookie);if(synced>0)showSyncNotification(synced);
        Location loc=lastLocation;if(loc!=null&&System.currentTimeMillis()-lastLocationPost>5000){postLocation(cookie,loc);lastLocationPost=System.currentTimeMillis();}
        String raw=request("GET",LIVE,null,cookie);if(raw==null||raw.isEmpty())return;JSONObject j=new JSONObject(raw);if(!j.optBoolean("ok"))return;
        if(!j.optBoolean("online",true)){p.edit().putBoolean("duty",false).apply();stopSelf();return;}
        JSONObject active=j.optJSONObject("active_ride");if(active!=null){activeBookingId=active.optInt("id");String url=HOME+"ride-job.php?id="+activeBookingId;updateDuty("Active ride • "+human(active.optString("status")),url);}else{activeBookingId=0;JSONObject af=j.optJSONObject("active_food");if(af!=null)updateDuty("Active food delivery • "+human(af.optString("status")),HOME+"food-job.php?id="+af.optInt("id"));else updateDuty("Online • Waiting for ride requests",HOME+"jobs.php");}
        JSONArray safety=j.optJSONArray("safety");if(safety!=null&&safety.length()>0){JSONObject e=safety.optJSONObject(0);if(e!=null&&"emergency".equals(e.optString("severity")))showJob(CHANNEL_JOBS,22001,"Buddhas Ride Safety Alert",e.optString("message","Open the provider app."),activeBookingId>0?HOME+"ride-job.php?id="+activeBookingId:HOME);}
        JSONArray next=j.optJSONArray("next_rides");JSONObject queued=j.optJSONObject("queued_ride");if(active!=null&&queued==null&&next!=null&&next.length()>0){JSONObject n=next.optJSONObject(0);if(n!=null)alertNext(n);}
        JSONArray rides=j.optJSONArray("rides");if(active==null&&rides!=null){for(int i=0;i<Math.min(5,rides.length());i++){JSONObject r=rides.optJSONObject(i);if(r!=null)alertRide(r);}}
        JSONArray food=j.optJSONArray("food");if(active==null&&food!=null){for(int i=0;i<Math.min(5,food.length());i++){JSONObject o=food.optJSONObject(i);if(o!=null)alertFood(o);}}
    }catch(Exception ignored){}finally{polling=false;}}

    private void postLocation(String cookie,Location l){try{if(l.getLatitude()<4.40||l.getLatitude()>21.30||l.getLongitude()<116.80||l.getLongitude()>126.80)return;String body=locationBody(l,activeBookingId);String raw=request("POST",LOCATION,body,cookie);if(!isOk(raw))enqueueLocation(l);}catch(Exception e){enqueueLocation(l);}}
    private String locationBody(Location l,int bookingId) throws Exception {return "lat="+enc(String.valueOf(l.getLatitude()))+"&lng="+enc(String.valueOf(l.getLongitude()))+"&heading="+enc(String.valueOf(l.hasBearing()?l.getBearing():0))+"&speed="+enc(String.valueOf(l.hasSpeed()?l.getSpeed():0))+"&accuracy="+enc(String.valueOf(l.hasAccuracy()?l.getAccuracy():0))+(bookingId>0?"&booking_id="+bookingId:"");}
    private int flushOffline(String cookie){int synced=0;try{SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);JSONArray actions=new JSONArray(p.getString("offline_actions","[]"));JSONArray remainA=new JSONArray();boolean blocked=false;for(int i=0;i<actions.length();i++){JSONObject x=actions.optJSONObject(i);if(x==null)continue;if(blocked){remainA.put(x);continue;}String body="kind="+enc(x.optString("kind","ride"))+"&id="+x.optInt("id")+"&action="+enc(x.optString("action"));String pin=x.optString("pin","");if(!pin.isEmpty())body+="&verification_pin="+enc(pin);String raw=request("POST",ACTIONS,body,cookie);if(isOk(raw))synced++;else{blocked=true;remainA.put(x);}}p.edit().putString("offline_actions",remainA.toString()).apply();
        JSONArray locs=new JSONArray(p.getString("offline_locations","[]"));JSONArray remainL=new JSONArray();blocked=false;for(int i=0;i<locs.length();i++){JSONObject x=locs.optJSONObject(i);if(x==null)continue;if(blocked){remainL.put(x);continue;}String body="lat="+enc(x.optString("lat"))+"&lng="+enc(x.optString("lng"))+"&heading="+enc(x.optString("heading"))+"&speed="+enc(x.optString("speed"))+"&accuracy="+enc(x.optString("accuracy"));if(x.optInt("booking_id")>0)body+="&booking_id="+x.optInt("booking_id");String raw=request("POST",LOCATION,body,cookie);if(isOk(raw))synced++;else{blocked=true;remainL.put(x);}}p.edit().putString("offline_locations",remainL.toString()).apply();
    }catch(Exception ignored){}return synced;}
    private boolean isOk(String raw){if(raw==null||raw.isEmpty())return false;try{return new JSONObject(raw).optBoolean("ok",false);}catch(Exception e){return false;}}

    private void alertRide(JSONObject r){int id=r.optInt("id");String key="ride:"+id;if(!shouldAlert(key))return;String name=r.optString("ride_name",r.optString("service_type","Ride"));String route=r.optString("pickup_name",r.optString("pickup_address","Pickup"))+" → "+r.optString("dropoff_address","Drop-off");String detail=name+" • "+route+" • ₱"+String.format(Locale.US,"%.2f",r.optDouble("fare",0))+" • "+String.format(Locale.US,"%.1f",r.optDouble("dispatch_distance",0))+" km to pickup";showJob(CHANNEL_RIDES,12000+(id%7000),"New "+name+" request",detail,HOME+"jobs.php");}
    private void alertNext(JSONObject r){int id=r.optInt("id");String key="next:"+id;if(!shouldAlert(key))return;String detail=String.format(Locale.US,"%.1f",r.optDouble("distance_from_dropoff_km",0))+" km from current drop-off • ₱"+String.format(Locale.US,"%.2f",r.optDouble("fare",0));showJob(CHANNEL_RIDES,19000+(id%2000),"Next Ride Available",detail,HOME);}
    private void alertFood(JSONObject o){int id=o.optInt("id");String key="food:"+id;if(!shouldAlert(key))return;String detail=o.optString("restaurant_name","Restaurant")+" → "+o.optString("delivery_address","Delivery address")+" • "+String.format(Locale.US,"%.1f",o.optDouble("pickup_distance_km",0))+" km to restaurant";showJob(CHANNEL_FOOD,15000+(id%7000),"New food delivery",detail,HOME+"food-orders.php");}
    private boolean shouldAlert(String key){try{SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);JSONArray a=new JSONArray(p.getString("seen_alerts","[]"));for(int i=0;i<a.length();i++)if(key.equals(a.optString(i)))return false;a.put(key);JSONArray keep=new JSONArray();for(int i=Math.max(0,a.length()-80);i<a.length();i++)keep.put(a.opt(i));p.edit().putString("seen_alerts",keep.toString()).putString("last_alert",key).apply();lastAlertKey=key;return true;}catch(Exception e){if(key.equals(lastAlertKey))return false;lastAlertKey=key;return true;}}

    private void showSyncNotification(int count){showJob(CHANNEL_JOBS,JOB_NOTIFICATION_ID,"Connection restored",count+" offline trip update"+(count==1?"":"s")+" synchronized.",activeBookingId>0?HOME+"ride-job.php?id="+activeBookingId:HOME);}
    private void showJob(String channel,int notificationId,String title,String message,String url){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);Notification.Builder b=builder(channel);b.setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(openIntent(url,notificationId)).setAutoCancel(true).setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_MESSAGE).setVibrate(new long[]{0,300,120,300,120,500});if(Build.VERSION.SDK_INT<26)b.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS);nm.notify(notificationId,b.build());}
    private Notification dutyNotification(String text,String url){Notification.Builder b=builder(CHANNEL_DUTY);return b.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Buddhas Ride Provider • Online").setContentText(text).setContentIntent(openIntent(url,DUTY_NOTIFICATION_ID)).setOngoing(true).setOnlyAlertOnce(true).setPriority(Notification.PRIORITY_LOW).build();}
    private void updateDuty(String text,String url){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(DUTY_NOTIFICATION_ID,dutyNotification(text,url));}
    private Notification.Builder builder(String channel){return Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);}
    private PendingIntent openIntent(String url,int code){Intent i=new Intent(this,MainActivity.class);i.setData(Uri.parse(url));i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);return PendingIntent.getActivity(this,code,i,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));}

    private void startLocationUpdates(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;try{locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,3000,3f,this,Looper.getMainLooper());if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000,5f,this,Looper.getMainLooper());Location g=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),n=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);lastLocation=g!=null?g:n;}catch(Exception ignored){}}
    @Override public void onLocationChanged(Location location){if(location!=null)lastLocation=location;}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    private String request(String method,String target,String body,String cookie){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(target).openConnection();c.setRequestMethod(method);c.setConnectTimeout(9000);c.setReadTimeout(9000);c.setRequestProperty("Cookie",cookie);c.setRequestProperty("User-Agent","BuddhasRideProviderAndroid/1.2.0");c.setRequestProperty("Accept","application/json");c.setUseCaches(false);if(body!=null){c.setDoOutput(true);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream os=c.getOutputStream()){os.write(bytes);}}int code=c.getResponseCode();InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();if(in==null)return "";StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}return code>=200&&code<300?sb.toString():null;}catch(Exception e){return null;}finally{if(c!=null)c.disconnect();}}
    private String enc(String s) throws Exception{return URLEncoder.encode(s==null?"":s,"UTF-8");}
    private String human(String s){return s==null?"":s.replace('_',' ');}
    private void stopDuty(){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("duty",false).apply();if(scheduler!=null){scheduler.shutdownNow();scheduler=null;}if(locationManager!=null){try{locationManager.removeUpdates(this);}catch(Exception ignored){}locationManager=null;}stopForeground(true);stopSelf();}
    @Override public void onDestroy(){if(scheduler!=null)scheduler.shutdownNow();if(locationManager!=null){try{locationManager.removeUpdates(this);}catch(Exception ignored){}}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}

    public static void ensureChannels(Context c){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel duty=new NotificationChannel(CHANNEL_DUTY,"Provider Online Tracking",NotificationManager.IMPORTANCE_LOW);duty.setDescription("Keeps Buddhas Ride Provider online and sends live GPS while on duty.");nm.createNotificationChannel(duty);NotificationChannel jobs=new NotificationChannel(CHANNEL_JOBS,"Provider Safety & Sync",NotificationManager.IMPORTANCE_HIGH);jobs.enableVibration(true);jobs.setVibrationPattern(new long[]{0,300,120,300,120,500});jobs.setDescription("Safety alerts and connection synchronization.");nm.createNotificationChannel(jobs);NotificationChannel rides=new NotificationChannel(CHANNEL_RIDES,"Ride / Car / Parcel Requests",NotificationManager.IMPORTANCE_HIGH);rides.enableVibration(true);rides.setVibrationPattern(new long[]{0,300,120,300,120,500});rides.setDescription("Incoming Moto, Car, Taxi, parcel and next-ride requests from rider.buddhaspinas.com.");nm.createNotificationChannel(rides);NotificationChannel food=new NotificationChannel(CHANNEL_FOOD,"Food Delivery Requests",NotificationManager.IMPORTANCE_HIGH);food.enableVibration(true);food.setVibrationPattern(new long[]{0,250,100,250,100,450});food.setDescription("Incoming Buddhas Food delivery bookings.");nm.createNotificationChannel(food);}
}
