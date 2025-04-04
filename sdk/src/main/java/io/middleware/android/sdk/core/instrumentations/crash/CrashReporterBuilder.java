package io.middleware.android.sdk.core.instrumentations.crash;

import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.android.instrumentation.crash.CrashDetails;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

/**
 * A builder of {@link CrashReporter}.
 */
public final class CrashReporterBuilder {

    CrashReporterBuilder() {
    }

    final List<AttributesExtractor<CrashDetails, Void>> additionalExtractors = new ArrayList<>();

    /**
     * Adds an {@link AttributesExtractor} that will extract additional attributes.
     */
    public CrashReporterBuilder addAttributesExtractor(
            AttributesExtractor<CrashDetails, Void> extractor) {
        additionalExtractors.add(extractor);
        return this;
    }

    /**
     * Returns a new {@link CrashReporter} with the settings of this {@link CrashReporter}.
     */
    public CrashReporter build() {
        return new CrashReporter(additionalExtractors);
    }
}