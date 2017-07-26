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

import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.util.Log;

/**
 * Created by alexandre on 10/12/15.
 */
public class SelectableListRow extends ListRow {
    private int mSelectedPosition;

    public SelectableListRow(HeaderItem header, ObjectAdapter adapter) {
        super(header, adapter);
    }

    public int getStartingSelectedPosition() {
        return mSelectedPosition;
    }

    public void setStartingSelectedPosition(int i) {
        mSelectedPosition = i;
        Log.d("changedebug", "setSelectedPosition " + i);

    }
}
