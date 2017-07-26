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

import com.archos.mediacenter.video.R;

import java.io.Serializable;

/**
 * Created by vapillon on 10/04/15.
 */
public class NetworkShortcut extends Shortcut implements Serializable {

    public NetworkShortcut(long id, String fullPath, String name, String friendlyUri) {
        super(id,fullPath, friendlyUri,name);
    }
    @Override
    public Uri getUri() {
        return Uri.parse(mFullPath);
    }

    @Override
    public int getImage() {
        return R.drawable.filetype_new_folder_indexed;
    }
}
