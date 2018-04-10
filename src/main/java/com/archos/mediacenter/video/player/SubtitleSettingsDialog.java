/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.archos.mediacenter.video.player;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.medialib.Subtitle;

import java.io.File;

public class SubtitleSettingsDialog extends AlertDialog implements
        SeekBar.OnSeekBarChangeListener, View.OnTouchListener, SubtitleColorPicker.ColorPickListener {

    private SeekBar mSizeSeekBar;
    private SeekBar mVertSeekBar;
    private TextView mSampleText;
    private CheckBox mSubOutlineCheckBox;
    private SubtitleManager mSubtitleManager;
    private SharedPreferences mSharedPreferences;
    private int mSize = 50;
    private int mVPos = 10;
    private boolean mOutline = false;
    private boolean touching=false;
    private View mRightSizeButton;
    private View mLeftSizeButton;
    private View mLeftVerticalButton;
    private View mRightVerticalButton;
    private static int REPEAT_TOUCH_ACTION = 0;
    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            if(mTouchedView != null){
                onAction(mTouchedView);
                sendEmptyMessageDelayed(REPEAT_TOUCH_ACTION, 200);
            }
        }


    };
    private View mTouchedView;
    private int mColor;

    public SubtitleSettingsDialog(Context context, SubtitleManager subtitleManager) {
        super(context);
        init(context, subtitleManager);
    }

    private void init(Context context, final SubtitleManager stm) {
        mSubtitleManager = stm;
        mSize = stm.getSize();
        mColor = stm.getColor();
        mOutline = stm.getOutlineState();
        setIcon(R.drawable.ic_menu_settings);

        getWindow().setGravity(Gravity.TOP);
        getWindow().setBackgroundDrawable(new ColorDrawable(VideoInfoCommonClass.getAlphaColor(ContextCompat.getColor(context, R.color.background_material_dark),128)));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        final LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final LinearLayout view = (LinearLayout) inflater.inflate(R.layout.subtitle_settings_dialog, null);

        ((SubtitleColorPicker)view.findViewById(R.id.color_layout)).setColorPickListener(this);

        setView(view);
        mLeftSizeButton = view.findViewById(R.id.left_a);
        mLeftSizeButton.setOnTouchListener(this);
        mRightSizeButton = view.findViewById(R.id.right_a);
        mRightSizeButton.setOnTouchListener(this);

        mRightVerticalButton = view.findViewById(R.id.right_icon);
        mRightVerticalButton.setOnTouchListener(this);
        mLeftVerticalButton = view.findViewById(R.id.left_icon);
        mLeftVerticalButton.setOnTouchListener(this);

        mSampleText = (TextView) view.findViewById(R.id.subtitle_sample_text);
        mSampleText.setTextSize(mSize);
        mSampleText.setTextColor(mColor);
        mSizeSeekBar = (SeekBar) view.findViewById(R.id.subtitle_size_seekbar);
        mSizeSeekBar.setOnSeekBarChangeListener(this);

        mVertSeekBar = (SeekBar) view.findViewById(R.id.subtitle_vert_seekbar);
        // 0.255 Range is what SubtitleManager.setVerticalPosition() expects
        mVertSeekBar.setMax(255);
        mVertSeekBar.setOnSeekBarChangeListener(this);

        mSubOutlineCheckBox = view.findViewById(R.id.subOutline);
        mSubOutlineCheckBox.setChecked(mOutline);
        mSubOutlineCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOutline = mSubOutlineCheckBox.isChecked();
                mSubtitleManager.setOutlineState(mOutline);
            }
        });

        setCancelable(true);
        setCanceledOnTouchOutside(true);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromTouch) {
        if (seekBar == mSizeSeekBar) {
            mSize = progress;
            if (mSampleText != null) {
                mSampleText.setTextSize(SubtitleManager.calcTextSize(progress));
            }
            if (mSubtitleManager != null) {
                mSubtitleManager.setSize(progress);
            }
        } else if (seekBar == mVertSeekBar) {
            mVPos = progress;
            if (mSubtitleManager != null) {
                if(!touching){
                    mSubtitleManager.fadeSubtitlePositionHint(true);
                    seekBar.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // TODO Auto-generated method stub
                            mSubtitleManager.fadeSubtitlePositionHint(false);
                        }
                    }, 200);
                }
                mSubtitleManager.setVerticalPosition(progress);
            }
        } else {
            // wtf
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        touching=true;
        if (seekBar == mVertSeekBar) {
            mSubtitleManager.fadeSubtitlePositionHint(true);
        }
    }
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        touching=false;
        if (seekBar == mVertSeekBar) {
            mSubtitleManager.fadeSubtitlePositionHint(false);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        Log.d("Player", "onDetachedFromWindow");
        mSampleText.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        mSharedPreferences.edit().putInt(PlayerActivity.KEY_SUBTITLE_SIZE, mSize).apply();
        mSharedPreferences.edit().putInt(PlayerActivity.KEY_SUBTITLE_VPOS, mVPos).apply();
        mSharedPreferences.edit().putBoolean(PlayerActivity.KEY_SUBTITLE_OUTLINE, mOutline).apply();
        mSubtitleManager.fadeSubtitlePositionHint(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSampleText.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

        // Set the initial position of the sliders and checkbox
        mSizeSeekBar.setProgress(mSubtitleManager.getSize());
        mVertSeekBar.setProgress(mSubtitleManager.getVerticalPosition());
        mSubOutlineCheckBox.setChecked(mSubtitleManager.getOutlineState());
        mSubtitleManager.setShowSubtitlePositionHint(true);

        // Force the initial focus on the size slider
        mSizeSeekBar.requestFocus();
    }

    public void onAction(View view) {
        if(view == mLeftSizeButton){
            if(mSize -1 >= 0) {
                mSize--;
                if (mSampleText != null) {
                    mSampleText.setTextSize(SubtitleManager.calcTextSize(mSize));
                }
                if (mSubtitleManager != null) {
                    mSubtitleManager.setSize(mSize);
                }
                mSizeSeekBar.setProgress(mSize);
            }
        }
        else if (view == mRightSizeButton){
            if(mSize +1 <= mSizeSeekBar.getMax()) {
                mSize++;
                if (mSampleText != null) {
                    mSampleText.setTextSize(SubtitleManager.calcTextSize(mSize));
                }
                if (mSubtitleManager != null) {
                    mSubtitleManager.setSize(mSize);
                }
                mSizeSeekBar.setProgress(mSize);
            }
        }
        else if (view == mLeftVerticalButton){
            if(mVPos -3 >=0) {
                mVPos=mVPos-3;
                if (mSubtitleManager != null) {
                    if (!touching) {
                        mSubtitleManager.fadeSubtitlePositionHint(true);
                        mVertSeekBar.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // TODO Auto-generated method stub
                                mSubtitleManager.fadeSubtitlePositionHint(false);
                            }
                        }, 200);
                    }
                    mSubtitleManager.setVerticalPosition(mVPos);
                    mVertSeekBar.setProgress(mVPos);
                }
            }
        }
        else if (view == mRightVerticalButton){
            if(mVPos +3 <=mVertSeekBar.getMax()) {
                mVPos=mVPos+3;
                if (mSubtitleManager != null) {
                    if (!touching) {
                        mSubtitleManager.fadeSubtitlePositionHint(true);
                        mVertSeekBar.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // TODO Auto-generated method stub
                                mSubtitleManager.fadeSubtitlePositionHint(false);
                            }
                        }, 200);
                    }
                    mSubtitleManager.setVerticalPosition(mVPos);
                    mVertSeekBar.setProgress(mVPos);
                }
            }
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                view.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
                mHandler.removeMessages(REPEAT_TOUCH_ACTION);
                mTouchedView = view;
                onAction(mTouchedView);
                mHandler.sendEmptyMessageDelayed(REPEAT_TOUCH_ACTION,200);
                break;

            case MotionEvent.ACTION_UP:
                view.setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
                mHandler.removeMessages(REPEAT_TOUCH_ACTION);
                mTouchedView = null;
            break;
        }

        return true;
    }

    @Override
    public void onColorPicked(int color) {
        mColor = color;
        mSubtitleManager.setColor(mColor);
        mSampleText.setTextColor(mColor);
    }
}
