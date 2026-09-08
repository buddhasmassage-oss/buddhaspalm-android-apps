package net.buddhaspinas.screenrecorder;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.widget.ImageView;

public class OverlayService extends Service {
    private WindowManager wm; private ImageView ball;
    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        ball=new ImageView(this);
        ball.setImageResource(R.drawable.ic_buddha);
        ball.setPadding(6,6,6,6);
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(88,88,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; lp.x=16; lp.y=220;
        ball.setOnClickListener(v->{ Intent i=new Intent(this,MainActivity.class); i.setAction("ACTION_REQUEST_CAPTURE"); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP); startActivity(i); });
        ball.setOnLongClickListener(v->{ stopService(new Intent(this,RecorderService.class).setAction(RecorderService.ACTION_STOP)); return true; });
        ball.setOnTouchListener(new View.OnTouchListener(){ float sx,sy; int ox,oy;
            public boolean onTouch(View v, MotionEvent e){
                if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();ox=lp.x;oy=lp.y;return false;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){lp.x=ox+(int)(e.getRawX()-sx);lp.y=oy+(int)(e.getRawY()-sy);wm.updateViewLayout(ball,lp);return true;}
                return false;
            }});
        wm.addView(ball,lp);
    }
    @Override public void onDestroy(){ if(wm!=null&&ball!=null) try{wm.removeView(ball);}catch(Exception ignored){} super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
