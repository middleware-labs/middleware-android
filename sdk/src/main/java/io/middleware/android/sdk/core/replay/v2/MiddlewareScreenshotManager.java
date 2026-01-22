package io.middleware.android.sdk.core.replay.v2;

import static io.middleware.android.sdk.utils.Constants.LOG_TAG;

import android.app.Activity;
import android.content.Context;
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

import androidx.compose.ui.platform.AbstractComposeView;
import androidx.compose.ui.platform.ComposeView;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;

interface ScreenshotCallback {
    void onScreenshot(Bitmap bitmap);
}

public class MiddlewareScreenshotManager {
    private String firstTs = "";
    private String lastTs = "";
    private final MiddlewareBuilder builder;
    private final LifecycleManager lifecycleManager;
    private final List<WeakReference<View>> sanitizedElements = new ArrayList<>();
    private Handler mainHandler;
    private WeakReference<Context> uiContext;
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService executorService;
    private Paint maskPaint;
    private int lastOrientation = -1;

    public MiddlewareScreenshotManager(MiddlewareBuilder builder, LifecycleManager lifecycleManager) {
        this.builder = builder;
        this.lifecycleManager = lifecycleManager;
    }

    public void start(Long startTs) {
        firstTs = startTs.toString();
        uiContext = new WeakReference<>(lifecycleManager.getContext());
        lastOrientation = -1;
        scheduledExecutor = Executors.newScheduledThreadPool(2);
        executorService = Executors.newCachedThreadPool();
        checkAndReportOrientationChange();
        long intervalMillis = builder.recordingOptions.getScreenshotInterval();
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            executorService.execute(() -> makeScreenshotAndSaveWithArchive(10));
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);

