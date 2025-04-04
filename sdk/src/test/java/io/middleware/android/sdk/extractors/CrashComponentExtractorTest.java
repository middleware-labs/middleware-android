package io.middleware.android.sdk.extractors;

import static io.middleware.android.sdk.utils.Constants.COMPONENT_CRASH;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import org.junit.jupiter.api.Test;

import io.middleware.android.sdk.core.instrumentations.crash.CrashAttributesExtractor;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

public class CrashComponentExtractorTest {
    @Test
    void shouldSetCrashComponent() {
        CrashAttributesExtractor crashComponentExtractor = new CrashAttributesExtractor();
        AttributesBuilder attributesBuilder = Attributes.builder();
        crashComponentExtractor.onStart(attributesBuilder, null, null);
        assertThat(attributesBuilder.build())
                .hasSize(2)
                .containsEntry(COMPONENT_KEY, COMPONENT_CRASH);
        assertThat(attributesBuilder.build())
                .hasSize(2)
                .containsEntry(EVENT_TYPE, COMPONENT_ERROR);
    }

}