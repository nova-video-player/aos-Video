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
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;

import java.lang.reflect.Field;


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
        super(context, R.style.SubtitleDelayDialog);

        getWindow().setGravity(Gravity.TOP);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        mContext = context;
        mCallBack = callBack;

        View titleView = LayoutInflater.from(context).inflate(R.layout.subtitle_delay_dialog_title, null);
        setCustomTitle(titleView);
        TextView titleTextView = titleView.findViewById(R.id.dialog_title);
        if (titleTextView != null) {
            titleTextView.setText(getFormattedDelay(context, delay));
        }

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.subtitle_delay_picker_dialog, null);
        setView(view);
        mSubtitleDelayPicker = (SubtitleDelayPickerAbstract) view.findViewById(R.id.subtitleDelayPicker);
        mSubtitleDelayPicker.init(delay, this);
        // Setup Spinner
        Spinner sp = (Spinner) view.findViewById(R.id.subtitle_delay_ratio_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                mContext, R.array.subtitle_delay_ratio_array, R.layout.custom_spinner_item);
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setSelection(ratio);
        sp.setOnItemSelectedListener(this);

        // Calculate widest item width
        int maxWidth = 0;
        View itemView = null;
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        
        for (int i = 0; i < adapter.getCount(); i++) {
            itemView = adapter.getDropDownView(i, null, sp);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);
            maxWidth = Math.max(maxWidth, itemView.getMeasuredWidth());
        }

        // Set dropdown position and width after layout
        int finalMaxWidth = maxWidth;
        sp.post(new Runnable() {
            @Override
            public void run() {
                sp.setDropDownVerticalOffset(sp.getHeight());
                sp.setDropDownHorizontalOffset(0);
                sp.setDropDownWidth(finalMaxWidth + 32); // Add some padding
            }
        });
        sp.setMinimumHeight(100);

        // Disable internal spinner item ripple (MOP)
        sp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Let the ripple animation begin naturally
                        v.setPressed(true);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Let ripple finish
                        v.setPressed(false);

                        // Trigger click manually to preserve accessibility
                        v.performClick();

                        // Delay dropdown ripple removal to avoid interfering with ripple
                        for (int i = 0; i < 5; i++) {
                            final int attempt = i;
                            sp.postDelayed(() -> {
                                try {
                                    Field popupField = AppCompatSpinner.class.getDeclaredField("mPopup");
                                    popupField.setAccessible(true);
                                    Object popupWindow = popupField.get(sp);

                                    if (popupWindow != null &&
                                            popupWindow.getClass().getName().equals("androidx.appcompat.widget.AppCompatSpinner$DropdownPopup")) {

                                        Field dropDownListField = popupWindow.getClass().getSuperclass().getDeclaredField("mDropDownList");
                                        dropDownListField.setAccessible(true);
                                        ListView listView = (ListView) dropDownListField.get(popupWindow);

                                        if (listView != null) {
                                            listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
                                            Log.d("SpinnerHack", "Dropdown ripple removed (attempt " + attempt + ")");
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e("SpinnerHack", "Reflection error: " + e.getMessage());
                                }
                            }, 100 + i * 50); // Start late to avoid clashing
                        }
                        return true; // We handled the touch completely
                }
                return false;
            }
        });

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
        TextView titleView = findViewById(R.id.dialog_title);
        if (titleView != null) {
            titleView.setText(getFormattedDelay(mContext, delay));
        }
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
