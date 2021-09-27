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

package com.archos.mediacenter.video.leanback.overlay;

import android.content.Context;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.archos.customizedleanback.app.MyVerticalGridFragment;
import com.archos.mediacenter.video.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ScannerAndScraperProgress must be created while in the Fragment.onViewCreated() AND must be "paused" and "resumed" in onPause() and onResume()
 * Created by vapillon on 26/05/15.
 */
public class Overlay {
    private static final Logger log = LoggerFactory.getLogger(Overlay.class);

    // For now i'm doing some basic polling...
    final static int REPEAT_PERIOD_MS = 1000;

    final Context mContext;
    final private View mOverlayRoot;

    ScannerAndScraperProgress mScanProgress;
    Clock mClock;

    /**
     * Must be created from the Fragment onViewCreated() method
     * @param fragment
     */
    public Overlay(Fragment fragment) {
        log.debug("Overlay: creation");
        if (!fragment.isAdded()) {
            throw new IllegalStateException("Overlay must be created once the fragment is added!");
        }

        mContext = fragment.getActivity();
        ViewGroup fragmentView = (ViewGroup)fragment.getView();
        if (fragmentView==null) {
            throw new IllegalStateException("Overlay must be created once the fragment has its view created!");
        }

        int parentViewId = -1;
        if (fragment instanceof BrowseSupportFragment) {
            parentViewId = R.id.browse_frame;
        } else if (fragment instanceof MyVerticalGridFragment) {
            parentViewId = R.id.browse_dummy;
        } else if (fragment instanceof DetailsSupportFragment) {
            parentViewId = R.id.details_fragment_root;
        } else if (fragment instanceof GuidedStepSupportFragment) {
            parentViewId = R.id.guidedstep_background_view_root;
        } else {
            throw new IllegalStateException("Overlay is not compatible with this fragment: "+fragment);
        }

        ViewGroup parentView = (ViewGroup)fragmentView.findViewById(parentViewId);
        if (parentView==null) {
            throw new IllegalStateException("parentView not found! Maybe IDs in the leanback library have been changed?");
        }

        LayoutInflater.from(mContext).inflate(R.layout.leanback_overlay, parentView);
        mOverlayRoot = parentView.findViewById(R.id.overlay_root);
        mScanProgress = new ScannerAndScraperProgress(mContext, mOverlayRoot);
        mClock = new Clock(mContext, mOverlayRoot);
    }

    /**
     * MUST be called in the fragment onResume method
     */
    public void destroy() {
        log.debug("destroy");
        mScanProgress.destroy();
        mClock.destroy();
    }

    /**
     * MUST be called in the fragment onDestroyView method
     */
    public void resume() {
        log.debug("resume");
        mScanProgress.resume();
        mClock.resume();
    }

    /**
     * MUST be called in the fragment onPause method
     */
    public void pause() {
        log.debug("pause");
        mScanProgress.pause();
        mClock.pause();
    }

    /**
     * To be called whenever you want to hide the overlay widgets
     */
    public void hide() {
        log.debug("hide");
        mOverlayRoot.setVisibility(View.GONE);
    }

    public void show() {
        log.debug("show");
        mOverlayRoot.setVisibility(View.VISIBLE);
    }
}
