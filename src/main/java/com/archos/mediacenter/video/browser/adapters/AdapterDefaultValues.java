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


package com.archos.mediacenter.video.browser.adapters;

import android.content.Context;

/**
 * There are several view modes (at least: list, grid and details). Each mode
 * has its default values. Each mode implements this interface so the browser
 * adapter can retrieve these defaults values without knowing the view mode.
 */
public interface AdapterDefaultValues {

    /**
     * @return the drawable id of the default directory thumbnail.
     */
    int getDefaultDirectoryThumbnail();

    /**
     * @return the drawable id of the default shortcut thumbnail.
     */
    int getDefaultShortcutThumbnail();

    /**
     * @return the drawable id of the default video file thumbnail.
     */
    int getDefaultVideoThumbnail();

    /**
     * @param state 0: not synced, 1: synced, 2: currently syncing
     * @return the drawable id of the default sync icon according there is one
     *         or not.
     */
    int getMediaSyncIcon(int state);

    /**
     * @return the layout id which will be inflated to get the item view.
     */
    int getLayoutId();

    /**
     * @return the layout id which will be inflated to get the item view depending on the item type.
     */
    int getLayoutId(int itemType);

    /**
     * @return the height and the width of the thumbnail.
     */
    int[] getThumnailHeightWidth(Context context);

    /**
     * @return the expandable zone id.
     */
    int getExpandedZone();

}
