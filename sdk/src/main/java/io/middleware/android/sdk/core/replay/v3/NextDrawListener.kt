package io.middleware.android.sdk.core.replay.v3

import android.os.Handler
import android.view.View
import android.view.ViewTreeObserver

/**
 * Triggers a throttled capture callback every time the given view (a window's
 * decor view) draws. Also flips [onDirty] on every draw so a concurrent
 * mask-rect walk can detect that the screen changed under it.
 *
 * Adapted from Curtains' NextDrawListener via PostHog Android (MIT licensed).
 */
internal class NextDrawListener(
    private val view: View,
    mainHandler: Handler,
    throttleDelayMs: Long,
    private val onDirty: () -> Unit,
    private val onThrottledDraw: () -> Unit,
) : ViewTreeObserver.OnDrawListener {
    private val throttler = Throttler(mainHandler, throttleDelayMs)

    override fun onDraw() {
        onDirty()
        throttler.throttle {
            onThrottledDraw()
        }
    }

    fun register() {
        if (view.isAlive()) {
            view.viewTreeObserver?.addOnDrawListener(this)
        }
    }

    fun unregister() {
        if (view.isAlive()) {
            view.viewTreeObserver?.removeOnDrawListener(this)
        }
    }
}

internal fun View.isAlive(): Boolean {
    return viewTreeObserver?.isAlive == true
}
