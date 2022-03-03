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

package com.archos.mediacenter.video.widget;

import com.archos.mediaprovider.video.VideoStore;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RemoteViewsFactoryAllVideos extends RemoteViewsFactoryBase {
    private static final Logger log = LoggerFactory.getLogger(RemoteViewsFactoryAllVideos.class);

    public RemoteViewsFactoryAllVideos(Context context, Intent intent) {
        super(context, intent);
        log.debug("Create AllVideos service for the video widget");
    }

    protected boolean loadData(Context context, int maxItemCount) {
        log.debug("loadData()");
    	String sortOrder = VideoStore.Video.Media.DEFAULT_SORT_ORDER + " LIMIT " + maxItemCount;
        ContentResolver resolver = context.getContentResolver();
        mCursor = resolver.query(MEDIA_DB_CONTENT_URI, VIDEO_FILES_COLUMNS, WHERE_NOT_HIDDEN, null, sortOrder);
		return (mCursor !=null);
    }

}