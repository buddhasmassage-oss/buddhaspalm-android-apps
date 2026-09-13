from pathlib import Path


def add_dependency(project):
    p=project/'app/build.gradle'; t=p.read_text()
    if 'firebase-messaging' not in t:
        t=t.replace('dependencies {', "dependencies {\n    implementation platform('com.google.firebase:firebase-bom:33.5.1')\n    implementation 'com.google.firebase:firebase-messaging'\n    implementation 'androidx.core:core:1.13.1'")
    p.write_text(t)


def add_service(project,pkg):
    d=project/'app/src/main/java'/Path(pkg.replace('.','/')); f=d/'BuddhasFirebaseMessagingService.java'
    f.write_text(f'''package {pkg};
import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.PendingIntent; import android.content.Intent; import android.os.Build;
import androidx.core.app.NotificationCompat; import com.google.firebase.messaging.FirebaseMessagingService; import com.google.firebase.messaging.RemoteMessage;
public class BuddhasFirebaseMessagingService extends FirebaseMessagingService {{
 private static final String CHANNEL="buddhas_staff_updates";
 @Override public void onNewToken(String token){{getSharedPreferences("MainActivity",MODE_PRIVATE).edit().putString("fcm_token",token==null?"":token).apply();}}
 @Override public void onMessageReceived(RemoteMessage msg){{
  String title=msg.getNotification()!=null&&msg.getNotification().getTitle()!=null?msg.getNotification().getTitle():"Buddhas Staff";
  String body=msg.getNotification()!=null&&msg.getNotification().getBody()!=null?msg.getNotification().getBody():"You have a new update."; String url=msg.getData().get("url");
  Intent i=new Intent(this,MainActivity.class); if(url!=null&&url.startsWith("https://staff.buddhaspalm.shop/"))i.setData(android.net.Uri.parse(url)); i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26){{NotificationChannel c=new NotificationChannel(CHANNEL,"Buddhas Staff Updates",NotificationManager.IMPORTANCE_HIGH);c.enableVibration(true);nm.createNotificationChannel(c);}}
  nm.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE),new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi).build());
 }}
}}
''')
    mp=project/'app/src/main/AndroidManifest.xml'; m=mp.read_text()
    if 'BuddhasFirebaseMessagingService' not in m:
        m=m.replace('</application>','''        <service android:name=".BuddhasFirebaseMessagingService" android:exported="false"><intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter></service>\n    </application>''')
    mp.write_text(m)


