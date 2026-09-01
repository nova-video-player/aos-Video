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

import static com.archos.mediacenter.video.browser.subtitlesmanager.ISO639codes.generateTrackName;

import android.content.Context;
import android.content.res.Resources;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.leanback.widget.VerticalGridView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediascraper.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by vapillon on 16/04/15.
 */
public class FileDetailsRowPresenter extends FullWidthRowPresenter implements BackgroundColorPresenter {

    private static final Logger log = LoggerFactory.getLogger(FileDetailsRowPresenter.class);

    private int mColor;
    Resources mR;

    private FileDetailsViewHolder mHolder;

    public class FileDetailsViewHolder extends RowPresenter.ViewHolder {
        /** the parent viewholder */
        final ViewHolder mFullWidthViewHolder;
        final ScrollView mScrollView;
        final TextView mFileNameTv, mFilePathTv, mFileSizeAndDurationTv, mFileErrorTv;
        final TextView mVideoTrackTv, mVideoDecoderTv, mAudioTracksTv, mSubtitlesTracksCol1Tv, mSubtitlesTracksCol2Tv;
        final View mProgress, mVideoGroup, mAudioGroup, mSubtitlesGroup;

        public FileDetailsViewHolder(ViewHolder parentViewHolder, View contentView) {
            super(parentViewHolder.view);

            mFullWidthViewHolder = parentViewHolder;
            mScrollView = (ScrollView) contentView.findViewById(R.id.file_details_scroll);

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

            mScrollView.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                int direction;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    direction = 1;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    direction = -1;
                } else {
                    return false;
                }
                View content = mScrollView.getChildAt(0);
                int scrollRange = content != null
                        ? Math.max(0, content.getHeight() - mScrollView.getHeight())
                        : 0;
                boolean canScroll = direction > 0
                        ? mScrollView.getScrollY() < scrollRange
                        : mScrollView.getScrollY() > 0;
                if (log.isDebugEnabled()) {
                    log.debug("File details DPAD {}: scrollY={}, scrollRange={}, viewportHeight={}, "
                                    + "contentHeight={}, frameworkCanScroll={}, hasFocus={}",
                            direction > 0 ? "DOWN" : "UP",
                            mScrollView.getScrollY(),
                            scrollRange,
                            mScrollView.getHeight(),
                            content != null ? content.getHeight() : -1,
                            mScrollView.canScrollVertically(direction),
                            mScrollView.hasFocus());
                }
                if (!canScroll) {
                    return moveToAdjacentRow(mScrollView, direction);
                }
                mScrollView.smoothScrollBy(0, direction * mScrollView.getHeight() / 2);
                return true;
            });
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

        if (mHolder != null)
            mHolder.mFullWidthViewHolder.getMainContainer().setBackgroundColor(color);
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.changeSelectLevel(holder, ((FileDetailsViewHolder) holder).mFullWidthViewHolder);
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
        vh.mFilePathTv.setText(VideoInfoCommonClass.getParentPath(videoObject));
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
            log.warn("file not reacheable? fileSize={}, videoTrack={}, audioTrackNb={}", videoMetadata.getFileSize(), videoMetadata.getVideoTrack(), videoMetadata.getAudioTrackNb());
            // sometimes metadata are set to zero but the file is there, can be due to libavosjni not loaded
            hideAudioVideoSubs(vh);
            vh.mFileErrorTv.setVisibility(View.VISIBLE);
            return;
        }

        // File size and duration
        {
            StringBuilder sb = new StringBuilder();
            sb.append(Formatter.formatFileSize(c, videoMetadata.getFileSize()));

            vh.mFileSizeAndDurationTv.setText(sb.toString());
            vh.mFileSizeAndDurationTv.setVisibility(View.VISIBLE);
        }

        final String SEP = "  ";

        // Video track
        VideoMetadata.VideoTrack video = videoMetadata.getVideoTrack();
        if (video != null) {

            vh.mVideoTrackTv.setText(VideoInfoCommonClass.getVideoTrackString(videoMetadata, mR));
            vh.mVideoDecoderTv.setVisibility(View.GONE);
            vh.mVideoGroup.setVisibility(View.VISIBLE);
        }
        else {
            vh.mVideoGroup.setVisibility(View.GONE);
        }

        // Audio track(s)


        CharSequence audioString = VideoInfoCommonClass.getAudioTrackString(videoMetadata, mR, c);
        if (audioString!=null) {
            vh.mAudioTracksTv.setText(audioString);
            vh.mAudioGroup.setVisibility(View.VISIBLE);
        }
        else {
            vh.mAudioGroup.setVisibility(View.GONE);
        }

        // Subtitles tracks info
        vh.mSubtitlesGroup.setVisibility(View.GONE);

        vh.mScrollView.scrollTo(0, 0);
        vh.mScrollView.post(() -> limitScrollViewHeight(vh.mScrollView));
        mHolder = vh;
    }

    private boolean moveToAdjacentRow(ScrollView scrollView, int direction) {
        ViewParent parent = scrollView.getParent();
        while (parent != null && !(parent instanceof VerticalGridView)) {
            parent = parent.getParent();
        }
        if (!(parent instanceof VerticalGridView)) {
            log.warn("Could not find the details VerticalGridView");
            return false;
        }

        VerticalGridView gridView = (VerticalGridView) parent;
        int currentPosition = gridView.getSelectedPosition();
        int nextPosition = currentPosition + direction;
        int itemCount = gridView.getAdapter() != null ? gridView.getAdapter().getItemCount() : 0;
        if (nextPosition < 0 || nextPosition >= itemCount) {
            if (log.isDebugEnabled()) {
                log.debug("No adjacent details row from row {} in direction {} (row count {})",
                        currentPosition, direction, itemCount);
            }
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("Moving focus from file details row {} to row {}", currentPosition, nextPosition);
        }
        gridView.setSelectedPositionSmooth(nextPosition);
        return true;
    }

    private void limitScrollViewHeight(ScrollView scrollView) {
        View content = scrollView.getChildAt(0);
        if (content == null) {
            return;
        }
        int maximumHeight = Math.round(scrollView.getResources().getDisplayMetrics().heightPixels * 0.8f);
        int contentHeight = content.getMeasuredHeight();
        ViewGroup.LayoutParams layoutParams = scrollView.getLayoutParams();
        if (contentHeight > maximumHeight) {
            if (layoutParams.height != maximumHeight) {
                layoutParams.height = maximumHeight;
                scrollView.setLayoutParams(layoutParams);
            }
        } else {
            if (layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                scrollView.setLayoutParams(layoutParams);
            }
        }
    }

    private String getSubtitleTrackList(Context context, int number, int offset, String separator, VideoMetadata videoMetadata) {
        StringBuilder sb = new StringBuilder();
        boolean isRtl = context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        String dirMarker = isRtl ? "\u200F" : "\u200E";
        for (int i=0 ; i<number ; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(dirMarker);
            int index = i + offset;
            String format = VideoUtils.getSubtitleFormatLabel(context, videoMetadata.getSubtitleTrack(index).format);
            VideoMetadata.SubtitleTrack track = videoMetadata.getSubtitleTrack(index);
            sb.append(Integer.toString(index + 1)).append(".").append(separator)
                    .append(StringUtils.removeHtmlTags(generateTrackName(context, track.name, track.language, format, track.disposition, false)) + separator);
        }
        return StringUtils.removeHtmlTags(sb.toString());
    }

    private void hideAudioVideoSubs(FileDetailsViewHolder vh) {
        vh.mFileSizeAndDurationTv.setVisibility(View.GONE);
        vh.mVideoGroup.setVisibility(View.GONE);
        vh.mAudioGroup.setVisibility(View.GONE);
        vh.mSubtitlesGroup.setVisibility(View.GONE);
    }
}
