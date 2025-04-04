package io.middleware.android.sdk.core.instrumentations.crash;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.android.common.RuntimeDetailsExtractor;
import io.opentelemetry.android.instrumentation.AndroidInstrumentation;
import io.opentelemetry.android.instrumentation.InstallationContext;
import io.opentelemetry.android.instrumentation.crash.CrashDetails;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.sdk.OpenTelemetrySdk;

public class CrashInstrumentation implements AndroidInstrumentation {
    private static final String INSTRUMENTATION_NAME = "crash";
    private final List<AttributesExtractor<CrashDetails, Void>> additionalExtractors =
            new ArrayList<>();

    public void addAttributesExtractor(AttributesExtractor<CrashDetails, Void> extractor) {
        additionalExtractors.add(extractor);
    }

    @Override
    public void install(@NonNull InstallationContext installationContext) {
        addAttributesExtractor(RuntimeDetailsExtractor.create(installationContext.getApplication()));
        CrashReporter crashReporter = new CrashReporter(additionalExtractors);
        crashReporter.install((OpenTelemetrySdk) installationContext.getOpenTelemetry());
    }
}