        scheduledExecutor.scheduleWithFixedDelay(() -> {
            executorService.execute(this::sendScreenshots);
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void makeScreenshotAndSaveWithArchive(int chunk) {
        try {
            checkAndReportOrientationChange();
            Bitmap screenShotBitmap = captureScreenshot();
            File screenShotFolder = getScreenshotFolder();
            File screenShotFile = new File(screenShotFolder, System.currentTimeMillis() + ".jpeg");
            try (FileOutputStream out = new FileOutputStream(screenShotFile)) {
                if (screenShotBitmap != null) {
                    out.write(compress(screenShotBitmap));
                }
            }
            File[] files = screenShotFolder.listFiles();
            if (files != null && files.length >= chunk) {
                archivateFolder(screenShotFolder);
            }

        } catch (IllegalStateException e) {
            Log.e(LOG_TAG, "Screenshot skipped: " + e.getMessage());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error making screenshot: " + e.getMessage());
        }
    }

    private File getScreenshotFolder() {
        Context context = uiContext.get();
        if (context == null) throw new IllegalStateException("No context");
        File folder = new File(context.getFilesDir(), "screenshots");
        folder.mkdirs();
        return folder;
    }

    private Bitmap captureScreenshot() throws Exception {
        Activity activity = lifecycleManager.getCurrentActivity();
        if (activity == null) {
            throw new IllegalStateException("No Activity available for screenshot");
        }

        if (activity.isFinishing() || activity.isDestroyed()) {
            throw new IllegalStateException("Activity is finishing or destroyed while taking screenshot");
        }

        final Bitmap[] result = new Bitmap[1];
        final Exception[] exception = new Exception[1];
        final Object lock = new Object();
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                screenShot(activity, bitmap -> {
                    synchronized (lock) {
                        result[0] = bitmap;
                        lock.notify();
                    }
                });
            } catch (Exception e) {
                synchronized (lock) {
                    lock.notify();
                }
            }
        });
        synchronized (lock) {
            lock.wait(5000); // 5 second timeout
        }
        if (exception[0] != null) throw exception[0];
        if (result[0] == null) throw new Exception("Screenshot timeout");
        return result[0];
    }

    private byte[] compress(Bitmap originalBitmap) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (originalBitmap.getWidth() <= 0 || originalBitmap.getHeight() <= 0) {
                throw new IllegalArgumentException("Invalid bitmap dimensions: " +
                        originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
            }

            int originalWidth = originalBitmap.getWidth();
            int originalHeight = originalBitmap.getHeight();
            float aspectRatio = (float) originalWidth / originalHeight;

            int newWidth, newHeight;

            int minResolution = 320;
            if (originalWidth < originalHeight) {
                newWidth = Math.max(minResolution, 1);
                newHeight = Math.max((int) (newWidth / aspectRatio), 1);
            } else {
                newHeight = Math.max(minResolution, 1);
                newWidth = Math.max((int) (newHeight * aspectRatio), 1);
            }

            String orientation = originalWidth < originalHeight ? "Portrait" : "Landscape";
            Log.d(LOG_TAG, "Screenshot scaling: " + orientation + " " + originalWidth + "x" +
                    originalHeight + " -> " + newWidth + "x" + newHeight);

            Bitmap updated;
            if (originalBitmap.getWidth() == newWidth && originalBitmap.getHeight() == newHeight) {
                updated = originalBitmap;
            } else {
                updated = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    updated.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, builder.recordingOptions.getQualityValue(), outputStream);
                } else {
                    updated.compress(Bitmap.CompressFormat.JPEG, builder.recordingOptions.getQualityValue(), outputStream);
                }
                return outputStream.toByteArray();
            } finally {
                if (updated != originalBitmap) {
                    updated.recycle();
                }
            }
        } finally {
            originalBitmap.recycle();
        }
    }

    private void screenShot(Activity activity, ScreenshotCallback screenshotCallback) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View view = activity.getWindow().getDecorView().getRootView();
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int width = view.getWidth();
            int height = view.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
            );
            if (mainHandler == null) {
                mainHandler = new Handler(Looper.getMainLooper());
            }
            try {
                PixelCopy.request(activity.getWindow(), bitmap, copyResult -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        bitmap.recycle();
                        return;
                    }

                    if (copyResult == PixelCopy.SUCCESS) {
                        try {
                            Bitmap maskedBitmap = applyMaskToScreenshot(bitmap, view);
                            screenshotCallback.onScreenshot(maskedBitmap);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Failed to apply mask: " + e.getMessage());
                            screenshotCallback.onScreenshot(bitmap);
                        }
                    } else {
                        Log.e(LOG_TAG, "PixelCopy failed with result: " + copyResult +
                                ", falling back to oldViewToBitmap");
                        bitmap.recycle();
                        try {
                            screenshotCallback.onScreenshot(oldViewToBitmap(view));
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Fallback screenshot failed: " + e.getMessage());
                        }
                    }
                }, mainHandler);
            } catch (Exception e) {
                Log.e(LOG_TAG, "PixelCopy request failed: " + e.getMessage());
                bitmap.recycle();
            }
        } else {
            try {
                screenshotCallback.onScreenshot(oldViewToBitmap(view));
            } catch (Exception e) {
                Log.e(LOG_TAG, "Screenshot failed: ${e.message}");
            }
        }
    }

    private Bitmap oldViewToBitmap(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        // Handle Jetpack Compose views
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof AbstractComposeView) {
                    child.draw(canvas);
                }
            }
        }

        // Draw masks over sanitized elements
        synchronized (sanitizedElements) {
            sanitizedElements.removeIf(ref -> ref.get() == null);

            for (WeakReference<View> weakRef : sanitizedElements) {
                View sanitizedView = weakRef.get();
                if (sanitizedView != null && sanitizedView.getVisibility() == View.VISIBLE &&
                        sanitizedView.isAttachedToWindow()) {

                    int[] location = new int[2];
                    sanitizedView.getLocationOnScreen(location);
                    int[] rootViewLocation = new int[2];
                    view.getLocationOnScreen(rootViewLocation);

                    int x = location[0] - rootViewLocation[0];
                    int y = location[1] - rootViewLocation[1];

                    canvas.save();
                    canvas.translate(x, y);
                    canvas.drawRect(0f, 0f, sanitizedView.getWidth(), sanitizedView.getHeight(), getMaskPaint());
                    canvas.restore();
                }
            }
        }

        iterateViewGroupForCompose(view, canvas, view);
        return bitmap;
    }

    private void iterateViewGroupForCompose(View rootView, Canvas canvas, View currentView) {
        if (currentView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) currentView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);

                if (child instanceof SanitizableViewGroup) {
                    Log.d(LOG_TAG, "SanitizableViewGroup found");
                    int[] location = new int[2];
                    child.getLocationOnScreen(location);
                    int[] rootViewLocation = new int[2];
                    rootView.getLocationOnScreen(rootViewLocation);
                    int x = location[0] - rootViewLocation[0];
                    int y = location[1] - rootViewLocation[1];

                    canvas.save();
                    canvas.translate(x, y);
                    canvas.drawRect(0f, 0f, child.getWidth(), child.getHeight(), getMaskPaint());
                    canvas.restore();
                } else if (child instanceof ComposeView) {
                    iterateViewGroupForCompose(rootView, canvas, child);
                } else if (child instanceof ViewGroup) {
                    iterateViewGroupForCompose(rootView, canvas, child);
                }
            }
        }
    }

    private Bitmap applyMaskToScreenshot(Bitmap bitmap, View rootView) {
        synchronized (sanitizedElements) {
            sanitizedElements.removeIf(ref -> ref.get() == null);

            if (sanitizedElements.isEmpty()) {
                return bitmap;
            }
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            int[] rootViewLocation = new int[2];
            rootView.getLocationOnScreen(rootViewLocation);
            int maskedCount = 0;
            for (WeakReference<View> weakRef : sanitizedElements) {
                View sanitizedView = weakRef.get();
                if (sanitizedView != null && sanitizedView.getVisibility() == View.VISIBLE &&
                        sanitizedView.isAttachedToWindow()) {
                    int[] location = new int[2];
                    sanitizedView.getLocationOnScreen(location);
                    int x = location[0];
                    int y = location[1];

                    canvas.save();
                    canvas.translate(x, y);
                    canvas.drawRect(0f, 0f, sanitizedView.getWidth(), sanitizedView.getHeight(), getMaskPaint());
                    canvas.restore();
                    maskedCount++;
                }
                if (maskedCount > 0) {
                    Log.d(LOG_TAG, "Applied mask to " + maskedCount + " sanitized element(s)");
                }
            }
            return mutableBitmap;
        }
    }

    private Paint getMaskPaint() {
        if (maskPaint == null) {
            maskPaint = new Paint();
            maskPaint.setStyle(Paint.Style.FILL);
            Bitmap patternBitmap = createCrossStripedPatternBitmap();
            maskPaint.setShader(new BitmapShader(patternBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }
        return maskPaint;
    }

    private Bitmap createCrossStripedPatternBitmap() {
        int patternSize = 80;
        Bitmap patternBitmap = Bitmap.createBitmap(patternSize, patternSize, Bitmap.Config.ARGB_8888);
        Canvas patternCanvas = new Canvas(patternBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.DKGRAY);
        paint.setStyle(Paint.Style.FILL);

        patternCanvas.drawColor(Color.WHITE);

        float stripeWidth = 20f;
        float gap = stripeWidth / 4;
        for (int i = -patternSize; i < patternSize * 2; i += (int) (stripeWidth + gap)) {
            patternCanvas.drawLine(i, -gap, i + patternSize, patternSize + gap, paint);
        }

        patternCanvas.rotate(90f, patternSize / 2f, patternSize / 2f);

        for (int i = -patternSize; i < patternSize * 2; i += (int) (stripeWidth + gap)) {
            patternCanvas.drawLine(i, -gap, i + patternSize, patternSize + gap, paint);
        }

        return patternBitmap;
    }

    public void stop() {
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdownNow();
            scheduledExecutor = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        terminate();
        synchronized (sanitizedElements) {
            sanitizedElements.clear();
        }
        mainHandler = null;
        lastOrientation = -1;
    }

    private void terminate() {
        executorService.execute(() -> {
            try {
                File screenshotFolder = getScreenshotFolder();
                archivateFolder(screenshotFolder);
                sendScreenshots();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error during termination: " + e.getMessage());
            }
        });
    }

    private void checkAndReportOrientationChange() {
        try {
            Context context = uiContext.get();
            if (context == null) return;
            int currentOrientation = context.getResources().getConfiguration().orientation;
            if (currentOrientation != lastOrientation) {
                lastOrientation = currentOrientation;
                String orientationName = currentOrientation == 1 ? "Portrait" :
                        currentOrientation == 3 ? "Landscape" : "Unknown";
                Log.d(LOG_TAG, "Current orientation: " + orientationName);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error checking orientation: " + e.getMessage());
        }
    }

    public void setViewForBlur(View myView) {
        sanitizedElements.add(new WeakReference<>(myView));
    }

    private void archivateFolder(File folder) {
        File[] screenshots = folder.listFiles();
        if (screenshots == null || screenshots.length == 0) {
            Log.d(LOG_TAG, "No screenshots to archive");
            return;
        }

        Arrays.sort(screenshots, Comparator.comparingLong(File::lastModified));

        try {
            ByteArrayOutputStream combinedData = new ByteArrayOutputStream();
            try (GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(combinedData);
                 TarArchiveOutputStream tarOs = new TarArchiveOutputStream(gzos)) {

                for (File jpeg : screenshots) {
                    lastTs = getNameWithoutExtension(jpeg);
                    String filename = firstTs + "_1_" + getNameWithoutExtension(jpeg) + ".jpeg";
                    byte[] readBytes = readFileBytes(jpeg);
                    TarArchiveEntry tarEntry = new TarArchiveEntry(filename);
                    tarEntry.setSize(readBytes.length);
                    tarOs.putArchiveEntry(tarEntry);
                    ByteArrayInputStream byteStream = new ByteArrayInputStream(readBytes);
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = byteStream.read(buffer)) != -1) {
                        tarOs.write(buffer, 0, n);
                    }
                    tarOs.closeArchiveEntry();
                }
            }

            File archiveFolder = getArchiveFolder();
            final String sessionId = Middleware.getInstance().getRumSessionId();
            if (sessionId.isEmpty()) {
                Log.d("Middleware", "SessionId is empty");
                return;
            }
            File archiveFile = new File(archiveFolder, sessionId + "-" + lastTs + ".tar.gz");

            try (FileOutputStream out = new FileOutputStream(archiveFile)) {
                out.write(combinedData.toByteArray());
            }

            executorService.execute(() -> {
                for (File screenshot : screenshots) {
                    deleteSafely(screenshot);
                }
            });
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error archiving folder: " + e.getMessage());
        }
    }

    private byte[] readFileBytes(File file) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = fis.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }
        return buffer.toByteArray();
    }

    private String getNameWithoutExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    private File getArchiveFolder() {
        Context context = uiContext.get();
        if (context == null) throw new IllegalStateException("No context");
        File folder = new File(context.getFilesDir(), "archives");
        folder.mkdirs();
        return folder;
    }

    private void deleteSafely(File file) {
        if (file.exists()) {
            try {
                file.delete();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error deleting file: " + e.getMessage());
            }
        }
    }

    public void sendScreenshots() {
        final String sessionId = Middleware.getInstance().getRumSessionId();
        if (sessionId.isEmpty()) {
            Log.d("Middleware", "SessionId is empty");
            return;
        }
        try {
            File archiveFolder = getArchiveFolder();
            File[] archives = archiveFolder.listFiles();
            if (archives == null || archives.length == 0) return;
            NetworkManager networkManager = new NetworkManager(builder.target, builder.rumAccessToken);
            for (File archive : archives) {
                byte[] imageData = readFileBytes(archive);
                networkManager.sendImages(sessionId, imageData, archive.getName(), new NetworkCallback() {
                    @Override
                    public void onSuccess(String response) {
                        executorService.execute(() -> {
                            deleteSafely(archive);
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                    }
                });
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error sending screenshot archives: " + e.getMessage());
        }
    }

    public void removeSanitizedElement(View element) {
        if (element == null) return;
        synchronized (sanitizedElements) {
            // Use Iterator to safely remove elements while iterating
            Iterator<WeakReference<View>> iterator = sanitizedElements.iterator();
            while (iterator.hasNext()) {
                WeakReference<View> ref = iterator.next();
                View v = ref.get();
                if (v == null || v == element) {
                    iterator.remove();
                }
            }
        }
    }
}