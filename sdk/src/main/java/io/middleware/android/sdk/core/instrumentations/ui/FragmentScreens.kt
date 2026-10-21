package io.middleware.android.sdk.core.instrumentations.ui

import android.app.Activity
import android.content.res.Resources
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.NavHostFragment
import java.util.Collections
import java.util.WeakHashMap

/**
 * Reports fragment screens as `screen_view`s, so a single-activity app (Jetpack
 * Navigation or hand-rolled fragment transactions) gets one view per screen
 * instead of one `MainActivity` view for the whole session.
 *
 * A resumed fragment is a screen when it is what the user navigated to: added
 * directly to the activity or as a destination of a [NavHostFragment], with a
 * visible view. Dialogs and bottom sheets ([DialogFragment]) sit over a screen,
 * and fragments nested inside a screen (tabs, embedded maps) are part of it, so
 * neither changes the screen.
 */
internal object FragmentScreens {

    private val registered: MutableSet<FragmentActivity> =
        Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))

    private val callbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (!isScreen(f)) {
                return
            }
            val activity = f.activity ?: return
            ScreenNames.setFragmentScreen(activity, screenName(f))
            ScreenViews.record(ScreenNames.resolve(activity))
        }
    }

    /**
     * Reports the screen for a resumed activity: the activity itself, unless it shows a
     * fragment screen, which reports itself once it resumes — so a single-activity app's
     * journey doesn't open with a MainActivity view.
     *
     * Fragment callbacks are registered here because onActivityResumed runs before
     * FragmentActivity resumes its fragments in onPostResume: the first screen is never
     * missed, even when the SDK starts after the activity was created.
     */
    @JvmStatic
    fun onActivityResumed(activity: Activity) {
        if (activity is FragmentActivity && registered.add(activity)) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)
        }
        if (!hostsScreen(activity)) {
            ScreenViews.record(ScreenNames.resolve(activity))
        }
    }

    private fun hostsScreen(activity: Activity): Boolean =
        activity is FragmentActivity &&
            activity.supportFragmentManager.fragments.any {
                it !is DialogFragment && it.view != null && !it.isHidden
            }

    @JvmStatic
    internal fun isScreen(f: Fragment): Boolean {
        if (f is DialogFragment || f is NavHostFragment || f.view == null || f.isHidden) {
            return false
        }
        val parent = f.parentFragment
        return parent == null || parent is NavHostFragment
    }

    /**
     * A navigation destination is named from the graph (label, route, then id), which
     * survives R8 renaming the fragment class; any other fragment by its class name.
     */
    @JvmStatic
    internal fun screenName(f: Fragment): String {
        val navHost = f.parentFragment as? NavHostFragment
        val destination = navHost?.navController?.currentDestination
        if (destination != null) {
            destination.label?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            destination.route?.takeIf { it.isNotBlank() }?.let { return it }
            try {
                return f.resources.getResourceEntryName(destination.id)
            } catch (e: Resources.NotFoundException) {
                // Generated id (route-based graph without a route string): fall through.
            }
        }
        return f.javaClass.simpleName
    }

    @JvmStatic
    internal fun resetForTest() {
        registered.clear()
    }
}
