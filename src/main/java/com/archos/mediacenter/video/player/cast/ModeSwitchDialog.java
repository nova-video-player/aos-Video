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

package com.archos.mediacenter.video.player.cast;

import android.app.Dialog;
import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.archos.mediacenter.video.R;

/**
 * Created by alexandre on 04/08/16.
 */
public class ModeSwitchDialog extends AlertDialog  {


    private RadioButton mStreamButton;
    private RadioButton mRemoteDisplayButton;
    private RadioGroup mRadioGroup;
    private AlertDialog mDialog;

    protected ModeSwitchDialog(Context context) {
        super(context);
    }

    protected ModeSwitchDialog(Context context, int theme) {
        super(context, theme);
    }


    public Dialog createDialog() {
        View root = LayoutInflater.from(getContext()).inflate(R.layout.cast_mode_dialog, null);
        mStreamButton = (RadioButton) root.findViewById(R.id.cast_stream_button);
        mRadioGroup = (RadioGroup)root.findViewById(R.id.radio_group);

        mRemoteDisplayButton = (RadioButton) root.findViewById(R.id.cast_remote_button);
        if(ArchosVideoCastManager.getInstance().isRemoteDisplayConnected())
            mRemoteDisplayButton.setChecked(true);
        else
            mStreamButton.setChecked(true);
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if(i==R.id.cast_stream_button)
                    ArchosVideoCastManager.getInstance().switchToVideoCast();
                else
                    ArchosVideoCastManager.getInstance().switchToDisplayCast();
                mDialog.dismiss();
            }
        });
        mDialog = new AlertDialog.Builder(getContext()).setView(root).create();
        return mDialog;
    }

    public void show(){
        createDialog().show();
    }


}
