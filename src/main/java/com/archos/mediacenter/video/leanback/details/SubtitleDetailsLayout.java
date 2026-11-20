// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.leanback.details;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.archos.mediacenter.video.R;

/** Reserve space for wrapped action labels before assigning the subtitle list its viewport. */
public final class SubtitleDetailsLayout extends LinearLayout {
    public SubtitleDetailsLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View tracks = findViewById(R.id.subtitle_tracks_scroll);
        if (tracks == null || tracks.getVisibility() == GONE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        ViewGroup.LayoutParams params = tracks.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // Leave room for the surrounding details card and Leanback's row offset.
        int budget = Math.round(getResources().getDisplayMetrics().heightPixels * 0.7f);
        int otherContent = getMeasuredHeight() - tracks.getMeasuredHeight();
        int minimumViewport = Math.round(48 * getResources().getDisplayMetrics().density);
        int available = Math.max(minimumViewport, budget - otherContent);
        if (tracks.getMeasuredHeight() > available) {
            params.height = available;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
