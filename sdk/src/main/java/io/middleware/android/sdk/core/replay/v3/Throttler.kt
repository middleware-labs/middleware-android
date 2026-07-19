package io.middleware.android.sdk.core.replay.v3

import android.os.Handler
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Leading + trailing edge throttle: runs immediately when [throttleDelayMs] has
 * passed since the last run, otherwise schedules a single trailing run on
 * [handler] for when the window ends. Extra calls inside the window are dropped.
 *
 * Adapted from PostHog Android's replay Throttler (MIT licensed).
 */
internal class Throttler(
    private val handler: Handler,
    throttleDelayMs: Long,
) {
    private var lastCall = 0L
    private val delayNs = TimeUnit.MILLISECONDS.toNanos(throttleDelayMs)
    private val isThrottling = AtomicBoolean(false)

    internal fun throttle(runnable: Runnable) {
        val currentTime = System.nanoTime()

        val timeSinceLastExecution = currentTime - lastCall
        if (timeSinceLastExecution >= delayNs) {
            if (!isThrottling.getAndSet(true)) {
                executeAndReleaseThrottle(runnable)
            }
        } else {
            if (!isThrottling.getAndSet(true)) {
                val remainingDelayMs = TimeUnit.NANOSECONDS.toMillis(delayNs - timeSinceLastExecution)
                handler.postDelayed({
                    executeAndReleaseThrottle(runnable)
                }, remainingDelayMs)
            }
        }
    }

    private fun executeAndReleaseThrottle(runnable: Runnable) {
        try {
            lastCall = System.nanoTime()
            runnable.run()
        } finally {
            isThrottling.set(false)
        }
    }
}
