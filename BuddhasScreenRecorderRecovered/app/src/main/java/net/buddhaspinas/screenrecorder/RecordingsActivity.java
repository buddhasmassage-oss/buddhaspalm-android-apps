package net.buddhaspinas.screenrecorder;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.*;

public class RecordingsActivity extends Activity {
    @Override public void onCreate(Bundle b){ super.onCreate(b); render(); }
    private void render(){
        ScrollView scroll=new ScrollView(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,24,24,24); scroll.addView(box);
        TextView h=new TextView(this); h.setText("My Phone Recordings"); h.setTextSize(24); h.setPadding(0,0,0,20); box.addView(h);
        String[] cols={MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.SIZE};
        try(Cursor c=getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cols,MediaStore.Video.Media.RELATIVE_PATH+" LIKE ?",new String[]{"%Buddhas Screen Recorder%"},MediaStore.Video.Media.DATE_ADDED+" DESC")){
            if(c!=null) while(c.moveToNext()){
                long id=c.getLong(0); String name=c.getString(1); Uri uri=ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,id);
                Button v=new Button(this); v.setText(name); v.setAllCaps(false); v.setOnClickListener(x->{ Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i); }); box.addView(v,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        } catch(Exception e){ TextView t=new TextView(this); t.setText("Could not read phone recordings: "+e.getMessage()); box.addView(t); }
        setContentView(scroll);
    }
}
