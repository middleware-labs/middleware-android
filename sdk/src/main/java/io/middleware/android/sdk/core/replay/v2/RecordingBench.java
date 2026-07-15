package io.middleware.android.sdk.core.replay.v2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.middleware.android.sdk.core.replay.RecordingFrequency;
import io.middleware.android.sdk.core.replay.RecordingQuality;

/**
 * Offline session-recording micro-benchmark mirroring
 * {@link MiddlewareScreenshotManager} compress + tar.gz batching.
 * Runs without a live Activity (unit-test friendly).
 */
public final class RecordingBench {

    private static final int MIN_RESOLUTION_PX = 320;

    private RecordingBench() {
    }

    public static final class Metrics {
        public final String scenario;
        public final String frequency;
        public final String quality;
        public final int frames;
        public final double durationMs;
        public final double avgCaptureMs;
        public final double p95CaptureMs;
        public final int avgEncodedBytes;
        public final int totalEncodedBytes;
        public final int gzipBatchBytes;
        public final double uploadMbPerMin;
        public final long intervalMs;
        public final int qualityValue;
        public final boolean sanitizeEnabled;

        Metrics(
                String scenario,
                String frequency,
                String quality,
                int frames,
                double durationMs,
                double avgCaptureMs,
                double p95CaptureMs,
                int avgEncodedBytes,
                int totalEncodedBytes,
                int gzipBatchBytes,
                double uploadMbPerMin,
                long intervalMs,
                int qualityValue,
                boolean sanitizeEnabled
        ) {
            this.scenario = scenario;
            this.frequency = frequency;
            this.quality = quality;
            this.frames = frames;
            this.durationMs = durationMs;
            this.avgCaptureMs = avgCaptureMs;
            this.p95CaptureMs = p95CaptureMs;
            this.avgEncodedBytes = avgEncodedBytes;
            this.totalEncodedBytes = totalEncodedBytes;
            this.gzipBatchBytes = gzipBatchBytes;
            this.uploadMbPerMin = uploadMbPerMin;
            this.intervalMs = intervalMs;
            this.qualityValue = qualityValue;
            this.sanitizeEnabled = sanitizeEnabled;
        }
    }

    public static Metrics run(
            String scenario,
            int frames,
            RecordingFrequency frequency,
            RecordingQuality quality,
            boolean sanitize,
            int width,
            int height
    ) throws Exception {
        RecordingOptions options = new RecordingOptions.Builder()
                .setFrequency(frequency)
                .setQuality(quality)
                .build();

        List<Double> samples = new ArrayList<>();
        List<byte[]> batch = new ArrayList<>();
        long t0 = System.nanoTime();

        for (int i = 0; i < frames; i++) {
            long frameStart = System.nanoTime();
            Bitmap screen = synthesizeScreen(width, height, i, sanitize);
            byte[] encoded = compress(screen, options.getQualityValue());
            samples.add((System.nanoTime() - frameStart) / 1_000_000.0);
            batch.add(encoded);
        }

        double durationMs = (System.nanoTime() - t0) / 1_000_000.0;
        byte[] gzip = tarGzip(batch);
        Collections.sort(samples);
        double p95 = percentile(samples, 95);
        double avg = avg(samples);
        int total = 0;
        for (byte[] b : batch) total += b.length;
        int avgBytes = total / Math.max(batch.size(), 1);

        long intervalMs = options.getScreenshotInterval();
        double framesPerMin = 60_000.0 / Math.max(intervalMs, 1);
        double bytesPerFrame = (double) gzip.length / Math.max(frames, 1);
        double uploadMbPerMin = (bytesPerFrame * framesPerMin) / (1024.0 * 1024.0);

        return new Metrics(
                scenario,
                frequency.name().toLowerCase(Locale.US),
                quality.name().toLowerCase(Locale.US),
                frames,
                round1(durationMs),
                round1(avg),
                round1(p95),
                avgBytes,
                total,
                gzip.length,
                round3(uploadMbPerMin),
                intervalMs,
                options.getQualityValue(),
                sanitize
        );
    }

    private static Bitmap synthesizeScreen(int width, int height, int frame, boolean sanitize) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.rgb(247, 244, 239));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(34f);
        for (int i = 0; i < 20; i++) {
            paint.setColor(i % 2 == 0 ? Color.WHITE : Color.rgb(240, 236, 230));
            canvas.drawRect(0, 80 + i * 70, width, 140 + i * 70, paint);
            paint.setColor(Color.rgb(40, 36, 30));
            canvas.drawText("Bench row " + i + " frame " + frame, 40, 125 + i * 70, paint);
        }
        // Payment field region
        paint.setColor(Color.WHITE);
        canvas.drawRect(40, height - 280, width - 40, height - 180, paint);
        paint.setColor(Color.DKGRAY);
        paint.setTextSize(40f);
        String card = sanitize ? "4111111111111111" : "Ada Lovelace";
        canvas.drawText(card, 60, height - 210, paint);
        if (sanitize) {
            // Cross-stripe mask over the card field (production sanitization proxy)
            Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
            mask.setColor(Color.argb(180, 100, 100, 100));
            mask.setStrokeWidth(6f);
            for (float x = 40; x < width - 40; x += 22) {
                canvas.drawLine(x, height - 280, x + 80, height - 180, mask);
            }
            // Confirm mask painted over digits for privacy score
            paint.setColor(Color.LTGRAY);
            canvas.drawRect(40, height - 280, width - 40, height - 180, paint);
        }
        return bmp;
    }

    /** Mirrors MiddlewareScreenshotManager.compress scaling + quality. */
    static byte[] compress(Bitmap originalBitmap, int quality) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int origW = originalBitmap.getWidth();
            int origH = originalBitmap.getHeight();
            float aspect = (float) origW / Math.max(origH, 1);

            int newW;
            int newH;
            if (origW < origH) {
                newW = Math.max(MIN_RESOLUTION_PX, 1);
                newH = Math.max((int) (newW / aspect), 1);
            } else {
                newH = Math.max(MIN_RESOLUTION_PX, 1);
                newW = Math.max((int) (newH * aspect), 1);
            }

            Bitmap scaled = (origW == newW && origH == newH)
                    ? originalBitmap
                    : Bitmap.createScaledBitmap(originalBitmap, newW, newH, true);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, outputStream);
                } else {
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
                }
                return outputStream.toByteArray();
            } finally {
                if (scaled != originalBitmap) {
                    scaled.recycle();
                }
                originalBitmap.recycle();
            }
        }
    }

    static byte[] tarGzip(List<byte[]> frames) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzos)) {
            long base = System.currentTimeMillis();
            for (int i = 0; i < frames.size(); i++) {
                byte[] data = frames.get(i);
                String name = base + "_1_" + (base + i) + ".jpeg";
                TarArchiveEntry entry = new TarArchiveEntry(name);
                entry.setSize(data.length);
                tar.putArchiveEntry(entry);
                tar.write(data);
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
        return bos.toByteArray();
    }

    private static double avg(List<Double> values) {
        double s = 0;
        for (double v : values) s += v;
        return s / Math.max(values.size(), 1);
    }

    private static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int idx = Math.min(sorted.size() - 1, (int) Math.ceil((p / 100.0) * sorted.size()) - 1);
        return sorted.get(Math.max(0, idx));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
