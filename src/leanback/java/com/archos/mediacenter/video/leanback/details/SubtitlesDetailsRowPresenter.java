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

package com.archos.mediacenter.video.leanback.details;

import android.content.Context;
import android.net.Uri;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by vapillon on 16/04/15.
 */
public class SubtitlesDetailsRowPresenter extends FullWidthRowPresenter implements BackgroundColorPresenter {

    private static final String TAG = "SubtitlesDetailsRowP";

    final SubtitleInterface mSubtitleInterface;
    private int mColor;

    private SubtitlesDetailsViewHolder mHolder;

    public class SubtitlesDetailsViewHolder extends RowPresenter.ViewHolder {
        /** the parent viewholder */
        final ViewHolder mFullWidthViewHolder;
        final View mProgress;
        final View mIntegratedSubsLabel, mIntegratedSubsRow;
        final TextView mIntegratedSubsCol1Tv, mIntegratedSubsCol2Tv;
        final View mExternalSubsLabel, mExternalSubsRow;
        final TextView mExternalSubsCol1Tv, mExternalSubsCol2Tv;
        final Button mDownloadSubsButton;
        final Button mChooseSubsButton;

        public SubtitlesDetailsViewHolder(ViewHolder parentViewHolder, View contentView) {
            super(parentViewHolder.view);
            mFullWidthViewHolder =parentViewHolder;
            mProgress = contentView.findViewById(R.id.progress);
            mIntegratedSubsLabel = contentView.findViewById(R.id.integrated_subtitles_label);
            mIntegratedSubsRow = contentView.findViewById(R.id.integrated_subtitles_row);
            mIntegratedSubsCol1Tv = (TextView)contentView.findViewById(R.id.integrated_subtitles_values_col1);
            mIntegratedSubsCol2Tv = (TextView)contentView.findViewById(R.id.integrated_subtitles_values_col2);
            mExternalSubsLabel = contentView.findViewById(R.id.external_subtitles_label);
            mExternalSubsRow = contentView.findViewById(R.id.external_subtitles_row);
            mExternalSubsCol1Tv = (TextView)contentView.findViewById(R.id.external_subtitles_values_col1);
            mExternalSubsCol2Tv = (TextView)contentView.findViewById(R.id.external_subtitles_values_col2);
            mDownloadSubsButton = (Button)contentView.findViewById(R.id.action_get_online_subtitles);
            mChooseSubsButton = (Button)contentView.findViewById(R.id.action_choose_subtitles);
        }
    }

    public SubtitlesDetailsRowPresenter(SubtitleInterface subtitleInterface, int color) {
        super();
        mColor = color;
        mSubtitleInterface = subtitleInterface;
        setHeaderPresenter(new RowHeaderPresenter());
    }

