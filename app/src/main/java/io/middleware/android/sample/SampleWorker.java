package io.middleware.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

public class SampleWorker extends Worker {

    private final Context context;

    public SampleWorker(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
        super(context, workerParameters);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        Middleware.getInstance().addEvent("Demo working is doing some work", Attributes.of(stringKey("workerId"), "sample"));
        Log.d("WORKER", "Starting background work");
        startBackgroundService();
        return Result.success();
    }

    private void startBackgroundService() {
        Intent intent = new Intent(context, SampleBackgroundService.class);
        Log.d("WORKER", "Service is getting started");
        context.startService(intent);
    }

    private static class SampleBackgroundService extends Service {

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent != null) {
                Log.d("SERVICE", "Started service and doing processing");
            }
            stopSelf();
            return START_STICKY;
        }
    }
}
