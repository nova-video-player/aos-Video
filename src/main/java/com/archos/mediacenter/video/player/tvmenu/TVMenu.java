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
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.FocusableTVCardView;

/**
 * Created by alexandre on 24/10/14.
 */
public class TVMenu extends LinearLayout implements FocusableTVCardView, TVSlaveView{
    LinearLayout ll;
    Context mContext;

    private ArrayList<View> ti;
    private int current;
    private int lastScroll;
    private OnClickListener oticl;
    private TVMenu slaveView;
    public TVMenu(Context mContext){
        super(mContext);
        this.mContext=mContext;
        ti = new ArrayList<View>();
        current=0;
        init();

    }
    public int getItemPostion(View item){
        int i = 0;
        for (View v : ti){
            
            if(v==item)
                return i;
            i++;
        }
        return -1;
    }
    public View getItem(int pos){
        if(pos<ti.size())
            return ti.get(pos);
        else 
            return null;
    }
    public void init(){
        this.oticl=null;
        this.current=0;
        this.setOrientation(LinearLayout.VERTICAL);
    }
    public TVMenu(Context context, AttributeSet attrs, int defStyle)
    {

        super(context, attrs, defStyle);
        ll.setOrientation(LinearLayout.VERTICAL);
        this.mContext=context;
        ti = new ArrayList<View>();
        current=0;
        init();

    }

    public TVMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext=context;
        ti = new ArrayList<View>();
        current=0;
        init();

    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        
    
        return false;
    }
    public void clean(){
        
        ti.clear();
        this.removeAllViews();
        if(slaveView!=null)
            slaveView.clean();
    }
    public void unCheckAll(){
        for (View v : ti){
            if(v instanceof Checkable){
                ((Checkable)v).setChecked(false);
            }
        }
    }
    public void setItems(int itemsId, int selected, boolean checkable){
        CharSequence[] txts = mContext.getResources().getTextArray(itemsId);
        int i=0;
        for(CharSequence txt:txts){
            createAndAddTVMenuItem(txt.toString(), checkable, selected==i);
            i++;
        }
    }

    public TVMenuItem createAndAddTVMenuItem(String text, boolean isFocusable){
        TVMenuItem tvmi = createAndAddTVMenuItem(text, false, false);

        if (!isFocusable) {
            tvmi.setFocusable(false);
            tvmi.findViewById(R.id.info_text).setFocusable(false);
        }

        return tvmi;
    }

    public TVMenuItem createAndAddTVMenuItem(String text, boolean isCheckable, boolean isChecked){
        View v;
        View v2;
        if(isCheckable){
            v = (View)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_item_checkable_layout, null);
        }
        else{
            v = (View)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_item_layout, null);
        }
        
        TVMenuItem tvmi = (TVMenuItem)v;
        tvmi.setText(text);
        tvmi.setChecked(isChecked);
        addTVMenuItem(tvmi);
        return tvmi;
        
    }
    public TVMenuItem createAndAddTVSwitchableMenuItem(String text,  boolean isChecked){
        View v;
        v = (View)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_item_switchable_layout, null);
       
        TVMenuItem tvmi = (TVMenuItem)v;
        tvmi.setText(text);
        tvmi.setChecked(isChecked);
        addTVMenuItem(tvmi);
        return tvmi;
    }
    public TVMenuItem createAndAddSlideTVMenuItem(String text){
        View v;
        View v2;
        
        v = (View)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_item_slide_layout, null);
            
        v2 = (View)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_item_slide_layout, null);
            
        
        
        TVMenuItem tvmi = (TVMenuItem)v;
        TVMenuItem tvmiSlave = (TVMenuItem)v2;
        tvmi.setSlaveView(tvmiSlave);
        tvmi.setText(text);

        addTVMenuItem(tvmi);
        return tvmi;
        
    }
    public View createAndAddSeparator(){
        View v;
        View v2;
        
        v = (View)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_separator_layout, null);
            
       

        addTVMenuItem(v);
        
        return v;
        
    }
    public void addTVMenuItem(View tmi){
        this.ti.add(tmi);
        this.addView(tmi);
        tmi.setOnClickListener(oticl);
        if(tmi instanceof TVSlaveView && slaveView!=null){
            if(((TVSlaveView)tmi).getSlaveView()==null)
                ((TVSlaveView)tmi).createSlaveView();
            slaveView.addTVMenuItem(((TVSlaveView)tmi).getSlaveView());
            
        }
        else if(slaveView!=null){// we add a separator
            
            View v2 = (View)LayoutInflater.from(mContext)
            .inflate(R.layout.menu_separator_layout, null);
            slaveView.addTVMenuItem(v2);
        }


    }

    public void setOnItemClickListener(OnClickListener oticl){
        this.oticl = oticl;
        for(int i=0; i<ti.size();i++ ) {
            if(ti.get(i) instanceof  TVMenuItem)
                ((TVMenuItem)ti.get(i)).setOnClickListener(oticl);
        }
    }


    @Override
    public boolean saveFocus(final View sv) {
        int i = 0;
        lastScroll=0;
        for(View t : ti ) {
            if (t.hasFocus()) {
                current = i;
                if(sv!=null)
                     lastScroll = sv.getScrollY();
                return true;
            }
            i++;
        }
        return false;
    }

    @Override
    public void restoreFocus(final ScrollView sv) {
        if(ti.size()>current) {
            sv.setScrollY(lastScroll);
            ti.get(current).requestFocus();

        }
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
        if(v instanceof TVMenu){
            slaveView = (TVMenu)v;
            slaveView.setFocusable(false);
            for(View view : ti){
                if(view instanceof TVSlaveView){
                    if(((TVSlaveView)view).getSlaveView()==null)
                        ((TVSlaveView)view).createSlaveView();
                    slaveView.addTVMenuItem(((TVSlaveView)view).getSlaveView());
                }
                else{// we add a separator
                
                    View v2 = (View)LayoutInflater.from(mContext)
                    .inflate(R.layout.menu_separator_layout, null);
                    slaveView.addTVMenuItem(v2);
                }
            }
        }
    }
    public int getCompleteHeight() {
        // TODO Auto-generated method stub
        int height = 0;
        for(View v : ti){
            if(v instanceof TVMenuItem){
                
                height+=((TVMenuItem)v).getCompleteHeight();
            }
            
        }
        return height;
    }
    @Override
    public View createSlaveView() {
        // TODO Auto-generated method stub
        //first we inflate tvmenu once again
        slaveView = (TVMenu)LayoutInflater.from(mContext)
                .inflate(R.layout.menu_layout, null);
        setSlaveView(slaveView);
        
        
        return null;
    }
    @Override
    public void removeSlaveView() {
        // TODO Auto-generated method stub
        for(View t: ti){
            if(t instanceof TVSlaveView)
                ((TVSlaveView) t).removeSlaveView();
        }
        slaveView=null;
    }
}
