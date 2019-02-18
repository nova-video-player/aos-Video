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
import android.content.res.Resources;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;

/**
 * Created by vapillon on 16/04/15.
 */
public class FileDetailsRowPresenter extends FullWidthRowPresenter implements BackgroundColorPresenter {

    private int mColor;
    Resources mR;




    public class FileDetailsViewHolder extends RowPresenter.ViewHolder {
        /** the parent viewholder */
        final ViewHolder mFullWidthViewHolder;
        final TextView mFileNameTv, mFilePathTv, mFileSizeAndDurationTv, mFileErrorTv;
        final TextView mVideoTrackTv, mVideoDecoderTv, mAudioTracksTv, mSubtitlesTracksCol1Tv, mSubtitlesTracksCol2Tv;
        final View mProgress, mVideoGroup, mAudioGroup, mSubtitlesGroup;

        public FileDetailsViewHolder(ViewHolder parentViewHolder, View contentView) {
            super(parentViewHolder.view);

            mFullWidthViewHolder = parentViewHolder;

            mFileNameTv = (TextView)contentView.findViewById(R.id.file_name);
            mFilePathTv = (TextView)contentView.findViewById(R.id.file_path);
            mFileSizeAndDurationTv = (TextView)contentView.findViewById(R.id.file_size_and_duration);
            mFileErrorTv = (TextView)contentView.findViewById(R.id.file_error);

            mProgress = contentView.findViewById(R.id.progress);
            mVideoGroup = contentView.findViewById(R.id.video_row);
            mVideoTrackTv = (TextView)mVideoGroup.findViewById(R.id.video_track);
            mVideoDecoderTv = (TextView)mVideoGroup.findViewById(R.id.video_decoder);
            mAudioGroup = contentView.findViewById(R.id.audio_row);
            mAudioTracksTv = (TextView)mAudioGroup.findViewById(R.id.audio_track);
            mSubtitlesGroup = contentView.findViewById(R.id.subtitles_row);
            mSubtitlesTracksCol1Tv = (TextView)mSubtitlesGroup.findViewById(R.id.subtitle_track_col1);
            mSubtitlesTracksCol2Tv = (TextView)mSubtitlesGroup.findViewById(R.id.subtitle_track_col2);
        }
    }

    public FileDetailsRowPresenter(int color) {
        super();
        mColor = color;
        setHeaderPresenter(new RowHeaderPresenter());
    }

