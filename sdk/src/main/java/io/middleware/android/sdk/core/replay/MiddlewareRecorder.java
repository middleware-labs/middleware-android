package io.middleware.android.sdk.core.replay;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.FrameMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.RequiresApi;

import java.util.LinkedList;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MiddlewareRecorder implements Window.OnFrameMetricsAvailableListener {

    private static final Integer MIN_TIME_BETWEEN_FRAMES_MS = 500;
    private final Middleware middleware;

    private final RRWebRecorder recorder = new RRWebRecorder();
    private Activity activity;

    private Canvas canvas;

    private CanvasDelegate canvasDelegate;

    private Long lastCapturedAtMs = 0L;

    public MiddlewareRecorder(Middleware middleware) {
        this.middleware = middleware;
    }

    public void startRecording(Activity activity) {
        this.activity = activity;

        activity.getWindow().addOnFrameMetricsAvailableListener(this, new Handler(Looper.getMainLooper()));
        activity.getWindow().setCallback(new WindowCallbackDelegate(activity.getWindow().getCallback()) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (event != null) {
                    final Long timestamp = System.currentTimeMillis();
                    recorder.onTouchEvent(timestamp, event);
                }
                return super.dispatchTouchEvent(event);
            }
        });
    }

    public ReplayRecording stopRecording() {
        activity.getWindow().removeOnFrameMetricsAvailableListener(this);
        activity = null;
        ReplayRecording replayRecording = new ReplayRecording();
        replayRecording.setSegmentId(0);
        replayRecording.setPayload(recorder.getRecording());
        ReplayEvent replayEvent = new ReplayEvent();
        replayEvent.setReplayType("mobile");
        replayEvent.setSegmentId(0);
        replayEvent.setTimestamp(recorder.endTimestampMs.doubleValue());
        replayEvent.setReplayStartTimestamp(recorder.startTimestampMs.doubleValue());
        if (middleware != null) {
            middleware.addRumEvent(replayRecording,
                    Attributes.of(stringKey("session.id"),
                            middleware.getRumSessionId()));
        }
        return replayRecording;
    }

    @Override
    public void onFrameMetricsAvailable(Window window, FrameMetrics frameMetrics, int dropCountSinceLastInvocation) {
        View view = activity != null ? activity.getWindow().getDecorView() : null;
        if (view != null) {
            captureFrame(view);
        }
    }

    private void captureFrame(View view) {
        if (view.getWidth() == 0 || view.getHeight() == 0 || view.getVisibility() == View.GONE) {
            return;
        }

        // Cheap debounce for testing
        // TODO remove
        long now = SystemClock.uptimeMillis();
        if (lastCapturedAtMs != 0 && (now - lastCapturedAtMs) < MIN_TIME_BETWEEN_FRAMES_MS) {
            return;
        }
        lastCapturedAtMs = now;

        if (canvasDelegate == null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            Bitmap bitmap = Bitmap.createBitmap(
                    displayMetrics.widthPixels,
                    displayMetrics.heightPixels,
                    Bitmap.Config.ARGB_8888
            );
            canvas = new Canvas(bitmap);
            canvasDelegate = new CanvasDelegate(
                    recorder,
                    canvas
            );
        }

        // Reset the canvas first, as it will be re-used for clipping operations
        canvas.restoreToCount(1);
        recorder.beginFrame(System.currentTimeMillis(), view.getWidth(), view.getHeight());

        int[] location = new int[2];
        LinkedList<View> items = new LinkedList<>();
        items.add(view);

        while (!items.isEmpty()) {
            View item = items.removeFirst();
            if (item != null && item.getVisibility() == View.VISIBLE) {
                if ("exclude".equals(item.getTag())) {
                    // Skip excluded widgets
                } else if (item instanceof ViewGroup && ((ViewGroup) item).willNotDraw()) {
                    // Skip layouts which don't draw anything
                } else {
                    item.getLocationOnScreen(location);
                    float x = location[0] + item.getTranslationX();
                    float y = location[1] + item.getTranslationY();

                    int saveCount = canvasDelegate.save();
                    recorder.translate(x, y);
                    ViewHelper.executeOnDraw(item, canvasDelegate);
                    canvasDelegate.restoreToCount(saveCount);
                }

                if (item instanceof ViewGroup) {
                    int childCount = ((ViewGroup) item).getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        items.add(((ViewGroup) item).getChildAt(i));
                    }
                }
            }
        }
    }
}
