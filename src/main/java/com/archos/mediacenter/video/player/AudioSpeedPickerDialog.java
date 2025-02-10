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
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;


public class AudioSpeedPickerDialog extends AlertDialog implements OnClickListener,
        AudioSpeedPicker.OnAudioSpeedChangedListener, AudioSpeedPickerDialogInterface {

    private static final String TAG = "AudioSpeedPickerDialog";

    private static final int CHANGE_SPEED = 1;
    private static final int CHANGE_SPEED_TIMEOUT = 750; //msec

    private final AudioSpeedPickerAbstract mAudioSpeedPicker;
    private final OnAudioSpeedChangeListener mCallBack;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AudioSpeedPickerDialog.this.handleMessage(msg);
        }
    };
    private final CheckBox mSaveSettingCB;

    public AudioSpeedPickerDialog(Context context, OnAudioSpeedChangeListener callBack, float speed) {
        super(context);

        getWindow().setGravity(Gravity.TOP);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        mCallBack = callBack;

        setIcon(R.drawable.ic_baseline_speed_24);
        setTitle(R.string.player_pref_audio_speed_title);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.audio_speed_picker_dialog, null);
        setView(view);
        mAudioSpeedPicker = (AudioSpeedPickerAbstract) view.findViewById(R.id.audioSpeedPicker);
        mSaveSettingCB = (CheckBox)view.findViewById(R.id.save_setting);
        mAudioSpeedPicker.init(speed, this);

        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }
    public void onAttachedToWindow(){
        super.onAttachedToWindow();
        mSaveSettingCB.setChecked( PreferenceManager.getDefaultSharedPreferences(getContext()).getFloat(getContext().getResources().getString(R.string.save_audio_speed_setting_pref_key), 1.0f)!=1.0f);

    }
    public void handleMessage(Message msg) {
        switch(msg.what) {
            case CHANGE_SPEED:
                if (mCallBack != null) {
                    mCallBack.onAudioSpeedChange(mAudioSpeedPicker, mAudioSpeedPicker.getSpeed());
                }
            break;
        }
    }

    @Override
    public void onStop() {
        mHandler.removeCallbacksAndMessages(null);
        if (mCallBack != null) {
            mCallBack.onAudioSpeedChange(mAudioSpeedPicker, mAudioSpeedPicker.getSpeed());
        }
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putFloat(getContext().getResources().getString(R.string.save_audio_speed_setting_pref_key),
                mSaveSettingCB.isChecked()?PlayerService.sPlayerService.getAudioSpeed():1.0f).commit();
    }

    @Override
    public void onAudioSpeedChanged(AudioSpeedPickerAbstract view, float speed) {
        mHandler.removeMessages(CHANGE_SPEED);
        Message msg = mHandler.obtainMessage(CHANGE_SPEED);
        mHandler.sendMessageDelayed(msg, CHANGE_SPEED_TIMEOUT);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mCallBack != null) {
            mCallBack.onAudioSpeedChange(mAudioSpeedPicker, mAudioSpeedPicker.getSpeed());
        }
    }

    public void updateSpeed(float speed) {
        mAudioSpeedPicker.updateSpeed(speed);
    }

    public void setStep(float step) {
        mAudioSpeedPicker.setStep(step);
    }

    public void setMin(float min) {
        mAudioSpeedPicker.setMin(min);
    }

    public void setMax(float max) {
        mAudioSpeedPicker.setMax(max);
    }

}
