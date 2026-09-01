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

import java.util.Locale;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AudioDelayPickerAbstract extends FrameLayout {

    private static final Logger log = LoggerFactory.getLogger(AudioDelayPickerAbstract.class);

    private int mSign = 1;
    private int mMinute = 0;
    private int mSecond = 0;
    private int mMilliSecond = 0;

    protected int mStep = 1;
    protected int mMin = 0;
    protected int mMax = 0;
    protected boolean hasMin = false;
    protected boolean hasMax = false;

    protected OnAudioDelayChangedListener mOnDelayChangedListener;

    public interface OnAudioDelayChangedListener {
        void onAudioDelayChanged(AudioDelayPickerAbstract view, int delay);
    }

    public AudioDelayPickerAbstract(Context context) {
        this(context, null);
    }

    public AudioDelayPickerAbstract(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AudioDelayPickerAbstract(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    public int getDelay() {
        return mSign * (mMinute * 60 * 1000 + mSecond * 1000 + mMilliSecond);
    }

    public CharSequence getFormattedDelay() {
        return String.format(Locale.getDefault(), "%s %d m %d s %03d ms", mSign == 1 ? " " : "-", mMinute, mSecond, mMilliSecond);
   }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    public void updateDelay(int delay) {
        mSign = (delay >= 0 ? 1 : -1);
        delay = Math.abs(delay);
        mMinute = (int) delay / 60 / 1000;
        mSecond = (int) (delay % (60 * 1000)) / 1000;
        mMilliSecond = (int) (delay % 1000);
        if (log.isDebugEnabled()) log.debug("updateDelay: delay={} mMinute={} mSecond={} mMilliSecond={}", delay, mMinute, mSecond, mMilliSecond);
    }

    public void setStep(int step){
        mStep = step;
    }

    public void setMin(int min) {
        hasMin = true;
        mMin = min - min % mStep;
        if (log.isDebugEnabled()) log.debug("setMin: mMin={}", mMin);
    }

    public void setMax(int max) {
        hasMax = true;
        mMax= (int)(max - Math.IEEEremainder(max, mStep));
        if (log.isDebugEnabled()) log.debug("setMax: mMax={}", mMax);
    }

    public void init(int delay, OnAudioDelayChangedListener onDelayChangedListener) {
        if (log.isDebugEnabled()) log.debug("init: delay={}", delay);
        updateDelay(delay);
        mOnDelayChangedListener = onDelayChangedListener;
    }

}
