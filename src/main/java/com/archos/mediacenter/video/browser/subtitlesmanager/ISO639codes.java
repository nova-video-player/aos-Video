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

import android.content.Context;

import com.archos.mediacenter.video.utils.VideoUtils;

/**
 * Created by vapillon on 18/05/15.
 */
public class ISO639codes {

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
