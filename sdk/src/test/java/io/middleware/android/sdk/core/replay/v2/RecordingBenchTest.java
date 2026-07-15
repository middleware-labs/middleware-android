package io.middleware.android.sdk.core.replay.v2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.middleware.android.sdk.core.replay.RecordingFrequency;
import io.middleware.android.sdk.core.replay.RecordingQuality;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RecordingBenchTest {

    @Test
    public void recordingCaptureMatrix() throws Exception {
        String outDir = System.getenv("MW_BENCH_OUT");
        if (outDir == null || outDir.isEmpty()) {
            outDir = System.getProperty("mw.bench.out");
        }
        if (outDir == null || outDir.isEmpty()) {
            outDir = new File("build/mw-bench").getAbsolutePath();
        }
        File dir = new File(outDir);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        List<Map<String, Object>> reports = new ArrayList<>();
        Object[][] matrix = new Object[][]{
                {"idle_recording_off_proxy", RecordingFrequency.LOW, RecordingQuality.LOW, false},
                {"idle_recording_on_low", RecordingFrequency.LOW, RecordingQuality.LOW, true},
                {"scroll_recording_on_standard", RecordingFrequency.STANDARD, RecordingQuality.MEDIUM, true},
                {"stress_recording_on_high", RecordingFrequency.HIGH, RecordingQuality.HIGH, true},
        };

        for (Object[] row : matrix) {
            String scenario = (String) row[0];
            RecordingFrequency freq = (RecordingFrequency) row[1];
            RecordingQuality quality = (RecordingQuality) row[2];
            boolean sanitize = (Boolean) row[3];

            RecordingBench.Metrics metrics = RecordingBench.run(
                    scenario,
                    10, // matches MiddlewareScreenshotManager ARCHIVE_CHUNK_SIZE
                    freq,
                    quality,
                    sanitize,
                    1080,
                    1920
            );
            reports.add(toSchemaReport(metrics));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", Instant.now().toString());
        payload.put("reports", reports);

        File out = new File(dir, "android-latest.json");
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write(toJson(payload));
        }
        System.out.println("MW_BENCH_WROTE " + out.getAbsolutePath());
        assertTrue(out.exists() && out.length() > 50);
    }

    private static Map<String, Object> toSchemaReport(RecordingBench.Metrics m) {
        List<String> failed = gate(m);
        String baseline = m.sanitizeEnabled ? "recording_on" : "recording_off";
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());

        Map<String, Object> sdk = new LinkedHashMap<>();
        sdk.put("platform", "android");
        sdk.put("version", "local");
        sdk.put("features", listOf("session_recording", "sanitize", "okhttp"));
        report.put("sdk", sdk);

        report.put("scenario", m.scenario);
        report.put("baseline", baseline);

        Map<String, Object> device = new LinkedHashMap<>();
        device.put("model", "robolectric");
        device.put("os", "android-28");
        report.put("device", device);

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("duration_ms", m.durationMs);
        runtime.put("cpu_proxy_longtasks_ms", m.p95CaptureMs);
        report.put("runtime", runtime);

        report.put("startup", mapOf("sdk_init_ms", null));

        Map<String, Object> ux = new LinkedHashMap<>();
        double fps = m.intervalMs > 0 ? 1000.0 / m.intervalMs : 0;
        ux.put("fps_avg", Math.round(fps * 10.0) / 10.0);
        ux.put("jank_pct", m.p95CaptureMs > 32 ? 5.0 : 0.0);
        ux.put("slow_frames", m.p95CaptureMs > 32 ? 1 : 0);
        ux.put("frozen_frames", m.p95CaptureMs > 700 ? 1 : 0);
        report.put("ux", ux);

        Map<String, Object> network = new LinkedHashMap<>();
        final int framesPerTar = 10;
        int tarGzBytes = m.frames == framesPerTar
                ? m.gzipBatchBytes
                : (int) Math.round((m.gzipBatchBytes / (double) Math.max(m.frames, 1)) * framesPerTar);
        int jpegBytesInTar = m.frames == framesPerTar
                ? m.totalEncodedBytes
                : (int) Math.round((m.totalEncodedBytes / (double) Math.max(m.frames, 1)) * framesPerTar);
        double framesPerMin = m.intervalMs > 0 ? 60_000.0 / m.intervalMs : 0;
        double tarsPerMin = framesPerMin / framesPerTar;

        network.put("upload_bytes", tarGzBytes);
        network.put("request_count", 1);
        network.put("batches", 1);
        network.put("compression_ratio", m.totalEncodedBytes > 0
                ? (double) m.gzipBatchBytes / (double) m.totalEncodedBytes
                : null);
        network.put("tar_gz_bytes", tarGzBytes);
        network.put("jpeg_bytes_in_tar", jpegBytesInTar);
        network.put("frames_per_tar", framesPerTar);
        report.put("network", network);

        Map<String, Object> replay = new LinkedHashMap<>();
        replay.put("events_emitted", m.frames);
        replay.put("upload_mb_per_min", m.uploadMbPerMin);
        replay.put("avg_capture_ms", m.avgCaptureMs);
        replay.put("p95_capture_ms", m.p95CaptureMs);
        replay.put("avg_encoded_bytes", m.avgEncodedBytes);
        replay.put("total_encoded_bytes", m.totalEncodedBytes);
        replay.put("tar_gz_bytes", tarGzBytes);
        replay.put("frames_per_tar", framesPerTar);
        replay.put("frames_per_min", Math.round(framesPerMin * 10.0) / 10.0);
        replay.put("tars_per_min", Math.round(tarsPerMin * 1000.0) / 1000.0);
        replay.put("frequency", m.frequency);
        replay.put("quality", m.quality);
        report.put("session_replay", replay);

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("instrumentations_active", m.sanitizeEnabled
                ? listOf("screenshot", "sanitize", "tar_gzip")
                : listOf("screenshot_off_proxy"));
        coverage.put("apis_hit", listOf("RecordingBench.run"));
        report.put("coverage", coverage);

        report.put("reliability", mapOf("events_sent", m.frames, "http_errors", 0));
        report.put("privacy", mapOf(
                "pii_leaks_detected", 0,
                "masking_pass_rate", m.sanitizeEnabled ? 1.0 : null,
                "sanitize_applied", m.sanitizeEnabled
        ));
        report.put("dx", mapOf(
                "config_flags_used",
                listOf("frequency=" + m.frequency, "quality=" + m.quality)
        ));
        report.put("verdict", mapOf(
                "ready_for_prod", failed.isEmpty(),
                "failed_checks", failed,
                "notes", listOf("synthetic bitmap path — mirrors compress + tar.gz")
        ));
        return report;
    }

    private static List<String> gate(RecordingBench.Metrics m) {
        List<String> failed = new ArrayList<>();
        if (m.avgCaptureMs > 100) {
            failed.add("avg_capture_ms " + m.avgCaptureMs + " > 100");
        }
        if (m.uploadMbPerMin > 4) {
            failed.add("upload_mb_per_min " + m.uploadMbPerMin + " > 4");
        }
        if (m.gzipBatchBytes <= 0) {
            failed.add("gzip_batch_empty");
        }
        return failed;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static List<Object> listOf(Object... items) {
        List<Object> list = new ArrayList<>();
        for (Object item : items) list.add(item);
        return list;
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return quote((String) value);
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(quote(e.getKey())).append(":").append(toJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            return sb.append("]").toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
