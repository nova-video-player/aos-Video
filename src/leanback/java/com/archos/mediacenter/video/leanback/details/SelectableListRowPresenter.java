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

import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;
import android.view.ViewGroup;

/**
 * Created by alexandre on 10/12/15.
 */
public class SelectableListRowPresenter extends ListRowPresenter {

    private ListRowPresenter.ViewHolder mViewHolder;

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        mViewHolder = (ViewHolder) super.createRowViewHolder(parent);

        return mViewHolder;
    }

    public ListRowPresenter.ViewHolder getViewHolder() {
        return mViewHolder;
    }
    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        setSelectedPosition((ListRowPresenter.ViewHolder)holder,((SelectableListRow)item).getStartingSelectedPosition());
        Log.d("changedebug", "onBindRowViewHolder " + ((ListRowPresenter.ViewHolder) holder).getGridView().getAdapter().getItemCount());

    }


    public void setSelectedPosition(ViewHolder item, int i) {
        Log.d("changedebug", "setSelectedPosition "+i);

        if(item!=null&&item.getGridView()!=null&&item.getGridView().getAdapter()!=null&&item.getGridView().getAdapter().getItemCount()>i) {
            item.getGridView().setSelectedPosition(i);
            Log.d("changedebug", "selecting "+i);

        }
    }
}
