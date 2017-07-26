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

package com.archos.mediacenter.video.leanback.adapter.object;

/**
 * Created by vapillon on 10/04/15.
 */
public class Icon {

    public enum ID {
        PREFERENCES,
        PRIVATE_MODE,
        LEGACY_UI,
        HELP_FAQ
    }

    final private ID mId;
    final private String mActiveName;
    final private String mInactiveName;
    final private int mActiveResId;
    final private int mInactiveResId;
    private boolean mActive;

    public Icon(ID id, String name, int iconResId) {
        mId = id;
        mActiveName = name;
        mInactiveName = name;
        mActiveResId = iconResId;
        mInactiveResId = iconResId;
        mActive = true;
    }

    public Icon(ID id, String activeName, String inactiveName, int activeResId, int inactiveResId, boolean active) {
        mId = id;
        mActiveName = activeName;
        mInactiveName = inactiveName;
        mActiveResId = activeResId;
        mInactiveResId = inactiveResId;
        mActive = active;
    }

    public ID getId() {
        return mId;
    }

    public String getName() {
        return mActive ? mActiveName : mInactiveName;
    }

    public int getIconResId() {
        return mActive ? mActiveResId : mInactiveResId;
    }

    public void setActive(boolean activate) {
        mActive = activate;
    }

    public boolean isActive() {
        return mActive;
    }

}
