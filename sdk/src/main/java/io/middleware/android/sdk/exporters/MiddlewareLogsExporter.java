package io.middleware.android.sdk.exporters;

import androidx.annotation.NonNull;

import java.util.Collection;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;

public class MiddlewareLogsExporter implements LogRecordExporter {

    private final LogRecordExporter logRecordExporter;

    public MiddlewareLogsExporter(LogRecordExporter logRecordExporter) {
        this.logRecordExporter = logRecordExporter;
    }

    @Override
    public CompletableResultCode export(@NonNull Collection<LogRecordData> logs) {
        try {
            return logRecordExporter.export(logs);
        } catch (Exception e) {
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode flush() {
        return logRecordExporter.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return logRecordExporter.shutdown();
    }
}
