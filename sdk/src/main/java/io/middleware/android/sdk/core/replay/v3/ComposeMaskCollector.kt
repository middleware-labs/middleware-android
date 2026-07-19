package io.middleware.android.sdk.core.replay.v3

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getAllSemanticsNodes
import io.middleware.android.sdk.utils.Constants.LOG_TAG

/**
 * Collects mask rects from a Compose root view's semantics tree. Must run on
 * the main thread (Compose requirement). All Compose types are referenced only
 * inside this class, and callers gate on [isComposeAvailable], so apps without
 * Compose on the classpath never load them.
 */
internal object ComposeMaskCollector {

    private const val ANDROID_COMPOSE_VIEW_CLASS_NAME = "androidx.compose.ui.platform.AndroidComposeView"
    private const val ANDROID_COMPOSE_VIEW = "AndroidComposeView"

    val isComposeAvailable: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            Class.forName(ANDROID_COMPOSE_VIEW_CLASS_NAME)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun isComposeView(view: View): Boolean {
        return isComposeAvailable && view.javaClass.name.contains(ANDROID_COMPOSE_VIEW)
    }

    /**
     * Walks the semantics tree of [view] (an AndroidComposeView) and adds the
     * window-space bounds of every node that must be masked.
     */
    fun collect(
        view: View,
        maskAllTextInputs: Boolean,
        maskAllImages: Boolean,
        out: MutableList<Rect>,
    ) {
        try {
            val semanticsOwner = (view as? RootForTest)?.semanticsOwner ?: return
            val semanticsNodes = semanticsOwner.getAllSemanticsNodes(true)

            for (node in semanticsNodes) {
                val hasText = node.config.contains(SemanticsProperties.Text)
                val hasEditableText = node.config.contains(SemanticsProperties.EditableText)
                val hasPassword = node.config.contains(SemanticsProperties.Password)
                val hasImage = node.config.contains(SemanticsProperties.ContentDescription)

                val isMaskEnabled = node.hasActiveModifier(MwReplayModifiers.MwReplayMask)
                val isUnmaskEnabled = node.hasActiveModifier(MwReplayModifiers.MwReplayUnmask)

                when {
                    // unmask has precedence over everything
                    isUnmaskEnabled -> Unit

                    isMaskEnabled -> out.add(node.boundsRect())

                    (hasText || hasEditableText) && (maskAllTextInputs || hasPassword) ->
                        out.add(node.boundsRect())

                    hasImage && maskAllImages ->
                        out.add(node.boundsRect())
                }
            }
        } catch (e: Throwable) {
            // swallow possible errors due to compose versioning, etc.
            Log.d(LOG_TAG, "Replay v3 compose mask collection failed: " + e.message)
        }
    }

    /**
     * True when the node or any ancestor carries the modifier with value true,
     * so mask/unmask modifiers propagate to descendants.
     */
    private fun SemanticsNode.hasActiveModifier(key: SemanticsPropertyKey<Boolean>): Boolean {
        var current: SemanticsNode? = this
        while (current != null) {
            if (current.config.contains(key) && current.config[key]) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun SemanticsNode.boundsRect(): Rect {
        val bounds = boundsInWindow
        return Rect(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt(),
        )
    }
}
