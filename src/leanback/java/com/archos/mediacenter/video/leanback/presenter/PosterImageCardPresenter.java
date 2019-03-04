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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.leanback.UnavailablePosterBroadcastReceiver;
import com.archos.mediacenter.video.picasso.ThumbnailRequestHandler;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.VideoProvider;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * Display a video, a movie, an episode (Poster or Thumbnail) or a TvShow (Poster)
 * using a regular ImageCardView
 * Created by vapillon on 10/04/15.
 */
public class PosterImageCardPresenter extends Presenter {

    public enum EpisodeDisplayMode {
        FOR_GENERAL_LIST,
        FOR_SEASON_LIST
    }

    final EpisodeDisplayMode mEpisodeDisplayMode;
    final Drawable mErrorDrawable;

    protected Context mContext;

    public class VideoViewHolder extends ViewHolder {
        private final View mOccurenciesView;
        private CustomImageCardview mCardView;
        private View mProgressBar;
        private PicassoImageCardViewTarget mImageCardViewTarget;

        public VideoViewHolder(Context context) {
            super(new CustomImageCardview(context));
            mOccurenciesView=  LayoutInflater.from(mContext).inflate(R.layout.leanback_video_occurencies, mCardView, false);
            mOccurenciesView.setVisibility(View.INVISIBLE);
            mCardView = (CustomImageCardview)view;

            mCardView.addViewToRoot(mOccurenciesView);
            mCardView.setMainImageDimensions(getWidth(context), getHeight(context));
            mCardView.setMainImage(new ColorDrawable(context.getResources().getColor(R.color.lb_basic_card_bg_color)));
            mCardView.getMainImageView().setBackgroundColor(context.getResources().getColor(R.color.lightblue900));
            mCardView.setFocusable(true);
            mCardView.setFocusableInTouchMode(true);

            View progressGroup = LayoutInflater.from(mContext).inflate(R.layout.leanback_resume, mCardView, false);
            mProgressBar = progressGroup.findViewById(R.id.resume_bar);
            // Set progressbar to 0 by default
            mProgressBar.getLayoutParams().width = 0;
            BaseCardView.LayoutParams lp = new BaseCardView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.viewType = BaseCardView.LayoutParams.VIEW_TYPE_INFO;
            mCardView.addView(progressGroup, mCardView.getChildCount(), lp);

            mImageCardViewTarget = new PicassoImageCardViewTarget(mCardView);
        }

        public void setOccurencies(int occurencies){
            if(occurencies>1)
                mOccurenciesView.setVisibility(View.VISIBLE);
            else
                mOccurenciesView.setVisibility(View.INVISIBLE);
            ((TextView)mOccurenciesView.findViewById(R.id.occurencies_text_view)).setText(String.valueOf(occurencies));
        }
        public ImageCardView getImageCardView() {
            return mCardView;
        }

        /**
         * non blocking (using Picasso to load the Uri)
         *
         * @param imageUri
         */
        protected void updateCardView(Uri imageUri, long videoId, boolean isLarge) {
            if(!(mImageCardViewTarget.getLastUri()!=null&&mImageCardViewTarget.getLastUri().equals(imageUri) && mImageCardViewTarget.isLastStateError())) {

                mImageCardViewTarget.setLastUri(imageUri);
                mCardView.setMainImageDimensions(getWidth(mContext, isLarge), getHeight(mContext, isLarge));
                mImageCardViewTarget.setVideoId(videoId);
                Picasso.get()
                        // must use an Uri here, does not work with path only
                        .load(imageUri)
                        .resize(getWidth(mContext, isLarge), getHeight(mContext, isLarge))
                        .centerCrop()
                        .error(mErrorDrawable)
                        .into(mImageCardViewTarget);
            }
            //if last update of vh failed with the same uri
            else
                updateCardView(mErrorDrawable);

        }

        /**
         * empty is 0, full is 100, one third is 33.333333f
         * @param resumePointInPercent
         * @param isLarge
         */
        public void setResumeInPercent(float resumePointInPercent, boolean isLarge) {
            // the use of getWidth() below is not very generic, but I tried getMeasuredWidth() and it is sometimes not set yet (race...)
            mProgressBar.getLayoutParams().width = (int)(getWidth(mContext,isLarge) * resumePointInPercent / 100f);
            mProgressBar.requestLayout();
        }

