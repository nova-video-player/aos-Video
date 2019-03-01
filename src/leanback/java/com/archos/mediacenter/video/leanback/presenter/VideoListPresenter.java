// Copyright 2017 Archos SA, 2019 Courville Software
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
import android.preference.PreferenceManager;
import android.view.View;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.VideoProvider;

/**
 * ListPresenter for Video objects including Video, Episodes, Tvshow (though not technically a Video)
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
        ListViewHolder vh = (ListViewHolder) viewHolder;

        if (item instanceof Movie || item instanceof Episode || item instanceof Video) {
            Video video = (Video) item;
            final Uri posterUri = video.getPosterUri();
            if (posterUri != null)
                vh.updateImageViewPoster(posterUri, video.getId());
            else if (video.isIndexed()&& (FileUtils.isLocal(video.getFileUri())||PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(VideoProvider.PREFERENCE_CREATE_REMOTE_THUMBS, false)))
                vh.updateImageViewThumbnail(video.getId());
            else
                vh.getImageView().setImageResource(R.drawable.filetype_new_video);
            if (item instanceof Episode) {
                Episode episode = (Episode) item;
                vh.setTitleText(vh.view.getResources().getString(R.string.leanback_episode_name_for_browser_list,
                        episode.getShowName(), episode.getSeasonNumber(), episode.getEpisodeNumber(), episode.getEpisodeName()));
            } else if (item instanceof Movie)
                vh.setTitleText(video.getName());
            else // Non-indexed or non-scraped video
                vh.setTitleText(video.getFilenameNonCryptic());
            if (item instanceof Movie || item instanceof Episode)
                if (mDisplayFileName) {
                    vh.setContentText(video.getFilenameNonCryptic());
                    vh.setContentTextVisibility(View.VISIBLE);
                } else {
                    vh.setContentTextVisibility(View.GONE);
                }
            else // Non-indexed or non-scraped video
                vh.setContentTextVisibility(View.GONE);
            int resumeMs = video.getResumeMs();
            if (resumeMs == 0 || resumeMs == PlayerActivity.LAST_POSITION_UNKNOWN) {
                vh.setResumeInPercent(0f);
            } else if (resumeMs == PlayerActivity.LAST_POSITION_END) {
                vh.setResumeInPercent(100f);
            } else {
                vh.setResumeInPercent(100 * resumeMs / (float) video.getDurationMs());
            }
            vh.setWatched(video.isWatched());
        } else if (item instanceof Tvshow) {
            Tvshow tvshow = (Tvshow) item;
            final Uri posterUri = tvshow.getPosterUri();
            if (posterUri != null) {
                vh.updateImageViewPoster(posterUri, tvshow.getTvshowId());
            } else {
                vh.updateImageViewThumbnail(tvshow.getTvshowId());
            }
            vh.setTitleText(tvshow.getName());
            vh.setContentText(tvshow.getCountString(ArchosUtils.getGlobalContext()));
            vh.setContentTextVisibility(View.VISIBLE);
        }
    }
}
