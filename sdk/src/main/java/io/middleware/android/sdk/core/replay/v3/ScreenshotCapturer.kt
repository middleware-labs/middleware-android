package io.middleware.android.sdk.core.replay.v3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import io.middleware.android.sdk.utils.Constants.LOG_TAG
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Captures window frames and turns them into masked, compressed data URIs.
 *
 * [capture] must be called on the main thread; the resulting bitmap is handed
 * to [onResult] on the PixelCopy handler thread (API >= 26) or synchronously on
 * the main thread (View.draw fallback). [toMaskedDataUri] is CPU-bound and
 * should run on the capture executor.
 */
internal class ScreenshotCapturer(private val quality: Int) {

    private var pixelCopyThread: HandlerThread? = null
    private var pixelCopyHandler: Handler? = null

    private val maskPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    /** Draws only where the frame is still transparent, i.e. behind what is there. */
    private val behindPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
    }

    @Synchronized
    private fun ensurePixelCopyHandler(): Handler {
        pixelCopyThread?.let { thread ->
            if (thread.isAlive) {
                pixelCopyHandler?.let { return it }
            }
        }
        val thread = HandlerThread("mw-replay-v3-pixelcopy").apply { start() }
        val handler = Handler(thread.looper)
        pixelCopyThread = thread
        pixelCopyHandler = handler
        return handler
    }

    @Synchronized
    fun shutdown() {
        pixelCopyThread?.quitSafely()
        pixelCopyThread = null
        pixelCopyHandler = null
    }

    /**
     * Grabs the current window content. Calls [onResult] with null when the
     * capture failed; the caller simply skips the frame.
     *
     * A window copy does not include [SurfaceView] content — those render to
     * their own surface, which is how Flutter draws its whole UI and how video
     * and map views draw theirs — so each visible SurfaceView is copied
     * separately and composited in.
     */
    fun capture(window: Window, decorView: View, onResult: (Bitmap?) -> Unit) {
        val width = decorView.width
        val height = decorView.height
        if (width <= 0 || height <= 0) {
            onResult(null)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onResult(drawViewToBitmap(decorView))
            return
        }

        val surfaces = visibleSurfaceViews(decorView)
        val decorLocation = IntArray(2).also { decorView.getLocationInWindow(it) }
        val surfaceRects = surfaces.map { surfaceView ->
            val location = IntArray(2).also { surfaceView.getLocationInWindow(it) }
            val left = location[0] - decorLocation[0]
            val top = location[1] - decorLocation[1]
            Rect(left, top, left + surfaceView.width, top + surfaceView.height)
        }

        // Every copy reports back on the PixelCopy handler thread, so the
        // results are only ever touched from that one thread.
        val handler = ensurePixelCopyHandler()
        var windowBitmap: Bitmap? = null
        val surfaceBitmaps = arrayOfNulls<Bitmap>(surfaces.size)
        val pending = AtomicInteger(1 + surfaces.size)
        val finishOne = {
            if (pending.decrementAndGet() == 0) {
                onResult(composite(width, height, windowBitmap, surfaceRects, surfaceBitmaps))
            }
        }

        copy(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888), handler, "window", { bitmap ->
            windowBitmap = bitmap
            finishOne()
        }) { bitmap, listener -> PixelCopy.request(window, bitmap, listener, handler) }

        surfaces.forEachIndexed { index, surfaceView ->
            val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
            copy(bitmap, handler, "surface", { copied ->
                surfaceBitmaps[index] = copied
                finishOne()
            }) { target, listener -> PixelCopy.request(surfaceView, target, listener, handler) }
        }
    }

    /**
     * Runs one PixelCopy [request] into [bitmap] and hands [onDone] the bitmap,
     * or null (recycling it) when the copy failed.
     */
    private fun copy(
        bitmap: Bitmap,
        handler: Handler,
        what: String,
        onDone: (Bitmap?) -> Unit,
        request: (Bitmap, PixelCopy.OnPixelCopyFinishedListener) -> Unit,
    ) {
        val listener = PixelCopy.OnPixelCopyFinishedListener { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                onDone(bitmap)
            } else {
                Log.d(LOG_TAG, "Replay v3 PixelCopy ($what) failed: $copyResult")
                bitmap.recycle()
                onDone(null)
            }
        }
        try {
            request(bitmap, listener)
        } catch (e: Throwable) {
            Log.d(LOG_TAG, "Replay v3 PixelCopy ($what) failed: " + e.message)
            bitmap.recycle()
            // keep the completion count moving on the same thread as the callbacks
            handler.post { onDone(null) }
        }
    }

    /**
     * Lays each copied surface into the window frame. A SurfaceView normally sits
     * behind its window, which leaves a transparent hole for it; there the surface
     * goes underneath so views drawn over it stay visible. If the window is opaque
     * over the surface (the hole reads back as solid), the surface is drawn on top.
     */
    private fun composite(
        width: Int,
        height: Int,
        windowBitmap: Bitmap?,
        surfaceRects: List<Rect>,
        surfaceBitmaps: Array<Bitmap?>,
    ): Bitmap? {
        if (surfaceBitmaps.all { it == null }) {
            return windowBitmap
        }
        val frame = windowBitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        surfaceBitmaps.forEachIndexed { index, surfaceBitmap ->
            if (surfaceBitmap == null) {
                return@forEachIndexed
            }
            val rect = surfaceRects[index]
            val paint = if (windowBitmap != null && isTransparentOver(windowBitmap, rect)) behindPaint else null
            canvas.drawBitmap(surfaceBitmap, null, rect, paint)
            surfaceBitmap.recycle()
        }
        return frame
    }

    private fun isTransparentOver(bitmap: Bitmap, rect: Rect): Boolean {
        val bounds = Rect(rect)
        if (!bounds.intersect(0, 0, bitmap.width, bitmap.height) || bounds.isEmpty) {
            return false
        }
        val xs = intArrayOf(bounds.left + bounds.width() / 4, bounds.centerX(), bounds.right - 1 - bounds.width() / 4)
        val ys = intArrayOf(bounds.top + bounds.height() / 4, bounds.centerY(), bounds.bottom - 1 - bounds.height() / 4)
        for (x in xs) {
            for (y in ys) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) {
                    return false
                }
            }
        }
        return true
    }

    private fun drawViewToBitmap(view: View): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap
        } catch (e: Throwable) {
            Log.d(LOG_TAG, "Replay v3 View.draw fallback failed: " + e.message)
            null
        }
    }

    /**
     * Draws the mask rects (device px), downscales to [SHORT_EDGE_PX] short
     * edge and compresses. Recycles [bitmap]. Returns null on failure.
     */
    fun toMaskedDataUri(bitmap: Bitmap, maskRects: List<Rect>): String? {
        try {
            val canvas = Canvas(bitmap)
            for (rect in maskRects) {
                canvas.drawRoundRect(RectF(rect), MASK_CORNER_RADIUS, MASK_CORNER_RADIUS, maskPaint)
            }

            val width = bitmap.width
            val height = bitmap.height
            val scale = SHORT_EDGE_PX.toFloat() / minOf(width, height)
            val scaled = if (scale < 1f) {
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
                bitmap.recycle()
                scaledBitmap
            } else {
                bitmap
            }

            val output = ByteArrayOutputStream()
            val mimeType: String
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, output)
                mimeType = "image/webp"
            } else {
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
                mimeType = "image/jpeg"
            }
            scaled.recycle()

            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            return "data:$mimeType;base64,$base64"
        } catch (e: Throwable) {
            Log.d(LOG_TAG, "Replay v3 frame processing failed: " + e.message)
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            return null
        }
    }

    companion object {
        /**
         * The on-screen [SurfaceView]s under [root] that have a live surface to copy.
         * Main thread only.
         */
        @JvmStatic
        fun visibleSurfaceViews(root: View): List<SurfaceView> {
            val found = ArrayList<SurfaceView>(1)
            collectSurfaceViews(root, found)
            return found
        }

        private fun collectSurfaceViews(view: View, found: MutableList<SurfaceView>) {
            if (!view.isShown) {
                return
            }
            if (view is SurfaceView) {
                if (view.width > 0 && view.height > 0 && view.holder.surface?.isValid == true) {
                    found.add(view)
                }
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collectSurfaceViews(view.getChildAt(i), found)
                }
            }
        }

        /** Target resolution of the shorter screen edge in the replayed frame. */
        private const val SHORT_EDGE_PX = 640
        private const val MASK_CORNER_RADIUS = 10f
    }
}
