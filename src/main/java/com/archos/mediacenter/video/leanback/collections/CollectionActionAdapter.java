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

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;

import java.util.ArrayList;

public class CollectionActionAdapter extends ObjectAdapter{

    public static final int ACTION_PLAY = 4;
    public static final int ACTION_MORE_DETAILS = 0;
    public static final int ACTION_MARK_COLLECTION_AS_WATCHED = 1;
    public static final int ACTION_MARK_COLLECTION_AS_NOT_WATCHED = 2;
    public static final int ACTION_UNINDEX = 5;
    public static final int ACTION_CHANGE_INFO = 3;
    public static final int ACTION_DELETE = 6;

    final ArrayList<Action> mActions;

    /**
     * @param context
     * @param collection
     */
    public CollectionActionAdapter(Context context, Collection collection) {
        mActions = new ArrayList<>(6);
        
        mActions.add(new Action(ACTION_PLAY, context.getString(R.string.play_selection)));

        // Limitation/Keep it simple: For Collection we always display "Mark watched", even if all movies are watched already
        mActions.add(new Action(ACTION_MARK_COLLECTION_AS_WATCHED, context.getString(R.string.mark_as_watched)));
        
        mActions.add(new Action(ACTION_DELETE, context.getString(R.string.delete)));
    }

    @Override
    public int size() {
        return mActions.size();
    }

    @Override
    public Object get(int position) {
        return mActions.get(position);
    }
}
