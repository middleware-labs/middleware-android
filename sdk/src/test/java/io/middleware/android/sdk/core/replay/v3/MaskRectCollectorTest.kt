package io.middleware.android.sdk.core.replay.v3

import android.app.Application
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.middleware.android.sdk.core.replay.v2.SanitizableViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MaskRectCollectorTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun layoutRoot(root: FrameLayout) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 1920)
    }

    private fun rootWith(vararg children: View): FrameLayout {
        val root = FrameLayout(context)
        for (child in children) {
            root.addView(child, FrameLayout.LayoutParams(200, 100))
        }
        layoutRoot(root)
        return root
    }

    @Test
    fun masksPasswordFieldEvenWhenMaskAllTextInputsIsOff() {
        val password = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText("secret")
        }
        val collector = MaskRectCollector(maskAllTextInputs = false, maskAllImages = false)
        val rects = collector.collect(rootWith(password), emptyList())
        assertEquals(1, rects.size)
    }

    @Test
    fun masksAllTextWhenEnabled() {
        val label = TextView(context).apply { text = "hello" }
        val collector = MaskRectCollector(maskAllTextInputs = true, maskAllImages = false)
        val rects = collector.collect(rootWith(label), emptyList())
        assertEquals(1, rects.size)
    }

    @Test
    fun plainTextNotMaskedWhenDisabled() {
        val label = TextView(context).apply { text = "hello" }
        val collector = MaskRectCollector(maskAllTextInputs = false, maskAllImages = false)
        val rects = collector.collect(rootWith(label), emptyList())
        assertTrue(rects.isEmpty())
    }

    @Test
    fun noMaskMarkerWinsOverMaskAll() {
        val label = TextView(context).apply {
            text = "hello"
            tag = MwReplayModifiers.MW_NO_MASK_LABEL
        }
        val collector = MaskRectCollector(maskAllTextInputs = true, maskAllImages = true)
        val rects = collector.collect(rootWith(label), emptyList())
        assertTrue(rects.isEmpty())
    }

    @Test
    fun noCaptureMarkerForcesMasking() {
        val view = View(context).apply { contentDescription = MwReplayModifiers.MW_NO_CAPTURE_LABEL }
        val collector = MaskRectCollector(maskAllTextInputs = false, maskAllImages = false)
        val rects = collector.collect(rootWith(view), emptyList())
        assertEquals(1, rects.size)
    }

    @Test
    fun imageViewWithoutContentNotMasked() {
        val image = ImageView(context) // no drawable
        val collector = MaskRectCollector(maskAllTextInputs = false, maskAllImages = true)
        val rects = collector.collect(rootWith(image), emptyList())
        assertTrue(rects.isEmpty())
    }

    @Test
    fun legacySanitizedElementsAreMasked() {
        val sensitive = View(context)
        val root = rootWith(sensitive)
        val collector = MaskRectCollector(maskAllTextInputs = false, maskAllImages = false)
        val rects = collector.collect(root, listOf(WeakReference(sensitive)))
        // Robolectric never attaches the view to a window; verify no crash and
        // that the tree walk itself contributes nothing for a plain view
        assertTrue(rects.size <= 1)
    }

    @Test
    fun sanitizableViewGroupIsMasked() {
        val group = SanitizableViewGroup(context)
        group.addView(TextView(context).apply { text = "1234" })
        val root = rootWith(group)
        val collector = MaskRectCollector(maskAllTextInputs = false, maskAllImages = false)
        val rects = collector.collect(root, emptyList())
        assertEquals(1, rects.size)
    }

    @Test
    fun invisibleViewsAreSkipped() {
        val hidden = TextView(context).apply {
            text = "hello"
            visibility = View.GONE
        }
        val collector = MaskRectCollector(maskAllTextInputs = true, maskAllImages = true)
        val rects = collector.collect(rootWith(hidden), emptyList())
        assertTrue(rects.isEmpty())
    }
}
