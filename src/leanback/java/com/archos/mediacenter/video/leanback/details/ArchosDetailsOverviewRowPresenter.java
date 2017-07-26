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

import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.archos.mediacenter.video.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * Created by alexandre on 20/02/17.
 */

public class ArchosDetailsOverviewRowPresenter extends DetailsOverviewRowPresenter {


    private final Presenter mDetailsPresenter;

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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.lb_details_overview, parent, false);
        ArchosHorizontalGridView gridView = (ArchosHorizontalGridView) v.findViewById(R.id.details_overview_actions);
        gridView.setViewHolderCreator(this);
        ViewHolder vh = new ViewHolder(v, mDetailsPresenter);

        Class[]classes = new Class[1];
        classes[0] = DetailsOverviewRowPresenter.ViewHolder.class;

        try {
            Method method = DetailsOverviewRowPresenter.class.getDeclaredMethod("initDetailsOverview",   classes);
            method.setAccessible(true);
            method.invoke(this, vh);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return super.createRowViewHolder(parent);
    }
}
