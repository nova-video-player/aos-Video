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
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.UnavailablePosterBroadcastReceiver;
import com.archos.mediacenter.video.picasso.ThumbnailRequestHandler;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * Display a video, a movie, an episode (Poster or Thumbnail) or a TvShow (Poster)
 * using a regular ImageCardView
 * Created by vapillon on 10/04/15.
 */
public abstract class ListPresenter extends Presenter {

    private Context mContext;

    public class ListViewHolder extends ViewHolder {
        private BaseCardView mBaseCardView;
        final private ImageView mImageView;
        final private TextView mTitleTv;
        final private TextView mContentTv;
        final private PicassoImageViewTarget mImageViewTarget;
        final private View mProgressBar;
        final private View mWatchedIcon;

        public ListViewHolder(Context context) {
            super(new BaseCardView(context));
            mBaseCardView = (BaseCardView)view;
            mBaseCardView.setBackgroundColor(context.getResources().getColor(R.color.lb_basic_card_info_bg_color));
            mBaseCardView.setFocusable(true);
            mBaseCardView.setFocusableInTouchMode(true);
            mBaseCardView.setCardType(BaseCardView.CARD_TYPE_MAIN_ONLY);

            LayoutInflater.from(context).inflate(R.layout.leanback_listitem_cardview_content, mBaseCardView, true);
            mImageView = (ImageView)mBaseCardView.findViewById(R.id.image);
            mTitleTv = (TextView)mBaseCardView.findViewById(R.id.title);
            mContentTv = (TextView)mBaseCardView.findViewById(R.id.content);
            mProgressBar = mBaseCardView.findViewById(R.id.resume_bar);
            mWatchedIcon = mBaseCardView.findViewById(R.id.watched_icon);
            // Hide progressbar and watched icon by default
            mProgressBar.setVisibility(View.GONE);
            mWatchedIcon.setVisibility(View.GONE);

            mImageViewTarget = new PicassoImageViewTarget(mImageView);
        }

        public ImageView getImageView() {
            return mImageView;
        }

        public void setTitleText(String title) {
            mTitleTv.setText(title);
        }
        public void setContentText(String content) {
            mContentTv.setText(content);
        }
        public void setContentTextVisibility(int visibility) {
            mContentTv.setVisibility(visibility);
        }

        public int getWidth(Context context) {
            return context.getResources().getDimensionPixelSize(R.dimen.list_thumbnail_width);
        }

        public int getHeight(Context context) {
            return context.getResources().getDimensionPixelSize(R.dimen.list_thumbnail_height);
        }

        /**
         * non blocking (using Picasso to load the Uri)
         *
         * @param posterUri
         */
        protected void updateImageViewPoster(Uri posterUri, long videoId) {

            //setting fallback : when fail to load poster (for example when file doesn't exist), try to load thumbnail
            mImageViewTarget.setVideoId(videoId);
            Picasso.get()
                    // must use an Uri here, does not work with path only
                    .load(posterUri)
                    .resize(getWidth(mContext), getHeight(mContext))
                    .centerCrop()
                    .error(R.drawable.filetype_new_video)
                    .into(mImageViewTarget);
        }

        protected void updateImageViewThumbnail(long videoId) {
            mImageViewTarget.setVideoId(-1);
            Picasso.get()
                    // must use an Uri here, does not work with path only
                    .load(ThumbnailRequestHandler.buildUri(videoId))
                    .resize(getWidth(mContext), getHeight(mContext))
                    .centerCrop()
                    .error(R.drawable.filetype_new_video)
                    .into(mImageViewTarget);
        }
        /**
         * empty is 0, full is 100, one third is 33.333333f
         * @param resumePointInPercent
         */
        public void setResumeInPercent(float resumePointInPercent) {
            if (resumePointInPercent==0f) {
                mProgressBar.setVisibility(View.GONE);
            } else {
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressBar.getLayoutParams().width = (int)(getWidth(mContext) * resumePointInPercent / 100f);
                mProgressBar.requestLayout();
            }
        }
        public void setWatched(boolean value) {
            mWatchedIcon.setVisibility( value ? View.VISIBLE : View.GONE);
        }
    }

    public ListPresenter() {
        super();
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mContext = parent.getContext();
        ListViewHolder vh = new ListViewHolder(parent.getContext());
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        ListViewHolder vh = (ListViewHolder)viewHolder;

        onBindListViewHolder(vh, item); // Call the version that must be implemented by child classes
    }

    abstract public void onBindListViewHolder(ListViewHolder viewHolder, Object item);

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        // Rest visibilities and image to default that each subclasses do not have to care about it
        ((ListViewHolder)viewHolder).getImageView().setImageDrawable(null);
        ((ListViewHolder)viewHolder).setContentTextVisibility(View.VISIBLE);
    }

    public class PicassoImageViewTarget implements Target {
        private ImageView mImageView;
        private long mVideoId;

        // Picasso documentation: Objects implementing this class must have a working implementation of Object.equals(Object) and Object.hashCode() for proper storage internally.
        @Override
        public boolean equals(Object other) {
            if (other==null || !(other instanceof PicassoImageViewTarget)) {
                return false;
            }
            // mImageView must never be null, no need to check it!
            return mImageView.equals( ((PicassoImageViewTarget)other).mImageView );
        }

        public PicassoImageViewTarget(ImageView imageView) {
            mImageView = imageView;
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
            mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mImageView.setImageBitmap(bitmap);
        }

        @Override
        public void onBitmapFailed(Exception e,Drawable drawable){
            if( mVideoId!=-1){
                UnavailablePosterBroadcastReceiver.sendBroadcast(mContext, mVideoId);
                mVideoId = -1;
            }else {
                mImageView.setScaleType(ImageView.ScaleType.CENTER);
                mImageView.setImageDrawable(drawable);
            }
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            // Do nothing
        }


        public void setVideoId(long videoId) {
            mVideoId = videoId;
        }
    }
}
