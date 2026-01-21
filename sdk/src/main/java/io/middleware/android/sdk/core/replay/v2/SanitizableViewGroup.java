package io.middleware.android.sdk.core.replay.v2;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class SanitizableViewGroup extends ViewGroup {
    public SanitizableViewGroup(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxHeight = 0;
        int maxWidth = 0;

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);

            measureChild(child, widthMeasureSpec, heightMeasureSpec);

            maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
        }

        int width = resolveSize(maxWidth, widthMeasureSpec);
        int height = resolveSize(maxHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }


    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {
        int count = getChildCount();
        for (int index = 0; index < count; index++) {
            View child = getChildAt(index);
            child.layout(
                    0,
                    0,
                    child.getMeasuredWidth(),
                    child.getMeasuredHeight()
            );
        }
    }
}