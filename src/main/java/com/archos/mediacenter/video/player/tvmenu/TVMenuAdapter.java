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

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.tvmenu.TVCardView.onFocusOutListener;

import java.util.ArrayList;

public class TVMenuAdapter {
    private String[] mDataset;
    private Context mActivity;
    private Window mWindow;
    private FrameLayout mView;
    private FrameLayout mViewSlave;
    private ArrayList<View> cards;
    private int pos;
    private int biggest;
    private int currentOffset;
    private int startOffset;
    private View last;
    private int mGapBetweenCards;
    private onFocusOutListener ofol;
    private boolean isCreated;

    public TVMenuAdapter(  FrameLayout frameLayout, Context mActivity, Window window){
        this.isCreated=false;
        this.mActivity = mActivity;
        this.mWindow = window;
        this.mView = frameLayout;
        this.mViewSlave=null;
        this.pos=0;
        this.biggest=0;
        this.startOffset=80;
        this.currentOffset=80;
        this.mGapBetweenCards=20;
        this.cards = new ArrayList<View>();
        this.last=null;

        if(cards.size()>0)
            ((TVCardView)cards.get(0).findViewById(R.id.card_view)).requestFocus();


    }



    public TVMenu createTVMenu(){
        TVMenu tvm = (TVMenu)LayoutInflater.from(mActivity)
                .inflate(R.layout.menu_layout, null);

        return tvm;
    }
    public boolean isCreated(){
        return isCreated;
    }

    /**
     * Change focus to a card view and translate container
     * @param v2
     * @param hasFocus
     */
    private void onSlaveFocusChange(View v2, boolean hasFocus){
        Log.d("adapter","focus on "+pos);
        TVCardView tvcv = (TVCardView) v2;
        if(((TVCardView) v2).hasFocus()) {


            ((TVCardView) v2).focus(true);
            DisplayMetrics displaymetrics = new DisplayMetrics();
            mWindow.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            int width = displaymetrics.widthPixels;
            mView.animate().translationX(-((TVCardView) v2).getPositionX() + width / 2).setDuration(200);
            if(mViewSlave!=null)
                mViewSlave.animate().translationX(-((TVCardView) v2).getPositionX() + width / 2).setDuration(200);

        }

    }




    // Create a new view (invoked by the layout manager) and its slave
    public TVCardView createAndAddView(Drawable on, Drawable off, String text){
        View v = createView(on, off, text);
        addView(v, cards.size());
        return (TVCardView)v.findViewById(R.id.card_view);

    }

