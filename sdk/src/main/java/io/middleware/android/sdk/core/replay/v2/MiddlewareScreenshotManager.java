package io.middleware.android.sdk.core.replay.v2;

import static io.middleware.android.sdk.utils.Constants.LOG_TAG;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.middleware.android.sdk.Middleware;

public class MiddlewareScreenshotManager {
    private final String token;
    private final String target;
    private final List<byte[]> bitmaps;
    private final Long firstTime;
    private final Boolean isBlur = true;
    private List<View> sanitizedElements = new ArrayList<>();
    private int lastIndex = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final long SCREENSHOT_INTERVAL = 330;
    private Activity currentActivity;
    private Runnable screenshotRunnable;

    public MiddlewareScreenshotManager(Long firstTime, String target, String token) {
        this.bitmaps = new ArrayList<>();
        this.firstTime = firstTime;
        this.target = target;
        this.token = token;
    }

    public void setActivity(Activity currentActivity) {
        this.currentActivity = currentActivity;
        final View rootView = currentActivity.getWindow().getDecorView().getRootView();
        rootView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                rootView.getViewTreeObserver().removeOnPreDrawListener(this);
                return true;
            }
        });
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Log.d(LOG_TAG, "Layout attached successfully, attaching recording thread.");
            start();
        });
    }

    public void start() {
        screenshotRunnable = new Runnable() {
            @Override
            public void run() {
                View rootView = currentActivity.getWindow().getDecorView().getRootView();
                Bitmap bitmap = getBitmap(rootView);
                if (isBlur) {
                    Canvas canvas = new Canvas(bitmap);
                    for (View view :
                            sanitizedElements) {
                        RectF frame = null;
                        if (view != null && view.isShown()) {
                            frame = getElementFrameInWindow(view);
                            if (frame != null) {
                                Paint paint = new Paint();
                                paint.setFilterBitmap(false);
                                paint.setColor(Color.BLACK);
                                canvas.drawRect(frame, paint);
                            }
                        } else {
                            if (frame != null) {
                                Paint clearPaint = new Paint();
                                clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                                canvas.drawRect(frame, clearPaint);
                            }
                        }
                    }
                }
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 25, byteArrayOutputStream);
                bitmaps.add(byteArrayOutputStream.toByteArray());
                if (bitmaps.size() > 10) {
                    sendScreenshots();
                }
                handler.postDelayed(this, SCREENSHOT_INTERVAL);
            }
        };

        // Initial delay before the first screenshot
        handler.postDelayed(screenshotRunnable, SCREENSHOT_INTERVAL);

    }

    public void stop() {
        if (handler != null && screenshotRunnable != null) {
            handler.removeCallbacks(screenshotRunnable);
            this.screenshotRunnable = null;
            Log.d(LOG_TAG, "Middleware session recording stopped.");
        }
    }

    private RectF getElementFrameInWindow(View element) {
        int[] location = new int[2];
        element.getLocationInWindow(location);
        return new RectF(location[0], location[1], location[0] + element.getWidth(), location[1] + element.getHeight());
    }

    public void setViewForBlur(View myView) {
        sanitizedElements.add(myView);
    }

    private static Bitmap getBitmap(View view) {
        view.setDrawingCacheEnabled(true);
        final Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
        view.setDrawingCacheEnabled(false);
        return bitmap;
    }

    public void sendScreenshots() {
        final String sessionId = Middleware.getInstance().getRumSessionId();
        if (sessionId.isEmpty()) {
            Log.d("Middleware", "SessionId is empty");
            return;
        }
        final String archiveName = sessionId + "-" + String.format("%06d", lastIndex) + ".tar.gz";
        byte[] gzData = createTarEntries();
        new MessageCollector(target, token).sendImagesBatch(gzData, archiveName);
        bitmaps.clear();
    }

    public void removeSanitizedElement(View element) {
        sanitizedElements.remove(element);
    }

    private byte[] createTarEntries() {
        ByteArrayOutputStream tarData = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(tarData)) {
            TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(gzOut);
            bitmaps.forEach(bytes -> {
                try {
                    final String filename = firstTime + "_1_" + String.format("%06d", lastIndex) + ".jpeg";
                    final TarArchiveEntry entry = new TarArchiveEntry(filename);
                    entry.setSize(bytes.length);
                    entry.setModTime(System.currentTimeMillis());
                    entry.setMode(0644);  // File permissions
                    tarOutput.putArchiveEntry(entry);
                    tarOutput.write(bytes);
                    lastIndex += 1;
                    tarOutput.closeArchiveEntry();
                } catch (IOException e) {
                    Log.e(LOG_TAG, Objects.requireNonNull(e.getMessage()));
                }
            });
        } catch (IOException e) {
            Log.e(LOG_TAG, Objects.requireNonNull(e.getMessage()));
        }
        return tarData.toByteArray();
    }
}
