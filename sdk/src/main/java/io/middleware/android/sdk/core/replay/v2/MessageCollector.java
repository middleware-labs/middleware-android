package io.middleware.android.sdk.core.replay.v2;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.middleware.android.sdk.Middleware;

class BatchArch {
    public String name;
    public byte[] data;

    public BatchArch(String name, byte[] data) {
        this.name = name;
        this.data = data;
    }
}

public class MessageCollector {
    private final List<BatchArch> imagesWaiting = new ArrayList<>();
    private final List<BatchArch> imagesSending = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String target;
    private final String token;

    public MessageCollector(String target, String token) {
        this.target = target;
        this.token = token;
    }

    public void stop() {
        flush();
    }

    public void sendImagesBatch(byte[] batch, String fileName) {
        final BatchArch images = new BatchArch(fileName, batch);
        imagesWaiting.add(images);

        handler.post(this::flushImages);
    }

    public void flush() {
        handler.post(this::flushImages);
    }

    private void flushImages() {
        if (imagesWaiting.isEmpty()) {
            return;
        }

        BatchArch images = imagesWaiting.remove(0);
        imagesSending.add(images);

        Log.d("Middleware", "Sending images " + images.name + " " + images.data.length);
        NetworkManager networkManager = new NetworkManager(target, token);
        networkManager.sendImages(Middleware.getInstance().getRumSessionId(), images.data, images.name, new NetworkCallback() {
            @Override
            public void onSuccess(String response) {
                imagesSending.removeIf(waiting -> images.name.equals(waiting.name));
                if (!response.isEmpty()) {
                    imagesWaiting.add(images);
                }
            }

            @Override
            public void onError(Exception e) {
            }
        });
    }
}
