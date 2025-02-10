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

package com.archos.mediacenter.video.leanback.tvshow;

import android.content.Context;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.SparseArrayObjectAdapter;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;

import java.util.ArrayList;

/**
 * Created by vapillon on 20/05/15.
 */
public class TvshowActionAdapter extends SparseArrayObjectAdapter {

    public static final int ACTION_PLAY = 0;
    public static final int ACTION_MORE_DETAILS = 1;
    public static final int ACTION_MARK_SHOW_AS_WATCHED = 2;
    public static final int ACTION_MARK_SHOW_AS_NOT_WATCHED = 3;
    public static final int ACTION_UNINDEX = 4;
    public static final int ACTION_CHANGE_INFO = 5;
    public static final int ACTION_DELETE = 6;

    final Context mContext;

    /**
     * @param context
     * @param tvshow
     */
    public TvshowActionAdapter(Context context, Tvshow tvshow) {
        mContext = context;
        set(ACTION_PLAY, new Action(ACTION_PLAY, context.getString(R.string.play_selection)));
        set(ACTION_MORE_DETAILS, new Action(ACTION_MORE_DETAILS, context.getString(R.string.leanback_action_more_details)));
        if (tvshow.isWatched()) {
            clear(ACTION_MARK_SHOW_AS_WATCHED);
            set(ACTION_MARK_SHOW_AS_NOT_WATCHED, new Action(ACTION_MARK_SHOW_AS_WATCHED, mContext.getString(R.string.mark_as_not_watched)));
        } else {
            clear(ACTION_MARK_SHOW_AS_NOT_WATCHED);
            set(ACTION_MARK_SHOW_AS_WATCHED, new Action(ACTION_MARK_SHOW_AS_WATCHED, mContext.getString(R.string.mark_as_watched)));
        }
        set(ACTION_UNINDEX, new Action(ACTION_UNINDEX, context.getString(R.string.video_browser_unindex_file)));
        set(ACTION_CHANGE_INFO, new Action(ACTION_CHANGE_INFO, context.getString(R.string.scrap_change)));
        set(ACTION_DELETE, new Action(ACTION_DELETE, context.getString(R.string.delete)));
        update(tvshow);
    }

    public void update(Tvshow tvshow) {
        if (tvshow.isWatched()) {
            clear(ACTION_MARK_SHOW_AS_WATCHED);
            set(ACTION_MARK_SHOW_AS_NOT_WATCHED, new Action(ACTION_MARK_SHOW_AS_WATCHED, mContext.getString(R.string.mark_as_not_watched)));
        } else {
            clear(ACTION_MARK_SHOW_AS_NOT_WATCHED);
            set(ACTION_MARK_SHOW_AS_WATCHED, new Action(ACTION_MARK_SHOW_AS_WATCHED, mContext.getString(R.string.mark_as_watched)));
        }
        notifyChanged();
    }
}
