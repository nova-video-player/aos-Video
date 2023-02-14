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

package com.archos.mediacenter.video.leanback.adapter.object;

import android.net.Uri;

import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.contentstorage.ContentStorageFileEditor;
import com.archos.filecorelibrary.ftp.FtpFileEditor;
import com.archos.filecorelibrary.jcifs.JcifsFileEditor;
import com.archos.filecorelibrary.localstorage.LocalStorageFileEditor;
import com.archos.filecorelibrary.sftp.SftpFileEditor;
import com.archos.filecorelibrary.zip.ZipFileEditor;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.io.Serializable;

public class GenericNetworkShortcut extends Shortcut implements Serializable {

    public GenericNetworkShortcut(long id, String fullPath, String name) {
       super(id, fullPath, fullPath, name);
    }

    public Uri getUri() {
        return Uri.parse(mFullPath);
    }
    @Override
    public int getImage() {
        Uri uri = getUri();
        return VideoUtils.getShortcutImageLeanback(uri);
    }
}
