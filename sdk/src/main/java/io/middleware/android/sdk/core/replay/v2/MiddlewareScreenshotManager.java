package io.middleware.android.sdk.core.replay.v2;

import static io.middleware.android.sdk.utils.Constants.LOG_TAG;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;

public class MiddlewareScreenshotManager {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------
    /**
     * Maximum number of screenshots kept in the screenshots/ folder before archiving.
     */
    private static final int ARCHIVE_CHUNK_SIZE = 10;

    /**
     * Minimum short-edge resolution for the compressed output.
     */
    private static final int MIN_RESOLUTION_PX = 320;

    /**
     * Reusable I/O buffer size for streaming file reads/writes.
     */
    private static final int IO_BUFFER_SIZE = 8192;

    /**
     * Guard against concurrent screenshot attempts piling up.
     * If a capture is still in flight we skip the next tick rather than queuing.
     */
    private final AtomicBoolean captureInFlight = new AtomicBoolean(false);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------
    private String firstTs = "";
    private String lastTs = "";

    private final MiddlewareBuilder builder;
    private final LifecycleManager lifecycleManager;

    /**
     * CopyOnWriteArrayList lets us iterate without locking from the main thread
     * while background threads add/remove elements.
     */
    private final CopyOnWriteArrayList<WeakReference<View>> sanitizedElements =
            new CopyOnWriteArrayList<>();

    /**
     * Dedicated single-thread executor for all file / network I/O.
     */
    private ExecutorService ioExecutor;

    /**
     * FIX #6: Single-thread scheduler is sufficient — the send task dispatches
     * to ioExecutor anyway, so a second scheduler thread was wasted.
     */
    private ScheduledExecutorService scheduler;

    /**
     * Main-thread handler – created once and reused.
     * PixelCopy callback is delivered here; we then hand off to ioExecutor.
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WeakReference<Context> uiContext;
    private int lastOrientation = -1;

    /**
     * Pre-built mask paint – created on the IO thread during start() so it is
     * never constructed on the UI thread during a hot screenshot path.
     */
    private volatile Paint maskPaint;

    /**
     * The pattern bitmap backing the mask shader.
     * FIX #7: Kept as a field so it can be recycled on stop() to free native memory.
     */
    private volatile Bitmap maskPatternBitmap;

    /**
     * Shared, long-lived network client (expensive to construct per-call).
     */
    private volatile NetworkManager networkManager;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public MiddlewareScreenshotManager(MiddlewareBuilder builder, LifecycleManager lifecycleManager) {
        this.builder = builder;
        this.lifecycleManager = lifecycleManager;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    public void start(Long startTs) {
        firstTs = startTs.toString();
        uiContext = new WeakReference<>(lifecycleManager.getContext());
        lastOrientation = -1;

        // Single-thread IO executor keeps file writes sequential (no corruption).
        ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mw-screenshot-io");
            t.setPriority(Thread.MIN_PRIORITY); // don't compete with UI
            return t;
        });

        // FIX #6: One thread is enough — capture runs inline, send dispatches to ioExecutor.
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mw-screenshot-scheduler");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        // Pre-warm the mask paint and the network client off the UI thread.
        ioExecutor.execute(() -> {
            getMaskPaint();
            networkManager = new NetworkManager(builder.target, builder.rumAccessToken);
        });

        checkAndReportOrientationChange();

        long intervalMillis = builder.recordingOptions.getScreenshotInterval();

