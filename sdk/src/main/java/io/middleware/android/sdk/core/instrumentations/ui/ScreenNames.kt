package io.middleware.android.sdk.core.instrumentations.ui

import android.app.Activity

/**
 * Screen-name store shared by the tap instrumentation and the v3 session
 * recorder. A manually-set name (e.g. from React Navigation via
 * Middleware.setScreenName) wins over the auto-derived Activity name —
 * mirroring the iOS SDK's ScreenName semantics. Native-only apps never set a
 * manual name, so their behavior is unchanged.
 */
object ScreenNames {

    @Volatile
    private var manualName: String? = null

    @JvmStatic
    fun setManual(name: String) {
        manualName = name
    }

    @JvmStatic
    fun isManuallySet(): Boolean = manualName != null

    /** Manual name wins; otherwise auto-derive from the activity. */
    @JvmStatic
    fun resolve(activity: Activity): String =
        manualName ?: activity.javaClass.simpleName

    @JvmStatic
    internal fun resetForTest() {
        manualName = null
    }
}
