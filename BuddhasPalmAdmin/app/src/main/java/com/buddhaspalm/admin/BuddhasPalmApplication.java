package com.buddhaspalm.admin;

import android.app.Application;

public class BuddhasPalmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OneSignalManager.initialize(this);
    }
}
