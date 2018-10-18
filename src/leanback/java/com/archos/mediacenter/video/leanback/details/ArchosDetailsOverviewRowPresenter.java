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

import android.support.v17.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.ViewGroup;



/**
 * Created by alexandre on 20/02/17.
 */

public class ArchosDetailsOverviewRowPresenter extends FullWidthDetailsOverviewRowPresenter {


    private final Presenter mDetailsPresenter;
    private ViewHolder mViewHolder;

    /**
     * Constructor for a DetailsOverviewRowPresenter.
     *
     * @param detailsPresenter The {@link Presenter} used to render the detailed
     *                         description of the row.
     */
    public ArchosDetailsOverviewRowPresenter(Presenter detailsPresenter) {
        super(detailsPresenter);
        mDetailsPresenter = detailsPresenter;
    }
    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        mViewHolder = (ViewHolder)super.createRowViewHolder(parent);
        return mViewHolder;
    }

    public int getState() {
        return mViewHolder.getState();
    }
}
