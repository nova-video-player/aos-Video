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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import com.archos.mediacenter.video.R;


public class BrightnessDialog extends AlertDialog implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "BrightnessDialog";

    private ArchosSeekBar mSeekBar;
    private ContentResolver mContentResolver;
    private Context mContext;

    // Backlight range is from 0 - 255. Need to make sure that user
    // doesn't set the backlight to 0 and get stuck
    private static final int MINIMUM_BACKLIGHT = 10;
    private static final int MAXIMUM_BACKLIGHT = 255;

    private static final int DEFAULT_BACKLIGHT = MAXIMUM_BACKLIGHT;
    private SwitchCompat mSystemMode;

    public BrightnessDialog(Context context) {
        super(context);
        init(context);
    }

    public BrightnessDialog(Context context, int theme) {
        super(context, theme);
        init(context);
    }
    
    /*
     * This method is only called once when the dialog is shown for the first time
     * (closing the dialog does not destroy it so it will only be destroyed when exiting the video player)
     */
    private void init(Context context) {
        mContext= context;
        mContentResolver = context.getContentResolver();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().getAttributes().gravity = Gravity.TOP;
        getWindow().getAttributes().y = 72;
        
        LayoutInflater inflater = 
            (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.brightness_dialog, null);
        setView(view);
        mSeekBar = (ArchosSeekBar) view.findViewById(R.id.brightness_seekbar);
        mSeekBar.setOnEnableListener(new ArchosSeekBar.OnEnableListener() {
            @Override
            public void onEnable() {
                mSystemMode.setChecked(false);
            }
        });
        mSystemMode = (SwitchCompat) view.findViewById(R.id.toggleButton);
        mSystemMode.setOnCheckedChangeListener(this);
        mSeekBar.setMax(MAXIMUM_BACKLIGHT - MINIMUM_BACKLIGHT);
        mSeekBar.setOnSeekBarChangeListener(this);


        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    /*
     * This method is called each time the dialog is shown so it can be used to check
     * settings which may have been modified by the user outside the video player 
     */
    public void onStart() {
        // Update the current brightness value
        int currentBrightness;
        mSeekBar.setEnabled(true);
        try {
            currentBrightness = (int) (((PlayerActivity)mContext).getWindow().getAttributes().screenBrightness*255f);
            if(currentBrightness<=0) {//read global brightness setting when not set on window
                currentBrightness = Settings.System.getInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS);
                mSystemMode.setChecked(true);
                mSeekBar.setEnabled(false);
                if(Settings.System.getInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)== Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    currentBrightness = MINIMUM_BACKLIGHT;


            }
            else{
                mSystemMode.setChecked(false);
            }
        } catch (SettingNotFoundException e) {
            currentBrightness = DEFAULT_BACKLIGHT;

        }
        mSeekBar.setProgress(currentBrightness - MINIMUM_BACKLIGHT);
        // Start listening to system brightness changes in order to be notified in case the user
        // has the possibility to change the system brightness while the video is beeing played 
        // (with the slider in the notifications panel/settings view on some devices for instance)
        mContentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), true, mBrightnessObserver);
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        // This method is called in two cases:
        // - once at start : this is only to set the initial value of the slider, don't try to apply the brightness value.
        // - each time the user drags the slider : always apply the new brightness value.
        if (fromTouch) {
            setBrightness(progress + MINIMUM_BACKLIGHT);
            if(mSystemMode.isChecked())
                mSystemMode.setChecked(false);
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        // NA
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        // We need to write the current brightness value to the settings when it is modified 
        // so that another application like the notifications panel/settings view can stay
        // synchronized if needed. However let's only write the value when the user releases 
        // the slider in order to avoid writing too often. Other applications giving access
        // to the brightness can't be in the foreground at the same time anyway.
       // int brightness_value = mSeekBar.getProgress() + MINIMUM_BACKLIGHT;
//       Settings.System.putInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness_value);
    }

    private ContentObserver mBrightnessObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            // Retrieve the current system brightness value
            int currentBrightness;
            try {
                currentBrightness = (int) (((PlayerActivity)mContext).getWindow().getAttributes().screenBrightness*255f);
                if(currentBrightness<=0) //read global brightness setting when not set on window
                    currentBrightness = Settings.System.getInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS);
            } catch (SettingNotFoundException e) {
                currentBrightness = DEFAULT_BACKLIGHT;
            }


            // The brightness has already been applied by the system, we just need to update the slider
            mSeekBar.setProgress(currentBrightness - MINIMUM_BACKLIGHT);
        }
    };


    @Override
    public void onDetachedFromWindow() {
        Log.d(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();

        int brightness_value = mSeekBar.getProgress() + MINIMUM_BACKLIGHT;
        //Settings.System.putInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness_value);

        mContentResolver.unregisterContentObserver(mBrightnessObserver);
    }

    private void setBrightness(int brightness) {
        PlayerBrightnessManager.getInstance().setBrightness((Activity) mContext, brightness);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

            mSeekBar.setEnabled(!b);
            if(b) {
                PlayerBrightnessManager.getInstance().setBrightness((Activity) mContext, -1);
            }else{
                setBrightness(mSeekBar.getProgress() + MINIMUM_BACKLIGHT);
            }
    }
}

