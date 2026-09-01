package com.buddhaspalm.admin;

import android.app.Application;

public class BuddhasPalmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OneSignalManager.initialize(this);
        // Ensure the native Android notification permission is requested even
        // before the website exposes an External ID. This prevents first-install
        // devices from remaining silently unsubscribed.
        OneSignalManager.requestNotificationPermission();
    }
}
