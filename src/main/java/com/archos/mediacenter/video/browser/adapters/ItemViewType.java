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

/**
 * Created by alexandre on 04/11/15.
 */
public class ItemViewType {
    public static final int ITEM_VIEW_TYPE_FILE = 0;        // File or folder
    public static final int ITEM_VIEW_TYPE_SERVER = 1;      // Media server on the network
    public static final int ITEM_VIEW_TYPE_SHORTCUT = 2;    // Network share shortcut
    public static final int ITEM_VIEW_TYPE_TITLE = 3;       // Title (a single line of text aligned to the left)
    public static final int ITEM_VIEW_TYPE_TEXT = 4;        // Text (a single line of text shifted to the right)
    public static final int ITEM_VIEW_TYPE_LONG_TEXT = 5;   // Long text (several lines of centered text displayed in a much higher tile)
    public static final int ITEM_VIEW_TYPE_HEADER_SHOW = 6;
    public static final int ITEM_VIEW_TYPE_SHOW = 7;
    public static final int ITEM_VIEW_TYPE_COUNT = 8;
}
