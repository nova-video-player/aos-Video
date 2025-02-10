// Copyright 2022 Courville Software
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
import android.widget.FrameLayout;


public abstract class AudioSpeedPickerAbstract extends FrameLayout {

    private float mSpeed = 1.0f;

    // audio speed granularity of 0.05f because IMHO 1.15x is the best speed mitigating gain of speed and brain strain (subjective)
    protected float mStep = 0.05f;
    protected float mMin = 0.25f;
    protected float mMax = 2.0f;
    protected boolean hasMin = true;
    protected boolean hasMax = true;

    protected OnAudioSpeedChangedListener mOnSpeedChangedListener;

    public interface OnAudioSpeedChangedListener {
        void onAudioSpeedChanged(AudioSpeedPickerAbstract view, float speed);
    }

    public AudioSpeedPickerAbstract(Context context) {
        this(context, null);
    }

    public AudioSpeedPickerAbstract(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AudioSpeedPickerAbstract(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    public float getSpeed() {
        return mSpeed;
    }

    public CharSequence getFormattedSpeed() {
        return String.format("x %.2f", mSpeed);
   }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    public void updateSpeed(float speed) {
        mSpeed = speed;
    }

    public void setStep(float step){
        mStep = step;
    }

    public void setMin(float min) {
        hasMin = true;
        mMin = min - min % mStep;
    }

    public void setMax(float max) {
        hasMax = true;
        mMax= (float)(max - Math.IEEEremainder(max, mStep));
    }

    public void init(float speed, OnAudioSpeedChangedListener onSpeedChangedListener) {
        updateSpeed(speed);
        mOnSpeedChangedListener = onSpeedChangedListener;
    }

}
