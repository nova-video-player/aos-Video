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

package com.archos.mediacenter.video.leanback.details;

import android.support.v17.leanback.widget.HeaderItem;
import android.text.SpannableString;

/**
 * Created by vapillon on 16/07/15.
 */
public class CastRow extends FullWidthRow {

    final private SpannableString mCast;
    final private String mDirectors;

    public CastRow(String header, SpannableString cast, String directors) {
        super(new HeaderItem(header));
        mCast = cast;
        mDirectors = directors;
    }

    public SpannableString getCast() {
        return mCast;
    }
    public String getDirectors() {
        return mDirectors;
    }
}
