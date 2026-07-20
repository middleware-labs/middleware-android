package io.middleware.android.sdk.core.instrumentations.ui

import android.app.Activity
import android.app.Application
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import io.middleware.android.sdk.core.replay.WindowCallbackDelegate
import io.middleware.android.sdk.utils.Constants.COMPONENT_KEY
import io.middleware.android.sdk.utils.Constants.COMPONENT_UI
import io.middleware.android.sdk.utils.Constants.EVENT_TYPE
import io.middleware.android.sdk.utils.Constants.LOG_TAG
import io.middleware.android.sdk.utils.Constants.RUM_TRACER_NAME
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.api.trace.Tracer
import java.util.WeakHashMap

/**
 * Captures user taps and emits them as RUM `tap` spans so the backend can build a
 * click heatmap for the mobile app (parity with the browser SDK's click events).
 *
 * Each activity window's [Window.Callback] is wrapped (the same non-intrusive hook the
 * v3 replay recorder uses — the original callback always runs first). On a completed tap
 * ([MotionEvent.ACTION_UP] within the touch slop of the down position) the decor view is
 * hit-tested to resolve the tapped view's identity, and a span is emitted carrying:
 *
 *  - `event.type` = `tap`, `component` = `ui`
 *  - `x`/`y` and `pageX`/`pageY` — tap position in **dp** (density-independent), matching
 *    the v3 replay's viewport units so a heatmap can overlay the replay screenshots
 *  - `viewport.width`/`viewport.height` — decor-view size in dp, for cross-device normalization
 *  - `target_xpath` — a synthetic selector (`Screen/ViewClass#resource-id`) so the heatmap can
 *    group taps by element and highlight top elements
 *  - `screen.name` and target identity (`target.class`, `target.resource_id`, `target.text`)
 *
 * Enabled by default; opt out via `MiddlewareBuilder.disableUIInstrumentation()`.
 */
class UIInstrumentation : AndroidInstrumentation {

    override fun install(installationContext: InstallationContext) {
        val application = installationContext.application
        val tracer = installationContext.openTelemetry.getTracer(RUM_TRACER_NAME)
        application.registerActivityLifecycleCallbacks(TapLifecycleCallbacks(tracer))
    }

    /** Installs/removes the tap-observing window callback as activities come and go. */
    private class TapLifecycleCallbacks(
        private val tracer: Tracer,
    ) : Application.ActivityLifecycleCallbacks {

        private val wrapped = WeakHashMap<Window, TapCallback>()

        override fun onActivityResumed(activity: Activity) {
            val window = activity.window ?: return
            if (window.callback is TapCallback) {
                return
            }
            val callback = TapCallback(activity, window.callback, tracer)
            wrapped[window] = callback
            window.callback = callback
        }

        override fun onActivityPaused(activity: Activity) {
            val window = activity.window ?: return
            val callback = wrapped.remove(window) ?: return
            // Only restore if nobody else wrapped the callback after us.
            if (window.callback === callback) {
                window.callback = callback.original
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    /**
     * [Window.Callback] decorator that observes touch gestures. The original callback always
     * receives the event first (via [WindowCallbackDelegate]); we only read the coordinates.
     */
    private class TapCallback(
        private val activity: Activity,
        val original: Window.Callback?,
        private val tracer: Tracer,
    ) : WindowCallbackDelegate(original) {

        private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f

        override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
            if (event != null) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                    }

                    MotionEvent.ACTION_UP -> {
                        // Only treat it as a tap if the finger barely moved (not a scroll/drag).
                        val movedX = kotlin.math.abs(event.rawX - downX)
                        val movedY = kotlin.math.abs(event.rawY - downY)
                        if (movedX <= touchSlop && movedY <= touchSlop) {
                            emitTap(event.rawX, event.rawY)
                        }
                    }
                }
            }
            return super.dispatchTouchEvent(event)
        }

        private fun emitTap(rawX: Float, rawY: Float) {
            try {
                val window = activity.window ?: return
                val decorView = window.peekDecorView() ?: return
                if (decorView.width <= 0 || decorView.height <= 0) {
                    return
                }
                val density = activity.resources.displayMetrics.density
                val xDp = (rawX / density).toLong()
                val yDp = (rawY / density).toLong()
                val widthDp = (decorView.width / density).toLong()
                val heightDp = (decorView.height / density).toLong()
                val screenName = activity.javaClass.simpleName

                val target = hitTest(decorView, rawX.toInt(), rawY.toInt())
                val targetClass = target?.javaClass?.simpleName
                val resourceId = target?.let { resourceIdName(it) }
                val text = target?.let { viewText(it) }

                val spanBuilder = tracer.spanBuilder("tap")
                    .setAttribute(COMPONENT_KEY, COMPONENT_UI)
                    .setAttribute(EVENT_TYPE, "tap")
                    .setAttribute("x", xDp)
                    .setAttribute("y", yDp)
                    .setAttribute("pageX", xDp)
                    .setAttribute("pageY", yDp)
                    .setAttribute("viewport.width", widthDp)
                    .setAttribute("viewport.height", heightDp)
                    .setAttribute("target_xpath", buildSelector(screenName, targetClass, resourceId))
                    .setAttribute("screen.name", screenName)

                if (targetClass != null) {
                    spanBuilder.setAttribute("target.class", targetClass)
                }
                if (resourceId != null) {
                    spanBuilder.setAttribute("target.resource_id", resourceId)
                }
                if (text != null) {
                    spanBuilder.setAttribute("target.text", text)
                }
                spanBuilder.startSpan().end()
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Failed to record UI tap event", e)
            }
        }

        /** Deepest visible view whose on-screen bounds contain the tap point. */
        private fun hitTest(root: View, screenX: Int, screenY: Int): View? {
            if (root.visibility != View.VISIBLE) {
                return null
            }
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            if (screenX < left || screenX > left + root.width ||
                screenY < top || screenY > top + root.height
            ) {
                return null
            }
            if (root is ViewGroup) {
                // Front-most child wins, so iterate in reverse draw order.
                for (i in root.childCount - 1 downTo 0) {
                    val hit = hitTest(root.getChildAt(i), screenX, screenY)
                    if (hit != null) {
                        return hit
                    }
                }
            }
            return root
        }

        private fun resourceIdName(view: View): String? {
            val id = view.id
            if (id == View.NO_ID) {
                return null
            }
            return try {
                view.resources.getResourceEntryName(id)
            } catch (e: Resources.NotFoundException) {
                null
            }
        }

        private fun viewText(view: View): String? {
            if (view is TextView && !view.text.isNullOrEmpty()) {
                return view.text.toString()
            }
            val contentDescription = view.contentDescription
            if (!contentDescription.isNullOrEmpty()) {
                return contentDescription.toString()
            }
            return null
        }

        private fun buildSelector(screenName: String, targetClass: String?, resourceId: String?): String {
            val builder = StringBuilder(screenName)
            builder.append('/').append(targetClass ?: "View")
            if (resourceId != null) {
                builder.append('#').append(resourceId)
            }
            return builder.toString()
        }
    }
}
