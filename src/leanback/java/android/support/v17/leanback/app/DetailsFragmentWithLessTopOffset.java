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

package android.support.v17.leanback.app;

import android.support.v17.leanback.widget.VerticalGridView;

/**
 * This class exists only to reduce the vertical offset above the DetailsRow in the DetailsFragment
 */
public class DetailsFragmentWithLessTopOffset extends DetailsFragment {

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
}