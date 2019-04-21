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
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.archos.mediacenter.video.R;
import com.archos.mediascraper.ScraperImage;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.ArrayList;

/**
 * Poster, just poster (no name, no details, just the image)
 * Created by vapillon on 10/04/15.
 */
public abstract class ScraperImagePresenter extends Presenter {

    private static final String TAG = "ScraperImagePresenter";
    
    private ArrayList<ViewHolder> mViewHolders = new ArrayList<>();
    private boolean mBigMode = false;

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

        private ScraperImage mImage;

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
            mImageViewTarget = new PicassoImageViewTarget(mImageView);
            
            updateSize();
        }
        public void setDrawable(Drawable d) {
            mImageView.setImageDrawable(d);
        }

        public void updateSize() {
            Context c = view.getContext();

            ViewGroup.LayoutParams cardLayoutParams = mBaseCardView.getLayoutParams();
            cardLayoutParams.width = getAdjustedWidth(c);
            cardLayoutParams.height = getAdjustedHeight(c);
            mBaseCardView.setLayoutParams(cardLayoutParams);

            ViewGroup.LayoutParams imageLayoutParams = mImageView.getLayoutParams();
            imageLayoutParams.width = getAdjustedWidth(c);
            imageLayoutParams.height = getAdjustedHeight(c);
            mImageView.setLayoutParams(imageLayoutParams);
        }

        public void setImage(ScraperImage image) {
            mImage = image;
        }

        public void loadImage() {
            Context c = view.getContext();

            Picasso.get()
                    .load(getAdjustedImageUrl(mImage))
                    .resize(getAdjustedWidth(c), getAdjustedHeight(c)) // better resize to card size, since backdrop files are pretty large
                    .centerCrop()
                    .error(R.drawable.filetype_new_image)
                    .into(mImageViewTarget);
        }
    }

    private int getAdjustedWidth(Context context) {
        int width = getWidth(context);

        if (mBigMode)
            width *= 2;

        return width;
    }

    private int getAdjustedHeight(Context context) {
        int height = getHeight(context);

        if (mBigMode)
            height *= 2;

        return height;
    }

    private String getAdjustedImageUrl(ScraperImage image) {
        String imageUrl = getImageUrl(image);

        if (mBigMode)
            imageUrl = image.getLargeUrl().replace("/w342/", "/w780/");

        return imageUrl;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ViewHolder viewHolder = new ViewHolder(parent.getContext());
        mViewHolders.add(viewHolder);
        return viewHolder;
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ((ViewHolder)viewHolder).setDrawable(null);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder vh = (ViewHolder)viewHolder;
        ScraperImage image = (ScraperImage)item;
        
        vh.setImage(image);
        vh.loadImage();
        vh.view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mBigMode = !mBigMode;

                for(ViewHolder holder : mViewHolders) {
                    holder.updateSize();
                    holder.loadImage();
                }

                return true;
            }
        });
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
