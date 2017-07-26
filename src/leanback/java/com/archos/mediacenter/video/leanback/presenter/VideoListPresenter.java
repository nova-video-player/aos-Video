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

import android.net.Uri;
import android.view.View;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.player.PlayerActivity;

/**
 * ListPresenter for Video objects
 * Created by vapillon on 10/04/15.
 */
public class VideoListPresenter extends ListPresenter {

    final private boolean mDisplayFileName;

    public VideoListPresenter(boolean displayFileName) {
        super();
        mDisplayFileName = displayFileName;
    }

    @Override
    public void onBindListViewHolder(ListViewHolder viewHolder, Object item) {
        ListViewHolder vh = (ListViewHolder)viewHolder;
        Video video = (Video)item;
        final Uri posterUri = video.getPosterUri();
        if (posterUri!=null) {
            vh.updateImageViewPoster(posterUri, video.getId());
        }
        else {
            vh.updateImageViewThumbnail(video.getId());
        }

        if (video instanceof Episode) {
            Episode episode = (Episode)video;
            vh.setTitleText(vh.view.getResources().getString(R.string.leanback_episode_name_for_browser_list,
                    episode.getShowName(), episode.getSeasonNumber(), episode.getEpisodeNumber(), episode.getEpisodeName()));
            if (mDisplayFileName) {
                vh.setContentText(video.getFilenameNonCryptic());
                vh.setContentTextVisibility(View.VISIBLE);
            } else {
                vh.setContentTextVisibility(View.GONE);
            }
        }
        else if (video instanceof Movie) {
            vh.setTitleText(video.getName());
            if (mDisplayFileName) {
                vh.setContentText(video.getFilenameNonCryptic());
                vh.setContentTextVisibility(View.VISIBLE);
            } else {
                vh.setContentTextVisibility(View.GONE);
            }
        }
        else {// Non-indexed or non-scraped video
            vh.setTitleText(video.getFilenameNonCryptic());
            vh.setContentTextVisibility(View.GONE);
        }

        int resumeMs = video.getResumeMs();
        if (resumeMs == 0 || resumeMs == PlayerActivity.LAST_POSITION_UNKNOWN) {
            vh.setResumeInPercent(0f);
        } else if (resumeMs == PlayerActivity.LAST_POSITION_END) {
            vh.setResumeInPercent(100f);
        } else {
            vh.setResumeInPercent(100 * resumeMs/(float)video.getDurationMs());
        }

        vh.setWatched(video.isWatched());
    }
}
