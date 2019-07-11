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

package com.archos.mediacenter.video.leanback.adapter.object;

import android.net.Uri;

import java.io.Serializable;

/**
 * Created by alexandre on 18/05/15.
 */
public abstract class Shortcut implements  Serializable{


    final protected long mId;
    final protected String mFullPath;
    final protected String mName;
    private final String mFriendlyUri;

    public Shortcut(long id, String fullPath, String friendlyUri, String name) {
        mId = id;
        mFullPath = fullPath;
        if(friendlyUri!=null&&!friendlyUri.isEmpty())
            mFriendlyUri = friendlyUri;
        else
            mFriendlyUri = mFullPath;
        mName = name;
    }

    public long getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public String getFullPath() {
        return mFullPath;
    }

    public abstract Uri getUri();

    public abstract int getImage();

    public String getFriendlyUri() {
        return mFriendlyUri;
    }
}
