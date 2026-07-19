package io.middleware.android.sdk.core.replay.v3

import android.graphics.Rect
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.VectorDrawable
import io.middleware.android.sdk.core.replay.v2.SanitizableViewGroup
import java.lang.ref.WeakReference

/**
 * Walks a window's view tree on the main thread and collects the screen-space
 * rects that must be blacked out on the captured frame. Rects are in device px
 * (the coordinate space of the PixelCopy bitmap).
 *
 * Masking rules (ported from PostHog Android's screenshot mode):
 *  - `mw-no-mask` in a view's tag or contentDescription always wins;
 *  - `mw-no-capture` forces masking of the whole view;
 *  - TextViews: masked when [maskAllTextInputs] or a password input type;
 *    EditText/Button mask only the text content area;
 *  - Spinners: masked when [maskAllTextInputs];
 *  - ImageViews: masked when [maskAllImages] and the drawable carries real
 *    content (color/gradient/vector/inset/layer drawables are decorative);
 *  - WebViews: masked whenever any masking is on (their content is opaque to us);
 *  - Compose roots: delegated to [ComposeMaskCollector];
 *  - legacy API compat: views registered via Middleware.addSanitizedElement and
 *    [SanitizableViewGroup] containers are always masked.
 */
internal class MaskRectCollector(
    private val maskAllTextInputs: Boolean,
    private val maskAllImages: Boolean,
) {

    private val passwordInputTypes = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    )

    fun collect(root: View, sanitizedElements: List<WeakReference<View>>): List<Rect> {
        val rects = mutableListOf<Rect>()
        for (ref in sanitizedElements) {
            val view = ref.get() ?: continue
            if (view.isVisibleForMasking() && view.isAttachedToWindow && view.rootView === root.rootView) {
                view.globalVisibleRect()?.let { rects.add(it) }
            }
        }
        walk(root, rects, mutableSetOf())
        return rects
    }

    private fun walk(view: View, out: MutableList<Rect>, visited: MutableSet<Int>) {
        val viewId = System.identityHashCode(view)
        if (!visited.add(viewId)) {
            return
        }
        if (!view.isVisibleForMasking()) {
            return
        }

        var walkChildren = false

        when {
            ComposeMaskCollector.isComposeView(view) -> {
                ComposeMaskCollector.collect(view, maskAllTextInputs, maskAllImages, out)
                // also walk view children for interop scenarios (AndroidView etc.)
                walkChildren = true
            }

            view.isUnmasked() -> {
                // mw-no-mask has precedence, skip masking (and don't descend:
                // the marker unmasks the whole subtree)
            }

            view is SanitizableViewGroup || view.isNoCapture() -> {
                view.globalVisibleRect()?.let { out.add(it) }
            }

            view is TextView -> {
                val hasContent = !view.text.isNullOrEmpty() || !view.hint.isNullOrEmpty()
                if (hasContent && view.shouldMaskTextView()) {
                    view.textAreaGlobalVisibleRect()?.let { out.add(it) }
                }
            }

            view is Spinner -> {
                if (maskAllTextInputs) {
                    view.globalVisibleRect()?.let { out.add(it) }
                }
            }

            view is ImageView -> {
                if (maskAllImages && view.drawable?.shouldMaskDrawable() == true) {
                    view.globalVisibleRect()?.let { out.add(it) }
                }
            }

            view is WebView -> {
                if (maskAllTextInputs || maskAllImages) {
                    view.globalVisibleRect()?.let { out.add(it) }
                }
            }

            view is ViewGroup && view.childCount > 0 -> {
                walkChildren = true
            }
        }

        if (walkChildren && view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                walk(child, out, visited)
            }
        }
    }

    private fun TextView.shouldMaskTextView(): Boolean {
        // inputType is 0-based against the variation constants
        return maskAllTextInputs || passwordInputTypes.contains(inputType - 1)
    }

    /**
     * For EditText/Button, shrink the mask to the text content area (excluding
     * padding and compound drawables); other TextViews mask the full view.
     */
    private fun TextView.textAreaGlobalVisibleRect(): Rect? {
        val fullRect = globalVisibleRect() ?: return null
        if (this !is EditText && this !is Button) {
            return fullRect
        }
        val left = fullRect.left + compoundPaddingLeft
        val top = fullRect.top + compoundPaddingTop
        val right = fullRect.right - compoundPaddingRight
        val bottom = fullRect.bottom - compoundPaddingBottom
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else fullRect
    }

    private fun Drawable.shouldMaskDrawable(): Boolean {
        return when (this) {
            is InsetDrawable, is ColorDrawable, is VectorDrawable, is GradientDrawable, is LayerDrawable -> false
            is BitmapDrawable -> bitmap?.isRecycled == false
            else -> true
        }
    }

    private fun View.isNoCapture(): Boolean {
        return (tag as? String)?.contains(MwReplayModifiers.MW_NO_CAPTURE_LABEL, ignoreCase = true) == true ||
            contentDescription?.toString()?.contains(MwReplayModifiers.MW_NO_CAPTURE_LABEL, ignoreCase = true) == true
    }

    private fun View.isUnmasked(): Boolean {
        return (tag as? String)?.contains(MwReplayModifiers.MW_NO_MASK_LABEL, ignoreCase = true) == true ||
            contentDescription?.toString()?.contains(MwReplayModifiers.MW_NO_MASK_LABEL, ignoreCase = true) == true
    }

    private fun View.isVisibleForMasking(): Boolean {
        return visibility == View.VISIBLE && width > 0 && height > 0
    }

    private fun View.globalVisibleRect(): Rect? {
        val rect = Rect()
        return if (getGlobalVisibleRect(rect)) rect else null
    }
}
