package io.middleware.android.sdk.exporters;

import java.util.Collection;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class MiddlewareSpanExporter implements SpanExporter {

    private final SpanExporter spanExporter;

    public MiddlewareSpanExporter(SpanExporter spanExporter) {
        this.spanExporter = spanExporter;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        try {
            return spanExporter.export(spans);
        } catch (Exception e) {
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode flush() {
        return spanExporter.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return spanExporter.shutdown();
    }
}