    public View createView(Drawable on, Drawable off, String text){
        View v = (View)LayoutInflater.from(mActivity)
                .inflate(R.layout.card_layout, null);
        ((TVCardView)v.findViewById(R.id.card_view)).setText(text);
        ((TVCardView)v.findViewById(R.id.card_view)).setOffDrawable(off);
        ((TVCardView)v.findViewById(R.id.card_view)).setOnDrawable(on);

        return v;
    }
    public void setCardViewVisibility(int visibility, TVCardView v){
        if(v.getVisibility()!=visibility){
            if(visibility == View.GONE){
                int pos=-1;
                int width=0;
                for(int i=0; i<cards.size(); i++){
                    TVCardView mcv = (TVCardView)cards.get(i).findViewById(R.id.card_view);
                    if(mcv!=null && mcv==v){
                        pos = mcv.getPos();
                        width = mcv.getOriginalWidth();
                        currentOffset-=width+mGapBetweenCards;
                        mcv.setVisibility(View.GONE);
                        if(mcv.getSlaveView()!=null)mcv.getSlaveView().setVisibility(View.GONE);
                        mcv.setFocusable(false);
                    }
                }
                if(pos>-1)
                    for(int i=0; i<cards.size(); i++){
                        TVCardView mcv = (TVCardView)cards.get(i).findViewById(R.id.card_view);
                        if(mcv!=null && mcv.getPos()>pos){
                            mcv.originalSetX(mcv.getOriginalX()-width-mGapBetweenCards);
                        }
                    }
            }
            else if(visibility == View.VISIBLE){
                int pos=-1;
                int width=0;
                for(int i=0; i<cards.size(); i++){
                    TVCardView mcv = (TVCardView)cards.get(i).findViewById(R.id.card_view);
                    if(mcv!=null && mcv==v){
                        pos = mcv.getPos();
                        width = mcv.getOriginalWidth();
                        currentOffset+=width+mGapBetweenCards;
                        mcv.setVisibility(View.VISIBLE);
                        mcv.setFocusable(true);
                    }
                }
    
                if(pos>-1)
                    for(int i=0; i<cards.size(); i++){
                        TVCardView mcv = (TVCardView)cards.get(i).findViewById(R.id.card_view);
                        if(mcv!=null && mcv.getPos()>pos){
                            mcv.originalSetX(mcv.getOriginalX()+width+mGapBetweenCards);
                        }
                    }
            }
        }
    }
    public void addView(final View v, int pos) {
        isCreated=true;
        TVCardView mcv = (TVCardView)v.findViewById(R.id.card_view);
        if(this.ofol!=null)
            mcv.setOnFocusOutListener(this.ofol);
        // set the view's size, margins, paddings and layout parameters

        mcv.setFocusable(true);
        mcv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v2, boolean hasFocus) {
                onSlaveFocusChange( v2, hasFocus);
            }
        });
        mcv.setPos(pos);
        mcv.originalSetX(currentOffset+mGapBetweenCards);

        currentOffset+=mcv.getOriginalWidth()+mGapBetweenCards;
        //grow mView
        int newWidth;
        android.view.ViewGroup.LayoutParams lp = mView.getLayoutParams();
        newWidth = lp.width+mcv.getOriginalWidth();

        if(mcv.getOriginalWidth()>biggest)
            newWidth+=mcv.getOriginalWidth()-biggest; //for last zoom effect   ;

        lp.width=newWidth;
        if(mViewSlave!=null){
            lp = mViewSlave.getLayoutParams();
            lp.width = newWidth;
        }
        mView.addView(v);       
        if(mViewSlave!=null){
            //we check if carview has a slave view
            if(mcv.getSlaveView()==null)
                mcv.createSlaveView();
            //then we add it on slave view
            mViewSlave.addView(mcv.getSlaveView().getParentView());


        }
        cards.add(v);

        last = mcv;

    }



    public void goToNext(){
        if(pos<cards.size()-1) {
            int oldPos = pos;


            pos++;
            while((cards.get(pos).findViewById(R.id.card_view)==null || !cards.get(pos).findViewById(R.id.card_view).isFocusable())&&pos<cards.size()-1)
                pos++;
            if(cards.get(pos).findViewById(R.id.card_view).isFocusable()){
                ((TVCardView)cards.get(oldPos).findViewById(R.id.card_view)).focus(false);
                cards.get(pos).requestFocus();
            }
            else
                pos=oldPos;
        }
    }

    public void goToPrevious(){
        if(pos>0) {
            int oldPos = pos;

            pos--;
            while((cards.get(pos).findViewById(R.id.card_view)==null || !cards.get(pos).findViewById(R.id.card_view).isFocusable())&&pos>0)
                pos--;
            if(cards.get(pos).findViewById(R.id.card_view).isFocusable()){
                ((TVCardView)cards.get(oldPos).findViewById(R.id.card_view)).focus(false);
                cards.get(pos).requestFocus();
            }
            else
                pos=oldPos;



        }
    }

    public void setOnFocusOutListener(onFocusOutListener ofol){
        this.ofol=ofol;
    }
    public void focusEnd(){
        if(cards.size()>pos)
            cards.get(pos).clearFocus();
    }
    public void setDiscrete(boolean state){
        if(!state)
            ((TVCardView)cards.get(pos).findViewById(R.id.card_view)).restoreFocus(null);
        else
            ((TVCardView)cards.get(pos).findViewById(R.id.card_view)).saveFocus(null);
        DisplayMetrics displaymetrics = new DisplayMetrics();
        mWindow.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        final int height = displaymetrics.heightPixels;
        YAnimation a = new YAnimation(state?height:-height,true);           
        a.setDuration(500);
        mView.startAnimation(a);


    }
    public void focusStart() {
        // TODO Auto-generated method stub
        //will make tv menu appears from bottom to the middle of the screen
        DisplayMetrics displaymetrics = new DisplayMetrics();
        mWindow.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        final int width = displaymetrics.heightPixels;
        mView.setY(width);
        if(mViewSlave!=null)
            mViewSlave.setY(width);
        YAnimation a = new YAnimation(width,true);
        a.setDuration(400);
        mView.startAnimation(a);
        if(cards.size()>pos&&!cards.get(pos).hasFocus()){
            while((cards.get(pos).findViewById(R.id.card_view)==null || !cards.get(pos).findViewById(R.id.card_view).isFocusable())&&pos<cards.size()-1)
                pos++;
            cards.get(pos).requestFocus();
        }
    }
    
    public void hideAnimation() {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        mWindow.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        final int width = displaymetrics.heightPixels;
        YAnimation a = new YAnimation(-width, false);
        
        a.setDuration(400);
        mView.startAnimation(a);
    }
    
    public void refocus(){
        
        if(cards!=null&& cards.size()>0&&pos>=0&&pos<cards.size()){
            if(cards.get(pos).findViewById(R.id.card_view)!=null && cards.get(pos).findViewById(R.id.card_view) instanceof TVCardView){
                View v2 = cards.get(pos).findViewById(R.id.card_view);
                DisplayMetrics displaymetrics = new DisplayMetrics();
                mWindow.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
                int width = displaymetrics.widthPixels;
                mView.setX(-((TVCardView) v2).getPositionX() + width / 2);
                if(mViewSlave!=null)
                    mViewSlave.setX(-((TVCardView) v2).getPositionX() + width / 2);
            }
            cards.get(pos).requestFocus();
            
            
        }
    }
    public void initializeSlaveView(FrameLayout v){

        mViewSlave=v;
        //iterate over views to create slave views, add them to new slave view
        for(View view : cards){
            if(view.findViewById(R.id.card_view)!=null ){
                TVCardView tvcv = (TVCardView)view.findViewById(R.id.card_view);
                if(tvcv.getSlaveView()==null){
                    tvcv.createSlaveView();
                    Log.d("adapter"," view created");
                }
                mViewSlave.addView(tvcv.getSlaveView().getParentView());

            }

        }
        android.view.ViewGroup.LayoutParams lp = mView.getLayoutParams();
        android.view.ViewGroup.LayoutParams lp2 = mViewSlave.getLayoutParams();
        lp2.width = lp.width;


    }
    public boolean hasSlaverView(){

        return mViewSlave!=null;
    }
    public void removeSlaveViews(){
        for(View view : cards){
            if(view.findViewById(R.id.card_view)!=null ){
                TVCardView tvcv = (TVCardView)view.findViewById(R.id.card_view);
                tvcv.removeSlaveView();   
            }
        }
        mViewSlave=null;
    }
    private class YAnimation extends Animation {
        private final int width;
        private final boolean bringToFront;
        private final float originalY;
        private float[] originalAlpha;
        public YAnimation(int width, boolean bringToFront) {
            this.width=width;
            this.originalY = mView.getY();
            this.bringToFront = bringToFront;
            this.originalAlpha = new float[cards.size()]; 
            int i=0;
            for (View v : cards){     
                if(v.findViewById(R.id.card_view)!=null)
                    this.originalAlpha[i]= v.findViewById(R.id.card_view).getAlpha();
                else 
                    this.originalAlpha[i]=1;
                i++;
            }

        }
        @Override
        protected void applyTransformation(float interpolatedTime,
                Transformation t) {
            mView.setY(originalY -width*interpolatedTime);
            if(bringToFront)
                mView.bringToFront();
            if(mViewSlave!=null){
                mViewSlave.setY(originalY -width*interpolatedTime);
                if(bringToFront)
                    mViewSlave.bringToFront();
            }
        }

        @Override
        public boolean willChangeBounds() {
            // TODO Auto-generated method stub
            return false;
        }
    }
    public void setFocusable(boolean b) {
        // TODO Auto-generated method stub
        mView.setFocusable(b);
    }


}
