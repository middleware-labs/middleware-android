package io.middleware.android.sdk.core.replay;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.Pair;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @noinspection Since15
 */
public class RRWebRecorder implements Recorder {

    private static final int TYPE_DOMCONTENTLOADEDEVENT = 0;
    private static final int TYPE_LOADEVENT = 1;
    private static final int TYPE_METAEVENT = 4;
    private static final int TYPE_FULLSNAPSHOTEVENT = 2;
    private static final int TYPE_INCREMENTALSNAPSHOTEVENT = 3;
    private static final int TYPE_BREADCRUMB = 5;

    private List<RREvent> rrEvents = new ArrayList<>();

    public Long startTimestampMs = 0L;
    public Long endTimestampMs = 0L;

    private List<Map<String, Object>> currentFrameCommands;

    private RREvent currentFrame = new RREvent(0L, 0, emptyMap());


    public List<RREvent> getRecording() {
        return rrEvents;
    }

    @Override
    public void beginFrame(long timestampMs, int width, int height) {
        if (rrEvents.isEmpty()) {
            startTimestampMs = timestampMs;

            //DOMContentLoadedEvent
            rrEvents.add(new RREvent(timestampMs, TYPE_DOMCONTENTLOADEDEVENT, emptyMap()));

            rrEvents.add(new RREvent(timestampMs, TYPE_LOADEVENT, emptyMap()));

            // MetaEvent
            Map<String, Object> metaEvents = new java.util.HashMap<>();
            metaEvents.put("href", "http://localhost");
            metaEvents.put("width", width);
            metaEvents.put("height", height);
            rrEvents.add(
                    new RREvent(
                            timestampMs,
                            TYPE_METAEVENT,
                            metaEvents
                    )
            );

            // FullSnapshotEvent
            rrEvents.add(
                    new RREvent(
                            timestampMs,
                            TYPE_FULLSNAPSHOTEVENT,
                            Map.of(
                                    "node", Map.of(
                                            "id", 1,
                                            "type", 0,
                                            "childNodes", List.of(
                                                    Map.of(
                                                            "type", 1,
                                                            "name", "html",
                                                            "id", 2,
                                                            "childNodes", emptyList()
                                                    ),
                                                    Map.of(
                                                            "id", 3,
                                                            "type", 2,
                                                            "tagName", "html",
                                                            "childNodes", List.of(
                                                                    Map.of(
                                                                            "id", 5,
                                                                            "type", 2,
                                                                            "tagName", "html",
                                                                            "childNodes", List.of(
                                                                                    Map.of(
                                                                                            "type", 2,
                                                                                            "tagName", "canvas",
                                                                                            "id", 7,
                                                                                            "attributes", Map.of(
                                                                                                    "id", "canvas",
                                                                                                    "width", width,
                                                                                                    "height", height
                                                                                            ),
                                                                                            "childNodes", emptyList()
                                                                                    )
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            ),
                                            "initialOffset", Map.of(
                                                    "left", 0,
                                                    "top", 0
                                            )
                                    )
                            )
                    )
            );

        }

        currentFrameCommands = new ArrayList<>();
        currentFrameCommands.add(
                Map.of(
                        "property", "clearRect",
                        "args", List.of(0, 0, width, height)
                )
        );
        // IncrementalSnapshotEvent
        currentFrame = new RREvent(
                timestampMs,
                TYPE_INCREMENTALSNAPSHOTEVENT,
                Map.of(
                        "source", 9,
                        "id", 7,
                        "type", 0,
                        "commands", currentFrameCommands
                )
        );
        rrEvents.add(currentFrame);
        endTimestampMs = timestampMs;
    }

    @Override
    public void save() {
        currentFrameCommands.add(
                Map.of("property", "save",
                        "args", emptyList())
        );
    }

    @Override
    public void restore() {
        currentFrameCommands.add(
                Map.of("property", "restore",
                        "args", emptyList())
        );
    }

    @Override
    public void restoreToCount(int currentSaveCount, int targetSaveCount) {
        final int countRestore = currentSaveCount - targetSaveCount;
        for (int i = 0; i < countRestore; i++) {
            currentFrameCommands.add(
                    Map.of("property", "restore",
                            "args", emptyList())
            );
        }
    }

    @Override
    public void translate(float dx, float dy) {
        currentFrameCommands.add(
                Map.of(
                        "property", "translate",
                        "args", List.of(dx, dy)
                )
        );
    }

    @Override
    public void clipRectF(float left, float top, float right, float bottom) {
        currentFrameCommands.add(
                Map.of(
                        "property", "beginPath",
                        "args", emptyList()
                )
        );
        currentFrameCommands.add(
                Map.of(
                        "property", "moveTo",
                        "args", List.of(left, top)
                )
        );
        currentFrameCommands.add(
                Map.of(
                        "property", "lineTo",
                        "args", List.of(right, top)
                )
        );
        currentFrameCommands.add(
                Map.of(
                        "property", "lineTo",
                        "args", List.of(right, bottom)
                )
        );
        currentFrameCommands.add(
                Map.of(
                        "property", "lineTo",
                        "args", List.of(left, bottom)
                )
        );
    }

    @Override
    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
        setupPaint(paint);
        currentFrameCommands.add(
                Map.of(
                        "property", "roundRect",
                        "args", List.of(left, top, right - left, bottom - top, rx)
                )
        );
        draw(paint);
    }

    private void draw(Paint paint) {
        if (paint.getStyle() == Paint.Style.FILL) {
            currentFrameCommands.add(
                    Map.of(
                            "property", "fill",
                            "args", emptyList()
                    )
            );
        } else if (paint.getStyle() == Paint.Style.STROKE) {
            currentFrameCommands.add(
                    Map.of(
                            "property", "stroke",
                            "args", emptyList()
                    )
            );
        } else {
            currentFrameCommands.add(
                    Map.of(
                            "property", "fill",
                            "args", emptyList()
                    )
            );
            currentFrameCommands.add(
                    Map.of(
                            "property", "stroke",
                            "args", emptyList()
                    )
            );
        }
    }

    private void setupPaint(Paint paint) {
        int red = Color.red(paint.getColor());
        int green = Color.green(paint.getColor());
        int blue = Color.blue(paint.getColor());
        int alpha = Color.alpha(paint.getColor());
        String color = String.format("rgba(%s,%s,%s,%s)", red, green, blue, alpha);

        currentFrameCommands.add(
                Map.of(
                        "property", "fillStyle",
                        "args", List.of(color),
                        "setter", true
                )
        );

        currentFrameCommands.add(
                Map.of(
                        "property", "strokeStyle",
                        "args", List.of(color),
                        "setter", true
                )
        );

        String fontWeight = "normal";
        if (paint.getTypeface() != null) {
            if (paint.getTypeface().isBold()) {
                fontWeight = "bold";
            } else if (paint.getTypeface().isItalic()) {
                fontWeight = "italic";
            }
        }


        currentFrameCommands.add(
                Map.of(
                        "property", "font",
                        "args", List.of(String.format("%s %spx sans-serif", fontWeight, (int) paint.getTextSize())),
                        "setter", true
                )
        );

        String textAlign;
        if (paint.getTextAlign() == Paint.Align.RIGHT) {
            textAlign = "right";
        } else if (paint.getTextAlign() == Paint.Align.CENTER) {
            textAlign = "center";
        } else {
            textAlign = "left";
        }

        currentFrameCommands.add(
                Map.of(
                        "property", "textAlign",
                        "args", List.of(textAlign),
                        "setter", true
                )
        );

        currentFrameCommands.add(
                Map.of(
                        "property", "lineWidth",
                        "args", List.of(paint.getStrokeWidth()),
                        "setter", true
                )
        );

        currentFrameCommands.add(
                Map.of(
                        "property", "globalAlpha",
                        "args", List.of(paint.getAlpha() / 255.0f),
                        "setter", true
                )
        );

        ColorFilter colorFilter = paint.getColorFilter();
        if (colorFilter != null) {
            if (colorFilter instanceof PorterDuffColorFilter) {
                Pair<Integer, PorterDuff.Mode> integerModePair = ViewHelper
                        .decodePorterDuffcolorFilter((PorterDuffColorFilter) colorFilter);
                if (integerModePair != null) {
                    currentFrameCommands.add(
                            Map.of(
                                    "property", "fillStyle",
                                    "args", List.of(integerModePair.first),
                                    "setter", true
                            )
                    );

                    currentFrameCommands.add(
                            Map.of(
                                    "property", "strokeStyle",
                                    "args", List.of(color),
                                    "setter", true
                            )
                    );
                }
            }
        }
    }

    @Override
    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        setupPaint(paint);
        currentFrameCommands.add(
                Map.of(
                        "property", "roundRect",
                        "args", List.of(cx, cy, 0.0, 360.0, radius)
                )
        );
        draw(paint);
    }

    @Override
    public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) {
        setupPaint(paint);

        // ctx.fillText("Hello world", 50, 90);
        String relevantText = text.subSequence(start, end).toString();
        currentFrameCommands.add(
                Map.of(
                        "property", "fillText",
                        "args", List.of(relevantText, x, y)
                )
        );
    }

    @Override
    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        setupPaint(paint);
        currentFrameCommands.add(
                Map.of(
                        "property", "fillRect",
                        "args", List.of(left, top, right - left, bottom - top)
                )
        );
    }

