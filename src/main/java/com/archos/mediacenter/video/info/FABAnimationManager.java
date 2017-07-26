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

package com.archos.mediacenter.video.info;

import android.view.View;
import android.view.animation.Animation;

/**
 * Created by alexandre on 18/03/16.
 */
public class FABAnimationManager implements Animation.AnimationListener {

    private final View mFabView;
    private final Animation mShowAnimation;
    private final Animation mHideAnimation;
    private boolean mIsHideCanceled;

    public FABAnimationManager(View FABView, Animation hideAnimation, Animation showAnimation){
        mFabView = FABView;
        mShowAnimation = showAnimation;
        mShowAnimation.setAnimationListener(this);
        mHideAnimation = hideAnimation;
        mHideAnimation.setAnimationListener(this);

    }

    public void showFAB(boolean animate){
        if(mFabView.getAnimation()!=mShowAnimation&&mFabView.getVisibility()!=View.VISIBLE) {
            if (mFabView.getAnimation() != null) {
                mIsHideCanceled = true;
                mFabView.getAnimation().cancel();
            }
            mFabView.setVisibility(View.VISIBLE);
            if(animate)
                mFabView.startAnimation(mShowAnimation);
        }
    }

    public void hideFAB(boolean animate){
        if(mFabView.getAnimation()!=mHideAnimation&&mFabView.getVisibility()!=View.GONE) {
            if (mFabView.getAnimation() != null) {
                mIsHideCanceled = true;
                mFabView.getAnimation().cancel();
            }
            if(animate)
                mFabView.startAnimation(mHideAnimation);
            else
                mFabView.setVisibility(View.GONE);
        }
    }
    @Override
    public void onAnimationStart(Animation animation) {
        if(animation==mHideAnimation)
            mIsHideCanceled = false;
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        if(animation == mHideAnimation&&!mIsHideCanceled)
            mFabView.setVisibility(View.GONE);
    }

    @Override
    public void onAnimationRepeat(Animation animation) {

    }

}
