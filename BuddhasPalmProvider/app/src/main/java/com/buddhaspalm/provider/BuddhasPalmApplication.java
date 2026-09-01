package com.buddhaspalm.provider;

import android.app.Application;

public class BuddhasPalmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OneSignalManager.initialize(this);
    }
}
