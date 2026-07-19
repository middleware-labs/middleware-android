package io.middleware.android.sdk.core.replay.v3

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import io.middleware.android.sdk.builders.MiddlewareBuilder
import io.middleware.android.sdk.core.replay.SessionRecorder
import io.middleware.android.sdk.core.replay.v2.LifecycleManager
import io.middleware.android.sdk.utils.Constants.LOG_TAG
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v3 session recorder: captures throttled, masked screenshots of the current
 * activity window on every draw and emits them as rrweb events (Meta +
 * FullSnapshot per epoch, img-src mutations per frame, MouseInteraction per
 * touch) through [RRWebExporterV3].
 *
 * An "epoch" — Meta + FullSnapshot pair — restarts when the recorder starts,
 * the session id rotates, the viewport size changes (rotation/multi-window),
 * or the app returns to the foreground.
 */
internal class ReplayRecorderV3(
    private val application: Application,
    builder: MiddlewareBuilder,
    private val lifecycleManager: LifecycleManager,
    private val exporter: RRWebExporterV3,
    private val sessionIdProvider: () -> String,
) : SessionRecorder, Application.ActivityLifecycleCallbacks {

    private val recordingOptions = builder.recordingOptions
    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotCapturer = ScreenshotCapturer(recordingOptions.qualityValue)
    private val maskRectCollector = MaskRectCollector(
        recordingOptions.isMaskAllTextInputs,
        recordingOptions.isMaskAllImages,
    )
    private val touchTracker = TouchEventTracker(::onTouchEvent)

    private val sanitizedElements = CopyOnWriteArrayList<WeakReference<View>>()
    private val drawListeners = WeakHashMap<View, NextDrawListener>()

    private val running = AtomicBoolean(false)
    private val captureInFlight = AtomicBoolean(false)

    private var captureExecutor: ExecutorService? = null

    // Epoch state. Written on the capture executor / main thread, volatile for
    // cross-thread visibility.
    @Volatile
    private var sentMeta = false

    @Volatile
    private var lastMetaWidthDp = -1

    @Volatile
    private var lastMetaHeightDp = -1

    @Volatile
    private var lastFrameHash = 0

    @Volatile
    private var lastSessionId: String? = null

    @Volatile
    private var lastScreenName: String? = null

    // ---------------------------------------------------------------------
    // SessionRecorder
    // ---------------------------------------------------------------------

    override fun start(startTimeMs: Long?) {
        if (!running.compareAndSet(false, true)) {
            return
        }
        resetEpoch()
        captureExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "mw-replay-v3-capture").apply {
                priority = Thread.MIN_PRIORITY
            }
        }
        application.registerActivityLifecycleCallbacks(this)
        mainHandler.post {
            lifecycleManager.currentActivity?.let { attach(it) }
        }
        Log.d(LOG_TAG, "Replay v3 recording started")
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        application.unregisterActivityLifecycleCallbacks(this)
        mainHandler.post {
            for ((_, listener) in drawListeners) {
                listener.unregister()
            }
            drawListeners.clear()
            lifecycleManager.currentActivity?.window?.let { touchTracker.uninstall(it) }
        }
        captureExecutor?.shutdown()
        captureExecutor = null
        screenshotCapturer.shutdown()
        exporter.flush()
        Log.d(LOG_TAG, "Replay v3 recording stopped")
    }

    override fun isRunning(): Boolean = running.get()

    override fun setViewForBlur(view: View) {
        sanitizedElements.add(WeakReference(view))
    }

    override fun removeSanitizedElement(element: View?) {
        if (element == null) {
            return
        }
        sanitizedElements.removeIf { ref ->
            val view = ref.get()
            view == null || view === element
        }
    }

    // ---------------------------------------------------------------------
    // Activity lifecycle
    // ---------------------------------------------------------------------

    private var resumedActivities = 0

    override fun onActivityResumed(activity: Activity) {
        val wasBackground = resumedActivities == 0
        resumedActivities++
        if (wasBackground && sentMeta) {
            // returning to the foreground: force a fresh Meta + FullSnapshot
            resetEpoch()
        }
        attach(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
        detach(activity)
        if (resumedActivities == 0) {
            // going background: get buffered events out while the process is alive
            exporter.flush()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun attach(activity: Activity) {
        if (!running.get()) {
            return
        }
        val decorView = activity.window?.peekDecorView() ?: return
        touchTracker.install(activity.window)
        if (!drawListeners.containsKey(decorView)) {
            val listener = NextDrawListener(
                decorView,
                mainHandler,
                recordingOptions.screenshotInterval,
                onDirty = {},
                onThrottledDraw = { captureFrame(activity) },
            )
            listener.register()
            drawListeners[decorView] = listener
        }
        // capture immediately so a static screen doesn't wait for its next draw
        mainHandler.post { captureFrame(activity) }
    }

    private fun detach(activity: Activity) {
        val decorView = activity.window?.peekDecorView()
        if (decorView != null) {
            drawListeners.remove(decorView)?.unregister()
        }
        activity.window?.let { touchTracker.uninstall(it) }
    }

    // ---------------------------------------------------------------------
    // Frame capture (entry on main thread)
    // ---------------------------------------------------------------------

    private fun captureFrame(activity: Activity) {
        if (!running.get() || activity.isFinishing || activity.isDestroyed) {
            return
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            return
        }
        var released = false
        try {
            val decorView = activity.window?.peekDecorView()
            if (decorView == null || decorView.width <= 0 || decorView.height <= 0) {
                captureInFlight.set(false)
                released = true
                return
            }

            val sessionId = sessionIdProvider()
            if (sessionId.isEmpty()) {
                captureInFlight.set(false)
                released = true
                return
            }
            if (sessionId != lastSessionId) {
                // session rotated: the old session's stream is complete, start
                // a new epoch under the new id
                resetEpoch()
                lastSessionId = sessionId
            }

            val density = activity.resources.displayMetrics.density
            val widthDp = (decorView.width / density).toInt()
            val heightDp = (decorView.height / density).toInt()
            val needsMeta = !sentMeta || widthDp != lastMetaWidthDp || heightDp != lastMetaHeightDp
            val href = "android-app://" + activity.packageName + "/" + activity.javaClass.simpleName
            val screenName = activity.javaClass.simpleName

            // main thread: the view tree can't change while we walk it
            val maskRects = maskRectCollector.collect(decorView, sanitizedElements)

            screenshotCapturer.capture(activity.window, decorView) { bitmap ->
                if (bitmap == null) {
                    captureInFlight.set(false)
                    return@capture
                }
                val executor = captureExecutor
                if (executor == null || executor.isShutdown || !running.get()) {
                    bitmap.recycle()
                    captureInFlight.set(false)
                    return@capture
                }
                executor.execute {
                    try {
                        processFrame(bitmap, maskRects, needsMeta, widthDp, heightDp, href, screenName, sessionId)
                    } catch (e: Throwable) {
                        Log.d(LOG_TAG, "Replay v3 frame failed: " + e.message)
                    } finally {
                        captureInFlight.set(false)
                    }
                }
            }
            released = true // ownership passed to the capture callback
        } finally {
            if (!released) {
                captureInFlight.set(false)
            }
        }
    }

    /** Runs on the capture executor. */
    private fun processFrame(
        bitmap: android.graphics.Bitmap,
        maskRects: List<android.graphics.Rect>,
        needsMeta: Boolean,
        widthDp: Int,
        heightDp: Int,
        href: String,
        screenName: String,
        sessionId: String,
    ) {
        val dataUri = screenshotCapturer.toMaskedDataUri(bitmap, maskRects) ?: return
        val frameHash = dataUri.hashCode()
        if (!needsMeta && frameHash == lastFrameHash) {
            return // identical frame, nothing to ship
        }

        val timestamp = System.currentTimeMillis()
        if (needsMeta) {
            exporter.enqueue(RRWebEvents.meta(href, widthDp, heightDp, timestamp), sessionId)
            exporter.enqueue(RRWebEvents.fullSnapshot(dataUri, widthDp, heightDp, timestamp), sessionId)
            sentMeta = true
            lastMetaWidthDp = widthDp
            lastMetaHeightDp = heightDp
        } else {
            exporter.enqueue(RRWebEvents.frameMutation(dataUri, timestamp), sessionId)
        }
        lastFrameHash = frameHash

        if (screenName != lastScreenName) {
            lastScreenName = screenName
            exporter.enqueue(RRWebEvents.screenCustom(screenName, timestamp), sessionId)
        }
    }

    // ---------------------------------------------------------------------
    // Touch capture
    // ---------------------------------------------------------------------

    private fun onTouchEvent(interactionType: Int, xPx: Float, yPx: Float, timestampMs: Long) {
        if (!running.get() || !sentMeta) {
            return // touches before the first FullSnapshot are unplayable
        }
        val sessionId = lastSessionId ?: return
        val activity = lifecycleManager.currentActivity ?: return
        val density = activity.resources.displayMetrics.density
        val event = RRWebEvents.touch(
            interactionType,
            (xPx / density).toInt(),
            (yPx / density).toInt(),
            timestampMs,
        )
        exporter.enqueue(event, sessionId)
    }

    private fun resetEpoch() {
        sentMeta = false
        lastMetaWidthDp = -1
        lastMetaHeightDp = -1
        lastFrameHash = 0
        lastScreenName = null
    }
}
