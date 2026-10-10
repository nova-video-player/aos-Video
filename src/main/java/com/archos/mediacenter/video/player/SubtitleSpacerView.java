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

package com.archos.mediacenter.video.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * Position-hint indicator for the subtitle vertical-offset control.
 *
 * SubtitleManager briefly shows this view (alpha-animated in, then out) while the user
 * drags the vertical-offset slider, so they can see where the bottom margin will land
 * before releasing. It plays no role in actual subtitle rendering — that is owned
 * entirely by libass's MarginV, applied natively via SubtitleEngine.setVerticalOffset().
 *
 * This used to also carry a second rendering path: drawing its background Drawable onto
 * a separate Surface (mExternalSurface) for the old dual-surface Java-canvas subtitle
 * renderer. That path's only assignment site was already commented out (SubtitleManager
 * never actually had a live external surface to give it), making every branch built
 * around it permanently unreachable — it has been removed rather than kept as dead code.
 * A plain View with a background Drawable, shown/hidden via alpha, is all this needs now.
 */
public class SubtitleSpacerView extends View {

    public SubtitleSpacerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
