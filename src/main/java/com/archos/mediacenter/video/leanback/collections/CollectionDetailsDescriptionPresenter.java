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

package com.archos.mediacenter.video.leanback.collections;

import android.graphics.Paint;
import androidx.leanback.widget.Presenter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediascraper.ShowTags;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CollectionDetailsDescriptionPresenter extends Presenter {

    private static final String TAG = "CollectionDetailsDescriptionPresenter";
    private static final boolean DBG = false;

    /**
     * The ViewHolder for the {@link CollectionDetailsDescriptionPresenter}.
     */
    public static class ViewHolder extends Presenter.ViewHolder {
        final TextView mTitle;
        final TextView mBody;
        final ImageView mTraktWatched;
        private ViewTreeObserver.OnPreDrawListener mPreDrawListener;

        public ViewHolder(final View view) {
            super(view);
            if (DBG) Log.d(TAG, "ViewHolder");
            mTitle = (TextView) view.findViewById(androidx.leanback.R.id.lb_details_description_title);
            mBody = (TextView) view.findViewById(androidx.leanback.R.id.lb_details_description_body);
            mTraktWatched = (ImageView) view.findViewById(R.id.trakt_watched);

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
                    int bodymaxLines = titleOnTwoLines ? 3 : 5; // MAGICAL
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
        if (DBG) Log.d(TAG, "onCreateViewHolder");
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.leanback_collection_details_description, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public final void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        if (DBG) Log.d(TAG, "onBindViewHolder");
        ViewHolder vh = (ViewHolder) viewHolder;
        Collection collection = (Collection) item;

        vh.mTitle.setText(collection.getName());
        vh.mBody.setText(collection.getPlot());

        vh.mTraktWatched.setVisibility(collection.isWatched() ? View.VISIBLE : View.GONE);
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

    private String getYearFormatted(Date date) {
        if (date != null && date.getTime() > 0) {
            return new SimpleDateFormat("yyyy").format(date);
        }
        return null;
    }
}
