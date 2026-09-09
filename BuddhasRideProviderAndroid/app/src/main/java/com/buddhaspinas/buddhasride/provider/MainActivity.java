package com.buddhaspinas.buddhasride.provider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://rider.buddhaspinas.com/provider/";
    private static final String HOST = "rider.buddhaspinas.com";
    private static final int REQ_LOCATION = 3201;
    private static final int REQ_FILE = 3202;
    private static final int REQ_NOTIFICATIONS = 3203;

    private WebView webView;
    private View splashOverlay;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureBars();
        ensureNotificationChannels();
        webView = findViewById(R.id.webView);
        splashOverlay = findViewById(R.id.splashOverlay);
        configureWebView();
        requestEssentialPermissions();
        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(resolveStartUrl(getIntent()));
    }

    private void configureBars() {
        Window w=getWindow();
        w.setStatusBarColor(Color.rgb(23,36,214));
        w.setNavigationBarColor(Color.WHITE);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            w.setDecorFitsSystemWindows(true);
            WindowInsetsController c=w.getInsetsController();
            if(c!=null)c.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    private void configureWebView(){
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString()+" BuddhasRideProviderAndroid/1.0.0");
        CookieManager cm=CookieManager.getInstance();cm.setAcceptCookie(true);try{cm.setAcceptThirdPartyCookies(webView,true);}catch(Throwable ignored){}
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.addJavascriptInterface(new ProviderBridge(),"BuddhasRideProviderAndroid");
        webView.setWebViewClient(new ProviderClient());
        webView.setWebChromeClient(new ProviderChrome());
    }

    private class ProviderClient extends WebViewClient{
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){return handleUri(request.getUrl());}
        @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return handleUri(Uri.parse(url));}
        @Override public void onPageFinished(WebView view,String url){
            CookieManager.getInstance().flush();
            if(splashOverlay!=null&&splashOverlay.getVisibility()==View.VISIBLE){splashOverlay.animate().alpha(0f).setDuration(200).withEndAction(()->splashOverlay.setVisibility(View.GONE)).start();}
            try{view.evaluateJavascript("(function(){document.documentElement.style.webkitTextSizeAdjust='100%';window.BuddhasRideProviderNative=true;window.dispatchEvent(new Event('br:provider-native-ready'));})();",null);}catch(Throwable ignored){}
        }
        @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
            if(request.isForMainFrame()){if(splashOverlay!=null)splashOverlay.setVisibility(View.GONE);Toast.makeText(MainActivity.this,"Unable to load Buddhas Ride Provider. Check your internet connection.",Toast.LENGTH_LONG).show();}
        }
    }

    private class ProviderChrome extends WebChromeClient{
        @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){
            if(origin==null||!origin.startsWith("https://"+HOST)){callback.invoke(origin,false,false);return;}
            boolean ok=Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            if(ok)callback.invoke(origin,true,true);else{geoOrigin=origin;geoCallback=callback;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);}
        }
        @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
            if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
            Intent i=params.createIntent();i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
            try{startActivityForResult(i,REQ_FILE);return true;}catch(ActivityNotFoundException e){fileCallback=null;return false;}
        }
    }

    private boolean handleUri(Uri uri){
        if(uri==null)return true;String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.US);String host=uri.getHost();
        if(("https".equals(scheme)||"http".equals(scheme))&&HOST.equalsIgnoreCase(host))return false;
        if("https".equals(scheme)||"http".equals(scheme)||"tel".equals(scheme)||"mailto".equals(scheme)||"sms".equals(scheme)||"geo".equals(scheme)||"market".equals(scheme)||"intent".equals(scheme)){
            try{Intent i="intent".equals(scheme)?Intent.parseUri(uri.toString(),Intent.URI_INTENT_SCHEME):new Intent(Intent.ACTION_VIEW,uri);startActivity(i);}catch(Exception ignored){}return true;
        }
        return true;
    }

    private String resolveStartUrl(Intent intent){
        if(intent!=null){Uri d=intent.getData();if(d!=null&&"https".equalsIgnoreCase(d.getScheme())&&HOST.equalsIgnoreCase(d.getHost()))return d.toString();String url=intent.getStringExtra("url");if(url!=null){Uri u=Uri.parse(url);if("https".equalsIgnoreCase(u.getScheme())&&HOST.equalsIgnoreCase(u.getHost()))return u.toString();}}
        return HOME_URL;
    }

    private void startDutyService(){
        String cookie=CookieManager.getInstance().getCookie(HOME_URL);if(cookie==null||cookie.trim().isEmpty())return;
        Intent i=new Intent(this,ProviderDutyService.class);i.setAction(ProviderDutyService.ACTION_START);i.putExtra(ProviderDutyService.EXTRA_COOKIE,cookie);
        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Throwable e){Toast.makeText(this,"Unable to start Online provider receiver.",Toast.LENGTH_SHORT).show();}
    }
    private void stopDutyService(){Intent i=new Intent(this,ProviderDutyService.class);i.setAction(ProviderDutyService.ACTION_STOP);try{startService(i);}catch(Throwable e){stopService(i);}}

    public class ProviderBridge{
        @JavascriptInterface public void setDuty(boolean active){runOnUiThread(()->{if(active)startDutyService();else stopDutyService();});}
        @JavascriptInterface public void notifyJob(String title,String message,String url){runOnUiThread(()->showLocalJobNotification(title,message,url));}
        @JavascriptInterface public String getAppVersion(){return "1.0.0";}
        @JavascriptInterface public String getPlatform(){return "android-provider";}
    }

    private void ensureNotificationChannels(){ProviderDutyService.ensureChannels(this);}
    private void showLocalJobNotification(String title,String message,String url){
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);Intent i=new Intent(this,MainActivity.class);i.setData(Uri.parse(url!=null&&url.startsWith("https://"+HOST)?url:HOME_URL+"jobs.php"));
            PendingIntent pi=PendingIntent.getActivity(this,9102,i,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
            android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(this,ProviderDutyService.CHANNEL_JOBS):new android.app.Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle(title==null?"New Buddhas Ride request":title).setContentText(message==null?"Open Provider to view and accept.":message).setStyle(new android.app.Notification.BigTextStyle().bigText(message)).setContentIntent(pi).setAutoCancel(true).setPriority(android.app.Notification.PRIORITY_MAX).setVibrate(new long[]{0,260,120,260,120,420});nm.notify(ProviderDutyService.JOB_NOTIFICATION_ID,b.build());
        }catch(Throwable ignored){}
    }

    private void requestEssentialPermissions(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_LOCATION&&geoCallback!=null){boolean ok=false;for(int r:grantResults)if(r==PackageManager.PERMISSION_GRANTED){ok=true;break;}geoCallback.invoke(geoOrigin,ok,ok);geoCallback=null;geoOrigin=null;}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_FILE&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode,data));fileCallback=null;}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(webView!=null)webView.loadUrl(resolveStartUrl(intent));}
    @Override protected void onSaveInstanceState(Bundle out){if(webView!=null)webView.saveState(out);super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}