    @Override
    public void concat(Matrix matrix) {

    }

    @Override
    public void scale(float sx, float sy) {
        currentFrameCommands.add(
                Map.of(
                        "property", "scale",
                        "args", List.of(sx, sy)
                )
        );
    }

    @Override
    public void rotate(float degrees) {
        currentFrameCommands.add(
                Map.of(
                        "property", "rotate",
                        "args", List.of(degrees)
                )
        );
    }

    @Override
    public void skew(float sx, float sy) {

    }

    @Override
    public void setMatrix(Matrix matrix) {

    }

    @Override
    public void onTouchEvent(long timestampMs, MotionEvent event) {
        int actionMasked = event.getActionMasked();
        RREvent rrEvent = null;
        if (actionMasked == ACTION_MOVE) {
            rrEvent = new RREvent(
                    timestampMs,
                    TYPE_INCREMENTALSNAPSHOTEVENT,
                    Map.of(
                            "positions", List.of(Map.of(
                                    "x", event.getX(),
                                    "y", event.getY(),
                                    "id", 7,
                                    "timeOffset", 0))
                    )
            );


        } else if (actionMasked == ACTION_DOWN || actionMasked == ACTION_UP) {
            int type = actionMasked == ACTION_DOWN ? 1 : 2;
            rrEvent = new RREvent(
                    timestampMs,
                    TYPE_INCREMENTALSNAPSHOTEVENT,
                    Map.of(
                            "source", 2,
                            "type", type,
                            "id", 7,
                            "x", event.getX(),
                            "y", event.getY()
                    )
            );
        }
        rrEvents.add(rrEvent);
    }

    @Override
    public void drawPath(Path path, Paint paint) {
        setupPaint(paint);

        List<PointF> points = new ArrayList<>();

        float[] coords = new float[2];

        float tolerance = 1f;
        PathMeasure pathMeasure = new PathMeasure(path, false);
        float length = pathMeasure.getLength();
        for (float i = 0; i <= length; i += tolerance) {
            pathMeasure.getPosTan(i, coords, null);
            points.add(new PointF(coords[0], coords[1]));
        }

        String type;
        switch (path.getFillType()) {
            case WINDING:
                type = "nonzero";
                break;
            case EVEN_ODD:
                type = "evenodd";
                break;
            case INVERSE_EVEN_ODD:
            case INVERSE_WINDING:
                type = "??";
                break;
            default:
                type = "unknown";
                break;
        }

        currentFrameCommands.add(
                Map.of(
                        "property", "beginPath",
                        "type", type,
                        "args", emptyList()
                )
        );

        for (int i = 0; i < points.size(); i += 3) {
            String command = (i == 0) ? "moveTo" : "lineTo";
            currentFrameCommands.add(
                    Map.of(
                            "property", command,
                            "args", List.of(points.get(i + 1), points.get(i + 2))
                    )
            );
        }
        draw(paint);
    }
}
