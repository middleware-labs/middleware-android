package io.middleware.android.sdk.core.replay.v3

import android.view.MotionEvent
import android.view.Window
import io.middleware.android.sdk.core.replay.WindowCallbackDelegate
import java.util.WeakHashMap

/**
 * Wraps a window's callback to observe touch down/up events for the replay's
 * touch indicators. The original callback always receives the event first.
 */
internal class TouchEventTracker(
    private val onTouch: (interactionType: Int, xPx: Float, yPx: Float, timestampMs: Long) -> Unit,
) {
    private val originals = WeakHashMap<Window, Window.Callback>()

    fun install(window: Window) {
        val current = window.callback
        if (current is TouchDelegateCallback) {
            return
        }
        originals[window] = current
        window.callback = TouchDelegateCallback(current)
    }

    fun uninstall(window: Window) {
        val original = originals.remove(window)
        // Only restore if nobody else wrapped the callback after us.
        if (original != null && window.callback is TouchDelegateCallback) {
            window.callback = original
        }
    }

    private inner class TouchDelegateCallback(original: Window.Callback?) :
        WindowCallbackDelegate(original) {

        override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
            if (event != null) {
                val interactionType = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> RRWebEvents.MOUSE_INTERACTION_TOUCH_START
                    MotionEvent.ACTION_UP -> RRWebEvents.MOUSE_INTERACTION_TOUCH_END
                    else -> -1
                }
                if (interactionType != -1) {
                    onTouch(interactionType, event.rawX, event.rawY, System.currentTimeMillis())
                }
            }
            return super.dispatchTouchEvent(event)
        }
    }
}
