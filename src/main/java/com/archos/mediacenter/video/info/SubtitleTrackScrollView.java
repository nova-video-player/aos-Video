// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.info;

import android.content.Context;
import android.util.AttributeSet;
import androidx.core.widget.NestedScrollView;

/** Keeps inline feedback and actions near long subtitle lists in the phone details page. */
public final class SubtitleTrackScrollView extends NestedScrollView {
    public SubtitleTrackScrollView(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.4f);
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
    }
}
