package io.middleware.android.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static io.opentelemetry.api.logs.Severity.ERROR;
import static io.opentelemetry.api.logs.Severity.INFO;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.internal.aggregator.EmptyMetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.logs.TestLogRecordData;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

class RumInitializerTest {
    private Application application;
    private Looper mainLooper;
    private Context context;
    private MiddlewareBuilder middlewareBuilder;
    private RumInitializer rumInitializer;
    private Middleware middleware;

    @BeforeEach
    void setup() {
        application = mock(Application.class, RETURNS_DEEP_STUBS);
        mainLooper = mock(Looper.class, RETURNS_DEEP_STUBS);
        context = mock(Context.class, RETURNS_DEEP_STUBS);
        middlewareBuilder = Middleware.builder()
                .setRumAccessToken("qwertytoken")
                .setTarget("https://middleware.io")
                .setProjectName("project")
                .setServiceName("service");
        when(application.getApplicationContext()).thenReturn(context);
        rumInitializer =
                new RumInitializer(middlewareBuilder, application, new AppStartupTimer());
        middleware = rumInitializer.initialize(application1 -> mock(CurrentNetworkProvider.class,
                RETURNS_DEEP_STUBS), mainLooper);
    }

    @Test
    void initializeRumInitializerTest() {
        assertNotNull(rumInitializer);
        assertNotNull(middleware);
    }

    @Test
    void shouldExportSpanTest() {
        long currentTimeNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
        final InMemorySpanExporter inMemorySpanExporter = InMemorySpanExporter.create();
        inMemorySpanExporter.export(Collections.singleton(createTestSpan(currentTimeNanos)));

        final List<SpanData> finishedSpanItems = inMemorySpanExporter.getFinishedSpanItems();
        assertEquals(1, finishedSpanItems.size());

        final SpanData spanData = finishedSpanItems.get(0);
        assertEquals("span", spanData.getName());
        assertEquals(SpanKind.INTERNAL, spanData.getKind());
    }

    @Test
    void shouldExportMetricTest() {
        final InMemoryMetricExporter inMemoryMetricExporter = InMemoryMetricExporter.create();
        final InMemoryMetricReader inMemoryMetricReader = InMemoryMetricReader.builder()
                .setMemoryMode(MemoryMode.REUSABLE_DATA)
                .setAggregationTemporalitySelector(inMemoryMetricExporter)
                .setAggregationTemporalitySelector(AggregationTemporalitySelector.lowMemory())
                .build();
        inMemoryMetricReader.register(new CollectionRegistration() {
            @Override
            public Collection<MetricData> collectAllMetrics() {
                return Collections.singleton(EmptyMetricData.getInstance());
            }
        });
        inMemoryMetricExporter.export(inMemoryMetricReader.collectAllMetrics());
        final List<MetricData> finishedMetricItems = inMemoryMetricExporter.getFinishedMetricItems();
        assertEquals(1, finishedMetricItems.size());
        MetricData metricData1 = finishedMetricItems.get(0);
        assertTrue(metricData1.isEmpty());
    }

    @Test
    void shouldExportLogs() {
        final InMemoryLogRecordExporter inMemoryLogRecordExporter = InMemoryLogRecordExporter.create();
        final LogRecordData infoLogRecordData = TestLogRecordData
                .builder()
                .setBody("I am info log")
                .setSeverity(INFO)
                .build();
        final LogRecordData errorLogRecordData = TestLogRecordData
                .builder()
                .setBody("I am error log")
                .setSeverity(ERROR)
                .build();
        inMemoryLogRecordExporter.export(Arrays.asList(infoLogRecordData, errorLogRecordData));

        final List<LogRecordData> finishedLogRecordItems = inMemoryLogRecordExporter.getFinishedLogRecordItems();
        assertEquals(2, finishedLogRecordItems.size());

        final LogRecordData infoData = finishedLogRecordItems.get(0);
        assertEquals("I am info log", infoData.getBody().asString());
        assertEquals(INFO, infoData.getSeverity());

        final LogRecordData errorData = finishedLogRecordItems.get(1);
        assertEquals("I am error log", errorData.getBody().asString());
        assertEquals(ERROR, errorData.getSeverity());
    }

    private TestSpanData createTestSpan(long startTimeNanos) {
        return TestSpanData.builder()
                .setName("span")
                .setKind(SpanKind.INTERNAL)
                .setStatus(StatusData.unset())
                .setStartEpochNanos(startTimeNanos)
                .setHasEnded(true)
                .setEndEpochNanos(startTimeNanos)
                .build();
    }
}

