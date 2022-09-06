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

package com.archos.mediacenter.video.player.tvmenu;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.AudioSpeedPickerAbstract;


public class AudioSpeedTVPicker extends AudioSpeedPickerAbstract implements TVSlaveView {

    private final String TAG = "AudioSpeedTVPicker";
    private final boolean DBG = false;

    private AudioSpeedTVPicker slaveView;
    private float mSpeed = 1.0f;
    private boolean updateText = true;
    private Context mContext;
    private int textSize = -1;

    private static final int CHANGE_SPEED = 1;
    private static final int CHANGE_SPEED_TIMEOUT = 750; // msec

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AudioSpeedTVPicker.this.handleMessage(msg);
        }
    };

    public AudioSpeedTVPicker(Context context) {
        this(context, null);
    }

    public AudioSpeedTVPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AudioSpeedTVPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        slaveView = null;

        init();
        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    public void init() {
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // TODO Auto-generated method stub
                setFocus(hasFocus);
                if (slaveView != null)
                    slaveView.setFocus(hasFocus);
            }
        });
    }

    public void setFocus(boolean hasFocus) {
        if (hasFocus) {
            this.setBackgroundResource(R.color.video_info_next_prev_button_focused);
        } else {
            this.setBackground(null);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    public void setUpdateText(boolean text) {
        this.updateText = text;
    }

    public void setHasMax(boolean max) {
        hasMax = max;
    }

    public void setHasmin(boolean min) {
        hasMin = min;
    }

    public void setTextViewWidth(int width) {
        ((TextView)findViewById(R.id.text_speed)).getLayoutParams().width = width;
        if (slaveView != null)
            slaveView.setTextViewWidth(width);
    }

    public void handleMessage(Message msg) {
        switch(msg.what) {
            case CHANGE_SPEED:
                if (mOnSpeedChangedListener != null) {
                    mOnSpeedChangedListener.onAudioSpeedChanged(this, getSpeed());
                }
                break;
        }
    }

    @Override
    public void onDetachedFromWindow() {
        mHandler.removeCallbacksAndMessages(null);
        if (mOnSpeedChangedListener != null) {
            mOnSpeedChangedListener.onAudioSpeedChanged(this, getSpeed());
        }
        super.onDetachedFromWindow();
    }

    private void changeAudioSpeed() {
        mHandler.removeMessages(CHANGE_SPEED);
        Message msg = mHandler.obtainMessage(CHANGE_SPEED);
        mHandler.sendMessageDelayed(msg, CHANGE_SPEED_TIMEOUT);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                float speed = (float) (getSpeed() + mStep - (getSpeed() + mStep) % mStep);
                if (DBG) Log.d(TAG,"(+) pressed old speed=" + getSpeed() + ", new speed=" + (hasMax && speed > mMax ? mMax : speed));
                updateNextDrawable(hasMax && speed >= mMax ? -1 : R.drawable.arrow_right_pressed);
                updatePreviousDrawable(hasMin && speed < mMin ? -1 : R.drawable.arrow_left);
                updateSpeedText(hasMax && speed > mMax ? mMax : speed);
                changeAudioSpeed();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                speed = (float) (getSpeed() - mStep - (getSpeed() - mStep) % mStep);
                if (DBG) Log.d(TAG, "(-) pressed old speed=" + getSpeed() + ", new speed=" + (hasMin && speed < mMin ? mMin : speed));
                updatePreviousDrawable(hasMin && speed <= mMin ? -1 : R.drawable.arrow_left_pressed);
                updateNextDrawable(hasMax && speed > mMax ? -1 : R.drawable.arrow_right);
                updateSpeedText(hasMin && speed < mMin ? mMin : speed);
                changeAudioSpeed();
                return true;
        }
        if (TVUtils.isOKKey(keyCode)) {
            if (!(hasMax && -getSpeed() > mMax || hasMin && -getSpeed() < mMin)) {
                updateSpeedText(-getSpeed());
                changeAudioSpeed();
            }
            return true;
        }

        // else, we send it to parent
        ViewParent p;
        View v = this;
        while ((p = v.getParent()) != null) {
            if (p instanceof TVCardView) {
                return ((TVCardView) p).onKeyDown(keyCode, event);
            } else if (p instanceof TVCardDialog) {
                return ((TVCardDialog) p).onKeyDown(keyCode, event);
            } else if (p instanceof View) {
                v = (View) p;
            } else {
                break;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                updateNextDrawable((getSpeed() < mMax || !hasMax) ? R.drawable.arrow_right : -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                updatePreviousDrawable((getSpeed() > mMin || !hasMin) ? R.drawable.arrow_left : -1);
                return true;
        }
        return TVUtils.isOKKey(keyCode);
    }

    public void updateTextViewSpeed() {
        setText(getFormattedSpeed().toString());
    }

    public void setTextSize(int size) {
        textSize = size;
        ((TextView)findViewById(R.id.text_speed)).setTextSize(size);
        if (slaveView != null)
            slaveView.setTextSize(size);
    }

    public void setText(String txt) {
        ((TextView)findViewById(R.id.text_speed)).setText(txt);
        if (slaveView != null)
            slaveView.setText(txt);
    }

    public void updateNextDrawable(int res){
        if (res > 0) {
            ((ImageView)findViewById(R.id.image_next)).setImageResource(res);
            ((ImageView)findViewById(R.id.image_next)).setVisibility(View.VISIBLE);
        } else {
            ((ImageView) findViewById(R.id.image_next)).setVisibility(View.INVISIBLE);
        }
        if (slaveView != null)
            slaveView.updateNextDrawable(res);
    }
   
    public void updatePreviousDrawable(int res) {
        if (res > 0) {
            ((ImageView)findViewById(R.id.image_previous)).setVisibility(View.VISIBLE);
            ((ImageView)findViewById(R.id.image_previous)).setImageResource(res);
        } else {
            ((ImageView) findViewById(R.id.image_previous)).setVisibility(View.INVISIBLE);
        }
        if (slaveView != null)
            slaveView.updatePreviousDrawable(res);
    }

    public void updateSpeedText(float speed) {
        super.updateSpeed(speed);
        if (updateText)
            updateTextViewSpeed();
    }
   
    @Override
    public void updateSlaveView() {
        // TODO Auto-generated method stub
    }

    @Override
    public View getSlaveView() {
        // TODO Auto-generated method stub
        return slaveView;
    }

    @Override
    public void setSlaveView(View v) {
        // TODO Auto-generated method stub
        if(v instanceof AudioSpeedTVPicker){
            slaveView = (AudioSpeedTVPicker) v;
            slaveView.setFocusable(false);
            slaveView.setText(getText());
            if (textSize != -1) {
                slaveView.setTextSize(textSize);
            }
            ((ImageView)slaveView.findViewById(R.id.image_previous)).setVisibility(((ImageView)findViewById(R.id.image_previous)).getVisibility());
            ((ImageView)slaveView.findViewById(R.id.image_next)).setVisibility(((ImageView)findViewById(R.id.image_next)).getVisibility());
            ((ImageView)slaveView.findViewById(R.id.image_previous)).setImageDrawable(((ImageView)findViewById(R.id.image_previous)).getDrawable());
            ((ImageView)slaveView.findViewById(R.id.image_next)).setImageDrawable(((ImageView) findViewById(R.id.image_next)).getDrawable());
            slaveView.setTextViewWidth(((TextView) findViewById(R.id.text_speed)).getLayoutParams().width);
        }
    }

    private float getTextSize() {
        // TODO Auto-generated method stub
        return ((TextView)findViewById(R.id.text_speed)).getTextSize();
    }

    private String getText() {
        // TODO Auto-generated method stub
        return (String) ((TextView)findViewById(R.id.text_speed)).getText();
    }

    public void init(float speed, OnAudioSpeedChangedListener onSpeedChangedListener) {
        super.init(speed, onSpeedChangedListener);
        updateNextDrawable((getSpeed() != mMax || !hasMax) ? R.drawable.arrow_right : -1);
        updatePreviousDrawable((getSpeed() != mMin || !hasMin) ? R.drawable.arrow_left : -1);
        updateSpeedText(getSpeed());
    }

    @Override
    public View createSlaveView() {
        // TODO Auto-generated method stub
        setSlaveView((AudioSpeedTVPicker) LayoutInflater.from(mContext)
                .inflate(R.layout.audio_speed_tv_picker, null));
        return slaveView;
    }

    @Override
    public void removeSlaveView() {
        // TODO Auto-generated method stub
        slaveView = null;
    }

}
