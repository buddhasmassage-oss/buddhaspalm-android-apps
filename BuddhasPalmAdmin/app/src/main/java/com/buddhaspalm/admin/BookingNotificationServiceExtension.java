package com.buddhaspalm.admin;

import androidx.annotation.Keep;

import com.onesignal.notifications.IDisplayableMutableNotification;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.INotificationServiceExtension;

import org.json.JSONObject;

@Keep
public class BookingNotificationServiceExtension implements INotificationServiceExtension {
    @Override
    public void onNotificationReceived(INotificationReceivedEvent event) {
        IDisplayableMutableNotification notification = event.getNotification();
        JSONObject data = notification.getAdditionalData();
        String type = data == null ? "" : data.optString("type", "");
        boolean booking = "new_booking".equalsIgnoreCase(type)
                || "booking".equalsIgnoreCase(type)
                || "booking_request".equalsIgnoreCase(type);

        // IMPORTANT: never call preventDefault() here. OneSignal must always be
        // allowed to display the push even if Android refuses to start the
        // continuous alarm service in the background.
        if (!booking) return;

        int bookingId = data == null ? 0 : data.optInt("booking_id", 0);
        try {
            BookingAlarmService.start(
                    event.getContext(),
                    notification.getTitle(),
                    notification.getBody(),
                    bookingId
            );
        } catch (Throwable ignored) {
            // OneSignal still displays the original notification.
        }
    }
}
