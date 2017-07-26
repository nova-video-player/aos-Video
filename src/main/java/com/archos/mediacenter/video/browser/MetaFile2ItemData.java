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

package com.archos.mediacenter.video.browser;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.utils.UpnpItemData;

/**
 * Created by alexandre on 30/04/15.
 */
public class MetaFile2ItemData extends ItemData {
    private final static String TAG = "SshItemData";
    protected MetaFile2 mMetafile;


    public MetaFile2ItemData(MetaFile2 metafile) {
        super();
        this.mMetafile = metafile;
    }
    @Override
    public int getType() {
        return ITEM_VIEW_TYPE_FILE;
    }
    @Override
    public Object getData() {
        return null;
    }

    @Override
    public String getTextData() {
        return mMetafile.getName();
    }

    @Override
    public UpnpItemData getUpnpItemData() {
        return null;
    }

    @Override
    public boolean isTextItem() {
        return false;
    }

    @Override
    public String getName() {
        return mMetafile.getName();
    }

    @Override
    public String getFileName() {
        return mMetafile.getName();
    }

    @Override
    public String getPath() {
        return mMetafile.getUri().toString();
    }

    @Override
    public String getIndexablePath() {
        return mMetafile.getUri().toString();
    }

    @Override
    public boolean isDirectory() {
        return mMetafile.isDirectory();
    }
    public MetaFile2 getMetaFile(){
        return mMetafile;
    }
    @Override
    public int compareTo(ItemData another) {
        if(another instanceof MetaFile2ItemData)
            return another.isDirectory()&&!this.isDirectory()?1:!another.isDirectory()&&this.isDirectory()?-1:getFileName().compareToIgnoreCase(another.getFileName());
        else
            return 1;
    }
}