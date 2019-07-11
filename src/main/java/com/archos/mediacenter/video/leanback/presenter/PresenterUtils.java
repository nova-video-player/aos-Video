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

package com.archos.mediacenter.video.leanback.presenter;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.VideoUtils;

/**
 * Created by vapillon on 11/05/15.
 */
public class PresenterUtils {

    static public int getIconResIdFor(MetaFile2 file) {
        if (file.isDirectory()) {
            return R.drawable.filetype_new_folder;
        }

        final String extension = file.getExtension();
        final String mimeType = file.getMimeType();
        if (extension==null || mimeType==null) {
            return R.drawable.filetype_new_generic;
        }
        else if (mimeType.startsWith("video/")) {
            return R.drawable.filetype_new_video;
        }
        // Special case for subtitles (not based on MimeType because these are text files)
        else if (VideoUtils.getSubtitleExtensions().contains(extension)) {
            return R.drawable.filetype_new_subtitles;
        }
        else if (extension.equals("torrent")) {
            return R.drawable.filetype_new_torrent;
        }
        else if (mimeType.startsWith("image/")) {
            return R.drawable.filetype_new_image;
        }
        else if (mimeType.startsWith("audio/")) {
            return R.drawable.filetype_new_audio;
        }
        else if (extension.equals("pdf")) {
            return R.drawable.filetype_new_pdf;
        }
        else if (mimeType.equals("text/html")) {
            return R.drawable.filetype_new_html;
        }
        // TODO special icon for .nfo?
        // TODO special icon for readme?
        else {
            return R.drawable.filetype_new_generic;
        }
    }
}
