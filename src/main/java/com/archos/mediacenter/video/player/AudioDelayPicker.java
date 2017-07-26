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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.archos.mediacenter.video.R;


public class AudioDelayPicker extends AudioDelayPickerAbstract {

    private final long initialRepeatDelay = 350;
    private final long repeatIntervalInMilliseconds = 150;

    private final Button mMinusButton;
    private final Button mPlusButton;
    private final TextView mAudioDelayTv;

    public AudioDelayPicker(Context context) {
        this(context, null);
    }

    public AudioDelayPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AudioDelayPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.audio_delay_picker, this, true);

        mAudioDelayTv = (TextView) findViewById(R.id.text_delay);

        mMinusButton = (Button) findViewById(R.id.minus);
        mMinusButton.setText("-");
        mMinusButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                int delay = (int) (getDelay() - mStep - (getDelay() - mStep) % mStep);
                updateDelay(hasMin && delay < mMin ? mMin : delay);
                if (mOnDelayChangedListener != null) {
                    mOnDelayChangedListener.onAudioDelayChanged(AudioDelayPicker.this, getDelay());
                }
            }
        });
        mMinusButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    removeCallbacks(repeatMinusClickRunnable);
                    postDelayed(repeatMinusClickRunnable, initialRepeatDelay);
                } else if (action == MotionEvent.ACTION_UP) {
                    removeCallbacks(repeatMinusClickRunnable);
                }
                return false;
            }
        });

        mPlusButton = (Button) findViewById(R.id.plus);
        mPlusButton.setText("+");
        mPlusButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                int delay = (int) (getDelay() + mStep - (getDelay() + mStep) % mStep);
                updateDelay(hasMax && delay > mMax ? mMax : delay);
                if (mOnDelayChangedListener != null) {
                    mOnDelayChangedListener.onAudioDelayChanged(AudioDelayPicker.this, getDelay());
                }
            }
        });
        mPlusButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    removeCallbacks(repeatPlusClickRunnable);
                    postDelayed(repeatPlusClickRunnable, initialRepeatDelay);
                } else if (action == MotionEvent.ACTION_UP) {
                    removeCallbacks(repeatPlusClickRunnable);
                }
                return false;
            }
        });

        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mMinusButton.setEnabled(enabled);
        mPlusButton.setEnabled(enabled);
    }

    @Override
    public void updateDelay(int delay) {
        super.updateDelay(delay);
        mAudioDelayTv.setText(getFormattedDelay().toString());
    }

    private Runnable repeatMinusClickRunnable = new Runnable() {
        @Override
        public void run() {
            mMinusButton.performClick();
            postDelayed(repeatMinusClickRunnable, repeatIntervalInMilliseconds);
        }
    };

    private Runnable repeatPlusClickRunnable = new Runnable() {
        @Override
        public void run() {
            mPlusButton.performClick();
            postDelayed(repeatPlusClickRunnable, repeatIntervalInMilliseconds);
        }
    };

}
