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
public class ScraperImagePosterPresenter extends ScraperImagePresenter {

    private static final String TAG = "ScraperImagePosterPresenter";

    public ScraperImagePosterPresenter() {
        super();
    }

    public int getWidth(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.poster_width);
    }

    public int getHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.poster_height);
    }

    /**
     * For posters the thumbnail is enough in this context (at least as long as this presenter is used only in TvshowMoreDetailsFragment...)
     * @param image
     * @return
     */
    @Override
    public String getImageUrl(ScraperImage image) {
        // Poster thumbnails on TMDB are to small -> use full resolution posters
        if (image.isMovie()) {
            return image.getLargeUrl();
        } else {
            // Poster thumbnails on TVDB are enough
            return image.getThumbUrl();
        }
    }
}
