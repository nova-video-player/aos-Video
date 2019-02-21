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
import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.leanback.tvshow.TvshowActionAdapter;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

public class SeasonPresenter extends Presenter {

    final Drawable mErrorDrawable;
    
    private long mActionId;

    private Context mContext;

    public class VideoViewHolder extends ViewHolder {
        private ImageCardView mCardView;
        private PicassoImageCardViewTarget mImageCardViewTarget;
        private TextView mInfoMessage;
        private boolean mConfirmDelete = false;

        public VideoViewHolder(Context context, boolean displayInfoMessage) {
            super(new ImageCardView(context));
            mCardView = (ImageCardView)view;
            mCardView.setMainImageDimensions(getWidth(context), getHeight(context));
            mCardView.setMainImage(new ColorDrawable(context.getResources().getColor(R.color.lb_basic_card_bg_color)));
            mCardView.setFocusable(true);
            mCardView.setFocusableInTouchMode(true);
            
            if (displayInfoMessage) {
                mInfoMessage = (TextView)LayoutInflater.from(mContext).inflate(R.layout.leanback_season_info_text, mCardView, false);
                BaseCardView.LayoutParams cardLp = new BaseCardView.LayoutParams(mInfoMessage.getLayoutParams());
                cardLp.viewType = BaseCardView.LayoutParams.VIEW_TYPE_INFO;
                mCardView.addView(mInfoMessage, cardLp);
            }

            mImageCardViewTarget = new PicassoImageCardViewTarget(mCardView);
        }

        public int getWidth(Context context) {
            return context.getResources().getDimensionPixelSize(R.dimen.poster_width);
        }

        public int getHeight(Context context) {
            return context.getResources().getDimensionPixelSize(R.dimen.poster_height);
        }

        public ImageCardView getImageCardView() {
            return mCardView;
        }

        public void setInfoMessage(CharSequence text) {
            mInfoMessage.setText(text);
        }

        public void setInfoColor(int color) {
            mInfoMessage.setBackgroundColor(color);
        }
        
        public boolean getConfirmDelete() {
            return mConfirmDelete;
        }

        public void enableConfirmDelete() {
            mConfirmDelete = true;

            mInfoMessage = (TextView)LayoutInflater.from(mContext).inflate(R.layout.leanback_season_info_text, mCardView, false);
            BaseCardView.LayoutParams cardLp = new BaseCardView.LayoutParams(mInfoMessage.getLayoutParams());
            cardLp.viewType = BaseCardView.LayoutParams.VIEW_TYPE_INFO;
            mCardView.addView(mInfoMessage, cardLp);

            final Resources r = mContext.getResources();

            setInfoMessage(r.getString(R.string.confirm_delete_short));
            setInfoColor(r.getColor(R.color.red));
        }

        /**
         * non blocking (using Picasso to load the Uri)
         * @param imageUri
         */
        protected void updateCardView(Uri imageUri) {
            Picasso.get()
                    // must use an Uri here, does not work with path only
                    .load(imageUri)
                    .resize(getWidth(mContext), getHeight(mContext))
                    .centerCrop()
                    .error(mErrorDrawable)
                    .into(mImageCardViewTarget);
        }
    }

    public SeasonPresenter(Context context, long actionId) {
        super();
        mErrorDrawable = context.getResources().getDrawable(R.drawable.filetype_new_video);
        mActionId = actionId;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mContext = parent.getContext();
        VideoViewHolder vh = new VideoViewHolder(parent.getContext(), mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        VideoViewHolder vh = (VideoViewHolder)viewHolder;
        Season season = (Season) item;

        // setup the watched flag BEFORE the poster because it is handled in a strange way in the ImageCardViewTarget
        // We show the top-right corner icon like for the individual videos, for consistency
        vh.mImageCardViewTarget.setWatchedFlag(mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED && season.allEpisodesWatched());

        vh.updateCardView(season.getPosterUri());

        final ImageCardView card = vh.getImageCardView();
        card.setTitleText(mContext.getString(R.string.season_identification, season.getSeasonNumber()));
        card.setContentText(mContext.getResources().getQuantityString(R.plurals.Nepisodes, season.getEpisodeTotalCount(), season.getEpisodeTotalCount()));

        if (mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED) {
            final Resources r = mContext.getResources();
            String desc;
            int color;
            if (season.allEpisodesWatched()) {
                desc = r.getString(R.string.all_episodes_watched);
                color = r.getColor(R.color.leanback_all_episodes_watched);
            }
            else if (season.allEpisodesNotWatched()) {
                desc = r.getString(R.string.no_episode_watched);
                color = r.getColor(R.color.leanback_no_episode_watched);
            }
            else {
                desc = r.getQuantityString(R.plurals.n_episodes_watched, season.getEpisodeWatchedCount(), season.getEpisodeWatchedCount());
                color = r.getColor(R.color.leanback_n_episodes_watched);
            }
            vh.setInfoMessage(desc);
            vh.setInfoColor(color);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        ((VideoViewHolder)viewHolder).getImageCardView().setMainImage(null);
    }

    public class PicassoImageCardViewTarget implements Target {
        final private ImageCardView mImageCardView;
        private boolean mWatchedFlag;

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
            mImageCardView.setMainImage(finalDrawable, fade);
            mImageCardView.setMainImageScaleType(ImageView.ScaleType.CENTER_CROP);
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable drawable){
            if (mImageCardView.getMainImage() != drawable) {
                mImageCardView.setMainImageScaleType(ImageView.ScaleType.CENTER);
                mImageCardView.setMainImage(drawable, true);
            } else {
                // Already the error drawable, do not load it again to avoid blinking
            }
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            // Do nothing
        }
    }
}
