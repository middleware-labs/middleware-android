package io.middleware.android.sdk.core.replay.v2;

public interface NetworkCallback {
    void onSuccess(String response);

    void onError(Exception e);
}
