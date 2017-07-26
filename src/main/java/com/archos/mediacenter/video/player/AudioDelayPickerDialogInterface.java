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

import android.os.Message;

public interface AudioDelayPickerDialogInterface {

    public interface OnAudioDelayChangeListener {
        void onAudioDelayChange(AudioDelayPickerAbstract delayPicker, int delay);
    }

    void handleMessage(Message msg);
    public void onStop();

    public void onAudioDelayChanged(AudioDelayPickerAbstract view, int delay);

    public void updateDelay(int delay);

}
