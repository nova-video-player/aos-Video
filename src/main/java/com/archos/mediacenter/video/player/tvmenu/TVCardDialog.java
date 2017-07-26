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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import com.archos.mediacenter.video.R;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;


public class TVCardDialog extends FrameLayout implements TVSlaveView  {
    private int pos;
    private int originalWidth;
    private float originalX;
    private OnClickListener ocl;
    private ArrayList<View> others;
    private String text;
    private TVCardDialog slaveView;
    public final static int DESTROYED=1;
    Context mContext;
    AttributeSet attrs;
    int defStyle;
    private boolean isSlaveView;
    private OnDialogResultListener onResult;
    
    
    public interface OnDialogResultListener{
        public void onResult(int code);
    }
    
   
    public TVCardDialog(Context context) {
        super(context);
        mContext=context;
        init();

    }

    public TVCardDialog(Context context, AttributeSet attrs, int defStyle) {

        super(context, attrs, defStyle);
        mContext = context;
        this.attrs = attrs;
        this.defStyle = defStyle;
        init();
    }

    public TVCardDialog(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        this.attrs = attrs;
        this.defStyle = 0;
        init();

    }

    private void init() {
        others = new ArrayList<View>();
        pos = 0;
        originalWidth=0;
        isSlaveView=false;
        onResult=null;

        
        requestFocus();
    }

    public TVCardDialog getSlaveView(){ return slaveView; }

    public void setOnSwitchClickListener(OnClickListener ocl) {
        if (findViewById(R.id.imageView) != null)
            findViewById(R.id.imageView).setOnClickListener(ocl);
        this.ocl = ocl;
    }
    public void setSlaveView(View slaveView){
        if(slaveView instanceof TVCardDialog){
            this.slaveView=(TVCardDialog)slaveView;
            this.slaveView.setText(text);
            this.slaveView.setSlaveMasterStatus(true);
            this.slaveView.setFocusable(false);
            for(View v:others){
                if(v instanceof TVSlaveView){
                    if(((TVSlaveView)v).getSlaveView()==null)
                        ((TVSlaveView)v).createSlaveView();
                    this.slaveView.addOtherView(((TVSlaveView)v).getSlaveView());
                    
                }
            }
            ((TVScrollView)findViewById(R.id.contentScrollView)).setOnScrollListener(new TVOnScrollListener() {
                @Override
                public void onScrollChanged() {
                    // TODO Auto-generated method stub
                    ((ScrollView)TVCardDialog.this.slaveView.findViewById(R.id.contentScrollView)).setScrollY(((TVScrollView)findViewById(R.id.contentScrollView)).getScrollY());
                }
            });
        }

    }
    public void exitDialog(){
        
        if(slaveView!=null)
            slaveView.setVisibility(View.GONE);
        setVisibility(View.GONE);
        try {
            if(slaveView!=null)
                slaveView.finalize();
            this.finalize();
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //click mapping
        if (keyCode==KeyEvent.KEYCODE_DPAD_CENTER && ocl != null) {
            this.ocl.onClick(this);

            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if(onResult!=null)
                onResult.onResult(DESTROYED);
            exitDialog();
            return true;
        }
        
        //handle focus on dialog
       
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP||keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                
            View v = findFocus().focusSearch(keyCode == KeyEvent.KEYCODE_DPAD_UP?FOCUS_UP:FOCUS_DOWN);
            if(v instanceof TVMenuSeparator)
            	v=v.focusSearch(keyCode == KeyEvent.KEYCODE_DPAD_UP?FOCUS_UP:FOCUS_DOWN);
            if(isViewInCard(v))
            	v.requestFocus();
            return true;
        }
        return true;

    }

    public boolean isViewInCard(View v){
    	 ViewParent p;
    	 if (v==this)
    		 return true;
    	while(v!=null&&(p=v.getParent())!=null){
            if(p==this){
                return true;
            }
            if(p instanceof View)
                v=(View)p;
            else 
                break;
        } 
    	return false;
    	
    }
    public void addOtherView(View view) {
        others.add(view);
        ((LinearLayout) findViewById(R.id.content)).addView(view);

        if(!isSlaveView&&slaveView!=null)
            if(view instanceof TVSlaveView){
                if(((TVSlaveView)view).getSlaveView()==null)
                    ((TVSlaveView)view).createSlaveView();
                this.slaveView.addOtherView(((TVSlaveView)view).getSlaveView());
            }

    }

    public void setText(String txt) {
        this.text=txt;
        ((TextView) findViewById(R.id.info_text)).setText(txt);
        if(slaveView!=null){
            slaveView.setText(txt);
        }
    }

    public void setDrawable(Drawable d) {

        if (findViewById(R.id.imageView) != null)
            ((ImageView) findViewById(R.id.imageView)).setImageDrawable(d);
    }

    @Override
    public void setOnFocusChangeListener(final OnFocusChangeListener ofcl) {
        super.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ofcl.onFocusChange(v, hasFocus);
                if (ocl == null && others.size() > 0)
                    others.get(0).requestFocus();
            }
        });
    }


    @Override
    public boolean hasFocus() {
        if ((((ImageView) findViewById(R.id.imageView)) != null && ((ImageView) findViewById(R.id.imageView)).isFocused()))
            return true;
        if (others.size() > 0)
            for (int i = 0; i < others.size(); i++) {
                if (others.get(i).hasFocus()) {
                    return true;
                }
            }
        
        return super.hasFocus();
    }
    public void setOnDialogResultListener(OnDialogResultListener onResult){
        this.onResult=onResult;
    }

    public void setPos(int i, int offset) {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        originalWidth = lp.width;
        this.pos = i;
        setX((originalWidth + 30) * i + offset);
        this.originalX = (originalWidth + 30) * i + offset;
    }
    public float getOriginalX(){
        return originalX;
    }
    public void originalSetX(float x){
        this.originalX = x;
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        originalWidth = lp.width;
        if(slaveView!=null)
            slaveView.originalSetX(x);
        super.setX(x);
    }
    public void setPos(int i) {

        this.pos = i;

    }
    public int getPos(){
        return pos;
    }


    public void setSlaveMasterStatus(boolean isSlaveView){
        this.isSlaveView = isSlaveView;

    }

    @Override
    public void updateSlaveView() {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public View createSlaveView() {
        // TODO Auto-generated method stub
        View v = (View)LayoutInflater.from(mContext).inflate(R.layout.card_dialog_layout, null);
        TVCardDialog tvcv =(TVCardDialog) v.findViewById(R.id.card_view);

        setSlaveView(tvcv);
        return v;
    }

   
    @Override
    public void removeSlaveView() {
        // TODO Auto-generated method stub
        for(View v :others){
            if(v instanceof TVSlaveView){
                ((TVSlaveView)v).removeSlaveView();
            }
        }
        Log.d("cardview","removing");
        slaveView=null;
    }



}
