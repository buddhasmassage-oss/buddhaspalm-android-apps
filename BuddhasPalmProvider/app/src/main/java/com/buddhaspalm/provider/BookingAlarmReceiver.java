package com.buddhaspalm.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BookingAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && BookingAlarmService.ACTION_STOP.equals(intent.getAction())) BookingAlarmService.stop(context);
    }
}
