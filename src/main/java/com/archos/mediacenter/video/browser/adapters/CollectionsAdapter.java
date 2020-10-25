// Copyright 2020 Courville Software
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


package com.archos.mediacenter.video.browser.adapters;

import android.content.Context;
import android.database.Cursor;

import com.archos.mediacenter.video.browser.adapters.mappers.CollectionCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediaprovider.video.VideoStore;

public class CollectionsAdapter extends PresenterAdapterByCursor  implements AdapterByVideoObjectsInterface{
    private final CollectionCursorMapper mCollectionsCursorMapper;
    public CollectionsAdapter(Context context, Cursor c) {
        super(context, c);
        mCollectionsCursorMapper = new CollectionCursorMapper();
        mCollectionsCursorMapper.bindColumns(c);
    }

    @Override
    public Video getVideoItem(int position) {
        return null;
    }

    public String getCover() {
        return getCursor().getString(getCursor().getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_C_POSTER_LARGE_FILE));
    }

    public Object getItem(int position){
        getCursor().moveToPosition(position);
        return mCollectionsCursorMapper.bind(getCursor());
    }

}
