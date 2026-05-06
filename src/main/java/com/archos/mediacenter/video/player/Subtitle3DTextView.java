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

package com.archos.mediacenter.video.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Surface;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Subtitle3DTextView extends LinearLayout {
    private static final Logger log = LoggerFactory.getLogger(Subtitle3DTextView.class);
    private final AttributeSet mAttrs;
    private final FrameLayout mPrimaryFrameLayout;
    private final FrameLayout mSecondaryFrameLayout;
    private SubtitleTextView mPrimaryTV = null;
    private SubtitleTextView mSecondaryTV = null;
    private Surface mExternalSurface = null;
    private boolean mNeed3d;

    public Subtitle3DTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPadding(0,0,0,0);
        mAttrs = attrs;
        mPrimaryTV = new SubtitleTextView(context, attrs);
        mPrimaryTV.setVisibility(VISIBLE); //no matter what was in attrs (visibility is taken into account in parent layout, not in textview)
        mPrimaryFrameLayout = new FrameLayout(context);
        mSecondaryFrameLayout = new FrameLayout(context);
        mSecondaryTV = new SubtitleTextView(context, attrs);
        mSecondaryTV.setVisibility(VISIBLE); //no matter what was in attrs (visibility is taken into account in parent layout, not in textview)
        addView(mPrimaryFrameLayout);
        mSecondaryFrameLayout.addView(mSecondaryTV);
        mPrimaryFrameLayout.addView(mPrimaryTV);
        addView(mSecondaryFrameLayout);
        mPrimaryFrameLayout.getLayoutParams().height =  ViewGroup.LayoutParams.MATCH_PARENT;
        mSecondaryFrameLayout.getLayoutParams().height =  ViewGroup.LayoutParams.MATCH_PARENT;
        ((LayoutParams)mPrimaryFrameLayout.getLayoutParams()).weight = 1;
        ((LayoutParams)mSecondaryFrameLayout.getLayoutParams()).weight = 1;
        ((FrameLayout.LayoutParams)mSecondaryTV.getLayoutParams()).gravity = Gravity.NO_GRAVITY;
        ((FrameLayout.LayoutParams)mPrimaryTV.getLayoutParams()).gravity = Gravity.NO_GRAVITY;
        ((FrameLayout.LayoutParams)mSecondaryTV.getLayoutParams()).height = ViewGroup.LayoutParams.WRAP_CONTENT;
        ((FrameLayout.LayoutParams)mPrimaryTV.getLayoutParams()).height = ViewGroup.LayoutParams.WRAP_CONTENT;
        ((FrameLayout.LayoutParams)mSecondaryTV.getLayoutParams()).width = ViewGroup.LayoutParams.WRAP_CONTENT;
        ((FrameLayout.LayoutParams)mPrimaryTV.getLayoutParams()).width = ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    public void setGravity3D(int gravity) {
        setGravity3D(gravity, gravity & Gravity.HORIZONTAL_GRAVITY_MASK);
    }

    public void setGravity3D(int positionGravity, int textJustification) {
        if (log.isDebugEnabled()) log.debug("setGravity3D: positionGravity={}, textJustification={}", positionGravity, textJustification);

        // Get FrameLayout.LayoutParams since the parent is a FrameLayout
        FrameLayout.LayoutParams primaryParams = (FrameLayout.LayoutParams) mPrimaryTV.getLayoutParams();
        FrameLayout.LayoutParams secondaryParams = (FrameLayout.LayoutParams) mSecondaryTV.getLayoutParams();

        // Set positioning gravity for the TextView within its parent container
        primaryParams.gravity = positionGravity;
        secondaryParams.gravity = positionGravity;
        mPrimaryTV.setLayoutParams(primaryParams);
        mSecondaryTV.setLayoutParams(secondaryParams);

        // Set text justification within the TextView for proper multi-line alignment
        // Combine vertical positioning from positionGravity with horizontal justification
        int textGravity = (positionGravity & Gravity.VERTICAL_GRAVITY_MASK) | textJustification;
        mPrimaryTV.setGravity(textGravity);
        mSecondaryTV.setGravity(textGravity);

        if (log.isDebugEnabled()) log.debug("setGravity3D: final textGravity={}", textGravity);

        // Request layout updates
        mPrimaryTV.requestLayout();
        mSecondaryTV.requestLayout();
    }
    
    @Override
    public void setVisibility(int visibility) {
        if (log.isDebugEnabled()) log.debug("setVisibility: visibility={}", visibility);
        super.setVisibility(visibility);
        if (mExternalSurface != null)  {
            try {
                Canvas c = mExternalSurface.lockCanvas(null);
                c.save();
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                c.restore();
                mExternalSurface.unlockCanvasAndPost(c);
            } catch (Exception ignored) {
            }
        }
    }
    
    public void setRenderingSurface(Surface s) {
        mExternalSurface = s;
        mPrimaryTV.setRenderingSurface(s);
        mSecondaryTV.setRenderingSurface(s);
    }


    public void setText(String s) {
        if (log.isDebugEnabled()) log.debug("setText: text={}", s);
        mPrimaryTV.setText(s);
        if(mNeed3d)
            mSecondaryTV.setText(s);
    }

    public void setTextColor(int color) {
        mPrimaryTV.setTextColor(color);
        mSecondaryTV.setTextColor(color);
    }

    public void setOutlineState(boolean outline) {
        mPrimaryTV.setOutlineState(outline);
        mSecondaryTV.setOutlineState(outline);
    }

    public void setBackgroundState(boolean show) {
        mPrimaryTV.setBackgroundState(show);
        mSecondaryTV.setBackgroundState(show);
    }

    public void setBackgroundOpacity(int opacity) {
        mPrimaryTV.setBackgroundOpacity(opacity);
        mSecondaryTV.setBackgroundOpacity(opacity);
    }
    
    public void setTextSize(float v) {
        mPrimaryTV.setTextSize(v);
        mSecondaryTV.setTextSize(v);
    }

    public void setText(SpannableStringBuilder spannableStringBuilder) {
        mPrimaryTV.setText(spannableStringBuilder);
        if(mNeed3d)
            mSecondaryTV.setText(spannableStringBuilder);
    }

    public void setUIMode(int uiMode) {
        if (log.isDebugEnabled()) log.debug("setUIMode: uiMode={}", uiMode);
        getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
        if(uiMode== VideoEffect.SBS_MODE||uiMode==VideoEffect.TB_MODE){
            mNeed3d = true;
            mSecondaryFrameLayout.setVisibility(VISIBLE);
            if(uiMode==VideoEffect.SBS_MODE){
                setOrientation(HORIZONTAL);
                mPrimaryFrameLayout.getLayoutParams().width = 0;
                mSecondaryFrameLayout.getLayoutParams().width = 0;
            }
            else if(uiMode==VideoEffect.TB_MODE){
                setOrientation(VERTICAL);
                mPrimaryFrameLayout.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mSecondaryFrameLayout.getLayoutParams().width =ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        }
        else {
            mNeed3d = false;
            mSecondaryFrameLayout.setVisibility(GONE);
        }

    }

    public void setScreenSize(int displayWidth, int displayHeight) {
        if (log.isDebugEnabled()) log.debug("setScreenSize: displayWidth={}, displayHeight={}", displayWidth, displayHeight);
        getLayoutParams().height = displayHeight;
    }
}
