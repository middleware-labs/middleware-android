package io.middleware.android.sdk.utils;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.UUID;

import io.opentelemetry.api.logs.Severity;

public class RumUtil {
    public static String getVersion(Application application){
        try {
            PackageInfo packageInfo = application.getApplicationContext().getPackageManager().getPackageInfo(application.getApplicationContext().getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
