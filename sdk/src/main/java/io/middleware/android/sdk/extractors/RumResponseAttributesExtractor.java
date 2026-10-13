package io.middleware.android.sdk.extractors;

import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;
import static io.middleware.android.sdk.utils.Constants.LINK_SPAN_ID_KEY;
import static io.middleware.android.sdk.utils.Constants.LINK_TRACE_ID_KEY;
import static io.middleware.android.sdk.utils.Constants.LOG_TAG;

import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.middleware.android.sdk.utils.ServerTimingHeaderParser;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.GzipSource;
import okio.Source;

/**
 * Adds RUM attributes to OkHttp client spans.
 *
 * <p>Server-Timing correlation ({@code link.traceId} / {@code link.spanId}) is always recorded.
 * Request/response headers and response bodies are opt-in, see
 * {@link io.middleware.android.sdk.builders.MiddlewareBuilder#setCaptureNetworkHeaders(boolean)}
 * and
 * {@link io.middleware.android.sdk.builders.MiddlewareBuilder#setCaptureResponseBodies(boolean)}.
 */
public class RumResponseAttributesExtractor implements AttributesExtractor<Interceptor.Chain, Response> {

    static final String REQUEST_HEADER_PREFIX = "http.request.header.";
    static final String RESPONSE_HEADER_PREFIX = "http.response.header.";
    static final String RESPONSE_BODY_KEY = "http.response.body";
    /** Bodies are truncated to this many (decoded) bytes. */
    static final int MAX_BODY_BYTES = 64 * 1024;
    static final String MASK_VALUE = "********";
    private static final String MASKED_HEADER = "x-access-token";

    private final ServerTimingHeaderParser serverTimingHeaderParser;
    private final boolean captureHeaders;
    private final boolean captureBodies;
    private final Set<String> ignoreHeaders;

    /** Records Server-Timing correlation only; no headers or bodies. */
    public RumResponseAttributesExtractor(ServerTimingHeaderParser serverTimingHeaderParser) {
        this(serverTimingHeaderParser, false, false, Collections.<String>emptySet());
    }

    /**
     * @param ignoreHeaders header names never recorded, compared case-insensitively
     */
    public RumResponseAttributesExtractor(
            ServerTimingHeaderParser serverTimingHeaderParser,
            boolean captureHeaders,
            boolean captureBodies,
            @Nullable Set<String> ignoreHeaders) {
        this.serverTimingHeaderParser = serverTimingHeaderParser;
        this.captureHeaders = captureHeaders;
        this.captureBodies = captureBodies;
        Set<String> lowered = new HashSet<>();
        if (ignoreHeaders != null) {
            for (String header : ignoreHeaders) {
                if (header != null) {
                    lowered.add(header.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.ignoreHeaders = lowered;
    }

    private void onResponse(AttributesBuilder attributes, Context context, Response response) {
        String serverTimingHeader = response.header("Server-Timing");
        String[] ids = serverTimingHeaderParser.parse(serverTimingHeader);
        if (ids.length == 2) {
            attributes.put(LINK_TRACE_ID_KEY, ids[0]);
            attributes.put(LINK_SPAN_ID_KEY, ids[1]);
        }
        if (captureHeaders) {
            putHeaders(attributes, RESPONSE_HEADER_PREFIX, response.headers());
            putHeaders(attributes, REQUEST_HEADER_PREFIX, response.request().headers());
        }
        // Reading the body blocks until up to MAX_BODY_BYTES have arrived, so don't pay for it
        // on spans that are going to be dropped anyway.
        if (captureBodies && Span.fromContext(context).isRecording()) {
            try {
                String body = readBody(response);
                if (body != null) {
                    attributes.put(RESPONSE_BODY_KEY, body);
                }
            } catch (Exception e) {
                // Capture is best-effort and must never break the request.
                Log.d(LOG_TAG, "Failed to capture response body", e);
            }
        }
    }

    private void putHeaders(AttributesBuilder attributes, String prefix, Headers headers) {
        // names() is case-insensitive, so repeated headers are grouped into one attribute.
        for (String name : headers.names()) {
            String lowered = name.toLowerCase(Locale.ROOT);
            if (ignoreHeaders.contains(lowered)) {
                continue;
            }
            List<String> values = headers.values(name);
            if (values.isEmpty()) {
                continue;
            }
            String value = MASKED_HEADER.equals(lowered) ? MASK_VALUE : join(values);
            attributes.put(prefix + normalizeHeaderName(name), value);
        }
    }

    private static String join(List<String> values) {
        if (values.size() == 1) {
            return values.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    /** {@code Content-Type} becomes {@code content_type}. */
    static String normalizeHeaderName(String name) {
        return name.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /** {@code application/json}, {@code *+json} and {@code text/plain}, ignoring parameters. */
    static boolean isCapturedBodyType(@Nullable String contentType) {
        if (contentType == null) {
            return false;
        }
        int semi = contentType.indexOf(';');
        String base = (semi >= 0 ? contentType.substring(0, semi) : contentType)
                .trim()
                .toLowerCase(Locale.ROOT);
        return base.equals("application/json") || base.endsWith("+json") || base.equals("text/plain");
    }

    /**
     * Reads up to {@link #MAX_BODY_BYTES} of the body without consuming it.
     *
     * <p>OkHttp's tracing interceptor is a network interceptor, so the response here is the raw
     * wire response: when OkHttp negotiated gzip transparently the body is still compressed. We
     * peek the underlying source (which buffers what we read so the app still sees every byte)
     * and inflate it ourselves.
     */
    @Nullable
    static String readBody(Response response) throws Exception {
        ResponseBody body = response.body();
        if (body == null || !isCapturedBodyType(response.header("Content-Type"))) {
            return null;
        }
        if (body.contentLength() == 0) {
            return null;
        }
        String encoding = response.header("Content-Encoding");
        Source source = body.source().peek();
        if (encoding != null && !encoding.trim().isEmpty()
                && !"identity".equalsIgnoreCase(encoding.trim())) {
            if (!"gzip".equalsIgnoreCase(encoding.trim())) {
                return null; // e.g. br, deflate: not decodable here
            }
            source = new GzipSource(source);
        }
        Buffer buffer = new Buffer();
        try {
            long remaining = MAX_BODY_BYTES;
            while (remaining > 0) {
                long read = source.read(buffer, remaining);
                if (read == -1) {
                    break;
                }
                remaining -= read;
            }
        } finally {
            // Closing a peek source leaves the real body open; this only releases the Inflater.
            source.close();
        }
        if (buffer.size() == 0) {
            return null;
        }
        Charset charset = StandardCharsets.UTF_8;
        MediaType mediaType = body.contentType();
        if (mediaType != null) {
            charset = mediaType.charset(StandardCharsets.UTF_8);
        }
        String text = buffer.readString(charset).trim();
        return text.isEmpty() ? null : text;
    }

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, Interceptor.Chain chain) {
        attributes.put(COMPONENT_KEY, "http");
        attributes.put(EVENT_TYPE, "fetch");
    }

    @Override
    public void onEnd(AttributesBuilder attributes, Context context, Interceptor.Chain chain, Response response, Throwable error) {
        if (response != null) {
            onResponse(attributes, context, response);
        }
    }
}
