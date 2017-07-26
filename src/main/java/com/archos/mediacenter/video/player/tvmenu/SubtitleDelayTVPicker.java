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

package com.archos.mediacenter.video.player.tvmenu;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.SubtitleDelayPickerAbstract;
import com.archos.mediacenter.video.player.SubtitleDelayPickerDialog;


public class SubtitleDelayTVPicker extends SubtitleDelayPickerAbstract implements TVSlaveView {

    /* UI Components */

    /**
     * How we notify users the date has changed.
     */
 
    private SubtitleDelayTVPicker slaveView;
    private int mStep=1;
    private boolean mHourFormat=false; 
    private int mMin=0;
    private int mMax=0;
    private boolean hasMin=false;
    private boolean hasMax=false;
    private boolean updateText=true;
    private boolean mLeftIsDown; // used for key repetition to increase steps
    private boolean mRightIsDown;
    private Context mContext;
    private int textSize=-1;
    private final static int INCREASE_STEPS = 0;
    /**
     * The callback used to indicate the user changes the date.
     */

    Handler mHandler = new Handler(){
        public void handleMessage(Message msg){
            if(msg.what==INCREASE_STEPS&&mLeftIsDown||mRightIsDown){
                removeMessages(INCREASE_STEPS);
                mStep = msg.arg1!=0?mStep+msg.arg1:msg.arg2!=0?mOriginalStep*msg.arg2:0;
                if(mStep<3*mOriginalStep)
                     mHandler.sendMessageDelayed(mHandler.obtainMessage(INCREASE_STEPS, msg.arg1, 0), 2000);
                else if(mStep==3*mOriginalStep)
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(INCREASE_STEPS, 0, 10), 3000);
                else if(mStep==10*mOriginalStep)
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(INCREASE_STEPS, 0, 100), 6000);
            }
        }
    };
    private int mOriginalStep = 1;

    public SubtitleDelayTVPicker(Context context) {
        this(context, null);
        mContext=context;
    }

    public SubtitleDelayTVPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        mContext=context;
    }
    public void setUpdateText(boolean t){
        this.updateText=t;
    }
    public SubtitleDelayTVPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext=context;
        slaveView=null;
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

   
        init();
        if (!isEnabled()) {
            setEnabled(false);
        }
    }

   public void init(){
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
    public void setFocus(boolean hasFocus){
        if(hasFocus){
     
            this.setBackgroundResource(R.color.video_info_next_prev_button_focused);
        }
        else
            this.setBackgroundDrawable(null);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
      
    }
    public void setMin(int min){
        hasMin=true;
        mMin=  min-min% mStep;
        
    }
    public void setMax(int max){
        
        hasMax=true;
        mMax= (int) (max-Math.IEEEremainder(max, mStep));
    }
    public void setHasMax(boolean max){
        hasMax=max;
    }
    public void setHasmin(boolean min){
        hasMin=min;
    }
    public void setTextViewWidth(int width){
        ((TextView)findViewById(R.id.text_delay)).getLayoutParams().width = width;
        if(slaveView!=null)
            slaveView.setTextViewWidth(width);
    }
    @Override
    public boolean   onKeyDown(int keyCode,  KeyEvent event){
        switch(keyCode){
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                int delay = (int) (getDelay()+mStep-(getDelay()+mStep)%mStep);
                updateNextDrawable(hasMax && delay >= mMax ? -1 : R.drawable.arrow_right_pressed);
                updatePreviousDrawable(hasMin&&delay<mMin?-1:R.drawable.arrow_left);
                updateDelay(hasMax&&delay>mMax?mMax:delay);
                if(!mRightIsDown) {
                    mRightIsDown = true;
                    mHandler.removeMessages(INCREASE_STEPS);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(INCREASE_STEPS, mOriginalStep, 0), 2000);
                }
                if(mOnDelayChangedListener!=null)
                    mOnDelayChangedListener.onDelayChanged(this, getDelay());
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                delay = (int) (getDelay()-mStep-(getDelay()-mStep)%mStep);

                updatePreviousDrawable(hasMin && delay <= mMin ? -1 : R.drawable.arrow_left_pressed);
                updateNextDrawable(hasMax&&delay>mMax?-1:R.drawable.arrow_right);
                if(!mLeftIsDown) {
                    mLeftIsDown = true;
                    mHandler.removeMessages(INCREASE_STEPS);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(INCREASE_STEPS, mOriginalStep, 0), 2000);
                }

                updateDelay(hasMin && delay < mMin ? mMin : delay);
                if(mOnDelayChangedListener!=null)
                    mOnDelayChangedListener.onDelayChanged(this, getDelay());
                return true;
                  
                
   
                
               
        }
        if(TVUtils.isOKKey(keyCode)){
            if(!(hasMax&& -getDelay()>mMax||hasMin&& -getDelay()<mMin)){
                updateDelay(-getDelay());
                if(mOnDelayChangedListener!=null)
                    mOnDelayChangedListener.onDelayChanged(this, getDelay());
                
            }
            return true;
        }
               
        //else, we send it to parent
        ViewParent p;
        View v = this;
        while((p=v.getParent())!=null){
            if(p instanceof TVCardView)
                return ((TVCardView)p).onKeyDown(keyCode, event);
            else if(p instanceof TVCardDialog)
                return ((TVCardDialog)p).onKeyDown(keyCode, event);
            else if(p instanceof View)
                v=(View)p;
            else 
                break;
        }
        return false;
        
    }
    @Override
    public boolean   onKeyUp(int keyCode,  KeyEvent event){
        switch(keyCode){
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                updateNextDrawable((getDelay() < mMax || !hasMax) ? R.drawable.arrow_right:-1);
                if(mRightIsDown) {
                    mRightIsDown = false;
                    mHandler.removeMessages(INCREASE_STEPS);
                    mStep = mOriginalStep;
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                updatePreviousDrawable((getDelay()>mMin||!hasMin)?R.drawable.arrow_left:-1);
                if(mLeftIsDown) {
                    mLeftIsDown = false;
                    mHandler.removeMessages(INCREASE_STEPS);
                    mStep = mOriginalStep;
                }
                return true;
        }
        return TVUtils.isOKKey(keyCode);
        
    }
   public void updateTextViewDelay(int delay){
       
       setText(mHourFormat? SubtitleDelayPickerDialog.getFormattedDelay(mContext, delay).toString():""+(delay/100));
       
   }
   public void setTextSize(int size){
       textSize=size;
       ((TextView)findViewById(R.id.text_delay)).setTextSize(size);
       if(slaveView!=null)
           slaveView.setTextSize(size);
   }

    public void setTextColor(int color){
        ((TextView)findViewById(R.id.text_delay)).setTextColor(color);
        if(slaveView!=null)
            slaveView.setTextColor(color);
    }
   public void setText(String txt){
       ((TextView)findViewById(R.id.text_delay)).setText(txt);
       if(slaveView!=null)
           slaveView.setText(txt);
   }
   public void updateNextDrawable(int res){
       if(res>0){
           ((ImageView)findViewById(R.id.image_next)).setImageResource(res);
           ((ImageView)findViewById(R.id.image_next)).setVisibility(View.VISIBLE);
       }
       else
           ((ImageView)findViewById(R.id.image_next)).setVisibility(View.INVISIBLE);
       if(slaveView!=null)
           slaveView.updateNextDrawable(res);

   }
   
   public void updatePreviousDrawable(int res){
       if(res>0){
           ((ImageView)findViewById(R.id.image_previous)).setVisibility(View.VISIBLE);
           ((ImageView)findViewById(R.id.image_previous)).setImageResource(res);
           
       }
       else
           ((ImageView)findViewById(R.id.image_previous)).setVisibility(View.INVISIBLE);
       if(slaveView!=null)
           slaveView.updatePreviousDrawable(res);

   }
   @Override
   public void updateDelay(int delay) {
       super.updateDelay(delay);
       if(updateText)
           updateTextViewDelay(delay);
   }
   
    @Override
    public void updateSlaveView() {
        // TODO Auto-generated method stub
        
    }
    public void setStep(int step){
        mStep=step*100;
        mOriginalStep = mStep;
    }
    public void setHourFormat(boolean h){
        mHourFormat=h;
    }
    @Override
    public View getSlaveView() {
        // TODO Auto-generated method stub
        return slaveView;
    }

    @Override
    public void setSlaveView(View v) {
        // TODO Auto-generated method stub
        if(v instanceof SubtitleDelayTVPicker){
            slaveView = (SubtitleDelayTVPicker) v;
            slaveView.setFocusable(false);
            slaveView.setText(getText());
            if(textSize!=-1)
            slaveView.setTextSize(textSize);
            ((ImageView)slaveView.findViewById(R.id.image_previous)).setVisibility(((ImageView)findViewById(R.id.image_previous)).getVisibility());
            ((ImageView)slaveView.findViewById(R.id.image_next)).setVisibility(((ImageView)findViewById(R.id.image_next)).getVisibility());
            ((ImageView)slaveView.findViewById(R.id.image_previous)).setImageDrawable(((ImageView)findViewById(R.id.image_previous)).getDrawable());
            ((ImageView)slaveView.findViewById(R.id.image_next)).setImageDrawable(((ImageView)findViewById(R.id.image_next)).getDrawable());
            slaveView.setTextViewWidth(((TextView)findViewById(R.id.text_delay)).getLayoutParams().width);

        }
        
    }
    private float getTextSize() {
        // TODO Auto-generated method stub
        return ((TextView)findViewById(R.id.text_delay)).getTextSize();
    }

    private String getText() {
        // TODO Auto-generated method stub
        return (String) ((TextView)findViewById(R.id.text_delay)).getText();
    }

    @Override
    public void init(int delay, OnDelayChangedListener onDelayChangedListener) {
        super.init(delay, onDelayChangedListener);
        updateNextDrawable((getDelay()!=mMax||!hasMax)?R.drawable.arrow_right:-1);
        updatePreviousDrawable((getDelay()!=mMin||!hasMin)?R.drawable.arrow_left:-1);
    }

    @Override
    public View createSlaveView() {
        // TODO Auto-generated method stub
    
        setSlaveView((SubtitleDelayTVPicker)LayoutInflater.from(mContext)
                .inflate(R.layout.subtitle_delay_tv_picker, null));
        return slaveView;
    }

    @Override
    public void removeSlaveView() {
        // TODO Auto-generated method stub
        slaveView=null;
    }

   
}
