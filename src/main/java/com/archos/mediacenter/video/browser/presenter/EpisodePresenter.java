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

package com.archos.mediacenter.video.browser.presenter;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;
import com.archos.mediacenter.video.browser.adapters.ItemViewType;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.player.PlayerActivity;

/**
 * Created by alexandre on 27/10/15.
 */
public class EpisodePresenter extends VideoPresenter implements Presenter {
    private boolean mIsBackgroundTransparent = false;

    public EpisodePresenter(Context context, AdapterDefaultValues defaultValues, ExtendedClickListener listener) {
        super(context, defaultValues, listener, null);
    }

    @Override
    public int getItemType() {
        return ItemViewType.ITEM_VIEW_TYPE_SHOW;
    }

    @Override
    public View bindView(View view, Object object, ThumbnailEngine.Result result, int positionInAdapter) {
        super.bindView(view, object, null, positionInAdapter);
        Episode episode = (Episode) object;
                // ------------------------------------------------
                // File-based item => fill the ViewHolder fields depending
                // on the file type (file, folder or shortcut)
                // ------------------------------------------------
        if(mIsBackgroundTransparent) {
            view.setBackgroundResource(R.drawable.episode_background);
        }
        ViewHolder holder = (ViewHolder) view.getTag();
        if(holder.secondLine!=null)
            holder.secondLine.setVisibility(View.VISIBLE);
        String name = episode.getName();
        if(name == null ||  name.isEmpty())
            name = episode.getShowName()+ " "+ mContext.getString(R.string.leanback_episode_SXEX_code, episode.getSeasonNumber(), episode.getEpisodeNumber());
        holder.name.setText(name);
        int resumePosition = episode.getRemoteResumeMs()>0?episode.getRemoteResumeMs():episode.getResumeMs();
        boolean resume = resumePosition>0 || resumePosition == PlayerActivity.LAST_POSITION_END;
        if (resume&&holder.resume!=null) {
            int duration = episode.getDurationMs();

            duration = duration > 0 ? duration : resumePosition>0&&resumePosition<=100? 100 : 0;//resume can now be a percentage

            boolean displayProgressSlider = /*!mThinPhoneInPortrait&&*/(duration>0 ||resumePosition == PlayerActivity.LAST_POSITION_END); // Display the progress bar if we know the duration
            setResume(displayProgressSlider,duration > 0 ? duration : 100, resumePosition, holder.resume);

        } else if(holder.resume!=null){
            // Show disabled video icon (there is no such disabled resume slider)
            holder.resume.setVisibility(View.GONE);

        }

        if(holder.name!=null)
            holder.name.setEllipsize(TextUtils.TruncateAt.END);

        holder.number.setText(""+episode.getEpisodeNumber());

        if(holder.expanded!=null)
            holder.expanded.setVisibility(View.GONE);


        return view;
    }

    public void setTransparent(boolean backgroundTransparent){
        mIsBackgroundTransparent = backgroundTransparent;
    }
}
