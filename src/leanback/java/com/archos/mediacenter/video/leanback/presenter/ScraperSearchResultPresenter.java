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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.archos.mediacenter.video.R;
import com.archos.mediascraper.SearchResult;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * Presenter for scraper search result objects (SearchResult)
 * Created by vapillon on 04/05/15.
 */
public class ScraperSearchResultPresenter extends Presenter {

    private Context mContext;

    public class ViewHolder extends Presenter.ViewHolder {
        private ImageCardView mCardView;
        private PicassoImageCardViewTarget mImageCardViewTarget;

        public ViewHolder(Context context) {
            super(new ImageCardView(context));
            mCardView = (ImageCardView)view;
            mCardView.setMainImageDimensions(getWidth(context), getHeight(context));
            mCardView.setMainImage(new ColorDrawable(context.getResources().getColor(R.color.lb_basic_card_bg_color)));
            mCardView.setFocusable(true);
            mCardView.setFocusableInTouchMode(true);

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

        /**
         * non blocking (using Picasso to load the Uri)
         * @param posterUri
         */
        protected void updateCardViewPoster(Uri posterUri) {
            Picasso.get()
                    // must use an Uri here, does not work with path only
                    .load(posterUri)
                    .resize(getWidth(mContext), getHeight(mContext))
                    .error(R.drawable.filetype_new_video)
                    .into(mImageCardViewTarget);
        }
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        mContext = parent.getContext();
        ViewHolder vh = new ViewHolder(parent.getContext());
        return vh;
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder vh = (ViewHolder)viewHolder;
        SearchResult searchResult = (SearchResult)item;

        vh.getImageCardView().setTitleText(searchResult.getTitle());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ((ViewHolder)viewHolder).getImageCardView().setMainImage(null);
    }

    public class PicassoImageCardViewTarget implements Target {
        private ImageCardView mImageCardView;

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

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
            Drawable bitmapDrawable = new BitmapDrawable(mContext.getResources(), bitmap);
            mImageCardView.setMainImageScaleType(ImageView.ScaleType.CENTER_CROP);

            // Do not fade-in when loading from cache memory (because it is most likely instantaneous in that case)
            boolean fade = (loadedFrom!= Picasso.LoadedFrom.MEMORY);
            mImageCardView.setMainImage(bitmapDrawable, fade);
        }

        @Override
        public void onBitmapFailed(Exception e,Drawable drawable){
            mImageCardView.setMainImageScaleType(ImageView.ScaleType.CENTER);
            mImageCardView.setMainImage(drawable, true);
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            // Do nothing
        }
    }
}
