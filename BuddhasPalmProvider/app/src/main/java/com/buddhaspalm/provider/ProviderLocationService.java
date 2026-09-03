package com.buddhaspalm.provider;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ProviderLocationService extends Service implements LocationListener {
    public static final String ACTION_START = "com.buddhaspalm.provider.LOCATION_START";
    public static final String ACTION_STOP = "com.buddhaspalm.provider.LOCATION_STOP";
    public static final String PREFS = "bp_provider_location";
    public static final String PREF_ACTIVE = "active";
    public static final String PREF_COOKIE = "cookie";
    public static final String PREF_ENDPOINT = "endpoint";
    private static final String CHANNEL_ID = "provider_work_location";
    private static final int NOTIFICATION_ID = 15001;
    private static final long MIN_TIME_MS = 30000L;
    private static final float MIN_DISTANCE_M = 10f;

    private LocationManager locationManager;
    private SharedPreferences prefs;
    private volatile boolean sending = false;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopTracking();
            return START_NOT_STICKY;
        }
        if (intent != null) {
            String cookie = intent.getStringExtra(PREF_COOKIE);
            String endpoint = intent.getStringExtra(PREF_ENDPOINT);
            SharedPreferences.Editor ed = prefs.edit().putBoolean(PREF_ACTIVE, true);
            if (cookie != null && !cookie.trim().isEmpty()) ed.putString(PREF_COOKIE, cookie);
            if (endpoint != null && !endpoint.trim().isEmpty()) ed.putString(PREF_ENDPOINT, endpoint);
            ed.apply();
        }
        startAsForeground();
        startTracking();
        return START_STICKY;
    }

    private void startAsForeground() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle("Buddhas Palm Provider")
                .setContentText("On-Duty work location sharing is active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pending)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "On-Duty Location", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shows while provider work-location sharing is active.");
        ch.setShowBadge(false);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    @SuppressWarnings("MissingPermission")
    private void startTracking() {
        if (!prefs.getBoolean(PREF_ACTIVE, false)) return;
        try {
            if (locationManager == null) return;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, this);
            }
            Location last = null;
            try { last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (last == null) try { last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            if (last != null) sendLocation(last);
        } catch (SecurityException e) {
            stopTracking();
        }
    }

    private void stopTracking() {
        prefs.edit().putBoolean(PREF_ACTIVE, false).apply();
        try { if (locationManager != null) locationManager.removeUpdates(this); } catch (Exception ignored) {}
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null && prefs.getBoolean(PREF_ACTIVE, false)) sendLocation(location);
    }

    private void sendLocation(Location location) {
        if (sending) return;
        final String endpoint = prefs.getString(PREF_ENDPOINT, "https://metro.buddhaspinas.com/api/provider-duty.php");
        final String cookie = prefs.getString(PREF_COOKIE, "");
        if (cookie == null || cookie.trim().isEmpty()) return;
        sending = true;
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(endpoint).openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(12000);
                con.setReadTimeout(12000);
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                con.setRequestProperty("Cookie", cookie);
                con.setRequestProperty("X-BP-Native-Location", "1");
                con.setRequestProperty("User-Agent", "BuddhasPalm-Provider-Android/1.5.0");
                StringBuilder body = new StringBuilder();
                body.append("action=ping");
                body.append("&latitude=").append(enc(String.valueOf(location.getLatitude())));
                body.append("&longitude=").append(enc(String.valueOf(location.getLongitude())));
                body.append("&accuracy=").append(enc(location.hasAccuracy() ? String.valueOf(location.getAccuracy()) : ""));
                body.append("&speed=").append(enc(location.hasSpeed() ? String.valueOf(location.getSpeed()) : ""));
                body.append("&heading=").append(enc(location.hasBearing() ? String.valueOf(location.getBearing()) : ""));
                int battery = readBatteryPercent();
                if (battery >= 0) body.append("&battery=").append(enc(String.valueOf(battery)));
                body.append("&recorded_at=").append(enc(isoUtc(location.getTime() > 0 ? location.getTime() : System.currentTimeMillis())));
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = con.getOutputStream()) { os.write(bytes); }
                int code = con.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? con.getInputStream() : con.getErrorStream();
                readAll(stream);
                if (code == 401 || code == 403 || code == 422) stopTracking();
            } catch (Exception ignored) {
            } finally {
                if (con != null) con.disconnect();
                sending = false;
            }
        }, "bp-location-ping").start();
    }

    private int readBatteryPercent() {
        try {
            BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return -1;
            int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return pct >= 0 && pct <= 100 ? pct : -1;
        } catch (Exception e) { return -1; }
    }

    private static String isoUtc(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        try { if (locationManager != null) locationManager.removeUpdates(this); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
