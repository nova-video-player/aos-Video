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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;

import com.archos.mediacenter.video.R;


public class AudioDelayPickerDialog extends AlertDialog implements OnClickListener,
        AudioDelayPicker.OnAudioDelayChangedListener, AudioDelayPickerDialogInterface {

    private static final String TAG = "AudioDelayPickerDialog";

    private static final int CHANGE_DELAY = 1;
    private static final int CHANGE_DELAY_TIMEOUT = 750; //msec

    private final AudioDelayPickerAbstract mAudioDelayPicker;
    private final OnAudioDelayChangeListener mCallBack;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AudioDelayPickerDialog.this.handleMessage(msg);
        }
    };
    private final CheckBox mSaveSettingCB;

    public AudioDelayPickerDialog(Context context, OnAudioDelayChangeListener callBack, int delay) {
        super(context);

        getWindow().setGravity(Gravity.TOP);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        mCallBack = callBack;

        setIcon(R.drawable.ic_menu_delay);
        setTitle(R.string.player_pref_audio_delay_title);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.audio_delay_picker_dialog, null);
        setView(view);
        mAudioDelayPicker = (AudioDelayPickerAbstract) view.findViewById(R.id.audioDelayPicker);
        mSaveSettingCB = (CheckBox)view.findViewById(R.id.save_setting);
        mAudioDelayPicker.init(delay, this);

        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }
    public void onAttachedToWindow(){
        super.onAttachedToWindow();
        mSaveSettingCB.setChecked( PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(getContext().getResources().getString(R.string.save_delay_setting_pref_key), 0)!=0);

    }
    public void handleMessage(Message msg) {
        switch(msg.what) {
            case CHANGE_DELAY:
                if (mCallBack != null) {
                    mCallBack.onAudioDelayChange(mAudioDelayPicker, mAudioDelayPicker.getDelay());
                }
            break;
        }
    }

    @Override
    public void onStop() {
        mHandler.removeCallbacksAndMessages(null);
        if (mCallBack != null) {
            mCallBack.onAudioDelayChange(mAudioDelayPicker, mAudioDelayPicker.getDelay());
        }
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putInt(getContext().getResources().getString(R.string.save_delay_setting_pref_key),
                mSaveSettingCB.isChecked()?PlayerService.sPlayerService.getAudioDelay():0).commit();
    }

    @Override
    public void onAudioDelayChanged(AudioDelayPickerAbstract view, int delay) {
        mHandler.removeMessages(CHANGE_DELAY);
        Message msg = mHandler.obtainMessage(CHANGE_DELAY);
        mHandler.sendMessageDelayed(msg, CHANGE_DELAY_TIMEOUT);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mCallBack != null) {
            mCallBack.onAudioDelayChange(mAudioDelayPicker, mAudioDelayPicker.getDelay());
        }
    }

    public void updateDelay(int delay) {
        mAudioDelayPicker.updateDelay(delay);
    }

    public void setStep(int step) {
        mAudioDelayPicker.setStep(step);
    }

    public void setMin(int min) {
        mAudioDelayPicker.setMin(min);
    }

    public void setMax(int max) {
        mAudioDelayPicker.setMax(max);
    }

}
