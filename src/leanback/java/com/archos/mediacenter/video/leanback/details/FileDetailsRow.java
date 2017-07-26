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

package com.archos.mediacenter.video.leanback.details;

import android.content.Context;
import android.support.v17.leanback.widget.HeaderItem;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;

/**
 * Created by vapillon on 16/04/15.
 */
public class FileDetailsRow extends FullWidthRow {

    final private Video mVideo;
    final int mPlayerType;

    public FileDetailsRow(Context context, Video video, int playerType) {
        super(new HeaderItem(context.getString(R.string.info_tab_file)));
        mVideo = video;
        mPlayerType = playerType;
    }

    public boolean shouldHideLoadingAndMetadata(){//hide when torrent and no metadata
        return mVideo!=null&&mVideo.getMetadata()==null&&mVideo.getFileUri().getLastPathSegment().endsWith("torrent");
    }

    public Video getVideo() {
        return mVideo;
    }

    public int getPlayerType() { return mPlayerType; }
}
