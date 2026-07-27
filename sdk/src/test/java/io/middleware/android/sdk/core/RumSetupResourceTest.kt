package io.middleware.android.sdk.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.middleware.android.sdk.Middleware
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RumSetupResourceTest {

    private fun rumSetup(configure: (io.middleware.android.sdk.builders.MiddlewareBuilder) -> Unit = {}): RumSetup {
        val builder = Middleware.builder()
            .setTarget("https://example.middleware.io")
            .setServiceName("test-service")
            .setProjectName("test-project")
            .setRumAccessToken("token")
        configure(builder)
        val application: Application = ApplicationProvider.getApplicationContext()
        return RumSetup(application, builder)
    }

    @Test
    fun wrapperResourceAttributesLandInResource() {
        val setup = rumSetup { builder ->
            builder.setResourceAttributes(
                Attributes.of(
                    stringKey("telemetry.sdk.name"), "middleware-react-native",
                    stringKey("mw.rum.sdk.version"), "2.0.0",
                )
            )
        }
        val attributes = setup.resource.attributes
        assertEquals("middleware-react-native", attributes.get(stringKey("telemetry.sdk.name")))
        assertEquals("2.0.0", attributes.get(stringKey("mw.rum.sdk.version")))
    }

    @Test
    fun sdkControlledAttributesWinOverWrapperAttributes() {
        val setup = rumSetup { builder ->
            builder.setResourceAttributes(
                Attributes.of(
                    stringKey("service.name"), "evil-override",
                    stringKey("recordingV3"), "0",
                )
            )
        }
        val attributes = setup.resource.attributes
        assertEquals("test-service", attributes.get(stringKey("service.name")))
        assertEquals("1", attributes.get(stringKey("recordingV3")))
    }

    @Test
    fun recordingFlagsSurviveNativeSessionRewrite() {
        val setup = rumSetup()
        // mirror what Middleware.setNativeSession does to the resource
        val rewritten = setup.resource.toBuilder()
            .put("session.id", "native-session")
            .put("session.start_time", "1750000000000")
            .build()
        assertEquals("1", rewritten.attributes.get(stringKey("recordingV3")))
        assertEquals("1", rewritten.attributes.get(stringKey("recording")))
        assertEquals("true", rewritten.attributes.get(stringKey("browser.trace")))
        assertEquals("native-session", rewritten.attributes.get(stringKey("session.id")))
    }
}
