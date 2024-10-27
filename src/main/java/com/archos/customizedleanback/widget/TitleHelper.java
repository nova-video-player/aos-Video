/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.archos.customizedleanback.widget;

import androidx.leanback.widget.BrowseFrameLayout;
import androidx.transition.TransitionManager;
import androidx.transition.Transition;
import androidx.transition.Fade;
import android.view.View;
import android.view.ViewGroup;

/**
 * Helper for managing {@link androidx.leanback.widget.TitleView}, including
 * transitions and focus movement.
 * Assumes the TitleView is overlayed on the topmost portion of the scene root view.
 */
public class TitleHelper {

    private final ViewGroup mSceneRoot;
    private final MyTitleView mTitleView;
    private Transition mTitleUpTransition;
    private Transition mTitleDownTransition;

    private void createTransitions() {
        mTitleUpTransition = new Fade(); // Customize as needed
        mTitleDownTransition = new Fade(); // Customize as needed
    }

    // When moving focus off the TitleView, this focus search listener assumes that the view that
    // should take focus comes before the TitleView in a focus search starting at the scene root.
    private final BrowseFrameLayout.OnFocusSearchListener mOnFocusSearchListener =
            new BrowseFrameLayout.OnFocusSearchListener() {
                @Override
                public View onFocusSearch(View focused, int direction) {
                    if (focused != mTitleView && direction == View.FOCUS_UP) {
                        return mTitleView;
                    }
                    final boolean isRtl = focused.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                    if (mTitleView.hasFocus() && direction == View.FOCUS_DOWN) {
                        return mSceneRoot;
                    }
                    return null;
                }
            };

    public TitleHelper(ViewGroup sceneRoot, MyTitleView titleView) {
        if (sceneRoot == null || titleView == null) {
            throw new IllegalArgumentException("Views may not be null");
        }
        mSceneRoot = sceneRoot;
        mTitleView = titleView;
        createTransitions(); // Call to initialize transitions
    }


    /**
     * Shows the title with a transition.
     */
    public void showTitle(boolean show) {
        TransitionManager.beginDelayedTransition(mSceneRoot, show ? mTitleDownTransition : mTitleUpTransition);

        if (show) {
            mTitleView.setVisibility(View.VISIBLE);
        } else {
            mTitleView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Returns the {@link MyTitleView}
     */
    public MyTitleView getTitleView() {
        return mTitleView;
    }

    /**
     * Returns a
     * {@link androidx.leanback.widget.BrowseFrameLayout.OnFocusSearchListener} which
     * may be used to manage focus switching between the title view and scene root.
     */
    public BrowseFrameLayout.OnFocusSearchListener getOnFocusSearchListener() {
        return mOnFocusSearchListener;
    }
}
