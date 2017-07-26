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

package com.archos.mediacenter.video.utils;

import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;

public enum SortOrder {
    DURATION(
        VideoColumns.DURATION,
        "0",
        "2147483647"
        ),
    SCRAPER_RATING(
        VideoColumns.SCRAPER_RATING,
        "0",
        "2147483647"
        ),
    SCRAPER_M_RATING(
        VideoColumns.SCRAPER_M_RATING,
        "0",
        "2147483647"
        ),
        ;

    private final String mAsc;
    private final String mDesc;

    /** sort by column, null values are replaced with either a higher or lower than normal value */
    SortOrder(String column, String lowNullValue, String highNullValue) {
        // high > low values, assign null to lowest value
        mDesc = "IFNULL(" + column + ", " + lowNullValue + ") DESC";
        // low > high values, assign null to highest value
        mAsc = "IFNULL(" + column + ", " + highNullValue + ") ASC";
    }

    public String getAsc() {
        return mAsc;
    }

    public String getDesc() {
        return mDesc;
    }

    public String get(boolean isDesc) {
        return isDesc ? mDesc : mAsc;
    }

}
