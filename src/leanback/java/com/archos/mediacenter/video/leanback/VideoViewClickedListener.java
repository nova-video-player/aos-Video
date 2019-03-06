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
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.view.View;

import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.leanback.details.VideoDetailsActivity;
import com.archos.mediacenter.video.leanback.details.VideoDetailsFragment;
import com.archos.mediacenter.video.leanback.presenter.ListPresenter;
import com.archos.mediacenter.video.leanback.search.VideoSearchFragment;
import com.archos.mediacenter.video.leanback.tvshow.TvshowActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowFragment;

/**
 * Created by vapillon on 13/04/15.
 */
public class VideoViewClickedListener implements OnItemViewClickedListener {

    final private Activity mActivity;

    public VideoViewClickedListener(Activity activity) {
        mActivity = activity;
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (item instanceof Video) {
            if(row!=null&&(row.getId() == MainFragment.ROW_ID_LAST_PLAYED || row.getId() == MainFragment.ROW_ID_LAST_ADDED || row.getId() == MainFragment.ROW_ID_ALL_MOVIES || row.getId() == VideoSearchFragment.ROW_ID)){
                showVideoDetails(mActivity, (Video) item, itemViewHolder, true, -1);
            }
            else
                showVideoDetails(mActivity, (Video) item, itemViewHolder, false, row!=null?row.getId():-1);
        }
        else if (item instanceof Tvshow) {
            showTvshowDetails(mActivity, (Tvshow) item, itemViewHolder);
        }
    }

    public static void showVideoDetails(Activity activity, Video video, Presenter.ViewHolder itemViewHolder, boolean forceSelection, long listId) {
        showVideoDetails(activity,video, itemViewHolder, true, forceSelection, true, listId, null, -1);
    }

    public static void showVideoDetails(Activity activity, Video video, Presenter.ViewHolder itemViewHolder, boolean animate, boolean forceSelection, boolean shouldLoadBackdrop, long listId, Fragment fragment, int requestCode) {
        Intent intent = new Intent(activity, VideoDetailsActivity.class);
        intent.putExtra(VideoDetailsFragment.EXTRA_VIDEO, video);
        intent.putExtra(VideoDetailsFragment.EXTRA_LIST_ID, listId);
        intent.putExtra(VideoDetailsFragment.EXTRA_FORCE_VIDEO_SELECTION, forceSelection);
        intent.putExtra(VideoDetailsFragment.EXTRA_SHOULD_LOAD_BACKDROP,shouldLoadBackdrop);
        View sourceView = null;
        if (itemViewHolder.view instanceof ImageCardView) {
            sourceView = ((ImageCardView) itemViewHolder.view).getMainImageView();
        } else if (itemViewHolder instanceof ListPresenter.ListViewHolder){
            sourceView = ((ListPresenter.ListViewHolder)itemViewHolder).getImageView();
        }
        if(animate) {
            Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    activity,
                    sourceView,
                    VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();

            if (fragment == null || requestCode == -1)
                activity.startActivity(intent, bundle);
            else
                fragment.startActivityForResult(intent, requestCode, bundle);
        }
        else{
            if (fragment == null || requestCode == -1)
                activity.startActivity(intent);
            else
                fragment.startActivityForResult(intent, requestCode);
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
}
