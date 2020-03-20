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

import com.archos.mediacenter.video.R;

import java.util.HashMap;

/**
 * Created by vapillon on 18/05/15.
 */
public class ISO639codes {

    static private HashMap<String, Integer> sMap = new HashMap<>();
    static {
        sMap.put("ar",  R.string.s_arabic);

        sMap.put("bg",  R.string.s_bulgarian);
        sMap.put("bul", R.string.s_bulgarian);

        sMap.put("bs",  R.string.Bosnian);
        sMap.put("bos", R.string.Bosnian);

        sMap.put("cs",  R.string.s_czech);
        sMap.put("ces", R.string.s_czech);
        sMap.put("cze", R.string.s_czech);

        sMap.put("da",  R.string.s_danish);
        sMap.put("dan", R.string.s_danish);

        sMap.put("de",  R.string.s_german);
        sMap.put("deu", R.string.s_german);
        sMap.put("ger", R.string.s_german);

        sMap.put("en",  R.string.s_english);
        sMap.put("eng", R.string.s_english);

        sMap.put("el",  R.string.s_greek);
        sMap.put("gre", R.string.s_greek);
        sMap.put("ell", R.string.s_greek);

        sMap.put("es",  R.string.s_spanish);
        sMap.put("esl", R.string.s_spanish);
        sMap.put("spa", R.string.s_spanish);

        sMap.put("fi",  R.string.s_finnish);
        sMap.put("fin", R.string.s_finnish);

        sMap.put("fr",  R.string.s_french);
        sMap.put("fre", R.string.s_french);
        sMap.put("fra", R.string.s_french);

        sMap.put("he",  R.string.s_hebrew);
        sMap.put("heb", R.string.s_hebrew);
        sMap.put("iw",  R.string.s_hebrew);

        sMap.put("hu",  R.string.s_hungarian);
        sMap.put("hun", R.string.s_hungarian);

        sMap.put("it",  R.string.s_italian);
        sMap.put("ita", R.string.s_italian);

        sMap.put("ja",  R.string.s_japanese);
        sMap.put("jpn", R.string.s_japanese);

        sMap.put("ko",  R.string.s_korean);
        sMap.put("kor", R.string.s_korean);

        sMap.put("nl",  R.string.s_dutch);
        sMap.put("nld", R.string.s_dutch);
        sMap.put("dut", R.string.s_dutch);

        sMap.put("no",  R.string.s_norwegian);
        sMap.put("nor", R.string.s_norwegian);

        sMap.put("pl",  R.string.s_polish);
        sMap.put("pol", R.string.s_polish);

        sMap.put("pt",  R.string.s_portuguese);
        sMap.put("por", R.string.s_portuguese);

        sMap.put("ru",  R.string.s_russian);
        sMap.put("rus", R.string.s_russian);

        sMap.put("ro",  R.string.Romanian);
        sMap.put("ron", R.string.Romanian);
        sMap.put("rum", R.string.Romanian);

        sMap.put("scr",  R.string.Serbo_Croatian);

        sMap.put("hr",  R.string.Croatian);
        sMap.put("hrv",  R.string.Croatian);

        sMap.put("sr",  R.string.Serbian);
        sMap.put("srp", R.string.Serbian);

        sMap.put("sv",  R.string.s_swedish);
        sMap.put("sve", R.string.s_swedish);
        sMap.put("swe", R.string.s_swedish);

        sMap.put("th",  R.string.s_thai);
        sMap.put("tha", R.string.s_thai);

        sMap.put("tr",  R.string.s_turkish);
        sMap.put("tur", R.string.s_turkish);

        sMap.put("zh",  R.string.s_chinese);
        sMap.put("zho", R.string.s_chinese);
        sMap.put("chi", R.string.s_chinese);

        sMap.put("vie", R.string.s_vietnamese);

        sMap.put("lt", R.string.s_lithuanian);
        sMap.put("lit", R.string.s_lithuanian);
    }

    /**
     *
     * @param context
     * @param code
     * @return a user-readable language name matching this code. Returns the code itself if no language string is found
     */
    static public String getLanguageNameForCode(Context context, String code) {
        Integer integer = sMap.get(code);
        if (integer==null) {
            return null;
        } else {
            return context.getString(integer.intValue());
        }
    }
}
