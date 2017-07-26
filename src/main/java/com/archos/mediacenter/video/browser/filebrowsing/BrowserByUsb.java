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

package com.archos.mediacenter.video.browser.filebrowsing;

import android.net.Uri;

import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.video.browser.MetaFile2ItemData;


public class BrowserByUsb extends BrowserByFolder {

    @Override
    protected Uri getDefaultDirectory() {
        return Uri.parse(getArguments().getString(BrowserCategory.MOUNT_POINT));
    }

    @Override
    public Uri getUriFromPosition(int position) {
        MetaFile2ItemData itemData = (MetaFile2ItemData)mBrowserAdapter.getItem(position);
        return itemData != null ? itemData.getMetaFile().getUri() : null;
    }
/*
    @Override
    protected void setupAdapter(boolean createNewAdapter) {

        mBrowserAdapter = new AdapterByExtStorage(getActivity().getApplicationContext(),
                mCommonDefaultView, mItemList,mCurrentDirectory, mFullFileList);
    }*/
}
