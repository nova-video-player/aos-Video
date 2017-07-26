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

import com.archos.filecorelibrary.MetaFile;
import com.archos.filecorelibrary.SmbItemData;
import com.archos.filecorelibrary.samba.SambaConfiguration;
import com.archos.mediacenter.utils.UpnpItemData;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;

public class ItemData implements Comparable<ItemData> {
    private static enum DataType {
        METAFILE,
        SMBITEM,
        UPNPITEM,
        SSHITEM,
        STRING,
    }
    private int mType;
    // Add here all the available item types (each item type can have its own layout)
    public static final int ITEM_VIEW_TYPE_FILE = 0;        // File or folder
    public static final int ITEM_VIEW_TYPE_SERVER = 1;      // Media server on the network
    public static final int ITEM_VIEW_TYPE_SHORTCUT = 2;    // Network share shortcut
    public static final int ITEM_VIEW_TYPE_TITLE = 3;       // Title (a single line of text aligned to the left)
    public static final int ITEM_VIEW_TYPE_TEXT = 4;        // Text (a single line of text shifted to the right)
    public static final int ITEM_VIEW_TYPE_LONG_TEXT = 5;   // Long text (several lines of centered text displayed in a much higher tile)
    public static final int ITEM_VIEW_TYPE_COUNT = 6;

    private DataType mDataType;
    private Object mData;   // Can be either a MetaFile instance or a string
    private VideoProperties mVp = null;
    private VideoDbInfo mVideoInfo = null;
    private String mInfo = null;
    private int mRemoteResumePosition = -1;

    private void init(int type, Object data, DataType dataType) {
        mType = type;
        mDataType = dataType;
        mData = data;
    }

    public ItemData(int type, Object data, DataType dataType) {
        init(type, data, dataType);
    }
    public ItemData(){}
    public ItemData(int type, MetaFile file) {
        init(type, file, DataType.METAFILE);
    }

    public ItemData(UpnpItemData upnpItem) {
        init(upnpItem.getType(), upnpItem, DataType.UPNPITEM);
    }
    public ItemData(int type, String text) {
        init(type, text, DataType.STRING);
    }

    public int getType() {
        return mType;
    }

    public Object getData() {
        return mData;
    }

    public String getTextData() {
        if (mDataType == DataType.UPNPITEM)
            return ((UpnpItemData)mData).getTextData();
        else if (mDataType == DataType.SMBITEM)
            return ((SmbItemData)mData).getTextData();

        else if (mDataType == DataType.STRING)
            return (String)mData;
        else
            return null;
    }

    public int getRemoteResumePosition() {
        return mRemoteResumePosition;
    }
    public UpnpItemData getUpnpItemData() {
        if (mDataType == DataType.UPNPITEM) {
            return (UpnpItemData)mData;
        }
        return null;
    }

    public boolean isTextItem() {
        if (mDataType == DataType.UPNPITEM)
            return ((UpnpItemData)mData).isTextItem();
        else if (mDataType == DataType.SMBITEM)
            return ((SmbItemData)mData).isTextItem();
        else
            return mDataType == DataType.STRING;
    }

    public String getName() {
        if (mVp != null)
            return mVp.getName();
        else
            return getFileName();
    }

    public String getFileName() {
        if (mDataType == DataType.STRING) {
            return (String) mData;
        } else if (mDataType == DataType.METAFILE ){
            MetaFile fileInfo = (MetaFile)mData;
            return fileInfo.getName();
        } else if (mDataType == DataType.SMBITEM) {
            return ((SmbItemData) mData).getName();
        } else if (mDataType == DataType.UPNPITEM) {
            return ((UpnpItemData)mData).getName();
        }
        
        return null;
    }

    public String getPath() {
        if (mDataType == DataType.STRING) {
            return (String) mData;
        } else if (mDataType == DataType.METAFILE ){
            MetaFile fileInfo = (MetaFile) mData;
            return fileInfo.getAccessPath();
        } else if (mDataType == DataType.SMBITEM) {
            return SambaConfiguration.getNoCredentialsPath(((SmbItemData) mData).getPath());
        } else if (mDataType == DataType.UPNPITEM) {
            return ((UpnpItemData) mData).getPath();
        }

        return "";
    }

    public void setVideoProperties(VideoProperties vp) {
        mVp = vp;
    }
    public void setRemoteResumePosition(int resume) {
        mRemoteResumePosition = resume;
    }
    public VideoProperties getVideoProperties() {
        return mVp;
    }



    public void setInfo(String info) {
        mInfo = info;
    }

    public String getInfo() {
        return mInfo;
    }
    //can be different for Metafile2ItemData
    public String getIndexablePath() {
        return getPath();
    }
    public boolean isDirectory() {
        if (mDataType == DataType.METAFILE) {
            return ((MetaFile)mData).isDirectory();
        } else if (mDataType == DataType.SMBITEM) {
            return ((SmbItemData)mData).isDirectory();
        } else if (mDataType == DataType.UPNPITEM) {
            return ((UpnpItemData)mData).isDirectory();
        }

        return false;
    }

    @Override
    public int compareTo(ItemData another) {
        if (isDirectory() != another.isDirectory())
            return another.isDirectory() ? 1 : -1;
        if (mDataType == DataType.UPNPITEM && another.mDataType == DataType.UPNPITEM)
            return ((UpnpItemData)mData).compareTo((UpnpItemData)another.mData);
        return getFileName().compareToIgnoreCase(another.getFileName());
    }
}