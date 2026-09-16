package net.buddhasprovider.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_FILE=1201,REQ_NOTIFICATIONS=1202;
    private WebView webView;private ProgressBar progress;private ValueCallback<Uri[]> fileCallback;private String fcmToken="";
    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);getWindow().setStatusBarColor(Color.rgb(6,43,84));getWindow().setNavigationBarColor(Color.rgb(6,43,84));RelativeLayout root=new RelativeLayout(this);root.setBackgroundColor(Color.rgb(6,43,84));webView=new WebView(this);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(228,184,78)));RelativeLayout.LayoutParams wp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT);RelativeLayout.LayoutParams pp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,6);pp.addRule(RelativeLayout.ALIGN_PARENT_TOP);root.addView(webView,wp);root.addView(progress,pp);setContentView(root);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}NotificationHelper.ensureChannel(this);configureWebView();requestNotificationPermission();loadFcmToken();if(savedInstanceState!=null)webView.restoreState(savedInstanceState);else loadInitialUrl(getIntent());}
    private void configureWebView(){WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(false);s.setUserAgentString(s.getUserAgentString()+" BuddhasTraining-Android/1.0.0");if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)s.setSafeBrowsingEnabled(true);CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(webView,false);webView.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri uri=request.getUrl();if(uri==null)return false;String scheme=uri.getScheme(),host=uri.getHost();if("https".equalsIgnoreCase(scheme)&&getString(R.string.allowed_host).equalsIgnoreCase(host))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException ignored){}return true;}@Override public void onPageFinished(WebView view,String url){super.onPageFinished(view,url);CookieManager.getInstance().flush();registerCurrentToken();}});webView.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView view,int newProgress){progress.setProgress(newProgress);progress.setVisibility(newProgress>=100?View.GONE:View.VISIBLE);}@Override public boolean onShowFileChooser(WebView webView,ValueCallback<Uri[]> callback,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;Intent intent=params.createIntent();try{startActivityForResult(intent,REQ_FILE);return true;}catch(ActivityNotFoundException e){fileCallback=null;return false;}}});}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);}
    private void loadFcmToken(){FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task->{if(!task.isSuccessful()||task.getResult()==null)return;fcmToken=task.getResult();registerCurrentToken();});}
    private void registerCurrentToken(){if(fcmToken==null||fcmToken.trim().isEmpty())return;FcmRegistrar.register(getApplicationContext(),fcmToken);}
    private void loadInitialUrl(Intent intent){Uri data=intent!=null?intent.getData():null;if(data!=null&&"https".equalsIgnoreCase(data.getScheme())&&getString(R.string.allowed_host).equalsIgnoreCase(data.getHost()))webView.loadUrl(data.toString());else webView.loadUrl(getString(R.string.start_url));}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);loadInitialUrl(intent);}
    @Override protected void onSaveInstanceState(Bundle outState){webView.saveState(outState);super.onSaveInstanceState(outState);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_FILE&&fileCallback!=null){Uri[] results=WebChromeClient.FileChooserParams.parseResult(resultCode,data);fileCallback.onReceiveValue(results);fileCallback=null;}}
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}

final class FcmRegistrar{
    private FcmRegistrar(){}
    static void register(Context context,String token){if(context==null||token==null||token.trim().isEmpty())return;final String clean=token.trim(),endpoint=context.getString(R.string.fcm_register_url);final String cookie;try{cookie=CookieManager.getInstance().getCookie(endpoint);}catch(Throwable ignored){return;}if(cookie==null||cookie.trim().isEmpty())return;new Thread(()->{HttpURLConnection con=null;try{con=(HttpURLConnection)new URL(endpoint).openConnection();con.setRequestMethod("POST");con.setConnectTimeout(10000);con.setReadTimeout(10000);con.setDoOutput(true);con.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");con.setRequestProperty("Cookie",cookie);con.setRequestProperty("X-BP-FCM","1");con.setRequestProperty("User-Agent","BuddhasTraining-Android/1.0.0");String device=Build.MANUFACTURER+" "+Build.MODEL;String body="token="+enc(clean)+"&package_name="+enc(context.getPackageName())+"&device_name="+enc(device)+"&platform=android";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);con.setFixedLengthStreamingMode(bytes.length);try(OutputStream os=con.getOutputStream()){os.write(bytes);}int code=con.getResponseCode();InputStream stream=code>=200&&code<400?con.getInputStream():con.getErrorStream();readAll(stream);}catch(Exception ignored){}finally{if(con!=null)con.disconnect();}},"BuddhasTraining-FCM-Register").start();}
    private static String enc(String value)throws Exception{return URLEncoder.encode(value==null?"":value,"UTF-8");}
    private static String readAll(InputStream in)throws Exception{if(in==null)return"";StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}return sb.toString();}
}

final class NotificationHelper{
    static final String CHANNEL_ID="buddhas_training_updates";private NotificationHelper(){}
    static void ensureChannel(Context context){if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return;NotificationManager m=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(m==null||m.getNotificationChannel(CHANNEL_ID)!=null)return;NotificationChannel c=new NotificationChannel(CHANNEL_ID,context.getString(R.string.notification_channel_name),NotificationManager.IMPORTANCE_HIGH);c.setDescription(context.getString(R.string.notification_channel_description));c.enableVibration(true);c.enableLights(true);c.setLightColor(Color.rgb(228,184,78));m.createNotificationChannel(c);}
    static void show(Context context,String title,String body,String url){ensureChannel(context);Intent intent=new Intent(context,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(url!=null&&!url.trim().isEmpty())try{intent.setData(Uri.parse(url.trim()));}catch(Exception ignored){}int request=(int)(System.currentTimeMillis()&0x7fffffff),flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)flags|=PendingIntent.FLAG_IMMUTABLE;PendingIntent pi=PendingIntent.getActivity(context,request,intent,flags);android.app.Notification.Builder b;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)b=new android.app.Notification.Builder(context,CHANNEL_ID);else{b=new android.app.Notification.Builder(context);b.setPriority(android.app.Notification.PRIORITY_HIGH);}b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title==null||title.isEmpty()?context.getString(R.string.app_name):title).setContentText(body==null?"":body).setStyle(new android.app.Notification.BigTextStyle().bigText(body==null?"":body)).setAutoCancel(true).setContentIntent(pi);NotificationManager m=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(m!=null)m.notify(request,b.build());}
}
