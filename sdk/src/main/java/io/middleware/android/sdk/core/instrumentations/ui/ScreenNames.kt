package io.middleware.android.sdk.core.instrumentations.ui

import android.app.Activity
import java.util.Collections
import java.util.WeakHashMap

/**
 * Screen-name store shared by the tap instrumentation and the v3 session
 * recorder. A manually-set name (e.g. from React Navigation via
 * Middleware.setScreenName) wins over the auto-derived name — mirroring the
 * iOS SDK's ScreenName semantics. Native-only apps never set a manual name, so
 * their screens are auto-derived: the activity's current fragment screen (see
 * [FragmentScreens]) when it hosts one, otherwise the Activity name.
 */
object ScreenNames {

    @Volatile
    private var manualName: String? = null

    private val fragmentScreens: MutableMap<Activity, String> =
        Collections.synchronizedMap(WeakHashMap())

    @JvmStatic
    fun setManual(name: String) {
        manualName = name
    }

    @JvmStatic
    fun isManuallySet(): Boolean = manualName != null

    /** Records the fragment screen [activity] is currently showing. */
    @JvmStatic
    fun setFragmentScreen(activity: Activity, name: String) {
        fragmentScreens[activity] = name
    }

    /** Manual name wins; otherwise the current fragment screen, then the activity. */
    @JvmStatic
    fun resolve(activity: Activity): String =
        manualName ?: fragmentScreens[activity] ?: activity.javaClass.simpleName

    @JvmStatic
    internal fun resetForTest() {
        manualName = null
        fragmentScreens.clear()
    }
}
