package com.buddhaspalm.admin;

import android.app.Application;

public class BuddhasPalmApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // OneSignal must be initialized before any other OneSignal calls.
        // Notification permission is intentionally NOT requested here. The
        // official OneSignal verification flow requests it only after a real,
        // server-assigned push subscription is observed and the user taps Got it.
        OneSignalManager.initialize(this);
    }
}
