// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.leanback.details;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

import androidx.leanback.widget.VerticalGridView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded, text-only details pane with explicit D-pad exits into Leanback rows. */
public class DetailsScrollView extends ScrollView {
    private static final Logger log = LoggerFactory.getLogger(DetailsScrollView.class);
    private float mMaximumHeightFraction = 0.8f;
    private boolean mFocusableOnlyWhenScrollable;
    private BoundaryHandler mBoundaryHandler = this::moveToAdjacentRow;

    public interface BoundaryHandler {
        boolean onBoundary(int direction);
    }

    public DetailsScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMaximumHeightFraction(float fraction) {
        mMaximumHeightFraction = fraction;
        requestLayout();
    }

    public void setBoundaryHandler(BoundaryHandler handler) {
        mBoundaryHandler = handler;
    }

    public void setFocusableOnlyWhenScrollable(boolean enabled) {
        mFocusableOnlyWhenScrollable = enabled;
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mFocusableOnlyWhenScrollable) {
            boolean scrollable = getChildCount() > 0 && getChildAt(0).getHeight() > getHeight();
            if (!scrollable && hasFocus()) mBoundaryHandler.onBoundary(1);
            setFocusable(scrollable);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * mMaximumHeightFraction);
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
        }
        // Measure each time so recycled rows shrink again when they contain less text.
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN && keyCode != KeyEvent.KEYCODE_DPAD_UP) {
            return super.onKeyDown(keyCode, event);
        }
        int direction = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1;
        View content = getChildAt(0);
        // Preserve the file-info workaround: do not rely on canScrollVertically()
        // or focus search to decide when a text-only pane has reached its boundary.
        int range = content != null ? Math.max(0, content.getHeight() - getHeight()) : 0;
        boolean canScroll = direction > 0 ? getScrollY() < range : getScrollY() > 0;
        if (log.isDebugEnabled()) {
            log.debug("Details pane {} DPAD {}: scrollY={}, scrollRange={}, viewportHeight={}, "
                            + "contentHeight={}, frameworkCanScroll={}, hasFocus={}",
                    getId(), direction > 0 ? "DOWN" : "UP", getScrollY(), range,
                    getHeight(), content != null ? content.getHeight() : -1,
                    canScrollVertically(direction), hasFocus());
        }
        if (canScroll) {
            smoothScrollBy(0, direction * getHeight() / 2);
            return true;
        }
        return mBoundaryHandler.onBoundary(direction) || super.onKeyDown(keyCode, event);
    }

    public boolean moveToAdjacentRow(int direction) {
        ViewParent parent = getParent();
        while (parent != null && !(parent instanceof VerticalGridView)) {
            parent = parent.getParent();
        }
        if (!(parent instanceof VerticalGridView)) {
            log.warn("Could not find the details VerticalGridView");
            return false;
        }
        VerticalGridView grid = (VerticalGridView) parent;
        int current = grid.getSelectedPosition();
        int next = current + direction;
        int count = grid.getAdapter() != null ? grid.getAdapter().getItemCount() : 0;
        if (next < 0 || next >= count) {
            log.debug("No adjacent details row from row {} in direction {} (row count {})", current, direction, count);
            return false;
        }
        log.debug("Moving focus from details row {} to row {}", current, next);
        grid.setSelectedPositionSmooth(next);
        return true;
    }
}
