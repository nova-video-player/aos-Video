// Copyright 2023 Courville Software
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

package com.archos.mediacenter.video.utils;

public class OpenSubtitlesQueryParams {
    private String fileName;
    private Long fileLength;
    private boolean isShow;
    private Integer episodeNumber;
    private Integer seasonNumber;
    private String showTitle;
    private String fileHash;
    private String imdbId;
    private String tmdbId;

    public OpenSubtitlesQueryParams() {
    }

    public OpenSubtitlesQueryParams(String fileName, String fileHash) {
        this.fileName = fileName;
        this.fileHash = fileHash;
    }

    public void setFileName(String fileName) { this.fileName = fileName;}
    public void setFileHash(String fileHash) { this.fileHash = fileHash;}
    public void setFileLength(long fileLength) { this.fileLength = fileLength;}
    public void setImdbId(String imdbId) { this.imdbId = imdbId;}
    public void setTmdbId(String tmdbId) { this.tmdbId = tmdbId;}
    public void setIsShow(boolean show) { isShow = show;}
    public void setEpisodeNumber(Integer episodeNumber) { this.episodeNumber = episodeNumber;}
    public void setSeasonNumber(Integer seasonNumber) { this.seasonNumber = seasonNumber;}
    public void setShowTitle(String showTitle) { this.showTitle = showTitle;}
    public void setOnlineId(OnlineId onlineId) {
        if (onlineId == null) return;
        if (onlineId.getImdbId() != null) imdbId = onlineId.getImdbId();
        if (onlineId.getImdbId() != null) tmdbId = onlineId.getTmdbId();
    }

    public String getFileName() { return fileName;}
    public String getFileHash() { return fileHash;}
    public Long getFileLength() { return fileLength;}
    public String getImdbId() { return imdbId;}
    public String getTmdbId() { return tmdbId;}
    public boolean isShow() { return isShow;}
    public Integer getEpisodeNumber() { return episodeNumber;}
    public Integer getSeasonNumber() { return seasonNumber;}
    public String getShowTitle() { return showTitle;}
}