        public void updateCardView(Drawable drawable) {
            if(mCardView.getMainImage()!=drawable){
                mCardView.setMainImageScaleType(ImageView.ScaleType.CENTER);
                mCardView.setMainImage(drawable, false);
            }
        }
    }

    public int getWidth(Context context, boolean isLarge) {
        if(isLarge)
            return context.getResources().getDimensionPixelSize(R.dimen.poster_width_large);
        return context.getResources().getDimensionPixelSize(R.dimen.poster_width);
    }

    public int getHeight(Context context, boolean isLarge) {
        if(isLarge)
            return context.getResources().getDimensionPixelSize(R.dimen.poster_height_large);
        return context.getResources().getDimensionPixelSize(R.dimen.poster_height);
    }

    public int getWidth(Context context) {

        return getWidth(context, false);
    }

    public int getHeight(Context context) {

        return  getHeight(context, false);
    }


    public PosterImageCardPresenter(Context context) {
        super();
        mEpisodeDisplayMode = EpisodeDisplayMode.FOR_GENERAL_LIST; // default
        mErrorDrawable = context.getResources().getDrawable(R.drawable.filetype_new_video);
    }

    public PosterImageCardPresenter(Context context, EpisodeDisplayMode displayMode) {
        super();
        mEpisodeDisplayMode = displayMode;
        mErrorDrawable = context.getResources().getDrawable(R.drawable.filetype_new_video);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mContext = parent.getContext();
        VideoViewHolder vh = getVideoViewHolder(parent.getContext());
        return vh;
    }

    private VideoViewHolder getVideoViewHolder(Context context) {
        return new VideoViewHolder(context);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        VideoViewHolder vh = (VideoViewHolder)viewHolder;

        if (item instanceof Video) {
            bindVideo(vh, (Video) item);
        }
        else if (item instanceof Tvshow) {
            bindTvshow(vh, (Tvshow) item);
        }
        else if (item instanceof MetaFile2) {
            bindMetaFile(vh, (MetaFile2)item);
        }
        else {
            throw new IllegalArgumentException("PosterPresenter do not handle this object: "+item);
        }
    }

    private void bindVideo(VideoViewHolder vh, Video video) {
        final ImageCardView card = vh.getImageCardView();

        // setup the trakt flag BEFORE the poster because it is handled in a strange way in the ImageCardViewTarget
        vh.mImageCardViewTarget.setWatchedFlag(video.isWatched());


        Uri posterUri = video.getPosterUri();
        boolean isLarge= false;
        if (video instanceof Episode) {
            // Special TvShow episode binding
            Episode episode = (Episode)video;
            Resources r = card.getResources();
            String episodeName = episode.getName();
            if (episodeName==null) episodeName="";
            switch (mEpisodeDisplayMode) {
                case FOR_GENERAL_LIST:
                    card.setTitleText(episode.getShowName());
                    card.setContentText(r.getString(R.string.leanback_episode_details_for_general_list, episode.getSeasonNumber(), episode.getEpisodeNumber(), episodeName));
                    break;
                case FOR_SEASON_LIST:
                    if(episode.getPictureUri()!=null) {
                        posterUri = episode.getPictureUri();
                        isLarge = true;
                    }
                    card.setTitleText(r.getString(R.string.leanback_episode_name_for_season_list, episode.getEpisodeNumber(), episodeName));
                    card.setContentText("");
                    break;
            }
        }
        else {
            if (video instanceof Movie) {
                // Special Movie binding
                card.setTitleText(video.getName());
                card.setContentText("");
            } else {
                // Non indexed case
                card.setTitleText(video.getFilenameNonCryptic());
                card.setContentText("");
            }
        }
        if (posterUri!=null) {
            if (!isLarge)
                vh.updateCardView(posterUri, video.getId(),isLarge);
            else
                vh.updateCardView(posterUri, -1, isLarge);
        }
        //don't try to load thumb when not indexed or not local && create remote thumb is set to false
        else if (video.isIndexed()&& (FileUtils.isLocal(video.getFileUri())||PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(VideoProvider.PREFERENCE_CREATE_REMOTE_THUMBS, false))) {
            // get/build Thumbnail
            vh.updateCardView(ThumbnailRequestHandler.buildUri(video.getId()),-1, false);
        }
        else {
            vh.updateCardView(mErrorDrawable);

        }
        int resumeMs = video.getResumeMs();
        if (resumeMs == 0 || resumeMs == PlayerActivity.LAST_POSITION_UNKNOWN) {
            vh.setResumeInPercent(0, isLarge);
        } else if (resumeMs == PlayerActivity.LAST_POSITION_END) {
            vh.setResumeInPercent(100f, isLarge);
        } else {
            vh.setResumeInPercent(100*resumeMs/(float)video.getDurationMs(), isLarge);
        }
        vh.setOccurencies(video.getOccurencies());
    }