    @Override
    public void setBackgroundColor(int color) {
        mColor = color;
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.changeSelectLevel(holder, ((FileDetailsViewHolder) holder).mFullWidthViewHolder, holder.getSelectLevel());
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        mR = parent.getResources();

        // We create the base class view holder first
        FullWidthRowPresenter.ViewHolder fullWidthViewHolder = (FullWidthRowPresenter.ViewHolder)super.createRowViewHolder(parent);

        // We expand the info view and put it inside the parent fullwidth container
        ViewGroup fullwidthContainer = (ViewGroup)fullWidthViewHolder.getMainContainer();
        View detailsView = LayoutInflater.from(parent.getContext()).inflate(R.layout.androidtv_detailled_info_group, fullwidthContainer, false);
        fullwidthContainer.addView(detailsView);

        fullwidthContainer.setBackgroundColor(mColor);
        
        // hacky
        View rootContainer = (View)fullwidthContainer.getParent();
        rootContainer.setPadding(rootContainer.getPaddingLeft(), rootContainer.getPaddingTop(), rootContainer.getPaddingRight(), 0);

        return new FileDetailsViewHolder(fullWidthViewHolder, detailsView);
    }


    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);
        final Context c = holder.view.getContext();

        Video videoObject = ((FileDetailsRow) item).getVideo();
        int playerType = ((FileDetailsRow) item).getPlayerType();
        FileDetailsViewHolder vh = (FileDetailsViewHolder) holder;
        vh.mFullWidthViewHolder.getMainContainer().setBackgroundColor(mColor);
        vh.mFileNameTv.setText(videoObject.getFilenameNonCryptic());

        String path = videoObject.getFriendlyPath();
        String parentPath = path.substring(0, path.lastIndexOf("/"));

        vh.mFilePathTv.setText(parentPath);
        vh.mProgress.setVisibility(View.GONE);
        vh.mFileErrorTv.setVisibility(View.GONE);

        // video metaData is null when creating the view at init, in that case we display a progress wheel
        VideoMetadata videoMetadata = videoObject.getMetadata();
        if (videoMetadata==null) {
            hideAudioVideoSubs(vh);
            if(!((FileDetailsRow) item).shouldHideLoadingAndMetadata())
                vh.mProgress.setVisibility(View.VISIBLE);
            else
                vh.mProgress.setVisibility(View.GONE);
            return;
        }

        // Special error case (99.9% of the time it happens when the specified file is not reachable)
        if (videoMetadata.getFileSize()==0 && videoMetadata.getVideoTrack()==null && videoMetadata.getAudioTrackNb()==0) {
            hideAudioVideoSubs(vh);
            vh.mFileErrorTv.setVisibility(View.VISIBLE);
            return;
        }

        // File size and duration
        {
            StringBuilder sb = new StringBuilder();
            sb.append(Formatter.formatFileSize(c, videoMetadata.getFileSize()));
            sb.append("          ");
            if (videoMetadata.getDuration() > 0) {
                sb.append(MediaUtils.formatTime(videoMetadata.getDuration()));
            }

            vh.mFileSizeAndDurationTv.setText(sb.toString());
            vh.mFileSizeAndDurationTv.setVisibility(View.VISIBLE);
        }

        final String SEP = "  ";

        // Video track
        VideoMetadata.VideoTrack video = videoMetadata.getVideoTrack();
        if (video != null) {

            vh.mVideoTrackTv.setText(VideoInfoCommonClass.getVideoTrackString(videoMetadata, mR));
            String decoder = VideoInfoCommonClass.getDecoder(videoMetadata,mR, playerType);
            if (decoder!=null) {
                vh.mVideoDecoderTv.setText(decoder);
                vh.mVideoDecoderTv.setVisibility(View.VISIBLE);
            } else {
                vh.mVideoDecoderTv.setVisibility(View.GONE);
            }

            vh.mVideoGroup.setVisibility(View.VISIBLE);
        }
        else {
            vh.mVideoGroup.setVisibility(View.GONE);
        }

        // Audio track(s)


        String audioString = VideoInfoCommonClass.getAudioTrackString(videoMetadata, mR, c);
        if (audioString!=null) {
            vh.mAudioTracksTv.setText(audioString);
            vh.mAudioGroup.setVisibility(View.VISIBLE);
        }
        else {
            vh.mAudioGroup.setVisibility(View.GONE);
        }

        // Subtitles tracks info
        int subtitleTrackNb = videoMetadata.getSubtitleTrackNb();
        if (subtitleTrackNb > 0) {
            boolean need2Columns = subtitleTrackNb > 5;
            int subtitleTrackCol1Nb = need2Columns ? ((subtitleTrackNb + 1) / 2) : subtitleTrackNb;
            int subtitleTrackCol2Nb = need2Columns ? (subtitleTrackNb / 2) : 0;
            vh.mSubtitlesTracksCol1Tv.setText(getSubtitleTrackList(c, subtitleTrackCol1Nb, 0,                   SEP, videoMetadata));
            vh.mSubtitlesTracksCol2Tv.setText(getSubtitleTrackList(c, subtitleTrackCol2Nb, subtitleTrackCol1Nb, SEP, videoMetadata));
            vh.mSubtitlesTracksCol2Tv.setVisibility(need2Columns ? View.VISIBLE : View.GONE);
            vh.mSubtitlesGroup.setVisibility(View.VISIBLE);
        }
        else {
            vh.mSubtitlesGroup.setVisibility(View.GONE);
        }
    }

    private String getSubtitleTrackList(Context context, int number, int offset, String separator, VideoMetadata videoMetadata) {
        StringBuilder sb = new StringBuilder();
        for (int i=0 ; i<number ; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            int index = i + offset;
            sb.append(Integer.toString(index + 1)).append(".").append(separator)
              .append(VideoUtils.getLanguageString(context, videoMetadata.getSubtitleTrack(index).name))
              .append(separator);
        }
        return sb.toString();
    }

    private void hideAudioVideoSubs(FileDetailsViewHolder vh) {
        vh.mFileSizeAndDurationTv.setVisibility(View.GONE);
        vh.mVideoGroup.setVisibility(View.GONE);
        vh.mAudioGroup.setVisibility(View.GONE);
        vh.mSubtitlesGroup.setVisibility(View.GONE);
    }
}
