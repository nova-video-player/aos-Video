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

package com.archos.mediacenter.video.leanback.adapter;

import android.database.Cursor;
import android.provider.BaseColumns;

import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.leanback.adapter.object.FtpShortcut;

/**
 * Created by vapillon on 10/04/15.
 */
public class FtpShortcutMapper  {

    private static final String TAG = "FtpShortcutMapper";

    int mIdColumn, mPathColumn, mNameColumn;

    public void bindColumns(Cursor c) {
        mIdColumn = c.getColumnIndex(BaseColumns._ID);
        mPathColumn = c.getColumnIndex(ShortcutDb.KEY_URI);
        mNameColumn = c.getColumnIndex(ShortcutDb.KEY_SHORTCUT_NAME);
    }

    public Object bind(Cursor c) {
        final long id = c.getLong(mIdColumn);
        String path = c.getString(mPathColumn);
        String name = c.getString(mNameColumn);
        return new FtpShortcut(id, path, name);
    }
}
