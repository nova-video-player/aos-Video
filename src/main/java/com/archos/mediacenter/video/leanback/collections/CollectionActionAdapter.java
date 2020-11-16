// Copyright 2020 Courville Software
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

package com.archos.mediacenter.video.leanback.collections;

import android.content.Context;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.SparseArrayObjectAdapter;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;

import java.util.ArrayList;

public class CollectionActionAdapter extends SparseArrayObjectAdapter {

    public static final int ACTION_PLAY = 1;
    public static final int ACTION_MARK_COLLECTION_AS_WATCHED = 2;
    public static final int ACTION_MARK_COLLECTION_AS_NOT_WATCHED = 3;
    public static final int ACTION_DELETE = 4;
    public static final int ACTION_CONFIRM_DELETE = 5;

    final Context mContext;

    /**
     * @param context
     * @param collection
     */
    public CollectionActionAdapter(Context context, Collection collection, Boolean displayConfirmDelete) {
        mContext = context;
        set(ACTION_PLAY, new Action(ACTION_PLAY, context.getString(R.string.play_selection)));
        update(collection, displayConfirmDelete);
    }

    public void update(Collection collection, boolean displayConfirmDelete) {
        if (!displayConfirmDelete) {
            clear(ACTION_CONFIRM_DELETE);
            set(ACTION_DELETE, new Action(ACTION_DELETE, mContext.getString(R.string.delete)));
        } else {
            clear(ACTION_DELETE);
            set(ACTION_CONFIRM_DELETE, new Action(ACTION_CONFIRM_DELETE, mContext.getString(R.string.confirm_delete_short)));
        }
        if (collection.isWatched()) {
            clear(ACTION_MARK_COLLECTION_AS_WATCHED);
            set(ACTION_MARK_COLLECTION_AS_NOT_WATCHED, new Action(ACTION_MARK_COLLECTION_AS_NOT_WATCHED, mContext.getString(R.string.mark_as_not_watched)));
        } else {
            clear(ACTION_MARK_COLLECTION_AS_NOT_WATCHED);
            set(ACTION_MARK_COLLECTION_AS_WATCHED, new Action(ACTION_MARK_COLLECTION_AS_WATCHED, mContext.getString(R.string.mark_as_watched)));
        }
        notifyChanged();
    }
}
