package io.middleware.android.sdk.core.replay.v2;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

public class NetworkManager {
    private static final String IMAGES_URL = "/v1/rum";

    private final String baseUrl;
    private final String token;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public NetworkManager(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    private Request createRequest(final String path) {
        final String url = baseUrl + path;
        return new Request.Builder()
                .url(url)
                .build();
    }

    private void callAPI(Request request, final NetworkCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new GzipRequestInterceptor())
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseData = response.body() != null ? response.body().string() : "";
                final int statusCode = response.code();
                handler.post(() -> {
                    if (statusCode >= 200 && statusCode < 300) {
                        callback.onSuccess(responseData);
                    } else {
                        callback.onError(new IOException("Error in call: " + statusCode));
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                handler.post(() -> callback.onError(e));
            }
        });
    }

    public void sendImages(String sessionId, byte[] images, String name, final NetworkCallback callback) {
        Request request = createRequest(IMAGES_URL);
        if (token == null) {
            callback.onError(new IOException("Token is null"));
            return;
        }


        String boundary = "Boundary-" + java.util.UUID.randomUUID().toString();
        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder(boundary)
                .setType(MultipartBody.FORM)
                .addFormDataPart("sessionId", sessionId)
                .addFormDataPart("batch", name,
                        RequestBody.create(images, MediaType.parse("application/gzip")));

        RequestBody requestBody = requestBodyBuilder.build();
        request = request.newBuilder()
                .post(requestBody)
                .header("Authorization", token)
                .build();

        callAPI(request, new NetworkCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    static class GzipRequestInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            if (originalRequest.body() == null || originalRequest.header("Content-Encoding") != null) {
                return chain.proceed(originalRequest);
            }

            Request compressedRequest = originalRequest.newBuilder()
                    .header("Content-Encoding", "gzip")
                    .method(originalRequest.method(), gzip(originalRequest.body()))
                    .build();
            return chain.proceed(compressedRequest);
        }

        private RequestBody gzip(final RequestBody body) {
            return new RequestBody() {
                @Override
                public MediaType contentType() {
                    return body.contentType();
                }

                @Override
                public long contentLength() {
                    return -1; // We don't know the compressed length in advance!
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    BufferedSink gzipSink = Okio.buffer(new GzipSink(sink));
                    body.writeTo(gzipSink);
                    gzipSink.close();
                }
            };
        }
    }
}
