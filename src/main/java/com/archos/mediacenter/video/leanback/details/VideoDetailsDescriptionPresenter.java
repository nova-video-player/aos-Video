/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.archos.mediacenter.video.leanback.details;

import android.animation.LayoutTransition;
import android.graphics.Paint;
import androidx.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediaprovider.video.VideoStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Started from AbstractDetailsDescriptionPresenter and modified
 */
public class VideoDetailsDescriptionPresenter extends Presenter {

    private static final Logger log = LoggerFactory.getLogger(VideoDetailsDescriptionPresenter.class);
    /**
     * I should not do that in theory, but it is so much simple to update the SINGLE object created by this class!
     */
    ViewHolder mSingleViewHolder;

    /**
     * The ViewHolder for the {@link VideoDetailsDescriptionPresenter}.
     */
    public static class ViewHolder extends Presenter.ViewHolder {
        final TextView mTitle;
        final View mEpisodeGroup;
        final View mContentRatingContainer;
        final TextView mEpisodeSXEX;
        final TextView mEpisodeTitle;
        final TextView mDate;
        final TextView mDuration;
        final TextView mRating;
        final TextView mContentRating;
        final TextView mBody;
        final ImageView mTraktWatched;
        final View mResolutionBadgeContainer;
        final TextView mResolutionBadge;
        final View mAudioBadgeContainer;
        final TextView mAudioBadge;
        final View m3dBadgeContainer;
        final TextView m3dBadge;
        final LinearLayout mBadgesLayout;
        final LayoutTransition mBadgesLayoutTransition;
        private ViewTreeObserver.OnPreDrawListener mPreDrawListener;

