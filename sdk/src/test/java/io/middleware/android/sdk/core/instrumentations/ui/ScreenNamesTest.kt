package io.middleware.android.sdk.core.instrumentations.ui

import android.app.Activity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenNamesTest {

    @After
    fun teardown() {
        ScreenNames.resetForTest()
    }

    @Test
    fun autoDerivesFromActivityByDefault() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        assertFalse(ScreenNames.isManuallySet())
        assertEquals("Activity", ScreenNames.resolve(activity))
    }

    @Test
    fun manualNameWins() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        ScreenNames.setManual("HomeScreen")
        assertTrue(ScreenNames.isManuallySet())
        assertEquals("HomeScreen", ScreenNames.resolve(activity))
    }

    @Test
    fun resetRestoresAutoDerivation() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        ScreenNames.setManual("HomeScreen")
        ScreenNames.resetForTest()
        assertEquals("Activity", ScreenNames.resolve(activity))
    }
}
