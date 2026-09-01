package com.buddhaspalm.hub;

import android.app.Application;

public class BuddhasPalmHubApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OneSignalManager.initialize(this);
    }
}