        public ViewHolder(final View view) {
            super(view);
            mTitle = (TextView) view.findViewById(androidx.leanback.R.id.lb_details_description_title);
            mEpisodeGroup = view.findViewById(R.id.episode_group);
            mEpisodeSXEX = (TextView) view.findViewById(R.id.episode_sxex);
            mEpisodeTitle = (TextView) view.findViewById(R.id.episode_title);
            mDate = (TextView) view.findViewById(R.id.date);
            mDuration = (TextView) view.findViewById(R.id.duration);
            mRating = (TextView) view.findViewById(R.id.rating);
            mContentRatingContainer = (View) view.findViewById(R.id.content_rating_container);
            mContentRating = (TextView) view.findViewById(R.id.content_rating);
            mBody = (TextView) view.findViewById(androidx.leanback.R.id.lb_details_description_body);
            mTraktWatched = (ImageView) view.findViewById(R.id.trakt_watched);
            mResolutionBadgeContainer = (View) view.findViewById(R.id.badge_resolution_container);
            mResolutionBadge = (TextView) view.findViewById(R.id.badge_resolution);
            mAudioBadgeContainer = (View) view.findViewById(R.id.badge_audio_container);
            mAudioBadge = (TextView) view.findViewById(R.id.badge_audio);
            m3dBadgeContainer = (View) view.findViewById(R.id.badge_3d_container);
            m3dBadge = (TextView) view.findViewById(R.id.badge_3d);

            // Disallow Video Badges Animation for now, will be allowed at end of VideoDetails enter transition
            // to prevent a huge animation glitch when VideoDetails is opened
            mBadgesLayout = ((LinearLayout)view.findViewById(R.id.badges));
            mBadgesLayoutTransition = mBadgesLayout.getLayoutTransition();
            mBadgesLayout.setLayoutTransition(null);

            mTitle.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    addPreDrawListener();
                }
            });
        }

        void addPreDrawListener() {
            if (mPreDrawListener != null) {
                return;
            }
            mPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    final boolean titleOnTwoLines = (mTitle.getLineCount() > 1);
                    final boolean hasEpisodeLine = (mEpisodeGroup.getVisibility()==View.VISIBLE);
                    int bodymaxLines = titleOnTwoLines ? 4 : 6; // MAGICAL
                    if (hasEpisodeLine) bodymaxLines-=1; // MAGICAL

                    if (mBody.getMaxLines() != bodymaxLines) {
                        mBody.setMaxLines(bodymaxLines);
                        return false;
                    } else {
                        removePreDrawListener();
                        return true;
                    }
                }
            };
            view.getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
        }

        void removePreDrawListener() {
            if (mPreDrawListener != null) {
                view.getViewTreeObserver().removeOnPreDrawListener(mPreDrawListener);
                mPreDrawListener = null;
            }
        }

        private Paint.FontMetricsInt getFontMetricsInt(TextView textView) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(textView.getTextSize());
            paint.setTypeface(textView.getTypeface());
            return paint.getFontMetricsInt();
        }
    }

    @Override
    public final ViewHolder onCreateViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.leanback_details_description, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public final void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder vh = (ViewHolder) viewHolder;
        Video video = (Video) item;

        if (video instanceof Episode) {
            Episode episode = (Episode)video;
            vh.mTitle.setText(((Episode) video).getShowName());
            vh.mEpisodeSXEX.setText(viewHolder.view.getContext().getString(R.string.leanback_episode_SXEX_code, episode.getSeasonNumber(), episode.getEpisodeNumber()));
            vh.mEpisodeTitle.setText(episode.getName());
            vh.mEpisodeGroup.setVisibility(View.VISIBLE);
            setTextOrSetGoneIfEmpty(vh.mDate, episode.getEpisodeDateFormatted());
            setTextOrSetGoneIfZero(vh.mRating, episode.getEpisodeRating());
            setTextOrSetGoneIfEmpty(vh.mContentRating, episode.getContentRating());
            vh.mContentRating.setTextColor(VideoDetailsFragment.getDominantColor());
            if (episode.getContentRating().isEmpty()) vh.mContentRatingContainer.setVisibility(View.GONE);
        }
        else if (video instanceof Movie){
            Movie movie = (Movie)video;
            vh.mTitle.setText(movie.getName());
            vh.mEpisodeGroup.setVisibility(View.GONE);
            setTextOrSetGoneIfEmpty(vh.mDate, Integer.toString(movie.getYear()));
            setTextOrSetGoneIfZero(vh.mRating, movie.getRating());
            setTextOrSetGoneIfEmpty(vh.mContentRating, movie.getContentRating());
            vh.mContentRating.setTextColor(VideoDetailsFragment.getDominantColor());
            if (movie.getContentRating().isEmpty()) vh.mContentRatingContainer.setVisibility(View.GONE);
        }
        else {
            // Non-scraped video
            vh.mTitle.setText(video.getFilenameNonCryptic());
            vh.mEpisodeGroup.setVisibility(View.GONE);
            vh.mDate.setVisibility(View.GONE);
            vh.mDuration.setVisibility(View.INVISIBLE);
            vh.mRating.setVisibility(View.GONE);
            vh.mContentRating.setVisibility(View.GONE);
            vh.mContentRatingContainer.setVisibility(View.GONE);
            //vh.mResolutionBadge.setVisibility(View.GONE);
            //vh.mResolutionBadgeContainer.setVisibility(View.GONE);
            //vh.mAudioBadge.setVisibility(View.GONE);
            //vh.mAudioBadgeContainer.setVisibility(View.GONE);
        }

        setTextOrSetInvisibleIfEmpty(vh.mDuration, MediaUtils.formatTime(video.getDurationMs()));

        vh.mBody.setText(video.getDescriptionBody());
        vh.mTraktWatched.setVisibility(video.isWatched() || video.getResumeMs() == PlayerActivity.LAST_POSITION_END ? View.VISIBLE : View.GONE);

        //We keep the viewholder reference only once it is bund
        mSingleViewHolder = vh;

        // We (now) have some case when the metadata are already available.
        // Setting it now avoids an ugly systematic icon blink...
        if (video.getMetadata()!=null) {
            displayActualVideoBadges(video);
        }
        else {
            displayGuessesVideoBadges(vh, video);
        }
    }

    private void setTextOrSetInvisibleIfEmpty(TextView mTextView, String text) {
        if (text==null || text.isEmpty()) {
            mTextView.setVisibility(View.INVISIBLE);
        } else {
            mTextView.setText(text);
            mTextView.setVisibility(View.VISIBLE);
        }
    }

    private void setTextOrSetGoneIfEmpty(TextView mTextView, String text) {
        if (text==null || text.isEmpty()) {
            mTextView.setVisibility(View.GONE);
        } else {
            mTextView.setText(text);
            mTextView.setVisibility(View.VISIBLE);
        }
    }

    private void setTextOrSetGoneIfZero(TextView mTextView, float value) {
        if (value == 0f) {
            mTextView.setVisibility(View.GONE);
        } else {
            mTextView.setText( Float.toString(value));
            mTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ViewHolder vh = (ViewHolder) viewHolder;
        // reset visibilities so that onBind() does not have to care about it
        vh.mEpisodeGroup.setVisibility(View.VISIBLE);
        vh.mDate.setVisibility(View.VISIBLE);
        vh.mDuration.setVisibility(View.VISIBLE);
        vh.mRating.setVisibility(View.VISIBLE);
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder holder) {
        // In case predraw listener was removed in detach, make sure
        // we have the proper layout.
        ViewHolder vh = (ViewHolder) holder;
        super.onViewAttachedToWindow(holder);
    }

    @Override
    public void onViewDetachedFromWindow(Presenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        vh.removePreDrawListener();
        super.onViewDetachedFromWindow(holder);
    }

    public void update(Video mVideo) {
        if (mSingleViewHolder != null) {
            onBindViewHolder(mSingleViewHolder, mVideo);
        }
    }

    public void allowVideoBadgesAnimation() {
        if (mSingleViewHolder != null && mSingleViewHolder.mBadgesLayout != null) {
            mSingleViewHolder.mBadgesLayout.setLayoutTransition(mSingleViewHolder.mBadgesLayoutTransition);
        }
    }

    /**
     * Display video badge according to the guess we got from the filename parsing
     */
    private void displayGuessesVideoBadges(ViewHolder vh, Video video) {
        boolean visible = false;
        String resolutionBadgeRes = "";
        switch (video.getNormalizedDefinition()) {
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_4K:
                resolutionBadgeRes = vh.view.getContext().getString(R.string.resolution_4k);
                visible = true;
                break;
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_1080P:
                resolutionBadgeRes = vh.view.getContext().getString(R.string.resolution_1080p);
                visible = true;
                break;
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_720P:
                resolutionBadgeRes = vh.view.getContext().getString(R.string.resolution_720p);
                visible = true;
                break;
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_SD:
                resolutionBadgeRes = vh.view.getContext().getString(R.string.resolution_SD);
                visible = true;
                break;
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_UNKNOWN:
            default:
                // Better display nothing (instead of SD) when we do not know
                visible = false;
                break;
        }

        log.debug("displayGuessesVideoBadges: " + resolutionBadgeRes + " visible " + visible);

        if (visible) {
            setTextOrSetGoneIfEmpty(vh.mResolutionBadge, resolutionBadgeRes);
            vh.mResolutionBadge.setTextColor(VideoDetailsFragment.getDominantColor());
            vh.mResolutionBadgeContainer.setVisibility(View.VISIBLE);
        }
        else {
            vh.mResolutionBadge.setVisibility(View.GONE);
            vh.mResolutionBadgeContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Display Video and Audio badges based on the actual VideoMetadata from the file.
     * HACK: only work if there is a (single) ViewHolder stored in mSingleViewHolder
     * @param video
     */
    public void displayActualVideoBadges(Video video) {
        if (mSingleViewHolder!=null) {
            VideoMetadata metadata = video.getMetadata();
            if (metadata==null) {
                log.error("updateVideoBadges should be called only once the metadata are known!");
                return;
            }

            // duration
            setTextOrSetInvisibleIfEmpty(mSingleViewHolder.mDuration, MediaUtils.formatTime(metadata.getDuration()));

            // 3d badge
            mSingleViewHolder.m3dBadge.setVisibility(video.is3D() ? View.VISIBLE : View.GONE);

            // Resolution badge
            final int w = metadata.getVideoWidth();
            final int h = metadata.getVideoHeight();
            log.debug("displayActualVideoBadges: " + w + "x" + h);
            String resolutionBadgeRes = "";
            boolean visible = false;
            if (w>=3840 || h>=2160) {
                resolutionBadgeRes = mSingleViewHolder.view.getContext().getString(R.string.resolution_4k);
                visible = true;
            }
	    // normally you would expect w>=1920 || h>=1080 but based on collection empirical observation we need to include a margin to detect fhd video resolution
            else if (w>=1728 || h>=1040) {
                resolutionBadgeRes = mSingleViewHolder.view.getContext().getString(R.string.resolution_1080p);
                visible = true;
            }
	    // normally you would expect w>=1280 || h>=720 but based on collection empirical observation we need to include a margin to detect hd video resolution
            else if (w>=1200 || h>=720) {
                resolutionBadgeRes = mSingleViewHolder.view.getContext().getString(R.string.resolution_720p);
                visible = true;
            }
            else if (w>0 && h>0){
                resolutionBadgeRes = mSingleViewHolder.view.getContext().getString(R.string.resolution_SD);
                visible = true;
            }

            log.debug("displayActualVideoBadges: " + resolutionBadgeRes + " visible " + visible);

            if (visible) {
                setTextOrSetGoneIfEmpty(mSingleViewHolder.mResolutionBadge, resolutionBadgeRes);
                mSingleViewHolder.mResolutionBadge.setTextColor(VideoDetailsFragment.getDominantColor());
                mSingleViewHolder.mResolutionBadgeContainer.setVisibility(View.VISIBLE);
            } else {
                mSingleViewHolder.mResolutionBadge.setVisibility(View.GONE);
                mSingleViewHolder.mResolutionBadgeContainer.setVisibility(View.GONE);
            }

            // Audio badge
            int audioChannels = -1;

            for (int n=0; n<metadata.getAudioTrackNb(); n++) {
                if (metadata.getAudioTrack(n).channels!=null) {
                    int channels = -1;

                    if (metadata.getAudioTrack(n).channels.startsWith("7.1"))
                        channels = 7;
                    else if (metadata.getAudioTrack(n).channels.startsWith("5.1"))
                        channels = 5;
                    else if (metadata.getAudioTrack(n).channels.startsWith("Stereo"))
                        channels = 2;

                    if (channels > audioChannels)
                        audioChannels = channels;
                }
            }

            if (audioChannels != -1) {
                String audioBadgeRes = "";

                if (audioChannels == 7) {
                    audioBadgeRes = mSingleViewHolder.view.getContext().getString(R.string.audio_7_1);
                } else if (audioChannels == 5) {
                    audioBadgeRes = mSingleViewHolder.view.getContext().getString(R.string.audio_5_1);
                } else if (audioChannels == 2) {
                    audioBadgeRes = mSingleViewHolder.view.getContext().getString(R.string.audio_2_0);
                }

                log.debug("displayActualVideoBadges " + audioBadgeRes + " visible true");

                setTextOrSetGoneIfEmpty(mSingleViewHolder.mAudioBadge, audioBadgeRes);
                mSingleViewHolder.mAudioBadge.setTextColor(VideoDetailsFragment.getDominantColor());
                mSingleViewHolder.mAudioBadgeContainer.setVisibility(View.VISIBLE);
            }
            else {
                mSingleViewHolder.mAudioBadge.setVisibility(View.GONE);
                mSingleViewHolder.mAudioBadgeContainer.setVisibility(View.GONE);
            }

        }
    }
}
