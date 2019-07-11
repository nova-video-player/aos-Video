// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.leanback.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

/**
 * Created by vapillon on 02/06/15.
 */
public class HintView extends FrameLayout{

    final View mRootView;
    final View mBox;
    final TextView mTextView;

    public HintView(Context context) {
        this(context, null);
    }

    public HintView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HintView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mRootView = LayoutInflater.from(context).inflate(R.layout.hint_view_widget, this, true);
        mBox = mRootView.findViewById(R.id.box);
        mTextView = (TextView)mRootView.findViewById(R.id.message);

        setFocusable(false);
    }

    public void setMessage(String message) {
        mTextView.setText(message);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // init alpha to null
        mRootView.setAlpha(0);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        if (this == changedView && mRootView != null) { // we're getting some onVisibilityChanged() while mRootView is not set yet (when calling the super constructor)
            if (visibility == View.VISIBLE) {
                // delayed slow appearance
                mRootView.animate().alpha(1).setStartDelay(1000).setDuration(1500);
            } else {
                // quick hide
                mRootView.animate().alpha(0);
            }
        }
        super.onVisibilityChanged(changedView, visibility);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mRootView.animate().cancel(); // not sure this is needed...
    }
}
