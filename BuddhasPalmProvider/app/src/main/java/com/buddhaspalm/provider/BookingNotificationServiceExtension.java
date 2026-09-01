package com.buddhaspalm.provider;

import com.onesignal.notifications.IDisplayableMutableNotification;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.INotificationServiceExtension;
import org.json.JSONObject;

public class BookingNotificationServiceExtension implements INotificationServiceExtension {
    @Override public void onNotificationReceived(INotificationReceivedEvent event) {
        IDisplayableMutableNotification notification=event.getNotification();
        JSONObject data=notification.getAdditionalData();
        String type=data==null?"":data.optString("type","");
        boolean booking="new_booking".equalsIgnoreCase(type)||"booking".equalsIgnoreCase(type)||"booking_request".equalsIgnoreCase(type);
        if(!booking) return;
        int bookingId=data==null?0:data.optInt("booking_id",0);
        try{
            event.preventDefault();
            BookingAlarmService.start(event.getContext(),notification.getTitle(),notification.getBody(),bookingId);
        }catch(Throwable error){
            try{notification.display();}catch(Throwable ignored){}
        }
    }
}
