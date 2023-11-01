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
    private String fileId;
    private String fileName;
    private String language;

    public OpenSubtitlesSearchResult() {}

    public OpenSubtitlesSearchResult(String fileId, String fileName, String language) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.language = language;
    }

    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public String getLanguage() { return language; }

    @Override
    public String toString() {
        return "{" + "fileId='" + fileId + "', fileName='" + fileName + "', language='" + language + "'}";
    }
}
