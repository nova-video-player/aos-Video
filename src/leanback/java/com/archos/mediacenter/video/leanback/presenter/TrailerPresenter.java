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
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.Box;
import com.archos.mediacenter.video.utils.TrailerServiceIconFactory;
import com.archos.mediascraper.ScraperTrailer;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * Created by vapillon on 10/04/15.
 */
public class TrailerPresenter extends PosterImageCardPresenter {

    public TrailerPresenter(Context context) {
        super(context);
    }



    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {

        ScraperTrailer box = (ScraperTrailer)item;
        final VideoViewHolder vh = (VideoViewHolder)viewHolder;

        final ImageCardView card = vh.getImageCardView();
        card.setMainImage(mContext.getResources().getDrawable(TrailerServiceIconFactory.getIconForService(box.mSite)), false);
        card.setMainImageScaleType(ImageView.ScaleType.CENTER);
        card.setTitleText(box.mName);

        Picasso.get()
                .load(getImageUrl(box))
                .resize(getWidth(mContext), getHeight(mContext)) // better resize to card size, since backdrop files are pretty large
                .centerCrop()
                .error(R.drawable.filetype_new_image)
                .into(vh.getImageCardView().getMainImageView());

    }

    public int getWidth(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.trailer_width);
    }

    public int getHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.trailer_height);
    }

    private Uri getImageUrl(ScraperTrailer box) {
        String base = "https://img.youtube.com/vi/%s/0.jpg";
        return Uri.parse(String.format(base, box.mVideoKey));
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }


    public class PicassoImageViewTarget implements Target {
        private ImageView mImageView;

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
        public void onBitmapFailed(Exception e, Drawable drawable){
            mImageView.setScaleType(ImageView.ScaleType.CENTER);
            mImageView.setImageDrawable(drawable);
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            // Do nothing
        }
    }
}
