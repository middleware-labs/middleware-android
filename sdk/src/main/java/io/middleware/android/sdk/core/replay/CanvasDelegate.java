package io.middleware.android.sdk.core.replay;

import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.DrawFilter;
import android.graphics.Matrix;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RenderNode;
import android.graphics.fonts.Font;
import android.graphics.text.MeasuredText;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;


public class CanvasDelegate extends Canvas {

    private static final String TAG = "Delegate";

    private final Recorder recorder;
    private final Canvas original;

    public CanvasDelegate(Recorder recorder, Canvas original) {
        this.recorder = recorder;
        this.original = original;
    }

    @Override
    public boolean isHardwareAccelerated() {
        return false;
    }

    @Override
    public void setBitmap(Bitmap bitmap) {
        // Log.d(TAG, "TODO setBitmap: ");
    }

    @Override
    public void enableZ() {
        // no-op, called by every ViewGroup
    }

    @Override
    public void disableZ() {
        // no-op
    }

    @Override
    public boolean isOpaque() {
        return super.isOpaque();
    }

    @Override
    public int getWidth() {
        return original.getWidth();
    }

    @Override
    public int getHeight() {
        return original.getHeight();
    }

    @Override
    public int getDensity() {
        return original.getDensity();
    }

    @Override
    public void setDensity(int density) {
        // no-op
    }

    @Override
    public int getMaximumBitmapWidth() {
        return 0;
    }

    @Override
    public int getMaximumBitmapHeight() {
        return 0;
    }

    @Override
    public int save() {
        recorder.save();
        return original.save();
    }

    // no override here, as it's marked as @removed
    public int save(int saveFlags) {
        return save();
    }

    @Override
    public int saveLayer(RectF bounds, Paint paint, int saveFlags) {
        // Log.d(TAG, "TODO saveLayer: ");
        return super.saveLayer(bounds, paint, saveFlags);
    }

    @Override
    public int saveLayer(RectF bounds, Paint paint) {
        // Log.d(TAG, "TODO saveLayer: ");
        return super.saveLayer(bounds, paint);
    }

    @Override
    public int saveLayer(float left, float top, float right, float bottom, Paint paint, int saveFlags) {
        // Log.d(TAG, "TODO saveLayer: ");
        return super.saveLayer(left, top, right, bottom, paint, saveFlags);
    }

    @Override
    public int saveLayer(float left, float top, float right, float bottom, Paint paint) {
        // Log.d(TAG, "TODO saveLayer: ");
        return super.saveLayer(left, top, right, bottom, paint);
    }

    @Override
    public int saveLayerAlpha(RectF bounds, int alpha, int saveFlags) {
        // Log.d(TAG, "TODO saveLayer: ");
        return super.saveLayerAlpha(bounds, alpha, saveFlags);
    }

    @Override
    public int saveLayerAlpha(RectF bounds, int alpha) {
        // Log.d(TAG, "TODO saveLayer: ");
        return super.saveLayerAlpha(bounds, alpha);
    }

