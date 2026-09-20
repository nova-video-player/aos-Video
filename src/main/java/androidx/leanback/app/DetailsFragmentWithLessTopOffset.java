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

package androidx.leanback.app;

import androidx.leanback.R;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.ItemAlignmentFacet;
import androidx.leanback.widget.VerticalGridView;

/**
 * This class exists only to reduce the vertical offset above the DetailsRow in the DetailsFragment
 */
public class DetailsFragmentWithLessTopOffset extends DetailsSupportFragment {

    private float mRatio = 1;

    /**
     * Set the ratio to be applied to the vertical top offset, the default value being defined by leanback
     * @param ratio if set to 1.0 it does not change anything, set lower than 1 it reduce the space.
     */
    public void setTopOffsetRatio(float ratio) {
        mRatio = ratio;
    }

    @Override
    void setVerticalGridViewLayout(VerticalGridView listview) {
        super.setVerticalGridViewLayout(listview);

        // This works for leanback 22.1.0

        //listview.setWindowAlignmentOffset( (int)(listview.getWindowAlignmentOffset() * mRatio) );

        // This works for leanback 22.2.0
         listview.setItemAlignmentOffset( (int)(listview.getItemAlignmentOffset() * mRatio) );
    }

    @Override
    protected void setupDetailsOverviewRowPresenter(FullWidthDetailsOverviewRowPresenter presenter) {
        ItemAlignmentFacet facet = new ItemAlignmentFacet();
        ItemAlignmentFacet.ItemAlignmentDef alignDef1 = new ItemAlignmentFacet.ItemAlignmentDef();
        alignDef1.setItemAlignmentViewId(R.id.details_frame);
        alignDef1.setItemAlignmentOffset(- getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_actions));
        alignDef1.setItemAlignmentOffsetPercent(0);
        ItemAlignmentFacet.ItemAlignmentDef alignDef2 = new ItemAlignmentFacet.ItemAlignmentDef();
        alignDef2.setItemAlignmentViewId(R.id.details_frame);
        alignDef2.setItemAlignmentFocusViewId(R.id.details_overview_description);
        alignDef2.setItemAlignmentOffset(- getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_actions));
        alignDef2.setItemAlignmentOffsetPercent(0);
        ItemAlignmentFacet.ItemAlignmentDef[] defs =
                new ItemAlignmentFacet.ItemAlignmentDef[] {alignDef1, alignDef2};
        facet.setAlignmentDefs(defs);
        presenter.setFacet(ItemAlignmentFacet.class, facet);
    }

    @Override
    protected void onSetDetailsOverviewRowStatus(FullWidthDetailsOverviewRowPresenter presenter,
            FullWidthDetailsOverviewRowPresenter.ViewHolder viewHolder, int adapterPosition,
            int selectedPosition, int selectedSubPosition) {
        if (selectedPosition >= adapterPosition) {
            presenter.setState(viewHolder, FullWidthDetailsOverviewRowPresenter.STATE_HALF);
        } else {
            presenter.setState(viewHolder, FullWidthDetailsOverviewRowPresenter.STATE_SMALL);
        }
    }
}