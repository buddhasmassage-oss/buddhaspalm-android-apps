package net.buddhaspalm.tutor;

import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import java.util.Date;
import java.util.List;

public class DownloadsActivity extends FragmentActivity {
    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); render(); }
    private void render() {
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(NativeUi.BG);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(NativeUi.dp(this,18),NativeUi.dp(this,18),NativeUi.dp(this,18),NativeUi.dp(this,28));scroll.addView(page);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);Button back=NativeUi.button(this,"‹ Back",Color.rgb(241,245,249),NativeUi.TEXT);back.setOnClickListener(v->finish());top.addView(back);TextView title=NativeUi.text(this,"Course Downloads",21,NativeUi.TEXT,true);title.setPadding(NativeUi.dp(this,12),0,0,0);top.addView(title);page.addView(top);NativeUi.addSpace(page,12);
        Button system=NativeUi.button(this,"Open Android Downloads",NativeUi.BLUE,Color.WHITE);system.setOnClickListener(v->{try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(Exception ignored){}});page.addView(system);NativeUi.addSpace(page,12);
        List<DownloadStore.Item> items=DownloadStore.list(this);if(items.isEmpty()){LinearLayout e=NativeUi.card(this);e.addView(NativeUi.text(this,"No Tutor downloads recorded yet",16,NativeUi.TEXT,true));NativeUi.addSpace(e,5);e.addView(NativeUi.text(this,"Files downloaded from the learning portal will appear here.",13,NativeUi.MUTED,false));page.addView(e);}else{Button clear=NativeUi.button(this,"Clear History",Color.rgb(241,245,249),NativeUi.TEXT);clear.setOnClickListener(v->{DownloadStore.clear(this);render();});page.addView(clear);NativeUi.addSpace(page,10);for(DownloadStore.Item item:items){LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,item.title,15,NativeUi.TEXT,true));NativeUi.addSpace(c,5);c.addView(NativeUi.text(this,DateFormat.format("MMM d, yyyy • h:mm a",new Date(item.time)).toString(),11,Color.rgb(148,163,184),false));page.addView(c);NativeUi.addSpace(page,10);}}
        NativeUi.applyInsets(scroll); setContentView(scroll);
    }
}
