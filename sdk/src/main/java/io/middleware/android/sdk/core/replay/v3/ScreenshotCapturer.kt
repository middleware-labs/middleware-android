package io.middleware.android.sdk.core.replay.v3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import io.middleware.android.sdk.utils.Constants.LOG_TAG
import java.io.ByteArrayOutputStream

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
     */
    fun capture(window: Window, decorView: View, onResult: (Bitmap?) -> Unit) {
        val width = decorView.width
        val height = decorView.height
        if (width <= 0 || height <= 0) {
            onResult(null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, bitmap, { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        onResult(bitmap)
                    } else {
                        Log.d(LOG_TAG, "Replay v3 PixelCopy failed: $copyResult")
                        bitmap.recycle()
                        onResult(null)
                    }
                }, ensurePixelCopyHandler())
            } catch (e: Throwable) {
                Log.d(LOG_TAG, "Replay v3 PixelCopy failed: " + e.message)
                bitmap.recycle()
                onResult(null)
            }
        } else {
            onResult(drawViewToBitmap(decorView))
        }
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
        /** Target resolution of the shorter screen edge in the replayed frame. */
        private const val SHORT_EDGE_PX = 640
        private const val MASK_CORNER_RADIUS = 10f
    }
}
