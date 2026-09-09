package com.buddhaspinas.buddhasride;

import android.app.Application;
import android.text.TextUtils;
import com.onesignal.OneSignal;

public class BuddhasRideApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        String appId = getString(R.string.onesignal_app_id).trim();
        if (!TextUtils.isEmpty(appId) && appId.matches("[0-9a-fA-F-]{36}")) {
            OneSignal.initWithContext(this, appId);
        }
    }
}
