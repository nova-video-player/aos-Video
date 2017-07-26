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

package com.archos.mediacenter.video.browser.loader;

import android.content.Context;

import com.archos.mediaprovider.video.VideoStore;

/**
 * Load the 100 latest videos
 * Created by vapillon on 10/04/15.
 */
public class LastAddedLoader extends VideoLoader {

    public LastAddedLoader(Context context) {
        super(context);
        init();
    }

    @Override
    public String getSortOrder() {
        return VideoStore.MediaColumns.DATE_ADDED + " DESC LIMIT 100";
    }
}