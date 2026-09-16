package net.buddhasprovider.admin;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class BuddhasFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        FcmRegistrar.register(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        String title = getString(R.string.app_name);
        String body = "";
        String url = "";
        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }
        if (message.getData() != null) {
            if (message.getData().containsKey("title") && !message.getData().get("title").isEmpty()) title = message.getData().get("title");
            if (message.getData().containsKey("body") && !message.getData().get("body").isEmpty()) body = message.getData().get("body");
            if (message.getData().containsKey("url")) url = message.getData().get("url");
        }
        NotificationHelper.show(getApplicationContext(), title, body, url);
    }
}
