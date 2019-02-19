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

import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.support.v17.leanback.graphics.ColorOverlayDimmer;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.archos.mediacenter.video.R;

/**
 * Created by vapillon on 15/04/15.
 */
public class FullWidthRowPresenter extends RowPresenter {

    public class ViewHolder extends RowPresenter.ViewHolder {

        private ColorOverlayDimmer mColorDimmer;
        private FrameLayout mMainContainer;

        public ViewHolder(View view) {
            super(view);
            mColorDimmer = ColorOverlayDimmer.createDefault(view.getContext());
            mMainContainer = (FrameLayout)view.findViewById(R.id.main_container);
        }
        public FrameLayout getMainContainer() {
            return mMainContainer;
        }
    }

    public FullWidthRowPresenter() {
        super();
        setSelectEffectEnabled(true);
    }

    @Override
    public boolean isUsingDefaultSelectEffect() {
        return false;
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.androidtv_fullwidth_row, parent, false);
        return new ViewHolder(v);
    }

    /**
     * This overriden method is required to have the Header displayed (too a long time to find out...)
     */
    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);
        
        // hacky
        View headerView = vh.getHeaderViewHolder().view;
        headerView.setPadding((int)dipToPixels(75), headerView.getPaddingTop(), headerView.getPaddingRight(), headerView.getPaddingBottom());
    }
    
    private static float dipToPixels(float dipValue) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, Resources.getSystem().getDisplayMetrics());
    }

    public void changeSelectLevel(RowPresenter.ViewHolder vh, ViewHolder holder) {
        super.onSelectLevelChanged(vh);
        holder.mColorDimmer.setActiveLevel(vh.getSelectLevel());
        int dimmedColor = holder.mColorDimmer.getPaint().getColor();
        ((ColorDrawable) holder.getMainContainer().getForeground().mutate()).setColor(dimmedColor);
    }
}