        // Capture task --------------------------------------------------------
        scheduler.scheduleWithFixedDelay(() -> {
            if (captureInFlight.compareAndSet(false, true)) {
                takeScreenshotAsync();
                // captureInFlight is reset inside takeScreenshotAsync callbacks.
            } else {
                Log.d(LOG_TAG, "Screenshot skipped – previous capture still in flight");
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);

        // Send task -----------------------------------------------------------
        scheduler.scheduleWithFixedDelay(
                () -> ioExecutor.execute(this::sendScreenshots),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        // Stop scheduling new work first.
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        // Drain remaining work (archive + send) then shut down.
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.execute(this::terminateFlush);
            ioExecutor.shutdown();
            ioExecutor = null;
        }

        sanitizedElements.clear();
        lastOrientation = -1;

        // FIX #7: Recycle the pattern bitmap to free native memory on stop().
        // maskPaint holds a BitmapShader referencing this bitmap — clear both.
        maskPaint = null;
        Bitmap pattern = maskPatternBitmap;
        if (pattern != null && !pattern.isRecycled()) {
            pattern.recycle();
            maskPatternBitmap = null;
        }
    }

    /**
     * Called by ioExecutor during stop() – archives any pending screenshots then sends.
     */
    private void terminateFlush() {
        try {
            File folder = getScreenshotFolder();
            archivateFolder(folder);
            sendScreenshots();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error during termination: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Screenshot capture – fully off the UI thread except the minimal PixelCopy
    // -------------------------------------------------------------------------

    /**
     * Initiates an async screenshot.
     * <p>
     * Strategy:
     * <ol>
     *   <li>Post a tiny Runnable to the main thread to *start* PixelCopy (required by the API).
     *   <li>PixelCopy delivers its result to {@link #mainHandler} – we immediately hand the
     *       raw bitmap to {@link #ioExecutor} for masking, scaling, compression and saving.
     *   <li>If PixelCopy is unavailable (pre-O) we fall back to View.draw() on the main thread,
     *       but that is a rare path.
     * </ol>
     * The UI thread is therefore only blocked for the PixelCopy *setup* call (~microseconds),
     * NOT for compression, file I/O or network work.
     */
    private void takeScreenshotAsync() {
        mainHandler.post(() -> {
            try {
                Activity activity = lifecycleManager.getCurrentActivity();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    captureInFlight.set(false);
                    return;
                }

                View decorView = activity.getWindow().getDecorView().getRootView();
                if (decorView == null || decorView.getWidth() <= 0 || decorView.getHeight() <= 0) {
                    captureInFlight.set(false);
                    return;
                }

                checkAndReportOrientationChange();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // PixelCopy: GPU-accurate, works with Maps / SurfaceView / TextureView.
                    Bitmap bitmap = Bitmap.createBitmap(
                            decorView.getWidth(), decorView.getHeight(), Bitmap.Config.ARGB_8888);

                    PixelCopy.request(activity.getWindow(), bitmap, copyResult -> {
                        // This callback runs on mainHandler – do the MINIMUM here.
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            bitmap.recycle();
                            captureInFlight.set(false);
                            return;
                        }
                        if (copyResult == PixelCopy.SUCCESS) {
                            // Capture mask rects on main thread (getLocationOnScreen requires it),
                            // then hand bitmap + rects off to the IO thread immediately.
                            List<int[]> maskRects = collectMaskRects(decorView);
                            if (ioExecutor != null && !ioExecutor.isShutdown()) {
                                ioExecutor.execute(() -> processBitmapAsync(bitmap, maskRects));
                            } else {
                                bitmap.recycle();
                                captureInFlight.set(false);
                            }
                        } else {
                            Log.e(LOG_TAG, "PixelCopy failed (" + copyResult + "), using fallback");
                            bitmap.recycle();
                            Bitmap fallback = drawViewToBitmap(decorView);
                            if (fallback != null && ioExecutor != null && !ioExecutor.isShutdown()) {
                                ioExecutor.execute(() -> processBitmapAsync(fallback, null));
                            } else {
                                if (fallback != null) fallback.recycle();
                                captureInFlight.set(false);
                            }
                        }
                    }, mainHandler);

                } else {
                    // Pre-O fallback: View.draw() on the main thread (unavoidable).
                    Bitmap fallback = drawViewToBitmap(decorView);
                    if (fallback != null && ioExecutor != null && !ioExecutor.isShutdown()) {
                        ioExecutor.execute(() -> processBitmapAsync(fallback, null));
                    } else {
                        if (fallback != null) fallback.recycle();
                        captureInFlight.set(false);
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error initiating screenshot: " + e.getMessage());
                captureInFlight.set(false);
            }
        });
    }

    /**
     * Called on the UI thread to snapshot the screen coordinates of all sanitized views.
     * Returns a list of [x, y, width, height] int arrays – safe to pass to the IO thread.
     * FIX #2: Also prunes dead WeakReferences to prevent accumulation over long sessions.
     */
    private List<int[]> collectMaskRects(View rootView) {
        List<int[]> rects = new ArrayList<>();
        List<WeakReference<View>> deadRefs = new ArrayList<>();

        int[] rootLoc = new int[2];
        rootView.getLocationOnScreen(rootLoc);

        for (WeakReference<View> ref : sanitizedElements) {
            View v = ref.get();
            if (v == null) {
                // FIX #2: Queue dead references for removal rather than letting them accumulate.
                deadRefs.add(ref);
                continue;
            }
            if (v.getVisibility() == View.VISIBLE && v.isAttachedToWindow()) {
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                rects.add(new int[]{loc[0], loc[1], v.getWidth(), v.getHeight()});
            }
        }

        // Prune dead refs outside the iteration loop (CopyOnWriteArrayList is safe here).
        sanitizedElements.removeAll(deadRefs);

        collectSanitizableGroups(rootView, rootLoc, rects);
        return rects;
    }

    private void collectSanitizableGroups(View view, int[] rootLoc, List<int[]> out) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof SanitizableViewGroup) {
                int[] loc = new int[2];
                child.getLocationOnScreen(loc);
                out.add(new int[]{loc[0], loc[1], child.getWidth(), child.getHeight()});
            } else {
                collectSanitizableGroups(child, rootLoc, out);
            }
        }
    }

    // -------------------------------------------------------------------------
    // IO-thread: mask → scale → compress → save
    // -------------------------------------------------------------------------

    /**
     * Everything from here down runs entirely on {@link #ioExecutor} – zero UI-thread impact.
     */
    private void processBitmapAsync(Bitmap bitmap, List<int[]> maskRects) {
        try {
            // 1. Apply masks (mutable copy only if needed).
            Bitmap masked = applyMasks(bitmap, maskRects);

            // 2. Scale + compress.
            byte[] compressed = compress(masked); // recycles masked internally

            // 3. Save to disk.
            saveScreenshot(compressed);

            // 4. Archive if enough files have accumulated.
            File folder = getScreenshotFolder();
            File[] files = folder.listFiles();
            if (files != null && files.length >= ARCHIVE_CHUNK_SIZE) {
                archivateFolder(folder);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error processing screenshot: " + e.getMessage());
        } finally {
            captureInFlight.set(false);
        }
    }

    /**
     * Applies mask rectangles to the bitmap.
     * If there are no masks the original bitmap is returned unchanged (no copy).
     */
    private Bitmap applyMasks(Bitmap bitmap, List<int[]> maskRects) {
        if (maskRects == null || maskRects.isEmpty()) return bitmap;

        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        bitmap.recycle();

        Canvas canvas = new Canvas(mutable);
        Paint paint = getMaskPaint();
        for (int[] r : maskRects) {
            canvas.save();
            canvas.translate(r[0], r[1]);
            canvas.drawRect(0f, 0f, r[2], r[3], paint);
            canvas.restore();
        }
        return mutable;
    }

    private void saveScreenshot(byte[] data) {
        try {
            File folder = getScreenshotFolder();
            File file = new File(folder, System.currentTimeMillis() + ".jpeg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error saving screenshot: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Compression (runs on IO thread)
    // -------------------------------------------------------------------------
    private byte[] compress(Bitmap originalBitmap) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (originalBitmap.getWidth() <= 0 || originalBitmap.getHeight() <= 0) {
                throw new IllegalArgumentException("Invalid bitmap dimensions: "
                        + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
            }

            int origW = originalBitmap.getWidth();
            int origH = originalBitmap.getHeight();
            float aspect = (float) origW / origH;

            int newW, newH;
            if (origW < origH) { // portrait
                newW = Math.max(MIN_RESOLUTION_PX, 1);
                newH = Math.max((int) (newW / aspect), 1);
            } else { // landscape / square
                newH = Math.max(MIN_RESOLUTION_PX, 1);
                newW = Math.max((int) (newH * aspect), 1);
            }

            Bitmap scaled = (origW == newW && origH == newH)
                    ? originalBitmap
                    : Bitmap.createScaledBitmap(originalBitmap, newW, newH, true);

            try {
                int quality = builder.recordingOptions.getQualityValue();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // FIX #4: Use WEBP_LOSSY instead of WEBP_LOSSLESS.
                    // Lossless WebP for screen recordings produces unnecessarily large files.
                    // Lossy at the configured quality setting is indistinguishable for replay
                    // and matches the JPEG behaviour on older API levels.
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, outputStream);
                } else {
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
                }
                return outputStream.toByteArray();
            } finally {
                if (scaled != originalBitmap) scaled.recycle();
            }
        } finally {
            originalBitmap.recycle();
        }
    }

    // -------------------------------------------------------------------------
    // Fallback: View.draw() – used pre-API 26 or when PixelCopy fails
    // (runs on UI thread; kept as lean as possible)
    // -------------------------------------------------------------------------
    private Bitmap drawViewToBitmap(View view) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            Log.e(LOG_TAG, "drawViewToBitmap failed: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Mask paint – created lazily on IO thread, never on UI thread
    // -------------------------------------------------------------------------
    private Paint getMaskPaint() {
        if (maskPaint == null) {
            synchronized (this) {
                if (maskPaint == null) {
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setStyle(Paint.Style.FILL);
                    // FIX #7: Store the pattern bitmap as a field so stop() can recycle it.
                    maskPatternBitmap = createCrossStripedPatternBitmap();
                    p.setShader(new BitmapShader(
                            maskPatternBitmap,
                            Shader.TileMode.REPEAT,
                            Shader.TileMode.REPEAT));
                    maskPaint = p;
                }
            }
        }
        return maskPaint;
    }

    private Bitmap createCrossStripedPatternBitmap() {
        int size = 80;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.DKGRAY);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);

        c.drawColor(Color.WHITE);

        float stripeStep = 25f;
        for (float i = -size; i < size * 2; i += stripeStep) {
            c.drawLine(i, -1, i + size, size + 1, p);
        }
        c.rotate(90f, size / 2f, size / 2f);
        for (float i = -size; i < size * 2; i += stripeStep) {
            c.drawLine(i, -1, i + size, size + 1, p);
        }
        return bmp;
    }

    // -------------------------------------------------------------------------
    // Archiving (runs on IO thread)
    // Streams directly to FileOutputStream – never buffers the full archive in RAM.
    // FIX #3: lastTs is now set once from the final sorted element, not inside the loop.
    // -------------------------------------------------------------------------
    private void archivateFolder(File folder) {
        File[] screenshots = folder.listFiles();
        if (screenshots == null || screenshots.length == 0) return;

        Arrays.sort(screenshots, Comparator.comparingLong(File::lastModified));

        final String sessionId = Middleware.getInstance().getRumSessionId();
        if (sessionId.isEmpty()) {
            Log.d(LOG_TAG, "SessionId is empty – skipping archive write");
            return;
        }

        // FIX #3: Set lastTs once from the last (most recent) sorted file, not inside the loop.
        lastTs = getNameWithoutExtension(screenshots[screenshots.length - 1]);

        File archiveFolder = getArchiveFolder();
        File archiveFile = new File(archiveFolder, sessionId + "-" + lastTs + ".tar.gz");

        // Stream directly to disk – never holds the full archive in RAM.
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream tarOs = new TarArchiveOutputStream(gzos)) {

            byte[] buf = new byte[IO_BUFFER_SIZE];

            for (File jpeg : screenshots) {
                String filename = firstTs + "_1_" + getNameWithoutExtension(jpeg) + ".jpeg";

                TarArchiveEntry entry = new TarArchiveEntry(filename);
                entry.setSize(jpeg.length());
                tarOs.putArchiveEntry(entry);

                try (FileInputStream fis = new FileInputStream(jpeg)) {
                    int n;
                    while ((n = fis.read(buf)) != -1) {
                        tarOs.write(buf, 0, n);
                    }
                }
                tarOs.closeArchiveEntry();
            }

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error archiving folder: " + e.getMessage());
            deleteSafely(archiveFile); // remove corrupt/incomplete archive
            return;
        }

        // Delete originals only after successful archive write.
        for (File f : screenshots) deleteSafely(f);
    }

    // -------------------------------------------------------------------------
    // Network send (runs on IO thread)
    // Passes File directly to NetworkManager — no full byte[] copy in RAM.
    // -------------------------------------------------------------------------
    public void sendScreenshots() {
        final String sessionId = Middleware.getInstance().getRumSessionId();
        if (sessionId.isEmpty()) {
            Log.d(LOG_TAG, "SessionId is empty – skipping send");
            return;
        }
        try {
            File archiveFolder = getArchiveFolder();
            File[] archives = archiveFolder.listFiles();
            if (archives == null || archives.length == 0) return;

            NetworkManager nm = networkManager;
            if (nm == null) {
                nm = new NetworkManager(builder.target, builder.rumAccessToken);
                networkManager = nm;
            }
            final NetworkManager finalNm = nm;

            for (File archive : archives) {
                finalNm.sendImages(sessionId, archive, archive.getName(), new NetworkCallback() {
                    @Override
                    public void onSuccess(String response) {
                        deleteSafely(archive);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(LOG_TAG, "Send failed for " + archive.getName() + ": " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error sending screenshot archives: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------
    public void setViewForBlur(View view) {
        sanitizedElements.add(new WeakReference<>(view));
    }

    public void removeSanitizedElement(View element) {
        if (element == null) return;
        sanitizedElements.removeIf(ref -> {
            View v = ref.get();
            return v == null || v == element;
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private void checkAndReportOrientationChange() {
        try {
            Context ctx = uiContext.get();
            if (ctx == null) return;
            int orientation = ctx.getResources().getConfiguration().orientation;
            if (orientation != lastOrientation) {
                lastOrientation = orientation;
                // FIX #5: Use Configuration constants instead of magic numbers.
                // ORIENTATION_LANDSCAPE = 2, not 3 as the original code assumed.
                String name;
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    name = "Portrait";
                } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    name = "Landscape";
                } else {
                    name = "Unknown";
                }
                Log.d(LOG_TAG, "Orientation: " + name);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Orientation check failed: " + e.getMessage());
        }
    }

    private File getScreenshotFolder() {
        Context ctx = uiContext.get();
        if (ctx == null) throw new IllegalStateException("No context");
        File folder = new File(ctx.getFilesDir(), "screenshots");
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        return folder;
    }

    private File getArchiveFolder() {
        Context ctx = uiContext.get();
        if (ctx == null) throw new IllegalStateException("No context");
        File folder = new File(ctx.getFilesDir(), "archives");
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        return folder;
    }

    private String getNameWithoutExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void deleteSafely(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
