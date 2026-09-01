package com.buddhaspalm.provider;

import android.app.Application;

public class BuddhasPalmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize OneSignal before any other SDK calls. Permission is requested
        // only from the required verification dialog after a real subscription ID
        // has been observed.
        OneSignalManager.initialize(this);
    }
}
