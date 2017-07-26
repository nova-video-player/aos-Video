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


/*
  * This class provides through a file path an access to some media info
  * retrieved from the database.
  */
public class VideoProperties {

    public boolean bookmark;

    public boolean isResume() {
        return resume;
    }

    public boolean isBookmark() {
        return bookmark;
    }

    public int getBookmarkPosition() {
        return bookmarkPosition;
    }

    public int getResumePosition() {
        return resumePosition;
    }

    public boolean isSubtitles() {
        return subtitles;
    }

    public int getDuration() {
        return duration;
    }

    public int getId() {
        return id;
    }

    public int getScraperType() {
        return scraperType;
    }

    public float getRating() {
        return rating;
    }

    public long getDate() {
        return date;
    }

    public int getTraktSeen() {
        return traktSeen;
    }

    public int getTraktLibrary() {
        return traktLibrary;
    }

    public int getVideoStereo() {
        return videoStereo;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public String getDetailLineOne() {
        return detailLineOne;
    }

    public String getDetailLineTwo() {
        return detailLineTwo;
    }

    public String getDetailLineThree() {
        return detailLineThree;
    }

    public String getName() {
        return name;
    }

    public String getNameGrid() {
        return nameGrid;
    }

    public String getNameList() {
        return nameList;
    }



    public void setBookmark(boolean bookmark) {
        this.bookmark = bookmark;
    }

    public void setBookmarkPosition(int bookmarkPosition) {
        this.bookmarkPosition = bookmarkPosition;
    }

    public void setResume(boolean resume) {
        this.resume = resume;
    }

    public void setResumePosition(int resumePosition) {
        this.resumePosition = resumePosition;
    }

    public void setSubtitles(boolean subtitles) {
        this.subtitles = subtitles;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setScraperType(int scraperType) {
        this.scraperType = scraperType;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public void setTraktSeen(int traktSeen) {
        this.traktSeen = traktSeen;
    }

    public void setTraktLibrary(int traktLibrary) {
        this.traktLibrary = traktLibrary;
    }

    public void setVideoStereo(int videoStereo) {
        this.videoStereo = videoStereo;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public void setDetailLineOne(String detailLineOne) {
        this.detailLineOne = detailLineOne;
    }

    public void setDetailLineTwo(String detailLineTwo) {
        this.detailLineTwo = detailLineTwo;
    }

    public void setDetailLineThree(String detailLineThree) {
        this.detailLineThree = detailLineThree;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNameGrid(String nameGrid) {
        this.nameGrid = nameGrid;
    }

    public void setNameList(String nameList) {
        this.nameList = nameList;
    }
    public int bookmarkPosition;
    public boolean resume;
    public int resumePosition;
    public boolean subtitles;
    public int duration;
    public int id;
    public int scraperType;
    public float rating;
    public long date;
    public int traktSeen;
    public int traktLibrary;
    public int videoStereo;
    public String coverPath;
    public String detailLineOne, detailLineTwo, detailLineThree;
    public String name, nameGrid, nameList;

    public VideoProperties(int id, int duration, boolean bookmark, int bookmarkPosition,
                           boolean resume, int resumePosition, boolean subtitles,
                           int scraperType, String name, String nameGrid, String nameList,
                           float rating, String detailLineOne, String detailLineTwo,
                           String detailLineThree, String coverPath, long date, int traktSeen,
                           int traktLibrary, int videoStereo) {
        this.id = id;
        this.duration = duration;
        this.bookmark = bookmark;
        this.bookmarkPosition = bookmarkPosition;
        this.resume = resume;
        this.resumePosition = resumePosition;
        this.subtitles = subtitles;
        this.scraperType = scraperType;
        this.name = name;
        this.nameGrid = nameGrid;
        this.nameList = nameList;
        this.rating = rating;
        this.detailLineOne = detailLineOne;
        this.detailLineTwo = detailLineTwo;
        this.detailLineThree = detailLineThree;
        this.coverPath = coverPath;
        this.date = date;
        this.traktSeen = traktSeen;
        this.traktLibrary = traktLibrary;
        this.videoStereo = videoStereo;
    }
}
