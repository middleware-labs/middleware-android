package io.middleware.android.sdk.extractors;

import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;
import static io.middleware.android.sdk.utils.Constants.LINK_SPAN_ID_KEY;
import static io.middleware.android.sdk.utils.Constants.LINK_TRACE_ID_KEY;

import io.middleware.android.sdk.utils.ServerTimingHeaderParser;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import okhttp3.Request;
import okhttp3.Response;

public class RumResponseAttributesExtractor implements AttributesExtractor<Request, Response> {

    private final ServerTimingHeaderParser serverTimingHeaderParser;

    public RumResponseAttributesExtractor(ServerTimingHeaderParser serverTimingHeaderParser) {
        this.serverTimingHeaderParser = serverTimingHeaderParser;
    }

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, Request request) {
        attributes.put(COMPONENT_KEY, "http");
        attributes.put(EVENT_TYPE, "fetch");
    }

    @Override
    public void onEnd(
            AttributesBuilder attributes,
            Context context,
            Request request,
            Response response,
            Throwable error) {
        if (response != null) {
            onResponse(attributes, response);
        }
    }

    private void onResponse(AttributesBuilder attributes, Response response) {
        String serverTimingHeader = response.header("Server-Timing");
        String[] ids = serverTimingHeaderParser.parse(serverTimingHeader);
        if (ids.length == 2) {
            attributes.put(LINK_TRACE_ID_KEY, ids[0]);
            attributes.put(LINK_SPAN_ID_KEY, ids[1]);
        }
        attributes.put("http.status_code", response.code());
        attributes.put("http.method", response.request().method());
        response.headers().forEach(header -> {
            attributes.put("http.response.header." + header.getFirst(), header.getSecond());
        });
        response.request().headers().forEach(header -> {
            attributes.put("http.request.header." + header.getFirst(), header.getSecond());
        });
    }
}