    /**
     * Use to bind a Tv Show (i.e. not an actual video file!)
     * CAUTION: this is not about an episode, but about about a whole show item
     * @param vh
     * @param tvshow
     */
    private void bindTvshow(VideoViewHolder vh, Tvshow tvshow) {
        final ImageCardView card = vh.getImageCardView();
        card.setTitleText(tvshow.getName());

        card.setContentText(tvshow.getCountString(card.getContext()));
        vh.updateCardView(tvshow.getPosterUri(), -1, false);
        vh.setOccurencies(0);
    }

    private void bindMetaFile(VideoViewHolder vh, MetaFile2 file) {
        final ImageCardView card = vh.getImageCardView();
        card.setMainImage(mContext.getResources().getDrawable(PresenterUtils.getIconResIdFor(file)), false);
        card.setMainImageScaleType(ImageView.ScaleType.CENTER);
        card.setTitleText(file.getName());
        card.setContentText("");
        vh.setOccurencies(0);

    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
       ((VideoViewHolder)viewHolder).getImageCardView().setMainImage(null);
    }

    public class PicassoImageCardViewTarget implements Target {
        final private ImageCardView mImageCardView;
        private boolean mWatchedFlag;
        private long mVideoId;
        private Uri mLastImageUri;
        private boolean mIsLastStateError;

        // Picasso documentation: Objects implementing this class must have a working implementation of Object.equals(Object) and Object.hashCode() for proper storage internally.
        @Override
        public boolean equals(Object other) {
            if (other==null || !(other instanceof PicassoImageCardViewTarget)) {
                return false;
            }
            // mImageCardView must never be null, no need to check it!
            return mImageCardView.equals( ((PicassoImageCardViewTarget)other).mImageCardView );
        }

        public PicassoImageCardViewTarget(ImageCardView imageCardView) {
            mImageCardView = imageCardView;
        }

        public void setWatchedFlag(boolean watched) {
            mWatchedFlag = watched;
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
            mIsLastStateError = false;
            Drawable posterDrawable = new BitmapDrawable(mContext.getResources(), bitmap);
            Drawable finalDrawable;
            if (mWatchedFlag) {
                Drawable layer[] = new Drawable[2];
                layer[0] = posterDrawable;
                BitmapDrawable icon = (BitmapDrawable) mContext.getResources().getDrawable(R.drawable.watched_icon_corner);
                icon.setGravity(Gravity.TOP | Gravity.RIGHT);
                layer[1] = icon;
                finalDrawable = new LayerDrawable(layer);
            } else {
                finalDrawable = posterDrawable; // simple
            }

            // Do not fade-in when loading from cache memory (because it is most likely instantaneous in that case)
            boolean fade = (loadedFrom!= Picasso.LoadedFrom.MEMORY);
            mImageCardView.setMainImage(finalDrawable, false);
            mImageCardView.setMainImageScaleType(ImageView.ScaleType.CENTER_CROP);
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable drawable){
            mIsLastStateError = true;
            if( mVideoId!=-1){
                UnavailablePosterBroadcastReceiver.sendBroadcast(mContext, mVideoId);
                mVideoId = -1;
            }else {
                if (mImageCardView.getMainImage() != drawable) {
                        mImageCardView.setMainImageScaleType(ImageView.ScaleType.CENTER);
                    mImageCardView.setMainImage(drawable, true);
                } else {
                    // Already the error drawable, do not load it again to avoid blinking
                }
            }
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            // Do nothing
        }

        public void setVideoId(Long videoId) {
            this.mVideoId = videoId;
        }


        public void setLastUri(Uri lastImageUri) {
            mLastImageUri = lastImageUri;
            mIsLastStateError = false;
        }

        public Uri getLastUri() {
            return mLastImageUri;
        }

        public boolean isLastStateError() {
            return mIsLastStateError;
        }
    }
}
