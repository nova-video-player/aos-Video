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
import android.net.Uri;
import android.util.Log;

import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Load the 100 latest played videos
 * Created by vapillon on 10/04/15.
 */
public class LastPlayedLoader extends VideoLoader {

    private static final String TAG = "LastPlayedLoader";
    private static final boolean DBG = false;

    public LastPlayedLoader(Context context) {
        super(context);
        init();
        // cf. https://github.com/nova-video-player/aos-AVP/issues/134 reduce strain
        // only updates the CursorLoader on data change every 10s since used only in MainFragment as nonScraped box presence
        if (VideoLoader.ALLVIDEO_THROTTLE) setUpdateThrottle(VideoLoader.ALLVIDEO_THROTTLE_DELAY);
        // When smart mode is enabled, add GROUP BY and HAVING via URI query parameters
        if (LoaderUtils.isSmartRecentlyRows()) {
            Uri baseUri = getUri();
            Uri.Builder builder = baseUri.buildUpon();
            builder.appendQueryParameter("group", "COALESCE(" + VideoStore.Video.VideoColumns.SCRAPER_M_IMDB_ID + ", " + VideoStore.Video.VideoColumns.SCRAPER_S_IMDB_ID + ")");
            builder.appendQueryParameter("having", VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " = MAX(" + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + ")");
            setUri(builder.build());
            if (DBG) Log.d(TAG, "Modified URI: " + builder.build());
        }
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection());

        if (sb.length() > 0) sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + "!=0");

        String selection = sb.toString();
        if (DBG) Log.d(TAG, "getSelection() returned: " + selection);
        return selection;
    }

    @Override
    public String getSortOrder() {
        String sortOrder;
        if (LoaderUtils.isSmartRecentlyRows()) {
            sortOrder = "MAX(" + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + ") DESC, "
                    + "COALESCE(" + VideoStore.Video.VideoColumns.SCRAPER_M_RELEASE_DATE + ", "
                    + "date(" + VideoStore.Video.VideoColumns.SCRAPER_E_AIRED + "/1000, 'unixepoch')) DESC "
                    + "LIMIT 50";
        } else {
            sortOrder = VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " DESC LIMIT 50";
        }
        if (DBG) Log.d(TAG, "getSortOrder() returned: " + sortOrder);
        return sortOrder;
    }
}
