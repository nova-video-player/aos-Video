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

package com.archos.mediacenter.video.leanback.filebrowsing;

import android.net.Uri;
import android.os.Environment;

import com.archos.mediacenter.video.R;

public class LocalListingActivity extends ListingActivity {

    static private Uri sRootUri = Uri.parse(
            "file://" + Environment.getExternalStorageDirectory().getPath()
                    + "/"); // important to end with "/"

    @Override
    protected ListingFragment getStartingFragment() {
        return new LocalListingFragment();
    }

    /**
     * We always start from root for local browsing
     */
    @Override
    protected Uri getStartingUri() {
        return getRootUri();
    }

    @Override
    protected String getStartingName() {
        return getRootName();
    }

    /**
     * For now we have a fixed root Uri for local browsing
     */
    @Override
    protected Uri getRootUri() {
        return sRootUri;
    }

    @Override
    protected String getRootName() {
        return getString(R.string.internal_storage);
    }
}
