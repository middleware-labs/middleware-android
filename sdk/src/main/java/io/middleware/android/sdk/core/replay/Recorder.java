package io.middleware.android.sdk.core.replay;

import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;

public interface Recorder {
    void beginFrame(long timestampMs, int width, int height);

    void save();

    void restore();

    void restoreToCount(int currentSaveCount, int targetSaveCount);

    void translate(float dx, float dy);

    void clipRectF(float left, float top, float right, float bottom);

    void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, Paint paint);

    void drawCircle(float cx, float cy, float radius, Paint paint);

    void drawText(CharSequence text, int start, int end, float x, float y, Paint paint);

    void drawRect(float left, float top, float right, float bottom, Paint paint);

    void concat(Matrix matrix);

    void scale(float sx, float sy);

    void rotate(float degrees);

    void skew(float sx, float sy);

    void setMatrix(Matrix matrix);

    void onTouchEvent(long timestampMs, MotionEvent event);

    void drawPath(Path path, Paint paint);
}
