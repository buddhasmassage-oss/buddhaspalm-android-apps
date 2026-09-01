package com.buddhaspalm.provider;

import android.app.Application;

public class BuddhasPalmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OneSignalManager.initialize(this);
        // Ensure first-install devices receive the Android notification prompt
        // even before the web session has exposed an External ID.
        OneSignalManager.requestNotificationPermission();
    }
}
