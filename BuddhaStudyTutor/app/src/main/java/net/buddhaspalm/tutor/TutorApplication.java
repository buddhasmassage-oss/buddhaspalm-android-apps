package net.buddhaspalm.tutor;

import android.app.Application;

public class TutorApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        FirebaseConfigManager.initializeFromCache(this);
        TutorFirebaseMessagingService.ensureNotificationChannel(this);
    }
}
