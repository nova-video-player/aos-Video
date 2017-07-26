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

package com.archos.mediacenter.video.browser.filebrowsing.network;

import android.content.Context;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.video.browser.filebrowsing.ListingAdapter;

import java.util.List;

/**
 * Created by alexandre on 29/10/15.
 */
public class AdapterByNetwork extends ListingAdapter {
    public AdapterByNetwork(Context context, List<Object> itemList, List<MetaFile2> fullList) {
        super(context, itemList, fullList);
    }

    public void setCurrentItemList(List<Object> mFilteredItemList) {
        mItemList = mFilteredItemList;
        notifyDataSetChanged();
    }
}
