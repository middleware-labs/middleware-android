package io.middleware.android.sdk.core.instrumentations.crash;

import java.util.List;

import io.opentelemetry.android.instrumentation.crash.CrashDetails;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.OpenTelemetrySdk;

public class CrashReporter {
    /**
     * Returns a new {@link CrashReporter} with the default settings.
     */
    public static CrashReporter create() {
        return builder().build();
    }

    /**
     * Returns a new {@link CrashReporter}.
     */
    public static CrashReporterBuilder builder() {
        return new CrashReporterBuilder();
    }

    private final List<AttributesExtractor<CrashDetails, Void>> additionalExtractors;

    CrashReporter(List<AttributesExtractor<CrashDetails, Void>> additionalExtractors) {
        this.additionalExtractors = additionalExtractors;
    }

    /**
     * Installs the crash reporting instrumentation on the given {@link OpenTelemetrySdk}.
     */
    public void install(OpenTelemetrySdk openTelemetry) {
        Thread.UncaughtExceptionHandler existingHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
                new CrashReportingExceptionHandler(
                        buildInstrumenter(openTelemetry),
                        openTelemetry.getSdkTracerProvider(),
                        existingHandler));
    }

    private Instrumenter<CrashDetails, Void> buildInstrumenter(OpenTelemetrySdk openTelemetry) {
        return Instrumenter.<CrashDetails, Void>builder(
                        openTelemetry, "io.opentelemetry.crash", crashDetails -> crashDetails.getThread().getName())
                .addAttributesExtractor(new CrashAttributesExtractor())
                .addAttributesExtractors(additionalExtractors)
                .buildInstrumenter();
    }
}
