package io.middleware.android.sdk.exporters;

import androidx.annotation.NonNull;

import java.util.Collection;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

public class MiddlewareMetricsExporter implements MetricExporter {

    private final MetricExporter metricExporter;

    public MiddlewareMetricsExporter(MetricExporter metricExporter) {
        this.metricExporter = metricExporter;
    }

    @Override
    public CompletableResultCode export(@NonNull Collection<MetricData> metrics) {
        try {
            return metricExporter.export(metrics);
        } catch (Exception e) {
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode flush() {
        return metricExporter.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return metricExporter.shutdown();
    }

    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return metricExporter.getAggregationTemporality(instrumentType);
    }
}
