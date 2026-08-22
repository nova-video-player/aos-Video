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

package com.archos.mediacenter.video.leanback;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityOptionsCompat;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.leanback.collections.CollectionActivity;
import com.archos.mediacenter.video.leanback.collections.CollectionFragment;
import com.archos.mediacenter.video.leanback.details.VideoDetailsActivity;
import com.archos.mediacenter.video.leanback.details.VideoDetailsFragment;
import com.archos.mediacenter.video.leanback.details.VideoDetailsTransitionPosterCache;
import com.archos.mediacenter.video.leanback.presenter.ListPresenter;
import com.archos.mediacenter.video.leanback.tvshow.TvshowActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by vapillon on 13/04/15.
 */
public class VideoViewClickedListener implements OnItemViewClickedListener {

    private static final Logger log = LoggerFactory.getLogger(VideoViewClickedListener.class);

    final private Activity mActivity;

    private static void traceVideoDetailsLaunch(long launchUptimeMs, String event) {
        if (log.isDebugEnabled()) {
            log.debug("details timing: event={}, sinceTapMs={}", event,
                    SystemClock.elapsedRealtime() - launchUptimeMs);
        }
    }

    private static void traceNextSourceFrame(View sourceView, long launchUptimeMs) {
        if (sourceView == null || !log.isDebugEnabled()) return;
        sourceView.postOnAnimation(() -> traceVideoDetailsLaunch(launchUptimeMs,
                "source-next-frame-after-launch-request"));
    }

    public VideoViewClickedListener(Activity activity) {
        mActivity = activity;
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (item instanceof Video) {
            if (!(mActivity instanceof VideosByListActivity))
                showVideoDetails(mActivity, (Video) item, itemViewHolder, true, -1);
            else
                showVideoDetails(mActivity, (Video) item, itemViewHolder, false, row!=null?row.getId():-1);
        }
        else if (item instanceof Tvshow) {
            showTvshowDetails(mActivity, (Tvshow) item, itemViewHolder);
        }
        else if (item instanceof Collection) {
            showCollectionDetails(mActivity, (Collection) item, itemViewHolder);
        }
    }

    public static void showVideoDetails(Activity activity, Video video, Presenter.ViewHolder itemViewHolder, boolean forceSelection, long listId) {
        showVideoDetails(activity, video, itemViewHolder, true, forceSelection, true, listId, null);
    }

    public static void showVideoDetails(Activity activity, Video video, Presenter.ViewHolder itemViewHolder, boolean animate, boolean forceSelection, boolean shouldLoadBackdrop, long listId, ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(activity, VideoDetailsActivity.class);
        intent.putExtra(VideoDetailsFragment.EXTRA_VIDEO, video);
        intent.putExtra(VideoDetailsFragment.EXTRA_LIST_ID, listId);
        intent.putExtra(VideoDetailsFragment.EXTRA_FORCE_VIDEO_SELECTION, forceSelection);
        intent.putExtra(VideoDetailsFragment.EXTRA_SHOULD_LOAD_BACKDROP, shouldLoadBackdrop);
        // Carries one monotonic origin through the transition so the details screen can
        // report where a cold-start delay is spent.
        long launchUptimeMs = SystemClock.elapsedRealtime();
        intent.putExtra(VideoDetailsFragment.EXTRA_DETAILS_LAUNCH_UPTIME_MS, launchUptimeMs);
        traceVideoDetailsLaunch(launchUptimeMs, "source-click-handler");
        View sourceView = null;
        if (itemViewHolder.view instanceof ImageCardView) {
            sourceView = ((ImageCardView) itemViewHolder.view).getMainImageView();
        } else if (itemViewHolder instanceof ListPresenter.ListViewHolder){
            sourceView = ((ListPresenter.ListViewHolder)itemViewHolder).getImageView();
        }
        if (sourceView instanceof ImageView) {
            Drawable drawable = ((ImageView) sourceView).getDrawable();
            VideoDetailsTransitionPosterCache.put(launchUptimeMs, drawable);
        }
        if (animate) {
            traceVideoDetailsLaunch(launchUptimeMs, "source-transition-options-start");
            ActivityOptionsCompat opts = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    activity, sourceView, VideoDetailsActivity.SHARED_ELEMENT_NAME);
            Bundle optionsBundle = opts.toBundle();
            traceVideoDetailsLaunch(launchUptimeMs, "source-transition-options-ready");
            traceNextSourceFrame(sourceView, launchUptimeMs);
            if (launcher != null) {
                traceVideoDetailsLaunch(launchUptimeMs, "source-before-launcher-launch");
                launcher.launch(intent, opts);
                traceVideoDetailsLaunch(launchUptimeMs, "source-after-launcher-launch");
            } else {
                traceVideoDetailsLaunch(launchUptimeMs, "source-before-startActivity");
                activity.startActivity(intent, optionsBundle);
                traceVideoDetailsLaunch(launchUptimeMs, "source-after-startActivity");
            }
        } else {
            if (launcher != null)
                launcher.launch(intent);
            else
                activity.startActivity(intent);
        }
    }

    public static void showTvshowDetails(Activity activity, Tvshow tvshow, Presenter.ViewHolder itemViewHolder) {
        Intent intent = new Intent(activity, TvshowActivity.class);
        intent.putExtra(TvshowFragment.EXTRA_TVSHOW, tvshow);
        View sourceView = null;
        Bundle bundle = null;
        if (itemViewHolder.view instanceof ImageCardView) {
            sourceView = ((ImageCardView) itemViewHolder.view).getMainImageView();
            bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    activity,
                    sourceView,
                    TvshowFragment.SHARED_ELEMENT_NAME).toBundle();
            activity.startActivity(intent, bundle);
        }
        else if (itemViewHolder.view instanceof BaseCardView) {
            activity.startActivity(intent);
        }
    }

    public static void showCollectionDetails(Activity activity, Collection collection, Presenter.ViewHolder itemViewHolder) {
        Intent intent = new Intent(activity, CollectionActivity.class);
        intent.putExtra(CollectionFragment.EXTRA_COLLECTION, collection);
        View sourceView = null;
        Bundle bundle = null;
        if (itemViewHolder.view instanceof ImageCardView) {
            sourceView = ((ImageCardView) itemViewHolder.view).getMainImageView();
            bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    activity,
                    sourceView,
                    CollectionFragment.SHARED_ELEMENT_NAME).toBundle();
            activity.startActivity(intent, bundle);
        }
        else if (itemViewHolder.view instanceof BaseCardView) {
            activity.startActivity(intent);
        }
    }
}
