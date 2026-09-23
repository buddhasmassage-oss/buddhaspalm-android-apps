package net.buddhaspalm.tutor;

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

public class NotificationInboxActivity extends FragmentActivity {
    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); render(); }

    private void render() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(NativeUi.BG);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(NativeUi.dp(this,18),NativeUi.dp(this,18),NativeUi.dp(this,18),NativeUi.dp(this,28));
        scroll.addView(page);
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = NativeUi.button(this, "‹ Back", Color.rgb(241,245,249), NativeUi.TEXT); back.setOnClickListener(v -> finish());
        top.addView(back); TextView title = NativeUi.text(this, "Notification Inbox", 21, NativeUi.TEXT, true); title.setPadding(NativeUi.dp(this,12),0,0,0); top.addView(title);
        page.addView(top); NativeUi.addSpace(page,14);
        List<NotificationStore.Item> items = NotificationStore.list(this);
        if (items.isEmpty()) {
            LinearLayout empty = NativeUi.card(this); empty.addView(NativeUi.text(this,"No saved notifications yet",16,NativeUi.TEXT,true)); NativeUi.addSpace(empty,5); empty.addView(NativeUi.text(this,"New BuddhaStudy push notifications will also be saved here.",13,NativeUi.MUTED,false)); page.addView(empty);
        } else {
            Button clear = NativeUi.button(this,"Clear Inbox",Color.rgb(254,242,242),Color.rgb(185,28,28)); clear.setOnClickListener(v->{NotificationStore.clear(this);render();}); page.addView(clear); NativeUi.addSpace(page,12);
            for (NotificationStore.Item item : items) {
                LinearLayout c = NativeUi.card(this); c.addView(NativeUi.text(this,item.title,15,NativeUi.TEXT,true)); NativeUi.addSpace(c,4); c.addView(NativeUi.text(this,item.body,13,NativeUi.MUTED,false)); NativeUi.addSpace(c,7);
                String when = item.time > 0 ? DateFormat.format("MMM d, yyyy • h:mm a", new Date(item.time)).toString() : ""; c.addView(NativeUi.text(this,when,11,Color.rgb(148,163,184),false));
                if (item.url != null && item.url.startsWith("https://tutor.buddhaspalm.net/")) { NativeUi.addSpace(c,10); Button open=NativeUi.button(this,"Open in Tutor",Color.rgb(238,242,255),Color.rgb(55,48,163)); open.setOnClickListener(v->{Intent i=new Intent(this,TutorWebActivity.class);i.putExtra("url",item.url);startActivity(i);});c.addView(open); }
                page.addView(c); NativeUi.addSpace(page,10);
            }
        }
        NativeUi.applyInsets(scroll); setContentView(scroll);
    }
}
