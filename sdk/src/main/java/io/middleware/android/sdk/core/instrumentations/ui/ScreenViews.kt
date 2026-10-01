package io.middleware.android.sdk.core.instrumentations.ui

import io.middleware.android.sdk.utils.Constants.COMPONENT_KEY
import io.middleware.android.sdk.utils.Constants.COMPONENT_UI
import io.middleware.android.sdk.utils.Constants.EVENT_TYPE
import io.opentelemetry.api.trace.Tracer

/**
 * Emits a `screen_view` span (the mobile counterpart of the browser's `pageview`)
 * whenever the visible screen changes; the backend counts these as RUM views.
 *
 * Armed only by [UIInstrumentation], so hybrid hosts (Flutter / React Native) that
 * disable UI instrumentation and emit their own `screen_view` from Dart/JS are not
 * double-counted when they push route names through `Middleware.setScreenName`.
 */
object ScreenViews {

    const val EVENT_TYPE_SCREEN_VIEW = "screen_view"

    @Volatile
    private var tracer: Tracer? = null
    private var lastScreenName: String? = null

    @JvmStatic
    fun install(tracer: Tracer) {
        this.tracer = tracer
    }

    /** Records a view of [screenName] unless it is already the current screen. */
    @JvmStatic
    fun record(screenName: String) {
        val tracer = tracer ?: return
        val previous: String?
        synchronized(this) {
            if (screenName == lastScreenName) {
                return
            }
            previous = lastScreenName
            lastScreenName = screenName
        }
        val span = tracer.spanBuilder(screenName)
            .setAttribute(COMPONENT_KEY, COMPONENT_UI)
            .setAttribute(EVENT_TYPE, EVENT_TYPE_SCREEN_VIEW)
            .setAttribute("screen.name", screenName)
        if (previous != null) {
            span.setAttribute("last.screen.name", previous)
        }
        span.startSpan().end()
    }

    @JvmStatic
    internal fun resetForTest() {
        synchronized(this) {
            tracer = null
            lastScreenName = null
        }
    }
}
