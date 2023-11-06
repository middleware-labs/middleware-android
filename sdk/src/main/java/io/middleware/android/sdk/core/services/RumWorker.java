package io.middleware.android.sdk.core.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

import io.middleware.android.sdk.core.models.RumData;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RumWorker extends Worker {
    private final Context context;
    private final WorkerParameters workerParams;

    public RumWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        this.workerParams = workerParams;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("RUM WORKER", "Starting sending rum data");
        Data inputData = workerParams.getInputData();
        RumData rumData = new RumData();
        rumData.setPayload(inputData.getString("payload"));
        rumData.setEndpoint(inputData.getString("endpoint"));
        rumData.setAccessToken(inputData.getString("accessToken"));
        int responseCode = postRum(rumData);
        Log.d("RumService", "Some Response Code" + responseCode);
        return Result.success();
    }

    private int postRum(RumData rumData) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.Companion.create(rumData.getPayload(), MEDIA_TYPE_JSON);
        Log.d("RumService", "Posting RUM DATA" + rumData.getPayload());
        Request request = new Request.Builder()
                .url(rumData.getEndpoint())
                .header("MW_API_KEY", rumData.getAccessToken())
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build();

        Response response = null;
        try {
            response = okHttpClient.newCall(request).execute();
            return response.code();
        } catch (IOException e) {
            Log.e("RumService", "Failed to send RUM data to Middleware :" + e.getMessage());
        } finally {
            if (response != null) {
                if (response.body() != null) {
                    response.body().close();
                }
            }
        }
        return -1;
    }

}
