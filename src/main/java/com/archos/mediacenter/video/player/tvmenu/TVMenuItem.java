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
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import com.archos.mediacenter.video.R;

/**
 * Created by alexandre on 24/10/14.
 */
public class TVMenuItem extends LinearLayout implements Checkable, TVSlaveView{
    private boolean isChecked;

    private String text;



    private OnClickListener ocl;
    private TVMenuItem slaveView;

    private Context mContext;
    public TVMenuItem(Context context, AttributeSet attrs, int defStyle)
    {

        super(context, attrs, defStyle);
        this.mContext = context;
        init();

    }

    public TVMenuItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        init();
    }
    public  TVMenuItem(Context context){
        super(context);
        this.mContext = context;
        init();
    }
    public void init(){
        this.text="";
        this.slaveView=null;
        isChecked=false;
        setOnFocusChangeListener(new OnFocusChangeListener() {   
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // TODO Auto-generated method stub
                setFocus(hasFocus);
                if(slaveView!=null)
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
    public void setOnClickListener(OnClickListener ocl){
        this.ocl = ocl;
        (findViewById(R.id.info_text)).setOnClickListener(ocl);
    }
    @Override
    public void setChecked(boolean checked) {
        if(slaveView!=null)
            slaveView.setChecked(checked);
        if(findViewById(R.id.info_text)!=null && findViewById(R.id.info_text) instanceof Checkable)
            ((Checkable)findViewById(R.id.info_text)).setChecked(checked);
    }

    public boolean isChecked(){
        if(findViewById(R.id.info_text)!=null && findViewById(R.id.info_text) instanceof Checkable)
            return ((Checkable)findViewById(R.id.info_text)).isChecked();
        return false;
    }

    @Override
    public void toggle() {
      
        if(findViewById(R.id.info_text)!=null && findViewById(R.id.info_text) instanceof Checkable){
            setChecked(!((Checkable)(findViewById(R.id.info_text))).isChecked());
        }
    
    }

    public void setText(String text){
        this.text=text;
        ((TextView) findViewById(R.id.info_text)).setText(this.text);
        if(slaveView!=null)
            slaveView.setText(text);
    }
    public String getText() { return text;}



    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event){
        //click mapping
        if(TVUtils.isOKKey(keyCode) &&ocl!=null) {
            this.ocl.onClick(this);
            return true;
        }
        return false;
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        //click mapping   
        if(TVUtils.isOKKey(keyCode) &&ocl!=null) {
            return true;
        }
        if (this.getParent() != null && this.getParent() instanceof TVMenu) {
            
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
               
        }
        return false;
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
        if(v instanceof TVMenuItem){
            slaveView = (TVMenuItem)v;
            slaveView.setText(text);
            slaveView.setFocusable(false);
            slaveView.setChecked(isChecked());
            slaveView.setVisibility(getVisibility());
        }
    }

    public int getCompleteHeight() {
        // TODO Auto-generated method stub
        return ((TextView)findViewById(R.id.info_text)).getLineCount()*((TextView)findViewById(R.id.info_text)).getLineHeight();
    }

    @Override
    public View createSlaveView() {
        // TODO Auto-generated method stub
        TVMenuItem slaveView;
        if(findViewById(R.id.info_text)!=null && findViewById(R.id.info_text) instanceof RadioButton){
            slaveView = (TVMenuItem)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_item_checkable_layout, null);
        }
        else if(findViewById(R.id.info_text)!=null && findViewById(R.id.info_text) instanceof Switch){
            slaveView = (TVMenuItem)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_item_switchable_layout, null);
        }
        else{
            slaveView = (TVMenuItem)(View)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_item_layout, null);
        }
        setSlaveView(slaveView);
        return slaveView;
    }

    @Override
    public void removeSlaveView() {
        // TODO Auto-generated method stub
        slaveView=null;
    }
}
