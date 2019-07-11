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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.Box;
import com.archos.mediaprovider.video.NetworkScannerServiceVideo;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by vapillon on 07/06/15.
 */
public class RescanBoxItemPresenter extends BoxItemPresenter implements NetworkScannerServiceVideo.ScannerListener {

    private static final String TAG = "RescanBoxItemPresenter";
    /**
     * Keep track of all attached views that are animated
     * (for now there is actually only one item IRL)
     */
    final List<View> mAttachedAnimatedViews = new LinkedList<>();

    /**
     * Boolean to be sure we register the presenter as NetworkScanner listener only once
     * (needed because the registration is done in onViewAttachedToWindow that may be called several times.
     * Yes, this is a bit crappy...)
     */
    private boolean mNetworkScannerListenerRegistered = false;

    @Override
    public BoxViewHolder onCreateViewHolder(ViewGroup parent) {
        return new BoxViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);

        BoxViewHolder vh = (BoxViewHolder)viewHolder;
        Box box = (Box)item;
        vh.getTextView().setText(box.getName());

        final Resources res = vh.mRoot.getResources();

        vh.mRoot.getLayoutParams().height = res.getDimensionPixelSize(R.dimen.smbshortcut_height);
        vh.mImageViewContainer.setBackgroundColor(res.getColor(R.color.green700));

        // For some reason the onViewAttachedToWindow/onViewDetachedFromWindow callbacks from the Presenter interface
        // do not work in all cases (when the view goes out of screen without being recycled)
        // ==> With the listener below it works fine
        vh.getImageView().addOnAttachStateChangeListener(mOnAttachStateChangeListener);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        super.onUnbindViewHolder(viewHolder);

        BoxViewHolder vh = (BoxViewHolder)viewHolder;

        // Be sure to cancel the rotating animation that may have been setup
        vh.getImageView().animate().cancel();
        vh.getImageView().removeOnAttachStateChangeListener(mOnAttachStateChangeListener);
    }

    final View.OnAttachStateChangeListener mOnAttachStateChangeListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View v) {

            // Keep track of this view and register the NetworkScanner listener if not already registered
            mAttachedAnimatedViews.add(v);
            Log.d(TAG, "onViewAttachedToWindow "+mAttachedAnimatedViews.size());

            if (mNetworkScannerListenerRegistered == false) {
                NetworkScannerServiceVideo.addListener(RescanBoxItemPresenter.this);
                mNetworkScannerListenerRegistered = true;
            }
            // Start animating the view if needed
            updateAnimation(v);
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            Log.d(TAG, "onViewDetachedFromWindow");

            v.animate().cancel();

            // We do not track tis view anymore, check if we still need the listener
            mAttachedAnimatedViews.remove(v);
            Log.d(TAG, "onViewDetachedFromWindow "+mAttachedAnimatedViews.size());
            if (mAttachedAnimatedViews.isEmpty()) {
                NetworkScannerServiceVideo.removeListener(RescanBoxItemPresenter.this);
                mNetworkScannerListenerRegistered = false;
            }
        }
    };

    /**
     * Implements NetworkScannerServiceVideo.ScannerListener
     */
    @Override
    public void onScannerStateChanged() {
        Log.d(TAG, "onScannerStateChanged "+NetworkScannerServiceVideo.isScannerAlive());
        // Update all the animated views
        for (View v : mAttachedAnimatedViews) {
            updateAnimation(v);
        }
    }

    /**
     * Start or stop the rotating animation on this view
     * @param v
     */
    private void updateAnimation(View v) {
        if (NetworkScannerServiceVideo.isScannerAlive()) {
            Log.d(TAG, "updateAnimation start");
            // HACK: the rotation is actually not infinite, but 10000 times...
            v.animate().rotationBy(360 * 10000).setDuration(1200 * 10000).setInterpolator(null).start(); // 1,2sec rotation period
        } else {
            Log.d(TAG, "updateAnimation stop");
            v.animate().cancel();
        }
    }
}
