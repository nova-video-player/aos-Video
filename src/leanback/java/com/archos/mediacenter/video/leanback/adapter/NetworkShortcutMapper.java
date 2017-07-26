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
import android.net.Uri;
import android.support.v17.leanback.database.CursorMapper;

import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.leanback.adapter.object.NetworkShortcut;

/**
 * Created by vapillon on 10/04/15.
 */
public class NetworkShortcutMapper extends CursorMapper {

    private static final String TAG = "NetworkShortcutMapper";

    int mIdColumn, mPathColumn, mIpPathColumn, mNameColumn,mFriendlyUriColumn;

    @Override
    public void bindColumns(Cursor c) {
        mIdColumn = c.getColumnIndex(ShortcutDbAdapter.KEY_ROWID);
        mPathColumn = c.getColumnIndex(ShortcutDbAdapter.KEY_PATH);
        mIpPathColumn = c.getColumnIndex(ShortcutDbAdapter.KEY_IPPATH);
        mNameColumn = c.getColumnIndex(ShortcutDbAdapter.KEY_NAME);
        mFriendlyUriColumn = c.getColumnIndex(ShortcutDbAdapter.KEY_FRIENDLY_URI);
    }

    @Override
    public Object bind(Cursor c) {
        final long id = c.getLong(mIdColumn);
        String path = c.getString(mPathColumn);
        String name = c.getString(mNameColumn);
        String friendlyUri = c.getString(mFriendlyUriColumn);
        Uri uri = Uri.parse(path);
        if(name==null||name.isEmpty())
            name = uri.getLastPathSegment();
        return new NetworkShortcut(id, path, name,friendlyUri);
    }
}