def patch(name,is_admin):
    project=Path(name); p=next((project/'app/src/main/java').rglob('MainActivity.java')); t=p.read_text(); pkg=t.splitlines()[0].replace('package ','').replace(';','').strip(); role='admin' if is_admin else 'staff'
    if 'com.google.firebase.FirebaseApp' not in t:
        imports='import com.google.firebase.FirebaseApp;\nimport com.google.firebase.FirebaseOptions;\nimport com.google.firebase.messaging.FirebaseMessaging;\nimport org.json.JSONObject;\n'
        t=t.replace('import org.json.JSONTokener;\n','import org.json.JSONTokener;\n'+imports) if 'import org.json.JSONTokener;' in t else t.replace('public class MainActivity',imports+'\npublic class MainActivity')
    if 'private String fcmToken' not in t:
        t=t.replace('    private WebView webView;\n','    private WebView webView;\n    private String fcmToken="";\n    private volatile String lastRegisteredFcmToken="";\n    private volatile boolean firebaseConfigLoading=false;\n')
    if 'ensureFirebaseReadyAndRegister();' not in t:
        t=t.replace('        configureWebView();\n','        configureWebView();\n        fcmToken=getPreferences(MODE_PRIVATE).getString("fcm_token","");\n        publishFcmTokenToPage();\n        ensureFirebaseReadyAndRegister();\n',1)
    target='                CookieManager.getInstance().flush();\n'
    if 'publishFcmTokenToPage();\n                ensureFirebaseReadyAndRegister();' not in t:
        t=t.replace(target,target+'                publishFcmTokenToPage();\n                ensureFirebaseReadyAndRegister();\n',1)
    if 'private void ensureFirebaseReadyAndRegister()' not in t:
        anchor='    private void restoreNativePushIdentity() {' if '    private void restoreNativePushIdentity() {' in t else '    private void loadInitialUrl(Intent intent) {'
        helper=f'''    private void publishFcmTokenToPage(){{if(webView==null||fcmToken==null||fcmToken.isEmpty())return;String a=JSONObject.quote(fcmToken),b=JSONObject.quote("{role}"),c=JSONObject.quote(getPackageName());webView.post(()->webView.evaluateJavascript("window.BP_NATIVE_FCM_TOKEN="+a+";window.dispatchEvent(new CustomEvent('bp:native-fcm-token',{{detail:{{token:"+a+",role:"+b+",packageName:"+c+"}}}}));",null));}}
    private void ensureFirebaseReadyAndRegister(){{if(!FirebaseApp.getApps(this).isEmpty()){{FirebaseMessaging.getInstance().getToken().addOnSuccessListener(x->{{fcmToken=x==null?"":x.trim();if(!fcmToken.isEmpty()){{getPreferences(MODE_PRIVATE).edit().putString("fcm_token",fcmToken).apply();publishFcmTokenToPage();registerFcmTokenWithServer();}}}});return;}}fetchFirebaseConfigFromServer();}}
    private void fetchFirebaseConfigFromServer(){{if(firebaseConfigLoading)return;final String base="https://staff.buddhaspalm.shop/api/fcm-config.php",cookie=CookieManager.getInstance().getCookie(base);if(cookie==null||cookie.trim().isEmpty())return;firebaseConfigLoading=true;new Thread(()->{{HttpURLConnection con=null;try{{String endpoint=base+"?package_name="+URLEncoder.encode(getPackageName(),"UTF-8");con=(HttpURLConnection)new URL(endpoint).openConnection();con.setRequestMethod("GET");con.setConnectTimeout(10000);con.setReadTimeout(12000);con.setRequestProperty("Cookie",cookie);con.setRequestProperty("X-BP-Native-FCM","1");int code=con.getResponseCode();String response=readAll(code>=200&&code<400?con.getInputStream():con.getErrorStream());if(code>=200&&code<300){{JSONObject j=new JSONObject(response);if(j.optBoolean("ok")){{FirebaseOptions o=new FirebaseOptions.Builder().setApplicationId(j.getString("application_id")).setApiKey(j.getString("api_key")).setProjectId(j.getString("project_id")).setGcmSenderId(j.getString("sender_id")).build();runOnUiThread(()->{{try{{if(FirebaseApp.getApps(MainActivity.this).isEmpty())FirebaseApp.initializeApp(MainActivity.this,o);ensureFirebaseReadyAndRegister();}}catch(Throwable ignored){{}}}});}}}}}}catch(Throwable ignored){{}}finally{{firebaseConfigLoading=false;if(con!=null)con.disconnect();}}}}).start();}}
    private void registerFcmTokenWithServer(){{final String token=fcmToken==null?"":fcmToken.trim();if(token.isEmpty()||token.equals(lastRegisteredFcmToken))return;final String endpoint="https://staff.buddhaspalm.shop/api/fcm-register.php",cookie=CookieManager.getInstance().getCookie(endpoint);if(cookie==null||cookie.trim().isEmpty())return;new Thread(()->{{HttpURLConnection con=null;try{{con=(HttpURLConnection)new URL(endpoint).openConnection();con.setRequestMethod("POST");con.setConnectTimeout(10000);con.setReadTimeout(12000);con.setDoOutput(true);con.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");con.setRequestProperty("Cookie",cookie);con.setRequestProperty("X-BP-Native-FCM","1");String device=(android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL).trim(),body="token="+enc(token)+"&role="+enc("{role}")+"&package_name="+enc(getPackageName())+"&device_name="+enc(device);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);con.setFixedLengthStreamingMode(bytes.length);try(OutputStream os=con.getOutputStream()){{os.write(bytes);}}int code=con.getResponseCode();String response=readAll(code>=200&&code<400?con.getInputStream():con.getErrorStream());if(code>=200&&code<300&&response.contains("\\\"ok\\\":true"))lastRegisteredFcmToken=token;}}catch(Throwable ignored){{}}finally{{if(con!=null)con.disconnect();}}}}).start();}}

'''
        t=t.replace(anchor,helper+anchor)
    t=t.replace('BuddhasPalm-Admin-Android/1.5.0','BuddhasPalm-Admin-Android/1.0.2').replace('BuddhasPalm-Provider-Android/1.5.0','BuddhasPalm-Provider-Android/1.0.2')
    p.write_text(t);add_dependency(project);add_service(project,pkg);print('FCM patched',p,pkg)

patch('BuddhasStaffNewAdmin',True)
patch('BuddhasStaffNewTherapist',False)
