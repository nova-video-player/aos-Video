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

package com.archos.mediacenter.video.browser.presenter;

import android.content.Context;
import android.content.res.Configuration;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesGrid;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesGridShort;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.player.TextShadowSpan;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;

import httpimage.HttpImageManager;

/**
 * Created by alexandre on 26/10/15.
 */
public class VideoGridShortPresenter extends VideoGridPresenter{
    public VideoGridShortPresenter(Context context, ExtendedClickListener onExtendedClick, HttpImageManager imageManager) {
        super(context, AdapterDefaultValuesGridShort.INSTANCE, onExtendedClick,imageManager);
    }
}
