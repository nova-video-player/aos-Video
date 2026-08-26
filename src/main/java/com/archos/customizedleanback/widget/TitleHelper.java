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
import androidx.core.view.ViewCompat;
import android.transition.Scene;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * Helper for managing {@link androidx.leanback.widget.TitleView}, including
 * transitions and focus movement.
 * Assumes the TitleView is overlayed on the topmost portion of the scene root view.
 */
public class TitleHelper {

    private ViewGroup mSceneRoot;
    private MyTitleView mTitleView;
    private Transition mTitleUpTransition;
    private Transition mTitleDownTransition;
    private Scene mSceneWithTitle;
    private Scene mSceneWithoutTitle;

    // When moving focus off the TitleView, this focus search listener assumes that the view that
    // should take focus comes before the TitleView in a focus search starting at the scene root.
    private final BrowseFrameLayout.OnFocusSearchListener mOnFocusSearchListener =
            new BrowseFrameLayout.OnFocusSearchListener() {
                @Override
                public View onFocusSearch(View focused, int direction) {
                    if (focused != mTitleView && direction == View.FOCUS_UP) {
                        return mTitleView;
                    }
                    final boolean isRtl = ViewCompat.getLayoutDirection(focused) ==
                            ViewCompat.LAYOUT_DIRECTION_RTL;
                    //final int forward = isRtl ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
                    if (mTitleView.hasFocus() && direction == View.FOCUS_DOWN /*|| direction == forward*/) {
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
        createTransitions();
    }

    // Inlines what androidx.leanback.transition.LeanbackTransitionHelper/TransitionHelper
    // do internally (both @RestrictTo(LIBRARY_GROUP)): since minSdk is 23 (> 21), their
    // pre-API21 fallback branches are dead code, and the API21+ branch just builds a
    // Slide transition targeting the title view (leanback's lb_title_in/lb_title_out
    // transition resources do the same, but are flagged PrivateResource since they are
    // not part of leanback's public API surface). Building the Slide transition
    // programmatically with the public android.transition APIs and targeting mTitleView
    // directly reproduces the same slide-down/slide-up-off-the-top behavior without
    // depending on any restricted or private leanback surface.
    private void createTransitions() {
        mTitleUpTransition = new Slide(Gravity.TOP);
        mTitleUpTransition.setInterpolator(new DecelerateInterpolator());
        mTitleUpTransition.addTarget(mTitleView);
        mTitleDownTransition = new Slide(Gravity.TOP);
        mTitleDownTransition.setInterpolator(new DecelerateInterpolator());
        mTitleDownTransition.addTarget(mTitleView);
        mSceneWithTitle = new Scene(mSceneRoot);
        mSceneWithTitle.setEnterAction(new Runnable() {
            @Override
            public void run() {
                mTitleView.setVisibility(View.VISIBLE);
            }
        });
        mSceneWithoutTitle = new Scene(mSceneRoot);
        mSceneWithoutTitle.setEnterAction(new Runnable() {
            @Override
            public void run() {
                mTitleView.setVisibility(View.INVISIBLE);
            }
        });
    }

    /**
     * Shows the title.
     */
    public void showTitle(boolean show) {
        if (show) {
            TransitionManager.go(mSceneWithTitle, mTitleDownTransition);
        } else {
            TransitionManager.go(mSceneWithoutTitle, mTitleUpTransition);
        }
    }

    /**
     * Returns the scene root ViewGroup.
     */
    public ViewGroup getSceneRoot() {
        return mSceneRoot;
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
