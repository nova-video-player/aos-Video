// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.leanback.details;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** Two stable columns, including for a lone action; translated labels determine each row's height. */
public final class SubtitleActionGrid extends ViewGroup {
    private final int gap, rowGap;

    public SubtitleActionGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = Math.round(16 * getResources().getDisplayMetrics().density);
        rowGap = Math.round(4 * getResources().getDisplayMetrics().density);
    }

    private List<View> visibleChildren() {
        List<View> children = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != GONE) children.add(getChildAt(i));
        }
        return children;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int cellWidth = Math.max(0, (width - getPaddingLeft() - getPaddingRight() - gap) / 2);
        int childWidth = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY);
        List<View> children = visibleChildren();
        int height = getPaddingTop() + getPaddingBottom();
        for (int i = 0; i < children.size(); i += 2) {
            int rowHeight = 0;
            for (int j = i; j < Math.min(i + 2, children.size()); j++) {
                children.get(j).measure(childWidth, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowHeight = Math.max(rowHeight, children.get(j).getMeasuredHeight());
            }
            for (int j = i; j < Math.min(i + 2, children.size()); j++) {
                children.get(j).measure(childWidth, MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY));
            }
            height += rowHeight + (i == 0 ? 0 : rowGap);
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        List<View> children = visibleChildren();
        int y = getPaddingTop();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < children.size(); i += 2) {
            for (int j = i; j < Math.min(i + 2, children.size()); j++) {
                View child = children.get(j);
                int offset = (j - i) * (child.getMeasuredWidth() + gap);
                int x = rtl ? getWidth() - getPaddingRight() - offset - child.getMeasuredWidth()
                        : getPaddingLeft() + offset;
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            }
            y += children.get(i).getMeasuredHeight() + rowGap;
        }
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }
    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }
}
