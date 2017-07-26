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

package android.support.v17.leanback.widget;

import android.view.View;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.presenter.IconItemPresenter;

/**
 * Dedicated row presenter for a list row containing Icon/IconItemPresenter
 * It has no shadow and handle the selection dim effect (with a huge ugly hack!)
 * Created by vapillon on 12/06/15.
 */
public class IconItemRowPresenter extends ListRowPresenter {

    private static final String TAG = "IconItemRowPresenter";

    public IconItemRowPresenter() {
        super();
        setShadowEnabled(false);
        setSelectEffectEnabled(false);
    }

    /**
     * Applies select level to header and draw a default color dim over each child
     * of {@link HorizontalGridView}.
     */
    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {

        // forward the dim effect to the header presenter
        if (holder.mHeaderViewHolder!=null && getHeaderPresenter()!=null) {
            getHeaderPresenter().setSelectLevel(holder.mHeaderViewHolder, holder.mSelectLevel);
        }

        final ViewHolder listRowViewHolder = (ViewHolder) holder;

        // Apply dim to all the Icon items in the list
        for (int i = 0, count = listRowViewHolder.mGridView.getChildCount(); i < count; i++) {
            View gridItem = listRowViewHolder.mGridView.getChildAt(i);

            // HACK: When creating the ViewHolder we did put a reference to the ViewHolder in the TAG of the root RelativeLayout
            // (I did not manage to do it with regular use of the ListRowPresenter...)
            View iconItemShell = gridItem.findViewById(R.id.icon_item_shell);
            if (iconItemShell!=null && (iconItemShell.getTag() instanceof IconItemPresenter.ViewHolder)) {
                ((IconItemPresenter.ViewHolder) iconItemShell.getTag()).setDimmed(holder.mSelectLevel < 0.5f);
            }
        }
    }
}
