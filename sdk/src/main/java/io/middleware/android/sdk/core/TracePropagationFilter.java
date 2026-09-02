package io.middleware.android.sdk.core;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Removes trace-context headers from requests whose URL matches none of the configured
 * {@code tracePropagationTargets}.
 *
 * <p>{@code OkHttpTelemetry} injects headers into every request it sees and offers no hook to
 * filter that, so the filtering happens after the fact. This is installed as a <em>network</em>
 * interceptor because {@code OkHttpTelemetry.newCallFactory} inserts its own injecting network
 * interceptor at index 0 — everything added to the client beforehand therefore runs after it and
 * sees the headers it wrote.
 *
 * <p>Only the outbound headers are removed. The client span is still recorded, so a request to a
 * third-party host is still timed and still appears in the session; it just does not hand that
 * host the application's trace ids.
 */
public final class TracePropagationFilter implements Interceptor {

    /**
     * Every header the configured propagators can write: W3C trace context and baggage, plus B3
     * in both its single-header and multi-header encodings.
     */
    private static final List<String> TRACE_HEADERS =
            Arrays.asList(
                    "traceparent",
                    "tracestate",
                    "baggage",
                    "b3",
                    "X-B3-TraceId",
                    "X-B3-SpanId",
                    "X-B3-ParentSpanId",
                    "X-B3-Sampled",
                    "X-B3-Flags");

    private final List<Pattern> targets;

    public TracePropagationFilter(List<Pattern> targets) {
        this.targets = targets;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        final Request request = chain.request();
        if (matches(request.url().toString())) {
            return chain.proceed(request);
        }
        final Request.Builder stripped = request.newBuilder();
        for (String header : TRACE_HEADERS) {
            stripped.removeHeader(header);
        }
        return chain.proceed(stripped.build());
    }

    /**
     * Substring match, not a whole-string match, so that a target of {@code api.example.com}
     * behaves the way it reads and matches the browser and React Native SDKs.
     */
    private boolean matches(String url) {
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).matcher(url).find()) {
                return true;
            }
        }
        return false;
    }
}
