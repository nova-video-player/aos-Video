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

package com.archos.mediacenter.video.browser.adapters;

import android.content.Context;
import android.database.Cursor;

import com.archos.mediacenter.utils.MediaUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alexandre on 27/10/15.
 */
public class CursorAdapterByVideo extends PresenterAdapterByCursor implements AdapterByVideoObjectsInterface, PresenterAdapterInterface{
    private final VideoCursorMapper mVideoCursorMapper;
    List<String> mAvailableSubtitlesFiles = new ArrayList<>();
    public CursorAdapterByVideo(Context context, Cursor c) {
        super(context, c);
        mVideoCursorMapper = new VideoCursorMapper();
        mVideoCursorMapper.publicBindColumns(c);
        buildAvailableSubtitlesFileList();
    }

    // check for local stored subtitles
    private void buildAvailableSubtitlesFileList() {
        mAvailableSubtitlesFiles.clear();
        // Get first all the files/folders located in the current folder
        File[] files = null;
        try {
            files = MediaUtils.getSubsDir(mContext).listFiles();
        }
        catch (SecurityException e) {
        }

        // Add to the list the files whose extension corresponds to a subtitle file
        if (files != null && files.length > 0) {
            for (File f : files) {
                if (f.isFile()) {
                    if (!f.getName().startsWith(".")) {
                        // Check the file extension
                        String extension = getExtension(f.getPath());
                        if (extension != null && VideoUtils.getSubtitleExtensions().contains(extension)) {
                            // This is a subtitles file => add it to the list
                            // (we only care about the filename so we can drop the extension)
                            String nameWithoutExtension = f.getName().substring(0, f.getName().length() - extension.length() - 1);
                            mAvailableSubtitlesFiles.add(nameWithoutExtension);
                        }
                    }
                }
            }
        }
    }


    public boolean hasLocalSubtitles(String fileNameWithoutExtension) {
        if (mAvailableSubtitlesFiles.size() > 0) {

            int i;
            for (i = 0; i < mAvailableSubtitlesFiles.size(); i++) {
                if (mAvailableSubtitlesFiles.get(i).startsWith(fileNameWithoutExtension)) {
                    return true;
                }
            }
        }
        return false;
    }
    @Override
    public Video getVideoItem(int position) {
        getCursor().moveToPosition(position);
        Video video = (Video) mVideoCursorMapper.publicBind(getCursor());
        video.setHasSubs(video.hasSubs()||hasLocalSubtitles(FileUtils.getFileNameWithoutExtension(video.getFileUri())));
        return video;
    }

    public Object getItem(int position){
        return getVideoItem(position);
    }
}
