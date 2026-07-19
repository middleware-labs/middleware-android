package io.middleware.android.sdk.core.replay.v3

import android.util.Log
import com.google.gson.Gson
import io.middleware.android.sdk.core.replay.RREvent
import io.middleware.android.sdk.utils.Constants.BASE_ORIGIN
import io.middleware.android.sdk.utils.Constants.LOG_TAG
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

/**
 * Buffers v3 rrweb events and ships them through the metrics endpoint using the
 * same wire format as the browser SDK's session recorder: an OTLP-JSON
 * `MetricsData` whose metrics are `rum_event` gauges with datapoint attributes
 * `type` / `timestamp` / `data`, POSTed gzip-compressed to `{target}/v1/metrics`.
 *
 * Buffering policy (in-memory only):
 *  - flush every [FLUSH_INTERVAL_MS], or immediately once the buffer holds
 *    [FLUSH_THRESHOLD_BYTES] of serialized event data;
 *  - failed batches are retried up to [MAX_RETRIES] times on later flush ticks;
 *  - when the buffer exceeds [MAX_BUFFER_BYTES] / [MAX_BUFFER_EVENTS], the oldest
 *    incremental events are dropped first — Meta and FullSnapshot events are kept
 *    because the frames that follow them are unplayable without them.
 */
internal open class RRWebExporterV3(
    target: String,
    private val token: String,
    private val resourceAttributesProvider: (sessionId: String) -> Map<String, String>,
) {
    private val gson = Gson()
    private val endpoint = "$target/v1/metrics"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private class PendingEvent(
        val sessionId: String,
        val type: Int,
        val timestampMs: Long,
        val dataJson: String,
        var retries: Int = 0,
    ) {
        val isKeyframe: Boolean
            get() = type == RRWebEvents.TYPE_FULL_SNAPSHOT || type == RRWebEvents.TYPE_META
    }

    private val lock = Any()
    private val buffer = ArrayDeque<PendingEvent>()
    private var bufferBytes = 0L

    private val shutdown = AtomicBoolean(false)
    private val flushInFlight = AtomicBoolean(false)

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "mw-replay-v3-export").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
            }
        }

    init {
        scheduler.scheduleWithFixedDelay(
            { flushInternal() },
            FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS
        )
    }

    open fun enqueue(event: RREvent, sessionId: String) {
        if (shutdown.get()) {
            return
        }
        val pending = PendingEvent(
            sessionId,
            event.type,
            event.timestamp,
            gson.toJson(event.data)
        )
        val shouldFlushNow: Boolean
        synchronized(lock) {
            buffer.addLast(pending)
            bufferBytes += pending.dataJson.length
            evictIfNeededLocked()
            shouldFlushNow = bufferBytes >= FLUSH_THRESHOLD_BYTES
        }
        if (shouldFlushNow) {
            scheduler.execute { flushInternal() }
        }
    }

    /** Asynchronously flushes everything currently buffered. */
    open fun flush() {
        if (!shutdown.get()) {
            scheduler.execute { flushInternal() }
        }
    }

    /** Flushes remaining events and stops the scheduler. */
    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            try {
                scheduler.execute { flushInternal() }
            } catch (ignored: Exception) {
                // scheduler already stopped
            }
            scheduler.shutdown()
        }
    }

    private fun evictIfNeededLocked() {
        while (buffer.size > MAX_BUFFER_EVENTS || bufferBytes > MAX_BUFFER_BYTES) {
            val victim = buffer.firstOrNull { !it.isKeyframe } ?: buffer.firstOrNull() ?: return
            buffer.remove(victim)
            bufferBytes -= victim.dataJson.length
            Log.d(LOG_TAG, "Replay v3 buffer full - dropped a type=" + victim.type + " event")
        }
    }

    private fun flushInternal() {
        if (!flushInFlight.compareAndSet(false, true)) {
            return
        }
        try {
            val batch: List<PendingEvent>
            synchronized(lock) {
                if (buffer.isEmpty()) {
                    return
                }
                batch = buffer.toList()
                buffer.clear()
                bufferBytes = 0
            }
            // Keep per-session streams intact: one payload per session id.
            for ((sessionId, events) in batch.groupBy { it.sessionId }) {
                val body = buildOtlpBody(sessionId, events)
                if (!send(body)) {
                    requeue(events)
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Replay v3 flush failed: " + e.message)
        } finally {
            flushInFlight.set(false)
        }
    }

    private fun requeue(events: List<PendingEvent>) {
        val retryable = events.filter { it.retries < MAX_RETRIES }
        val dropped = events.size - retryable.size
        if (dropped > 0) {
            Log.w(LOG_TAG, "Replay v3 dropped $dropped events after $MAX_RETRIES failed sends")
        }
        if (retryable.isEmpty()) {
            return
        }
        synchronized(lock) {
            for (event in retryable.asReversed()) {
                event.retries++
                buffer.addFirst(event)
                bufferBytes += event.dataJson.length
            }
            evictIfNeededLocked()
        }
    }

    /**
     * Builds the OTLP-JSON payload. Field names are camelCase to match the
     * browser SDK's RRWebExporter output, which this backend path was built for.
     */
    private fun buildOtlpBody(sessionId: String, events: List<PendingEvent>): String {
        val resourceAttributes = resourceAttributesProvider(sessionId).map { (key, value) ->
            linkedMapOf("key" to key, "value" to linkedMapOf("stringValue" to value))
        }
        val metrics = events.map { event ->
            linkedMapOf(
                "name" to "rum_event",
                "gauge" to linkedMapOf(
                    "dataPoints" to listOf(
                        linkedMapOf(
                            "attributes" to listOf(
                                attr("type", event.type.toString()),
                                attr("timestamp", event.timestampMs.toString()),
                                // data is the event payload's JSON, shipped as a string
                                attr("data", event.dataJson),
                            ),
                            "timeUnixNano" to (event.timestampMs * 1_000_000L).toString(),
                            "asDouble" to 0,
                        )
                    )
                ),
            )
        }
        val payload = linkedMapOf(
            "resourceMetrics" to listOf(
                linkedMapOf(
                    "resource" to linkedMapOf(
                        "attributes" to resourceAttributes,
                        "droppedAttributesCount" to 0,
                    ),
                    "scopeMetrics" to listOf(
                        linkedMapOf(
                            "scope" to emptyMap<String, Any>(),
                            "metrics" to metrics,
                        )
                    ),
                )
            )
        )
        return gson.toJson(payload)
    }

    private fun attr(key: String, value: String): Map<String, Any> =
        linkedMapOf("key" to key, "value" to linkedMapOf("stringValue" to value))

    private fun send(body: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Origin", BASE_ORIGIN)
                .header("Authorization", token)
                .header("Content-Encoding", "gzip")
                .post(gzip(body).toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(LOG_TAG, "Replay v3 export failed with status " + response.code)
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Replay v3 export failed: " + e.message)
            false
        }
    }

    private fun gzip(body: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 5_000L
        private const val FLUSH_THRESHOLD_BYTES = 512 * 1024L
        private const val MAX_BUFFER_BYTES = 3 * 1024 * 1024L
        private const val MAX_BUFFER_EVENTS = 300
        private const val MAX_RETRIES = 3
    }
}
