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

package com.archos.mediacenter.video.browser.subtitlesmanager;

import static com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager.convertYTSSubNamingExceptions;
import static com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager.getSubLanguageFromSubPath;

import android.content.Context;

import com.archos.mediacenter.video.utils.VideoUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by vapillon on 18/05/15.
 */
public class ISO639codes {

    private static final Logger log = LoggerFactory.getLogger(ISO639codes.class);

    static public String replaceLanguageCodeInString(Context context, String string) {
        // treat specific yts external subtitles naming for external subtitles
        String result = convertYTSSubNamingExceptions(string);
        log.debug("replaceLanguageCodeInString: string={} result={}", string, result);
        if (string.equals(result))
            result = com.archos.mediacenter.utils.ISO639codes.replaceLanguageCodeInString(getSubLanguageFromSubPath(context, string));
        if (result.startsWith("s_"))
            return VideoUtils.getLanguageString(context, result).toString();
        else return result;
    }

    static public String getLanguageNameForLetterCode(Context context, String code) {
        String result = com.archos.mediacenter.utils.ISO639codes.getLanguageNameForLetterCode(code);
        if (result.startsWith("s_"))
            return VideoUtils.getLanguageString(context, result).toString();
        else return result;
    }

    static public String getLanguageNameFor2LetterCode(Context context, String code) {
        String result = com.archos.mediacenter.utils.ISO639codes.getLanguageNameFor2LetterCode(code);
        if (result.startsWith("s_"))
            return VideoUtils.getLanguageString(context, result).toString();
        else return result;
    }

    static public String getLanguageNameOrStringFor2LetterCode(Context context, String code) {
        return com.archos.mediacenter.utils.ISO639codes.getLanguageNameFor2LetterCode(code);
    }

    static public String getLanguageNameFor3LetterCode(Context context, String code) {
        String result = com.archos.mediacenter.utils.ISO639codes.getLanguageNameFor3LetterCode(code);
        if (result.startsWith("s_"))
            return VideoUtils.getLanguageString(context, result).toString();
        else return result;
    }
}
