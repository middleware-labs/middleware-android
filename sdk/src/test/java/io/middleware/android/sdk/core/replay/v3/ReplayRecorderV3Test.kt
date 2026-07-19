package io.middleware.android.sdk.core.replay.v3

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.middleware.android.sdk.builders.MiddlewareBuilder
import io.middleware.android.sdk.core.replay.RREvent
import io.middleware.android.sdk.core.replay.v2.LifecycleManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs on SDK 25 so the recorder uses the synchronous View.draw capture path —
 * PixelCopy callbacks never fire under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25])
class ReplayRecorderV3Test {

    private class FakeExporter : RRWebExporterV3("http://localhost:1", "token", { emptyMap() }) {
        val events = CopyOnWriteArrayList<Pair<RREvent, String>>()

        override fun enqueue(event: RREvent, sessionId: String) {
            events.add(Pair(event, sessionId))
        }

        override fun flush() = Unit
    }

    private lateinit var controller: ActivityController<Activity>
    private lateinit var exporter: FakeExporter
    private lateinit var recorder: ReplayRecorderV3
    private var sessionId = "session-1"

    @Before
    fun setup() {
        val application: Application = ApplicationProvider.getApplicationContext()
        controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        exporter = FakeExporter()
        recorder = ReplayRecorderV3(
            application,
            MiddlewareBuilder(),
            LifecycleManager(application, activity),
            exporter,
        ) { sessionId }
    }

    @After
    fun teardown() {
        recorder.stop()
        shadowOf(Looper.getMainLooper()).idle()
        exporter.shutdown()
    }

    /** Waits for the background capture executor to publish events. */
    private fun awaitEvents(minCount: Int): List<Pair<RREvent, String>> {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (exporter.events.size >= minCount) {
                return exporter.events.toList()
            }
            Thread.sleep(20)
        }
        throw AssertionError("expected at least $minCount events, got " + exporter.events.size)
    }

    @Test
    fun startEmitsMetaThenFullSnapshot() {
        controller.setup() // create -> start -> resume -> visible
        recorder.start(System.currentTimeMillis())
        shadowOf(Looper.getMainLooper()).idle()
        // resume happened before start(); attach current activity now
        recorder.onActivityResumed(controller.get())

        val events = awaitEvents(2).map { it.first }
        assertEquals(RRWebEvents.TYPE_META, events[0].type)
        assertEquals(RRWebEvents.TYPE_FULL_SNAPSHOT, events[1].type)
        val metaData = events[0].data
        assertTrue((metaData["width"] as Int) > 0)
        assertTrue((metaData["height"] as Int) > 0)
        assertTrue((metaData["href"] as String).startsWith("android-app://"))
    }

    @Test
    fun sessionRotationStartsNewEpoch() {
        controller.setup()
        recorder.start(System.currentTimeMillis())
        shadowOf(Looper.getMainLooper()).idle()
        recorder.onActivityResumed(controller.get())
        awaitEvents(2)

        sessionId = "session-2"
        recorder.onActivityResumed(controller.get())

        val deadline = System.currentTimeMillis() + 5000
        var secondSessionEvents: List<RREvent> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            secondSessionEvents = exporter.events
                .filter { it.second == "session-2" }
                .map { it.first }
            if (secondSessionEvents.any { it.type == RRWebEvents.TYPE_FULL_SNAPSHOT }) {
                break
            }
            Thread.sleep(20)
        }
        assertTrue(
            "expected fresh Meta for the new session",
            secondSessionEvents.any { it.type == RRWebEvents.TYPE_META }
        )
        assertTrue(
            "expected fresh FullSnapshot for the new session",
            secondSessionEvents.any { it.type == RRWebEvents.TYPE_FULL_SNAPSHOT }
        )
    }

    @Test
    fun stopPreventsFurtherCaptures() {
        controller.setup()
        recorder.start(System.currentTimeMillis())
        assertTrue(recorder.isRunning)
        recorder.stop()
        assertFalse(recorder.isRunning)
    }
}
