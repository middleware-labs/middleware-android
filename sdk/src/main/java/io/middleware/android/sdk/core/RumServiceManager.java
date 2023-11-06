package io.middleware.android.sdk.core;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import io.middleware.android.sdk.core.models.RumData;
import io.middleware.android.sdk.core.services.RumWorker;

public class RumServiceManager {
    @SuppressLint("RestrictedApi")
    public static void startWorker(Context context, RumData rumData) {
        Data.Builder builder = new Data.Builder();
        builder.put("endpoint", rumData.getEndpoint());
        builder.put("accessToken", rumData.getAccessToken());
        builder.put("payload", rumData.getPayload());
        WorkRequest demoWorkRequest = new OneTimeWorkRequest.Builder(RumWorker.class)
                .setInputData(builder.build())
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueue(demoWorkRequest);
    }
}
