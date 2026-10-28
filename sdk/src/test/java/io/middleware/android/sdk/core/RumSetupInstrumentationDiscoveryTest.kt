package io.middleware.android.sdk.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.middleware.android.sdk.Middleware
import io.middleware.android.sdk.builders.MiddlewareBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RumSetupInstrumentationDiscoveryTest {

    private fun rumSetup(configure: (MiddlewareBuilder) -> Unit = {}): RumSetup {
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
    fun nativeAppsKeepOtelInstrumentationDiscovery() {
        assertTrue(rumSetup().isInstrumentationDiscoveryEnabled)
    }

    @Test
    fun disablingActivityLifecycleTurnsOffDiscovery() {
        // Hybrid hosts: otel's ActivityLifecycleInstrumentation would otherwise start an
        // AppStart span under the SDK's own session before the host injects its session id.
        assertFalse(
            rumSetup { it.disableActivityLifecycleMonitoring() }.isInstrumentationDiscoveryEnabled
        )
    }
}
