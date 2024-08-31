// Copyright 2024 Courville Software
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

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;

import com.archos.mediacenter.video.R;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocaleConfigParser {

    public static List<Locale> getLocales(Context context) {
        List<Locale> locales = new ArrayList<>();
        XmlResourceParser parser = context.getResources().getXml(R.xml.locales_config);

        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.getName().equals("locale")) {
                    String language = parser.getAttributeValue(null, "language");
                    String country = parser.getAttributeValue(null, "country");
                    if (language != null) {
                        if (country != null) {
                            locales.add(new Locale(language, country));
                        } else {
                            locales.add(new Locale(language));
                        }
                    } else {
                        Log.e("LocaleConfigParser", "Language attribute is missing in locales config");
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            String TAG = "LocaleConfigParser";
            Log.e(TAG, "Error parsing locales config", e);
        } finally {
            parser.close();
        }

        return locales;
    }
}