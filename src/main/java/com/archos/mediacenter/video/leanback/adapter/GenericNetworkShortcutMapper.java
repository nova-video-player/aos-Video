// Copyright 2022 Courville Software
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

package com.archos.mediacenter.video.leanback.adapter;

import android.database.Cursor;
import android.provider.BaseColumns;

import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.leanback.adapter.object.GenericNetworkShortcut;

public class GenericNetworkShortcutMapper  {

    private static final String TAG = "GenericNetworkShortcutMapper";

    int mIdColumn, mPathColumn, mNameColumn, mFriendlyUriColumn;

    public void bindColumns(Cursor c) {
        mIdColumn = c.getColumnIndex(BaseColumns._ID);
        mPathColumn = c.getColumnIndex(ShortcutDb.KEY_URI);
        mNameColumn = c.getColumnIndex(ShortcutDb.KEY_SHORTCUT_NAME);
        mFriendlyUriColumn = c.getColumnIndex(ShortcutDb.KEY_FRIENDLY_URI);
    }

    public Object bind(Cursor c) {
        final long id = c.getLong(mIdColumn);
        String path = c.getString(mPathColumn);
        String name = c.getString(mNameColumn);
        String friendlyUri = c.getString(mFriendlyUriColumn);
        if (friendlyUri == null)
            return new GenericNetworkShortcut(id, path, name, path);
        else
            return new GenericNetworkShortcut(id, path, name, friendlyUri);
    }
}
