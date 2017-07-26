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

package com.archos.mediacenter.video.leanback;

import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ObjectAdapter;

/**
 * This class is only to be able to make the difference between regular ListRow for which the
 * presenter must display shadow and this special ListRow for which the presenter must not
 * display shadows.
 * Need a different class because the difference is done using a ClassPresenterSelector
 * Created by vapillon on 29/04/15.
 */
public class ShadowLessListRow extends ListRow {

    public ShadowLessListRow(HeaderItem headerItem, ObjectAdapter adapter) {
        super(headerItem, adapter);
    }
}
