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

import com.archos.mediacenter.video.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.NumberPicker;


public abstract class SubtitleDelayPickerAbstract extends FrameLayout {

    /* UI Components */



    /**
     * How we notify users the date has changed.
     */


    protected int mSign = 1;
    protected int mMinute;
    protected int mSecond;
    protected int mDeciSecond;
    
    protected OnDelayChangedListener mOnDelayChangedListener;
    /**
     * The callback used to indicate the user changes the date.
     */
  
    public interface OnDelayChangedListener {
        void onDelayChanged(SubtitleDelayPickerAbstract view, int delay);
    }
    public SubtitleDelayPickerAbstract(Context context) {
        this(context, null);
    }

    public SubtitleDelayPickerAbstract(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SubtitleDelayPickerAbstract(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);


        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    public int getDelay() {
        Log.d("delay", "delay"+mDeciSecond+mSecond+mMinute);
        return mSign * (mMinute * 60 * 1000 + mSecond * 1000 + mDeciSecond * 100);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
     
    }

    public void updateDelay(int delay) {
        mSign = (delay >= 0 ? 1 : -1);
        delay *= mSign;
        mMinute = (int) delay / 60 / 1000;
        delay %= 60 * 1000;
        mSecond = (int) delay / 1000;
        delay %= 1000;
        mDeciSecond = (int) delay / 100;
        
    }

    public void init(int delay, OnDelayChangedListener onDelayChangedListener) {
        updateDelay(delay);
        mOnDelayChangedListener = onDelayChangedListener;
    }

    
}
