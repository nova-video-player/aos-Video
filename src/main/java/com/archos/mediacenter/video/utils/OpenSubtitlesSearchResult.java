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

public class OpenSubtitlesSearchResult {
    private String id;
    private String fileId;
    private String fileName;
    private String language;
    private String release; // release name
    private String movie_name; // scraped movie or episode name
    private String parent_title; // show name
    private Boolean moviehash_match; // moviehash_match
    private Integer season_number; // season, 0 if movie
    private Integer episode_number; // episode, 0 if movie
    private String feature_type; // movie or episode

    public OpenSubtitlesSearchResult() {}

    public OpenSubtitlesSearchResult(String fileId, String fileName, String language) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.language = language;
    }

    public String getId() { return id; }
    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public String getLanguage() { return language; }
    public String getRelease() { return release; }
    public String getMovieName() { return movie_name; }
    public String getParentTitle() { return parent_title; }
    public Boolean getMoviehashMatch() { return moviehash_match; }
    public Integer getSeasonNumber() { return season_number; }
    public Integer getEpisodeNumber() { return episode_number; }
    public String getFeatureType() { return feature_type; }

    public void setId(String id) { this.id = id; }
    public void setRelease(String release) { this.release = release; }
    public void setMovieName(String movie_name) { this.movie_name = movie_name; }
    public void setParentTitle(String parent_title) { this.parent_title = parent_title; }
    public void setMoviehashMatch(Boolean moviehash_match) { this.moviehash_match = moviehash_match; }
    public void setSeasonNumber(Integer season_number) { this.season_number = season_number; }
    public void setEpisodeNumber(Integer episode_number) { this.episode_number = episode_number; }
    public void setFeatureType(String feature_type) { this.feature_type = feature_type; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public void setLanguage(String language) { this.language = language; }

    @Override
    public String toString() {
        return "{" +
                "fileId='" + fileId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", language='" + language + '\'' +
                ", release='" + release + '\'' +
                ", movie_name='" + movie_name + '\'' +
                ", parent_title='" + parent_title + '\'' +
                ", moviehash_match=" + moviehash_match +
                ", season_number=" + season_number +
                ", episode_number=" + episode_number +
                ", feature_type='" + feature_type + '\'' +
                '}';
    }
}
