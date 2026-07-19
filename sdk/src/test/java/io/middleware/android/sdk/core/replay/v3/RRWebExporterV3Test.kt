package io.middleware.android.sdk.core.replay.v3

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class RRWebExporterV3Test {

    private lateinit var server: MockWebServer
    private lateinit var exporter: RRWebExporterV3

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        exporter = RRWebExporterV3(
            server.url("/").toString().removeSuffix("/"),
            "test-token",
        ) { sessionId ->
            linkedMapOf(
                "mw.rum" to "true",
                "recordingV3" to "1",
                "session.id" to sessionId,
            )
        }
    }

    @AfterEach
    fun teardown() {
        exporter.shutdown()
        server.shutdown()
    }

    private fun takeRequestBody(): JsonObject {
        val request = server.takeRequest(10, TimeUnit.SECONDS)
        assertNotNull(request, "expected an export request")
        assertEquals("/v1/metrics", request!!.path)
        assertEquals("test-token", request.getHeader("Authorization"))
        assertEquals("gzip", request.getHeader("Content-Encoding"))
        val json = GZIPInputStream(request.body.inputStream()).bufferedReader().readText()
        return JsonParser.parseString(json).asJsonObject
    }

    @Test
    fun exportsOtlpMetricsShape() {
        server.enqueue(MockResponse().setResponseCode(200))

        exporter.enqueue(RRWebEvents.meta("app://x", 400, 800, 1000L), "session-1")
        exporter.enqueue(RRWebEvents.frameMutation("data:image/webp;base64,AA", 2000L), "session-1")
        exporter.flush()

        val body = takeRequestBody()
        val resourceMetrics = body.getAsJsonArray("resourceMetrics")
        assertEquals(1, resourceMetrics.size())

        val resource = resourceMetrics[0].asJsonObject.getAsJsonObject("resource")
        val resourceAttrs = resource.getAsJsonArray("attributes").associate {
            it.asJsonObject["key"].asString to
                it.asJsonObject["value"].asJsonObject["stringValue"].asString
        }
        assertEquals("1", resourceAttrs["recordingV3"])
        assertEquals("session-1", resourceAttrs["session.id"])

        val metrics = resourceMetrics[0].asJsonObject
            .getAsJsonArray("scopeMetrics")[0].asJsonObject
            .getAsJsonArray("metrics")
        assertEquals(2, metrics.size())

        val first = metrics[0].asJsonObject
        assertEquals("rum_event", first["name"].asString)
        val dataPoint = first.getAsJsonObject("gauge").getAsJsonArray("dataPoints")[0].asJsonObject
        assertEquals("1000000000", dataPoint["timeUnixNano"].asString)
        val pointAttrs = dataPoint.getAsJsonArray("attributes").associate {
            it.asJsonObject["key"].asString to
                it.asJsonObject["value"].asJsonObject["stringValue"].asString
        }
        assertEquals("4", pointAttrs["type"])
        assertEquals("1000", pointAttrs["timestamp"])
        assertEquals("""{"href":"app://x","width":400,"height":800}""", pointAttrs["data"])
    }

    @Test
    fun groupsBatchesBySession() {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))

        exporter.enqueue(RRWebEvents.meta("app://x", 400, 800, 1000L), "session-a")
        exporter.enqueue(RRWebEvents.meta("app://x", 400, 800, 2000L), "session-b")
        exporter.flush()

        val sessions = mutableSetOf<String>()
        repeat(2) {
            val body = takeRequestBody()
            val attrs = body.getAsJsonArray("resourceMetrics")[0].asJsonObject
                .getAsJsonObject("resource")
                .getAsJsonArray("attributes").associate {
                    it.asJsonObject["key"].asString to
                        it.asJsonObject["value"].asJsonObject["stringValue"].asString
                }
            sessions.add(attrs["session.id"]!!)
        }
        assertEquals(setOf("session-a", "session-b"), sessions)
    }

    @Test
    fun retriesFailedBatchOnNextFlush() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))

        exporter.enqueue(RRWebEvents.frameMutation("data:image/webp;base64,AA", 1000L), "session-1")
        exporter.flush()
        takeRequestBody() // first attempt fails
        Thread.sleep(300) // let the failed batch requeue before flushing again

        exporter.flush()
        val retried = takeRequestBody()
        val metrics = retried.getAsJsonArray("resourceMetrics")[0].asJsonObject
            .getAsJsonArray("scopeMetrics")[0].asJsonObject
            .getAsJsonArray("metrics")
        assertEquals(1, metrics.size())
    }

    @Test
    fun dropsBatchAfterMaxRetries() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }

        exporter.enqueue(RRWebEvents.frameMutation("data:image/webp;base64,AA", 1000L), "session-1")
        // 1 initial attempt + 3 retries
        repeat(4) {
            exporter.flush()
            takeRequestBody()
            Thread.sleep(300) // let the failed batch requeue before flushing again
        }

        exporter.flush()
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNull(request, "batch should be dropped after max retries")
    }

    @Test
    fun evictionKeepsKeyframes() {
        // fill the buffer far beyond the event cap with incremental frames plus
        // one meta and one full snapshot at the front
        exporter.enqueue(RRWebEvents.meta("app://x", 400, 800, 1L), "s")
        exporter.enqueue(RRWebEvents.fullSnapshot("data:image/webp;base64,AA", 400, 800, 2L), "s")
        repeat(400) { index ->
            exporter.enqueue(RRWebEvents.frameMutation("data:image/webp;base64,F$index", 10L + index), "s")
        }

        server.enqueue(MockResponse().setResponseCode(200))
        exporter.flush()

        val body = takeRequestBody()
        val metrics = body.getAsJsonArray("resourceMetrics")[0].asJsonObject
            .getAsJsonArray("scopeMetrics")[0].asJsonObject
            .getAsJsonArray("metrics")
        // buffer was capped, but the meta (type 4) and full snapshot (type 2) survived
        assertTrue(metrics.size() <= 300)
        val types = metrics.map { metric ->
            metric.asJsonObject.getAsJsonObject("gauge").getAsJsonArray("dataPoints")[0]
                .asJsonObject.getAsJsonArray("attributes")
                .first { it.asJsonObject["key"].asString == "type" }
                .asJsonObject["value"].asJsonObject["stringValue"].asString
        }
        assertTrue(types.contains("4"), "meta event must survive eviction")
        assertTrue(types.contains("2"), "full snapshot must survive eviction")
    }
}
