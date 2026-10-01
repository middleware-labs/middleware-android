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
    fun standaloneReportsTheMiddlewareSdkVersion() {
        // Regression: only rum.sdk.version was set, to the otel-android version.
        val attributes = rumSetup().resource.attributes
        assertEquals(
            io.middleware.android.sdk.BuildConfig.MW_SDK_VERSION,
            attributes.get(stringKey("mw.rum.sdk.version")),
        )
    }

    @Test
    fun wrapperGlobalAttributesSetTheSdkVersion() {
        // The Flutter SDK passes its version in the global attributes.
        val setup = rumSetup { builder ->
            builder.setGlobalAttributes(Attributes.of(stringKey("mw.rum.sdk.version"), "2.1.2"))
        }
        assertEquals("2.1.2", setup.resource.attributes.get(stringKey("mw.rum.sdk.version")))
    }

    @Test
    fun sdkControlledAttributesWinOverWrapperAttributes() {
        val setup = rumSetup { builder ->
            builder.setResourceAttributes(
                Attributes.of(
                    stringKey("service.name"), "evil-override",
                    stringKey("recording"), "0",
                )
            )
        }
        val attributes = setup.resource.attributes
        assertEquals("test-service", attributes.get(stringKey("service.name")))
        assertEquals("1", attributes.get(stringKey("recording")))
    }

    @Test
    fun recordingFlagsSurviveNativeSessionRewrite() {
        val setup = rumSetup()
        // mirror what Middleware.setNativeSession does to the resource
        val rewritten = setup.resource.toBuilder()
            .put("session.id", "native-session")
            .put("session.start_time", "1750000000000")
            .build()
        assertEquals("1", rewritten.attributes.get(stringKey("recording")))
        assertEquals("true", rewritten.attributes.get(stringKey("browser.trace")))
        assertEquals("native-session", rewritten.attributes.get(stringKey("session.id")))
    }
}
