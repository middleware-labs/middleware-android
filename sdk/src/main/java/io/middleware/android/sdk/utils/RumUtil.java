package io.middleware.android.sdk.utils;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.UUID;

public class RumUtil {
    public static String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    public static String getVersion(Application application){
        try {
            PackageInfo packageInfo = application.getApplicationContext().getPackageManager().getPackageInfo(application.getApplicationContext().getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

}
