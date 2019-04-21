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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.Context;

import com.archos.mediacenter.video.R;
import com.archos.mediascraper.ScraperImage;

/**
 * Poster, just poster (no name, no details, just the image)
 * Created by vapillon on 10/04/15.
 */
public class ScraperImageBackdropPresenter extends ScraperImagePresenter {

    private static final String TAG = "ScraperImageBackdropPresenter";

    public ScraperImageBackdropPresenter() {
        super();
    }

    public int getWidth(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.details_backdrop_width);
    }

    public int getHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.details_backdrop_height);
    }

    /**
     * For backdrops, the thumbnail is too small, need the full res file
     * @param image
     * @return
     */
    @Override
    public String getImageUrl(ScraperImage image) {
        return image.getLargeUrl();
    }
}
