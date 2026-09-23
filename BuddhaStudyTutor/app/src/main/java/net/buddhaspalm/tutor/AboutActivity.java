package net.buddhaspalm.tutor;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

public class AboutActivity extends FragmentActivity {
    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); build(); }
    private void build(){ScrollView s=new ScrollView(this);s.setBackgroundColor(NativeUi.BG);LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(NativeUi.dp(this,18),NativeUi.dp(this,18),NativeUi.dp(this,18),NativeUi.dp(this,30));s.addView(p);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=NativeUi.button(this,"‹ Back",Color.rgb(241,245,249),NativeUi.TEXT);back.setOnClickListener(v->finish());top.addView(back);TextView title=NativeUi.text(this,"About BuddhaStudy",21,NativeUi.TEXT,true);title.setPadding(NativeUi.dp(this,12),0,0,0);top.addView(title);p.addView(top);NativeUi.addSpace(p,14);LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,"BuddhaStudy Tutor",22,NativeUi.TEXT,true));NativeUi.addSpace(c,4);c.addView(NativeUi.text(this,"Version "+versionName()+" • "+getPackageName(),12,NativeUi.MUTED,false));NativeUi.addSpace(c,14);c.addView(NativeUi.text(this,"Native Android capabilities",15,NativeUi.TEXT,true));NativeUi.addSpace(c,6);c.addView(NativeUi.text(this,"• Biometric/fingerprint credential protection\n• Firebase push notifications with native inbox\n• Native file chooser and Android downloads\n• Native dashboard and navigation\n• Offline/retry handling for the learning portal\n• Android share and external-link handling",13,NativeUi.MUTED,false));NativeUi.addSpace(c,14);Button site=NativeUi.button(this,"Official Tutor Website",NativeUi.BLUE,Color.WHITE);site.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://tutor.buddhaspalm.net/")));}catch(Exception ignored){}});c.addView(site);p.addView(c);NativeUi.applyInsets(s); setContentView(s);}
    private String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "1.0.9";}}
}
