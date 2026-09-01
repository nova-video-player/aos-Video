// Copyright 2026 Courville Software
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
import android.util.Log;

import com.archos.mediaprovider.video.VideoStore;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by vapillon on 10/04/15.
 */
public class VideosSelectionInPlaylistLoader extends VideosSelectionLoader {


    public VideosSelectionInPlaylistLoader(Context context, String listOfMoviesIds) {
        super(context, listOfMoviesIds, DEFAULT_SORT);
    }

    //disable sort order for now
    @Override
    public String getSortOrder() {
        if(mListOfIds==null) //called too early by init()
            return "";
        String[] pairsOrderId = mListOfIds.split(",");
        if(pairsOrderId.length>1) {
            String finalOrder = "CASE " + VideoStore.Video.VideoColumns._ID + " \n";
            int i = 0;
            for (String pair : pairsOrderId) {
                finalOrder += "WHEN " + pair + " THEN " + i + "\n";
                i++;
            }
            finalOrder += "END \n";
            return finalOrder;

        }
        return null;
    }
}
