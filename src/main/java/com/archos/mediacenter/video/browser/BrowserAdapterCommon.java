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


package com.archos.mediacenter.video.browser;

import android.widget.ImageView;

public interface BrowserAdapterCommon {

    boolean hasBookmark();

    boolean hasBookmark(int position);

    int getBookmarkPosition(int position);

    String getInfo();

    int getDuration();

    ItemData getItemData(int position);

    String getName();

    String getName(int position);

    String getPath(int position);

    boolean hasResume();

    boolean hasResume(int position);

    int getResumePosition(int position);

    boolean hasSubtitles();

    boolean hasSubtitles(int position);

    String getNameList();

    String getNameGrid();

    String getDetailLineOne();

    String getDetailLineTwo();

    String getDetailLineThree();

    long getDate();

    String getCover();

    float getRating();

    int getScraperType();
    int getScraperType(int position);

    String getShowId();
    String getShowId(int position);

    String getSeasonNumber();
    String getSeasonNumber(int position);

    String getEpisodeNumber();
    String getEpisodeNumber(int position);
    
    void setUpnpImage(ImageView imageView, int position);

    boolean isTraktSeen();
    boolean isTraktSeen(int position);
    boolean isTraktLibrary();
    boolean isTraktLibrary(int position);

    boolean is3DVideo();
    boolean is3DVideo(int position);
}
