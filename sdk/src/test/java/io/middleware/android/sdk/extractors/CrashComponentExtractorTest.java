package io.middleware.android.sdk.extractors;


import static io.middleware.android.sdk.utils.Constants.COMPONENT_CRASH;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

public class CrashComponentExtractorTest {
    @Test
    void shouldSetCrashComponent() {
        CrashComponentExtractor crashComponentExtractor = new CrashComponentExtractor();
        AttributesBuilder attributesBuilder = Attributes.builder();
        crashComponentExtractor.onStart(attributesBuilder, null, null);
        assertThat(attributesBuilder.build())
                .hasSize(1)
                .containsEntry(COMPONENT_KEY, COMPONENT_CRASH);
    }

}