    @Override
    public void setBackgroundColor(int color) {
        mColor = color;

        if (mHolder != null)
            mHolder.mFullWidthViewHolder.getMainContainer().setBackgroundColor(color);
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.changeSelectLevel(holder, ((SubtitlesDetailsViewHolder) holder).mFullWidthViewHolder);
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        // We create the base class view holder first
        ViewHolder fullWidthViewHolder = (ViewHolder)super.createRowViewHolder(parent);

        // We expand the info view and put it inside the parent fullwidth container
        ViewGroup fullwidthContainer = fullWidthViewHolder.getMainContainer();
        View detailsView = LayoutInflater.from(parent.getContext()).inflate(R.layout.androidtv_subtitles_info_group, fullwidthContainer, false);
        fullwidthContainer.addView(detailsView);
        fullwidthContainer.setBackgroundColor(mColor);

        return new SubtitlesDetailsViewHolder(fullWidthViewHolder, detailsView);
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);
        final Context c = holder.view.getContext();
        final Video videoObject = ((SubtitlesDetailsRow) item).getVideo();
        final VideoMetadata videoMetadata = videoObject.getMetadata();
        final List<SubtitleManager.SubtitleFile> externalSubs = ((SubtitlesDetailsRow) item).getExternalSubs();
        SubtitlesDetailsViewHolder vh = (SubtitlesDetailsViewHolder) holder;
        vh.mFullWidthViewHolder.getMainContainer().setBackgroundColor(mColor);
        vh.mDownloadSubsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSubtitleInterface.performSubtitleDownload();
            }
        });
        vh.mChooseSubsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSubtitleInterface.performSubtitleChoose();
            }
        });

        if (videoMetadata!=null) {
            vh.mDownloadSubsButton.setVisibility(View.VISIBLE);

            Uri uri = videoObject.getFileUri();

            if (uri.getScheme().equals("file") || uri.getScheme().equals("smb"))
                vh.mChooseSubsButton.setVisibility(View.VISIBLE);
            else
                vh.mChooseSubsButton.setVisibility(View.GONE);

            vh.mProgress.setVisibility(View.GONE);
        }
        else {
            // I leave the button visible here so that it catches the focus even if the video info is not loaded yet
            vh.mDownloadSubsButton.setVisibility(View.VISIBLE);
            vh.mChooseSubsButton.setVisibility(View.GONE);
            vh.mProgress.setVisibility(View.VISIBLE);
        }

        // Internal subs from metadata
        List<VideoMetadata.SubtitleTrack> internalSubs = new LinkedList<VideoMetadata.SubtitleTrack>();
        if (videoMetadata!=null) {
            for (int n=0 ; n<videoMetadata.getSubtitleTrackNb() ; n++) {
                VideoMetadata.SubtitleTrack sub = videoMetadata.getSubtitleTrack(n);
                if (!sub.isExternal) {
                    internalSubs.add(sub);
                }
            }
        }
        if (internalSubs.isEmpty()) {
            vh.mIntegratedSubsRow.setVisibility(View.GONE);
            vh.mIntegratedSubsLabel.setVisibility(View.GONE);
        } else {
            boolean need2Columns = internalSubs.size() > 5;
            int internalSubsCol1Nb = need2Columns ? ((internalSubs.size() + 1) / 2) : internalSubs.size();
            int internalSubsCol2Nb = need2Columns ? (internalSubs.size() / 2) : 0;
            vh.mIntegratedSubsCol1Tv.setText(getFormattedSubList(c, internalSubsCol1Nb, 0,                  internalSubs));
            vh.mIntegratedSubsCol2Tv.setText(getFormattedSubList(c, internalSubsCol2Nb, internalSubsCol1Nb, internalSubs));
            vh.mIntegratedSubsCol2Tv.setVisibility(need2Columns ? View.VISIBLE : View.GONE);
            vh.mIntegratedSubsRow.setVisibility(View.VISIBLE);
            vh.mIntegratedSubsLabel.setVisibility(View.VISIBLE);
        }

        // External subs from the given list
        if (externalSubs == null || externalSubs.isEmpty()) {
            vh.mExternalSubsRow.setVisibility(View.GONE);
            vh.mExternalSubsLabel.setVisibility(View.GONE);
        }
        else {
            boolean need2Columns = externalSubs.size() > 5;
            int externalSubsCol1Nb = need2Columns ? ((externalSubs.size() + 1) / 2) : externalSubs.size();
            int externalSubsCol2Nb = need2Columns ? (externalSubs.size() / 2) : 0;
            vh.mExternalSubsCol1Tv.setText(getFormattedExternalSubList(externalSubsCol1Nb, 0,                  externalSubs));
            vh.mExternalSubsCol2Tv.setText(getFormattedExternalSubList(externalSubsCol2Nb, externalSubsCol1Nb, externalSubs));
            vh.mExternalSubsCol2Tv.setVisibility(need2Columns ? View.VISIBLE : View.GONE);
            vh.mExternalSubsRow.setVisibility(View.VISIBLE);
            vh.mExternalSubsLabel.setVisibility(View.VISIBLE);
        }

        mHolder = vh;
    }

    @Override
    protected void onRowViewSelected(RowPresenter.ViewHolder vh, boolean selected) {
        super.onRowViewSelected(vh, selected);
        if (selected) {
            ((SubtitlesDetailsViewHolder)vh).mDownloadSubsButton.requestFocus();
        }
    }

    private String getFormattedSubList(Context c, int number, int offset, List<VideoMetadata.SubtitleTrack> list) {
        final String SEP = "  ";
        StringBuilder sb = new StringBuilder();
        for (int i=0 ; i<number ; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            int index = i + offset;
            sb.append(Integer.toString(index + 1)).append(".").append(SEP)
              .append(VideoUtils.getLanguageString(c, list.get(index).name)).append(SEP);
        }
        return sb.toString();
    }

    private String getFormattedExternalSubList(int number, int offset, List<SubtitleManager.SubtitleFile> list) {
        final String SEP = "  ";
        StringBuilder sb = new StringBuilder();
        for (int i=0 ; i<number ; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            int index = i + offset;
            sb.append(Integer.toString(index + 1)).append(".").append(SEP)
              .append(list.get(index).mName).append(SEP);
        }
        return sb.toString();
    }

}
