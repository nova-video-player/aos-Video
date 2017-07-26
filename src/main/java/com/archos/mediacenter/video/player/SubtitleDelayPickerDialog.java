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
import com.archos.mediacenter.video.info.VideoInfoCommonClass;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;


/**
 * A simple dialog containing an {@link android.widget.SubtitleDelayPicker}.
 */
public class SubtitleDelayPickerDialog extends AlertDialog implements OnClickListener,
        SubtitleDelayPicker.OnDelayChangedListener, OnItemSelectedListener, SubtitleDelayPickerDialogInterface{

    private static final int CHANGE_DELAY = 1;
    private static final int CHANGE_DELAY_TIMEOUT = 750; //msec
    private static final String TAG = "SubtitleDelayDlg";

    private final SubtitleDelayPickerAbstract mSubtitleDelayPicker;
    private final OnDelayChangeListener mCallBack;
    private final Context mContext;
    private int mRatio = 0;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            SubtitleDelayPickerDialog.this.handleMessage(msg);
        }
    };



 

    public SubtitleDelayPickerDialog(Context context, OnDelayChangeListener callBack, int delay, int ratio, boolean hasRatio) {
        super(context);

        getWindow().setGravity(Gravity.TOP);
        getWindow().setBackgroundDrawable(new ColorDrawable(VideoInfoCommonClass.getAlphaColor(ContextCompat.getColor(context, R.color.background_material_dark),128)));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        mContext = context;
        mCallBack = callBack;

        setIcon(R.drawable.ic_menu_delay);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.subtitle_delay_picker_dialog, null);
        setView(view);
        mSubtitleDelayPicker = (SubtitleDelayPickerAbstract) view.findViewById(R.id.subtitleDelayPicker);
        mSubtitleDelayPicker.init(delay, this);
        // Setup Spinner
        Spinner sp = (Spinner) view.findViewById(R.id.subtitle_delay_ratio_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                mContext, R.array.subtitle_delay_ratio_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setSelection(ratio);
        sp.setOnItemSelectedListener(this);

        updateTitle(delay);

        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    /*package*/ public void handleMessage(Message msg) {
        switch(msg.what) {
            case CHANGE_DELAY:
                if (mCallBack != null) {
                    mCallBack.onDelayChange(mSubtitleDelayPicker, mSubtitleDelayPicker.getDelay(), mRatio);
                }
            break;
        }
    }

    @Override
    public void onStop() {
        mHandler.removeCallbacksAndMessages(null);
        if (mCallBack != null) {
            mCallBack.onDelayChange(mSubtitleDelayPicker, mSubtitleDelayPicker.getDelay(), mRatio);
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mCallBack != null) {
            mCallBack.onDelayChange(mSubtitleDelayPicker, mSubtitleDelayPicker.getDelay(), mRatio);
        }
    }

    public void onDelayChanged(SubtitleDelayPickerAbstract view, int delay) {
        updateTitle(delay);
        mHandler.removeMessages(CHANGE_DELAY);
        Message msg = mHandler.obtainMessage(CHANGE_DELAY);
        mHandler.sendMessageDelayed(msg, CHANGE_DELAY_TIMEOUT);
    }

    public void updateDelay(int delay) {
        mSubtitleDelayPicker.updateDelay(delay);
        updateTitle(delay);
    }

    public static CharSequence getFormattedDelay(Context context, int delay) {
        int sign = delay >= 0 ? 1 : -1;
        delay = Math.abs(delay);
        int minute = (int) delay / 60 / 1000;
        float second = (float) (delay % (60 * 1000)) / 1000;

        return (sign == 1 ? "  " : "- ") +
               minute + " m " +
               second + " s";
    }

    private void updateTitle(int delay) {
        setTitle(getFormattedDelay(mContext, delay));
    }

    /* (non-Javadoc)
     * @see android.widget.AdapterView.OnItemSelectedListener#onItemSelected(android.widget.AdapterView, android.view.View, int, long)
     * Ratio Spinner Callback
     */
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // unless we do some magic tricks position/id would both work
        mRatio = position;
    }

    /* (non-Javadoc)
     * @see android.widget.AdapterView.OnItemSelectedListener#onNothingSelected(android.widget.AdapterView)
     * Ratio Spinner Callback
     */
    public void onNothingSelected(AdapterView<?> parent) {
        // No selection, Nothing to do.
    }

  

   
}