    @Override
    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha, int saveFlags) {
        // Log.d(TAG, "TODO saveLayer: ");
        return super.saveLayerAlpha(left, top, right, bottom, alpha, saveFlags);
    }

    @Override
    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha) {
        // Log.d(TAG, "TODO saveLayer: ");
        return super.saveLayerAlpha(left, top, right, bottom, alpha);
    }

    @Override
    public void restore() {
        // Log.d(TAG, "restore: ");
        recorder.restore();
        original.restore();
    }

    @Override
    public int getSaveCount() {
        return original.getSaveCount();
    }

    @Override
    public void restoreToCount(int saveCount) {
        // Log.d(TAG, "restoreToCount: " + saveCount);
        recorder.restoreToCount(original.getSaveCount(), saveCount);
        original.restoreToCount(saveCount);
    }

    @Override
    public void translate(float dx, float dy) {
        // Log.d(TAG, "translate: dx: Float, dy: Float");
        recorder.translate(dx, dy);
        original.translate(dx, dy);
    }

    @Override
    public void scale(float sx, float sy) {
        // Log.d(TAG, "TODO scale: ");
        recorder.scale(sx, sy);
        original.scale(sx, sy);
    }

    @Override
    public void rotate(float degrees) {
        // Log.d(TAG, "TODO rotate: ");
        recorder.rotate(degrees);
        original.rotate(degrees);
    }

    @Override
    public void skew(float sx, float sy) {
        // Log.d(TAG, "TODO skew: ");
        recorder.skew(sx, sy);
        original.skew(sx, sy);
    }

    @Override
    public void concat(Matrix matrix) {
        // Log.d(TAG, "concat: ");
        recorder.concat(matrix);
        original.concat(matrix);
    }

    @Override
    public void setMatrix(Matrix matrix) {
        // Log.d(TAG, "TODO setMatrix: ");
        recorder.setMatrix(matrix);
        original.setMatrix(matrix);
    }

    @Override
    public void getMatrix(Matrix ctm) {
        // Log.d(TAG, "TODO getMatrix: ");
        original.getMatrix(ctm);
    }

    @Override
    public boolean clipRect(RectF rect, Region.Op op) {
        // Log.d(TAG, "TODO clipRect: rect: RectF, op: Region.Op");
        return original.clipRect(rect, op);
    }

    @Override
    public boolean clipRect(Rect rect, Region.Op op) {
        // Log.d(TAG, "TODO clipRect: rect: Rect, op: Region.Op");
        return original.clipRect(rect, op);
    }

    @Override
    public boolean clipRect(RectF rect) {
        // Log.d(TAG, "TODO clipRect: rect: RectF");
        recorder.clipRectF(rect.left, rect.top, rect.right, rect.bottom);
        return original.clipRect(rect);
    }

    @Override
    public boolean clipRect(Rect rect) {
        // Log.d(TAG, "TODO clipRect: rect: Rect");
        recorder.clipRectF(rect.left, rect.top, rect.right, rect.bottom);
        return original.clipRect(rect);
    }

    @Override
    public boolean clipRect(float left, float top, float right, float bottom, Region.Op op) {
        // Log.d(TAG, "TODO clipRect: left: Float, top: Float, right: Float, bottom: Float, op: Region.Op");
        return original.clipRect(left, top, right, bottom, op);
    }

    @Override
    public boolean clipRect(float left, float top, float right, float bottom) {
        // Log.d(TAG, "clipRect: left: Float, top: Float, right: Float, bottom: Float");
        recorder.clipRectF(left, top, right, bottom);
        return original.clipRect(left, top, right, bottom);
    }

    @Override
    public boolean clipRect(int left, int top, int right, int bottom) {
        // Log.d(TAG, "TODO clipRect: left: Int, top: Int, right: Int, bottom: Int");
        recorder.clipRectF(left, top, right, bottom);
        return original.clipRect(left, top, right, bottom);
    }

    @Override
    public boolean clipOutRect(RectF rect) {
        // Log.d(TAG, "TODO clipOutRect: rect: RectF");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return original.clipOutRect(rect);
        }
        return original.clipRect(rect);
    }

    @Override
    public boolean clipOutRect(Rect rect) {
        // Log.d(TAG, "TODO clipOutRect: ");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return original.clipOutRect(rect);
        }
        return original.clipRect(rect);
    }

    @Override
    public boolean clipOutRect(float left, float top, float right, float bottom) {
        // Log.d(TAG, "TODO clipOutRect: ");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return original.clipOutRect(left, top, right, bottom);
        }
        return original.clipRect(left, top, right, bottom);
    }

    @Override
    public boolean clipOutRect(int left, int top, int right, int bottom) {
        // Log.d(TAG, "TODO clipOutRect: ");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return original.clipOutRect(left, top, right, bottom);
        }
        return original.clipRect(left, top, right, bottom);
    }

    @Override
    public boolean clipPath(Path path, Region.Op op) {
        // Log.d(TAG, "TODO clipPath: ");
        return original.clipPath(path, op);
    }

    @Override
    public boolean clipPath(Path path) {
        // Log.d(TAG, "TODO clipPath: ");
        return original.clipPath(path);
    }

    @Override
    public boolean clipOutPath(Path path) {
        // Log.d(TAG, "TODO clipOutPath: ");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return original.clipOutPath(path);
        }
        return original.clipPath(path);
    }

    @Override
    public DrawFilter getDrawFilter() {
        // Log.d(TAG, "TODO clipOutPath: ");
        return null;
    }

    @Override
    public void setDrawFilter(DrawFilter filter) {
        // Log.d(TAG, "TODO setDrawFilter: ");
    }

    @Override
    public boolean quickReject(RectF rect, EdgeType type) {
        // Log.d(TAG, "TODO quickReject: ");
        return original.quickReject(rect, type);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public boolean quickReject(RectF rect) {
        // Log.d(TAG, "TODO quickReject: ");
        return original.quickReject(rect);
    }

    @Override
    public boolean quickReject(Path path, EdgeType type) {
        // Log.d(TAG, "TODO quickReject: ");
        return original.quickReject(path, type);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public boolean quickReject(Path path) {
        // Log.d(TAG, "TODO quickReject: ");
        return original.quickReject(path);
    }

    @Override
    public boolean quickReject(float left, float top, float right, float bottom, EdgeType type) {
        // Log.d(TAG, "TODO quickReject: ");
        return original.quickReject(left, top, right, bottom, type);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public boolean quickReject(float left, float top, float right, float bottom) {
        // Log.d(TAG, "TODO quickReject: ");
        return original.quickReject(left, top, right, bottom);
    }

    @Override
    public boolean getClipBounds(Rect bounds) {
        return original.getClipBounds(bounds);
    }

    @Override
    public void drawPicture(Picture picture) {
        // Log.d(TAG, "TODO drawPicture: ");
        // original.drawPicture(picture);
    }

    @Override
    public void drawPicture(Picture picture, RectF dst) {
        // Log.d(TAG, "TODO drawPicture: ");
        // original.drawPicture(picture, dst);
    }

    @Override
    public void drawPicture(Picture picture, Rect dst) {
        // Log.d(TAG, "TODO drawPicture: ");
        // original.drawPicture(picture, dst);
    }

    @Override
    public void drawArc(RectF oval, float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
        // Log.d(TAG, "TODO drawArc: ");
        // original.drawArc(oval, startAngle, sweepAngle, useCenter, paint);
    }

    @Override
    public void drawArc(float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
        // Log.d(TAG, "drawArc: ");
        // original.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint);
    }

    @Override
    public void drawARGB(int a, int r, int g, int b) {
        // Log.d(TAG, "drawARGB: ");
        // original.drawARGB(a, r, g, b);
    }

    @Override
    public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        Log.d(TAG, "drawBitmap: ");
        // original.drawBitmap(bitmap, left, top, paint);
    }

    @Override
    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        Log.d(TAG, "drawBitmap: ");
        // original.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) {
        Log.d(TAG, "drawBitmap: ");
        // original.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void drawBitmap(
            int[] colors,
            int offset,
            int stride,
            float x,
            float y,
            int width,
            int height,
            boolean hasAlpha,
            Paint paint) {
        Log.d(TAG, "drawBitmap: ");
        // original.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint);
    }

    @Override
    public void drawBitmap(
            int[] colors,
            int offset,
            int stride,
            int x,
            int y,
            int width,
            int height,
            boolean hasAlpha,
            Paint paint) {
        Log.d(TAG, "drawBitmap: ");
        // original.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint);
    }

    @Override
    public void drawBitmap(Bitmap bitmap, Matrix matrix, Paint paint) {
        Log.d(TAG, "drawBitmap: ");
        // original.drawBitmap(bitmap, matrix, paint);
    }

    @Override
    public void drawBitmapMesh(
            Bitmap bitmap,
            int meshWidth,
            int meshHeight,
            float[] verts,
            int vertOffset,
            int[] colors,
            int colorOffset,
            Paint paint) {
        Log.d(TAG, "drawBitmapMesh: ");
    }

    @Override
    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        // Log.d(TAG, "drawCircle: ");
        recorder.drawCircle(cx, cy, radius, paint);
        // original.drawCircle(cx, cy, radius, paint);
    }

    @Override
    public void drawColor(int color) {
        Log.d(TAG, "drawColor: ");
        // original.drawColor(color);
    }

    @Override
    public void drawColor(long color) {
        Log.d(TAG, "drawColor: ");
        // original.drawColor(color);
    }

    @Override
    public void drawColor(int color, PorterDuff.Mode mode) {
        Log.d(TAG, "drawColor: ");
        // original.drawColor(color, mode);
    }

    @Override
    public void drawColor(int color, BlendMode mode) {
        Log.d(TAG, "drawColor: ");
        // original.drawColor(color, mode);
    }

    @Override
    public void drawColor(long color, BlendMode mode) {
        Log.d(TAG, "drawColor: ");
        // original.drawColor(color, mode);
    }

    @Override
    public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
        Log.d(TAG, "drawLine: ");
        // original.drawLine(startX, startY, stopX, stopY, paint);
    }

    @Override
    public void drawLines(float[] pts, int offset, int count, Paint paint) {
        Log.d(TAG, "drawLines: ");
        // original.drawLines(pts, offset, count, paint);
    }

    @Override
    public void drawLines(float[] pts, Paint paint) {
        Log.d(TAG, "drawLines: ");
        // original.drawLines(pts, paint);
    }

    @Override
    public void drawOval(RectF oval, Paint paint) {
        Log.d(TAG, "drawOval: ");
        // original.drawOval(oval, paint);
    }

    @Override
    public void drawOval(float left, float top, float right, float bottom, Paint paint) {
        Log.d(TAG, "drawOval: ");
        // original.drawOval(left, top, right, bottom, paint);
    }

    @Override
    public void drawPaint(Paint paint) {
        Log.d(TAG, "drawPaint: ");
        // original.drawPaint(paint);
    }

    @Override
    public void drawPatch(NinePatch patch, Rect dst, Paint paint) {
        Log.d(TAG, "drawPatch: ");
        // original.drawPatch(patch, dst, paint);
    }

    @Override
    public void drawPatch(NinePatch patch, RectF dst, Paint paint) {
        Log.d(TAG, "drawPatch: ");
        // original.drawPatch(patch, dst, paint);
    }

    @Override
    public void drawPath(Path path, Paint paint) {
        Log.d(TAG, "drawPath: " + path);
        recorder.drawPath(path, paint);
        // original.drawPath(path, paint);
    }

    @Override
    public void drawPoint(float x, float y, Paint paint) {
        Log.d(TAG, "drawPoint: ");
        // original.drawPoint(x, y, paint);
    }

    @Override
    public void drawPoints(float[] pts, int offset, int count, Paint paint) {
        Log.d(TAG, "drawPoints: ");
        // original.drawPoints(pts, offset, count, paint);
    }

    @Override
    public void drawPoints(float[] pts, Paint paint) {
        Log.d(TAG, "drawPoints: ");
        // original.drawPoints(pts, paint);
    }

    @Override
    public void drawPosText(char[] text, int index, int count, float[] pos, Paint paint) {
        Log.d(TAG, "drawPosText: ");
        // original.drawPosText(text, index, count, pos, paint);
    }

    @Override
    public void drawPosText(String text, float[] pos, Paint paint) {
        Log.d(TAG, "drawPosText: ");
        // original.drawPosText(text, pos, paint);
    }

    @Override
    public void drawRect(RectF rect, Paint paint) {
//        Log.d(TAG, "drawRect: 0");
        recorder.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint);
        // original.drawRect(rect, paint);
    }

    @Override
    public void drawRect(Rect r, Paint paint) {
        // Log.d(TAG, "drawRect: 1");
        recorder.drawRect(r.left, r.top, r.right, r.bottom, paint);
        // original.drawRect(r, paint);
    }

    @Override
    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        // Log.d(TAG, "drawRect: 2");
        recorder.drawRect(left, top, right, bottom, paint);
        // original.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public void drawRGB(int r, int g, int b) {
        Log.d(TAG, "drawRGB: ");
        // original.drawRGB(r, g, b);
    }

    @Override
    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        // Log.d(TAG, "drawRoundRect: 0");
        recorder.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint);
    }

    @Override
    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
        // Log.d(TAG, "drawRoundRect: 1");
        recorder.drawRoundRect(left, top, right, bottom, rx, ry, paint);
    }

    @Override
    public void drawDoubleRoundRect(RectF outer, float outerRx, float outerRy, RectF inner, float innerRx, float innerRy, Paint paint) {
        Log.d(TAG, "drawDoubleRoundRect: 2");
        // original.drawDoubleRoundRect(outer, outerRx, outerRy, inner, innerRx, innerRy, paint);
    }

    @Override
    public void drawDoubleRoundRect(RectF outer, float[] outerRadii, RectF inner, float[] innerRadii, Paint paint) {
        Log.d(TAG, "drawDoubleRoundRect: ");
        // original.drawDoubleRoundRect(outer, outerRadii, inner, innerRadii, paint);
    }

    @Override
    public void drawGlyphs(int[] glyphIds, int glyphIdOffset, float[] positions, int positionOffset, int glyphCount, Font font, Paint paint) {
        Log.d(TAG, "drawGlyphs: ");
    }

    @Override
    public void drawText(char[] text, int index, int count, float x, float y, Paint paint) {
        // Log.d(
        // TAG,
        // "drawText: text: char[], index: int, count: int, x: float, y: float, paint: Paint"
        // );
        recorder.drawText(new String(text), 0, text.length, x, y, paint);
    }

    @Override
    public void drawText(String text, float x, float y, Paint paint) {
        // Log.d(TAG, "drawText: text: String, x: float, y: float, paint: Paint");
        recorder.drawText(text, 0, text.length(), x, y, paint);
    }

    @Override
    public void drawText(String text, int start, int end, float x, float y, Paint paint) {
        // Log.d(TAG, "drawText: text: String, start: int, end: int, x: float, y: float, paint: Paint");
        recorder.drawText(text, start, end, x, y, paint);
    }

    @Override
    public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) {
        // Log.d(
        //     TAG,
        //     "drawText: text: CharSequence, start: int, end: int, x: float, y: float, paint: Paint"
        // );
        recorder.drawText(text, start, end, x, y, paint);
    }

    @Override
    public void drawTextOnPath(char[] text, int index, int count, Path path, float hOffset, float vOffset, Paint paint) {
        Log.d(TAG, "drawTextOnPath: ");
        // original.drawTextOnPath(text, index, count, path, hOffset, vOffset, paint);
    }

    @Override
    public void drawTextOnPath(String text, Path path, float hOffset, float vOffset, Paint paint) {
        Log.d(TAG, "drawTextOnPath: ");
        // original.drawTextOnPath(text, path, hOffset, vOffset, paint);
    }

    @Override
    public void drawTextRun(char[] text, int index, int count, int contextIndex, int contextCount, float x, float y, boolean isRtl, Paint paint) {
        Log.d(TAG, "drawTextRun: ");
        // original.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint);
    }

    @Override
    public void drawTextRun(CharSequence text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, Paint paint) {
        Log.d(TAG, "drawTextRun: ");
        // original.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
    }

    @Override
    public void drawTextRun(MeasuredText text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, Paint paint) {
        Log.d(TAG, "drawTextRun: ");
        // original.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
    }

    @Override
    public void drawVertices(VertexMode mode, int vertexCount, float[] verts, int vertOffset, float[] texs, int texOffset, int[] colors, int colorOffset, short[] indices, int indexOffset, int indexCount, Paint paint) {
        Log.d(TAG, "drawVertices: ");
    }

    @Override
    public void drawRenderNode(RenderNode renderNode) {
        Log.d(TAG, "drawRenderNode: ");
        // original.drawRenderNode(renderNode);
    }
}
