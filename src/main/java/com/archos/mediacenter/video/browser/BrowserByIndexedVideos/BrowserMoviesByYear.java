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

package com.archos.mediacenter.video.browser.BrowserByIndexedVideos;

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.ThumbnailEngineVideo;
import com.archos.mediacenter.video.browser.loader.MoviesByYearLoader;
import com.archos.mediacenter.video.utils.CustomTypefaceSpan;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediaprovider.video.VideoStore;

import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.loader.content.Loader;

public class BrowserMoviesByYear extends BrowserMoviesBy {

    @Override
    public int getThumbnailsType() {
        return ThumbnailEngineVideo.TYPE_MOVIE_YEAR;
    }

    @Override
    protected Uri getCursorUri() {
        return VideoStore.RAW_QUERY;
    }



	public void addSortOptionsSubmenus(ActionBarSubmenu submenu) {
	    // MENU_ITEM_NAME is not a typo here, because the year will be copied to the name column
        submenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_date_desc), MENU_ITEM_SORT + MENU_ITEM_NAME + MENU_ITEM_DESC);
        submenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_date_asc), MENU_ITEM_SORT + MENU_ITEM_NAME + MENU_ITEM_ASC);
    }

    private SpannableString applyCustomFont(@StringRes int resId) {
        String family ="";
        Typeface typeface = ResourcesCompat.getFont(mContext, R.font.nhaasgroteskdspro_75bd);
        int color = ContextCompat.getColor(mContext, android.R.color.white);
        float textSize = 18f; // in SP
        String text = mContext.getString(resId);
        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new CustomTypefaceSpan(family, typeface, textSize, color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @Override
    protected String getDefaultSortOrder() {
        return COLUMN_NAME+" COLLATE LOCALIZED DESC";
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new MoviesByYearLoader(getContext(), mSortOrder).getV4CursorLoader(false, mPreferences.getBoolean(VideoPreferencesCommon.KEY_HIDE_WATCHED, false));
    }

}
