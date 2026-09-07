package net.buddhaspalm.school;

import android.app.Application;

public class SchoolApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SchoolFirebaseConfigManager.initializeFromCache(this);
        SchoolFirebaseMessagingService.ensureNotificationChannel(this);
    }
}
