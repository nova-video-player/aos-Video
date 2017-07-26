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

package com.archos.mediacenter.video.browser.adapters.object;

import android.content.Context;
import android.net.Uri;

import com.archos.mediascraper.BaseTags;

import java.io.Serializable;

/**
 * Created by vapillon on 10/04/15.
 */
public class Base  implements Serializable {

    final private String mName;

    // Using the string version of the Uri in order to be Serializable (Uri is not)
    final private String mPosterUriString;

    public Base(String name, Uri posterUri) {
        mName = name;
        mPosterUriString = (posterUri!=null) ? posterUri.toString() : null;
    }

    public String getName() {
        return mName;
    }

    public Uri getPosterUri() {
        return (mPosterUriString!=null) ? Uri.parse(mPosterUriString) : null;
    }

    /**
     * CAUTION this method performs full new DB query!
     * Default implementation returns null
     * @return
     */
    public BaseTags getFullScraperTags(Context context) {
        return null;
    }
}
