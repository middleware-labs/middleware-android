package io.middleware.android.sdk.core.replay;

import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ViewHelper {

    private static final String TAG = "ViewHelper";

    private static final Method forName;
    private static final Method getDeclaredMethod;
    private static final Method getDeclaredField;

    static {
        try {
            forName = Class.class.getDeclaredMethod("forName", String.class);
            getDeclaredMethod = Class.class.getDeclaredMethod("getMethod", String.class, Class[].class);
            getDeclaredField = Class.class.getDeclaredMethod("getDeclaredField", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to initialize ViewHelper", e);
        }
    }

    private static final Class<?> porterDuffColorFilterClass;

    static {
        try {
            porterDuffColorFilterClass = (Class<?>) forName.invoke(null, "android.graphics.PorterDuffColorFilter");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ViewHelper", e);
        }
    }

    private static final Method onDrawMethod;

    static {
        try {
            onDrawMethod = View.class.getDeclaredMethod("onDraw", Canvas.class);
            onDrawMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to initialize ViewHelper", e);
        }
    }

    public static void executeOnDraw(View view, Canvas canvas) {
        // Performs drawing operations for a view, without their child views
        if (view.getBackground() != null) {
            view.getBackground().draw(canvas);
        }
        try {
            onDrawMethod.invoke(view, canvas);
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute onDraw method", e);
        }
    }

    public static Pair<Integer, PorterDuff.Mode> decodePorterDuffcolorFilter(PorterDuffColorFilter colorFilter) {
        try {
            Field modeField = (Field) getDeclaredField.invoke(porterDuffColorFilterClass, "mMode");
            modeField.setAccessible(true);
            Method colorMethod = (Method) getDeclaredMethod.invoke(porterDuffColorFilterClass, "getColor", null);

            PorterDuff.Mode mode = (PorterDuff.Mode) modeField.get(colorFilter);
            int color = (int) colorMethod.invoke(colorFilter);
            return new Pair<>(color, mode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode porter duff color filter, returning null", e);
        }
        return null;
    }
}
