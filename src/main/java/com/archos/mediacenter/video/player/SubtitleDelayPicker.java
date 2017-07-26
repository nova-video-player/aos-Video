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
import com.archos.mediacenter.video.player.SubtitleDelayPickerAbstract.OnDelayChangedListener;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.NumberPicker;


public class SubtitleDelayPicker extends SubtitleDelayPickerAbstract{

    /* UI Components */
    private final Button        mSignButton;
    private final NumberPicker  mMinutePicker;
    private final NumberPicker  mSecondPicker;
    private final NumberPicker  mDeciSecondPicker;

    /**
     * How we notify users the date has changed.
     */


    private int mSign = 1;
    private int mMinute;
    private int mSecond;
    private int mDeciSecond;

    /**
     * The callback used to indicate the user changes the date.
     */
   
  
    public SubtitleDelayPicker(Context context) {
        this(context, null);
    }

    public SubtitleDelayPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SubtitleDelayPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.subtitle_delay_picker, this, true);

        mSignButton = (Button) findViewById(R.id.sign);
        mSignButton.setText("+");
        mSignButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mSign *= -1;
                if (mOnDelayChangedListener != null) {
                    mOnDelayChangedListener.onDelayChanged(SubtitleDelayPicker.this, getDelay());
                }
                updateSpinners();
            }
        });

        final NumberPicker.Formatter twoDigitFormatter = new NumberPicker.Formatter() {
            final StringBuilder mBuilder = new StringBuilder();
            final java.util.Formatter mFmt = new java.util.Formatter(mBuilder, java.util.Locale.US);
            final Object[] mArgs = new Object[1];

            public String format(int value) {
                mArgs[0] = value;
                mBuilder.delete(0, mBuilder.length());
                mFmt.format("%02d", mArgs);
                return mFmt.toString();
            }
        };

        mMinutePicker = (NumberPicker) findViewById(R.id.minute);
        mMinutePicker.setFormatter(twoDigitFormatter);
        mMinutePicker.setMinValue(0);
        mMinutePicker.setMaxValue(59);
        mMinutePicker.setOnLongPressUpdateInterval(100);
        mMinutePicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                mMinute = newVal;
                if (mOnDelayChangedListener != null) {
                    mOnDelayChangedListener.onDelayChanged(SubtitleDelayPicker.this, getDelay());
                }
                updateSpinners();
            }
        });
        mSecondPicker = (NumberPicker) findViewById(R.id.second);
        mSecondPicker.setFormatter(twoDigitFormatter);
        mSecondPicker.setMinValue(0);
        mSecondPicker.setMaxValue(59);
        mSecondPicker.setOnLongPressUpdateInterval(100);
        mSecondPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                mSecond = newVal;
                if (oldVal == 0 && newVal == 59) {
                    if (mMinute == 0) {
                        mSecond = 0;
                    } else {
                        mMinute--;
                    }
                } else if (oldVal == 59 && newVal == 0) {
                    mMinute++;
                }

                if (mOnDelayChangedListener != null) {
                    mOnDelayChangedListener.onDelayChanged(SubtitleDelayPicker.this, getDelay());
                }
                updateSpinners();
            }
        });
        mDeciSecondPicker = (NumberPicker) findViewById(R.id.deci_second);
        mDeciSecondPicker.setMinValue(0);
        mDeciSecondPicker.setMaxValue(9);
        mDeciSecondPicker.setOnLongPressUpdateInterval(100);
        mDeciSecondPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                mDeciSecond = newVal;
                if (oldVal == 0 && newVal == 9) {
                    if (mSecond == 0) {
                        mDeciSecond = 0;
                    } else {
                        mSecond--;
                    }
                } else if (oldVal == 9 && newVal == 0) {
                    mSecond++;
                }
                // Adjust max day for leap years if needed
                if (mOnDelayChangedListener != null) {
                    mOnDelayChangedListener.onDelayChanged(SubtitleDelayPicker.this, getDelay());
                }
                updateSpinners();
            }
        });

        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    public int getDelay() {
        return mSign * (mMinute * 60 * 1000 + mSecond * 1000 + mDeciSecond * 100);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mMinutePicker.setEnabled(enabled);
        mSecondPicker.setEnabled(enabled);
        mDeciSecondPicker.setEnabled(enabled);
    }

    public void updateDelay(int delay) {
        mSign = (delay >= 0 ? 1 : -1);
        delay *= mSign;
        mMinute = (int) delay / 60 / 1000;
        delay %= 60 * 1000;
        mSecond = (int) delay / 1000;
        delay %= 1000;
        mDeciSecond = (int) delay / 100;
        updateSpinners();
    }



    private void updateSpinners() {
        mSignButton.setText(mSign == 1 ? "+" : "-");
        mMinutePicker.setValue(mMinute);
        mSecondPicker.setValue(mSecond);
        mDeciSecondPicker.setValue(mDeciSecond);
    }
}
