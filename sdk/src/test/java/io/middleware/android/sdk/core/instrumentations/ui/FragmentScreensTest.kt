package io.middleware.android.sdk.core.instrumentations.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FragmentScreensTest {

    open class ViewFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View = FrameLayout(requireContext()).apply { id = View.generateViewId() }
    }

    class HomeFragment : ViewFragment()
    class TabFragment : ViewFragment()
    class ConfirmDialog : DialogFragment()

    /** A screen that embeds a child fragment, like a tab or a map. */
    class DetailsFragment : ViewFragment() {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            childFragmentManager.beginTransaction().add(view.id, TabFragment()).commitNow()
        }
    }

    /** Single-activity app with hand-rolled fragment transactions. */
    class SingleActivity : FragmentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, HomeFragment())
                .commitNow()
        }
    }

    /** Single-activity app on Jetpack Navigation. */
    class NavActivity : FragmentActivity() {
        lateinit var host: NavHostFragment

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            host = NavHostFragment()
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, host)
                .setPrimaryNavigationFragment(host)
                .commitNow()
            val provider = host.navController.navigatorProvider
            val fragments = provider.getNavigator(FragmentNavigator::class.java)
            val graph = NavGraph(provider.getNavigator(NavGraphNavigator::class.java))
            graph.addDestination(FragmentNavigator.Destination(fragments).apply {
                route = "home"
                label = "Home Screen"
                setClassName(HomeFragment::class.java.name)
            })
            graph.addDestination(FragmentNavigator.Destination(fragments).apply {
                route = "details/{id}"
                setClassName(DetailsFragment::class.java.name)
            })
            graph.setStartDestination("home")
            host.navController.graph = graph
        }
    }

    private val exporter = InMemorySpanExporter.create()
    private val provider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
    private val application: Application = RuntimeEnvironment.getApplication()

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) =
            FragmentScreens.onActivityResumed(activity)

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    @Before
    fun setup() {
        ScreenViews.resetForTest()
        ScreenNames.resetForTest()
        FragmentScreens.resetForTest()
        ScreenViews.install(provider.get("test"))
        application.registerActivityLifecycleCallbacks(activityCallbacks)
    }

    @After
    fun teardown() {
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        ScreenViews.resetForTest()
        ScreenNames.resetForTest()
        FragmentScreens.resetForTest()
        provider.close()
    }

    private fun screenViews(): List<String> = exporter.finishedSpanItems
        .filter { it.attributes.get(AttributeKey.stringKey("event.type")) == "screen_view" }
        .map { it.attributes.get(AttributeKey.stringKey("screen.name"))!! }

    @Test
    fun activityWithoutFragmentsReportsItself() {
        Robolectric.buildActivity(Activity::class.java).setup()
        assertEquals(listOf("Activity"), screenViews())
    }

    @Test
    fun fragmentScreensReplaceTheActivity() {
        val controller = Robolectric.buildActivity(SingleActivity::class.java).setup()
        val activity = controller.get()
        val fm = activity.supportFragmentManager

        fm.beginTransaction()
            .replace(android.R.id.content, DetailsFragment())
            .addToBackStack(null)
            .commit()
        fm.executePendingTransactions()

        // Dialogs sit over the screen, and the tab nested in Details is part of it.
        ConfirmDialog().showNow(fm, "confirm")
        (fm.findFragmentByTag("confirm") as DialogFragment).dismissNow()

        assertEquals("DetailsFragment", ScreenNames.resolve(activity))

        fm.popBackStackImmediate()

        // Backgrounding and returning is not a new view.
        controller.pause().resume()

        assertEquals(listOf("HomeFragment", "DetailsFragment", "HomeFragment"), screenViews())
    }

    @Test
    fun navigationDestinationsAreNamedFromTheGraph() {
        val activity = Robolectric.buildActivity(NavActivity::class.java).setup().get()

        activity.host.navController.navigate("details/42")
        activity.supportFragmentManager.executePendingTransactions()
        activity.host.childFragmentManager.executePendingTransactions()

        assertEquals(listOf("Home Screen", "details/{id}"), screenViews())
    }
}
