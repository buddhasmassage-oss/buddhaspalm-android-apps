package com.buddhaspalm.provider;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class BookingAlarmOpenActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BookingAlarmService.stop(this);
        int bookingId=getIntent()!=null?getIntent().getIntExtra("booking_id",0):0;
        Intent open=new Intent(this,MainActivity.class)
                .setData(Uri.parse("https://metro.buddhaspinas.com/notifications.php"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(bookingId>0) open.putExtra("booking_id",bookingId);
        startActivity(open);
        finish();
    }
}
