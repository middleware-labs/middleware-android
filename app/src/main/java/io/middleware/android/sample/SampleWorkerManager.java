package io.middleware.android.sample;

import android.content.Context;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

public class SampleWorkerManager {
    public static void startWorker(Context context) {
        WorkRequest demoWorkRequest = new OneTimeWorkRequest.Builder(SampleWorker.class).build();
        WorkManager.getInstance(context).enqueue(demoWorkRequest);
    }
}
