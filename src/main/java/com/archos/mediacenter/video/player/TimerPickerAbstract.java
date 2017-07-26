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
import android.widget.FrameLayout;


public abstract class TimerPickerAbstract extends FrameLayout {

    private int mMinute = 0;
    private int mHour = 0;


    protected int mStep = 1;
    protected int mMin = 0;
    protected int mMax = 0;
    protected boolean hasMin = false;
    protected boolean hasMax = false;

    protected OnTimerChangedListener mOnDelayChangedListener;

    public interface OnTimerChangedListener {
        void onTimerChanged(TimerPickerAbstract view, int delay);
    }

    public TimerPickerAbstract(Context context) {
        this(context, null);
    }

    public TimerPickerAbstract(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimerPickerAbstract(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    public int getDelay() {
        return (mMinute * 60 * 1000 + mHour * 60 * 60 * 1000);
    }

    public CharSequence getFormattedDelay() {
        return String.format("%d h %d m", mHour, mMinute);
   }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    public void updateDelay(int delay) {
        delay = Math.abs(delay);
        mMinute = ((int) delay / 60 / 1000 )% 60;
        mHour = (int) delay / 60 / 60 / 1000;

    }

    public void setStep(int step){
        mStep = step;
    }

    public void setMin(int min) {
        hasMin = true;
        mMin = min - min % mStep;
    }

    public void setMax(int max) {
        hasMax = true;
        mMax= (int)(max - Math.IEEEremainder(max, mStep));
    }

    public void init(int delay, OnTimerChangedListener onDelayChangedListener) {
        updateDelay(delay);
        mOnDelayChangedListener = onDelayChangedListener;
    }

}
