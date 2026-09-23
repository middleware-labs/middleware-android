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
class RumSetupSamplingTest {

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

    private fun builtRumSetup(ratio: Double): RumSetup {
        val setup = rumSetup { it.setSessionSamplingRatio(ratio) }
        setup.setTraces()
        setup.bindSessionProvider(setup.build())
        return setup
    }

    @Test
    fun withoutTracesEverySessionIsSampledIn() {
        assertTrue(rumSetup().isSessionSampledIn())
    }

    @Test
    fun fullRatioSamplesTheSessionIn() {
        assertTrue(builtRumSetup(1.0).isSessionSampledIn())
    }

    @Test
    fun zeroRatioSamplesTheSessionOut() {
        assertFalse(builtRumSetup(0.0).isSessionSampledIn())
    }
}
