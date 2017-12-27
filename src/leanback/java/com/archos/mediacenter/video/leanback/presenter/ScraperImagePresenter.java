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
import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.archos.mediacenter.video.R;
import com.archos.mediascraper.ScraperImage;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * Poster, just poster (no name, no details, just the image)
 * Created by vapillon on 10/04/15.
 */
public abstract class ScraperImagePresenter extends Presenter {

    private static final String TAG = "ScraperImagePresenter";

    public ScraperImagePresenter() {
        super();
    }

    abstract public int getWidth(Context context);

    abstract public int getHeight(Context context);

    /** child classes can choose to return the thumb URL or the fullsize URL */
    abstract public String getImageUrl(ScraperImage image);

    public class ViewHolder extends Presenter.ViewHolder {

        final private BaseCardView mBaseCardView;
        final private ImageView mImageView;
        final private PicassoImageViewTarget mImageViewTarget;

        public ViewHolder(Context context) {
            super(new CustomBaseCardview(context));
            mBaseCardView = (BaseCardView)view;
            mBaseCardView.setBackgroundColor(context.getResources().getColor(R.color.lb_basic_card_bg_color));
            mBaseCardView.setFocusable(true);
            mBaseCardView.setFocusableInTouchMode(true);
            mBaseCardView.setCardType(BaseCardView.CARD_TYPE_MAIN_ONLY);
            mBaseCardView.setLayoutParams(new ViewGroup.LayoutParams(getWidth(context), getHeight(context)));
            mImageView = (ImageView)LayoutInflater.from(context).inflate(R.layout.leanback_imageonly_cardview_content, mBaseCardView, false);
            mBaseCardView.addView(mImageView);
            ViewGroup.LayoutParams lp = mImageView.getLayoutParams();
            lp.width = getWidth(context);
            lp.height = getHeight(context);
            mImageView.setLayoutParams(lp);
            mImageViewTarget = new PicassoImageViewTarget(mImageView);
        }
        public void setImage(Drawable d) {
            mImageView.setImageDrawable(d);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(parent.getContext());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ((ViewHolder)viewHolder).setImage(null);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder vh = (ViewHolder)viewHolder;
        Context c = vh.view.getContext();

        ScraperImage image = (ScraperImage)item;
        Picasso.get()
                .load(getImageUrl(image))
                .resize(getWidth(c), getHeight(c)) // better resize to card size, since backdrop files are pretty large
                .centerCrop()
                .error(R.drawable.filetype_new_image)
                .into(vh.mImageViewTarget);
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
