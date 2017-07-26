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


package com.archos.mediacenter.video.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.widget.TextView;

import java.io.File;

abstract public class BaseInfo {

    protected final static String EMPTY = "";

    protected boolean mValid = false;

    private Bitmap mBitmap;

    /**
     * Whenever this instance contains valid information
     */
    public boolean isValid() {
        return mValid;
    }

    /**
     * Get the cover file
     * 
     * @return java.io.File
     */
    abstract public File getCover();

    /**
     * Convenient method to store a bitmap (i.e. post-processed cover file) in
     * this instance
     */
    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    /**
     * Convenient method to retrieve the bitmap (i.e. post-processed cover file)
     * previously stored using setBitmap()
     */
    public Bitmap getBitmap() {
        return mBitmap;
    }

    /**
     * Convenient method to retrieve the title formatted.
     */
    abstract public CharSequence getFormattedTitle(Context context, int viewMode);

    /**
     * Convenient methods for CommonDetailsView
     */
    abstract public void setDetailName(TextView view, Resources res);

    abstract public void setDetailLineOne(TextView view, Resources res);

    abstract public void setDetailLineTwo(TextView view, Resources res);

    abstract public void setDetailLineThree(TextView view, Resources res);

    abstract public float getDetailLineRating();

    abstract public void setDetailLineReleaseDate(TextView view, Resources res);
}
