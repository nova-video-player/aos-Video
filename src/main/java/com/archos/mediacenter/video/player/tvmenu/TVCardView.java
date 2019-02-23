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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;

import com.archos.environment.ArchosFeatures;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.FocusableTVCardView;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/*
 * 
 * Class used for TV 
 * must handle animation (growing of cards, rescaling, transparency)
 * 
 */
public class TVCardView extends FrameLayout implements Checkable, FocusableTVCardView, TVSlaveView {
    private static final float MIN_FOCUSED_CARDVIEW_HEIGHT_COEFF = (float) 1.1;
    private int pos;
    private int originalWidth;
    private int originalHeight;
    private float originalX;
    private Drawable on;
    private Drawable off;
    private OnClickListener ocl;
    private boolean isChecked;
    private ArrayList<View> others;
    private int lastFocused;
    private String text;
    private TVCardView slaveView;
    private Context mContext;
    private AttributeSet attrs;
    private int defStyle;
    private final int minAlpha = 122;
    private onFocusOutListener ofol;
    private View parentView;
    public interface onFocusOutListener{
        public boolean onFocusOut(int keyCode);
    }
    public TVCardView(Context context) {
        super(context);
        mContext=context;
        init();

    }

    public TVCardView(Context context, AttributeSet attrs, int defStyle) {

        super(context, attrs, defStyle);
        mContext = context;
        this.attrs = attrs;
        this.defStyle = defStyle;
        init();
    }

    public TVCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        this.attrs = attrs;
        this.defStyle = 0;
        init();

    }

    private void init() {
        this.isChecked = false;
        this.others = new ArrayList<View>();
        this.lastFocused = 0;
        this.pos = 0;
        this.originalWidth=0;
        this.originalHeight=0;
        this.ofol=null;
        if(Build.VERSION.SDK_INT>=21)
            setAlpha((float) (minAlpha/255.0));
        else
            getBackground().setAlpha(minAlpha);
    }
    //dimensions for animations

    public TVCardView getSlaveView(){return slaveView;}
    public int getCurrentWidth() {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        return lp.width;
    }

    public int getCurrentHeight() {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        return lp.height;
    }

    public int getImageViewCurrentWidth() {
        View v = (View) findViewById(R.id.topView);
        if (v != null) {
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            return lp.width > 0 ? lp.width : getCurrentWidth();
        }
        return 0;
    }

    public int getImageViewCurrentHeight() {
        View v = (View) findViewById(R.id.topView);
        if (v != null) {
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            return lp.height > 0 ? lp.height : getCurrentHeight();
        }
        return 0;
    }

    public int getOriginalWidth() {
        if(originalWidth==0)
            originalWidth=getCurrentWidth();

        return originalWidth;
    }
    public void setOriginalWidth(int w) {
        if(originalHeight==0)
            originalHeight=getCurrentHeight();
        originalWidth=w;
    }
    public void setOriginalHeight(int h) {
        originalHeight=h;
    }
    public void setOriginalX(float X) {
        originalX=X;
    }
    public float getPositionX() {
        return getX() + (getCurrentWidth() / 2);
    }


    //Switch button


    public void setOnDrawable(Drawable on) {
        this.on = on;
        updateDrawable();
        if(slaveView!=null)
            slaveView.setOnDrawable(on);
    }

    public void setOffDrawable(Drawable off) {
        this.off = off;
        updateDrawable();
        if(slaveView!=null)
            slaveView.setOffDrawable(off);
    }


    @Override
    public void setChecked(boolean checked) {
        isChecked = checked;
        updateDrawable();
        if(slaveView!=null)
            slaveView.setChecked(checked);
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        isChecked = !isChecked;
        setChecked(isChecked);
    }

    private void updateDrawable() {

        setDrawable(isChecked && this.on != null ? this.on : this.off != null ? this.off : this.on);

    }

    public void setOnSwitchClickListener(OnClickListener ocl) {
        if (findViewById(R.id.imageView) != null)
            findViewById(R.id.imageView).setOnClickListener(ocl);
        this.ocl = ocl;
    }
    public void setOnFocusOutListener(onFocusOutListener ofol){
        this.ofol = ofol;
    }
    @Override
    public void setSlaveView(View slaveView){
        if(slaveView instanceof TVCardView){
            this.slaveView=(TVCardView) slaveView;
            //attributes
            this.slaveView.setX(getX());
            this.slaveView.setScaleX(getScaleX());
            this.slaveView.setScaleY(getScaleY());
            this.slaveView.setOffDrawable(off);
            this.slaveView.setOnDrawable(on);
            this.slaveView.setText(text);
            this.slaveView.setVisibility(getVisibility());
            this.slaveView.setOriginalWidth(originalWidth);
            this.slaveView.setOriginalHeight(originalHeight);
            this.slaveView.setOriginalX(originalX);
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
                    if(TVCardView.this.slaveView!=null)
                        ((ScrollView)TVCardView.this.slaveView.findViewById(R.id.contentScrollView)).setScrollY(((TVScrollView)findViewById(R.id.contentScrollView)).getScrollY());
                }
            });
        }

    }
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event){
        //click mapping   
        if(TVUtils.isOKKey(keyCode) &&ocl!=null) {
            return true;
        }
        return false;
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //click mapping
        if(TVUtils.isOKKey(keyCode) &&ocl!=null) {
            this.ocl.onClick(this);
            return true;
        }
        //handle focus on dialog
        else if ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN||keyCode == KeyEvent.KEYCODE_DPAD_UP)&&this.ofol!=null) {
            View v = findFocus().focusSearch(keyCode == KeyEvent.KEYCODE_DPAD_DOWN?FOCUS_DOWN:FOCUS_UP);
            if(v instanceof TVMenuSeparator){
            	v = v.focusSearch(keyCode == KeyEvent.KEYCODE_DPAD_DOWN?FOCUS_DOWN:FOCUS_UP);          	
            }
            if(isViewInCard(v)){
            	v.requestFocus();
            	return true;
            }
            else if(this.ofol!=null)
            	return this.ofol.onFocusOut(keyCode);
        }
        return false;

    }

    public boolean isViewInCard(View v){
    	ViewParent p;
    	if(v==this)
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
        view.setVisibility(GONE);
        if(slaveView!=null)
            if(view instanceof TVSlaveView){
                if(((TVSlaveView)view).getSlaveView()==null)
                    ((TVSlaveView)view).createSlaveView();
                this.slaveView.addOtherView(((TVSlaveView)view).getSlaveView());
            }
        if(hasFocus())
            focus(true);
    }

    public void setText(String txt) {
        this.text=txt;
        ((TextView) findViewById(R.id.info_text)).setText(txt);
    }
    
    public void setText2(String txt) {
        TextView textView = (TextView)findViewById(R.id.info_text2);

        textView.setText(txt);
        textView.setVisibility(View.VISIBLE);
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

    public float getOriginalX(){
        return originalX;
    }
    public void originalSetX(float x){
        this.originalX = x;
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        originalHeight = lp.height;
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

    @Override
    public boolean saveFocus(final View sv) {
        if ((((ImageView) findViewById(R.id.imageView)) != null && ((ImageView) findViewById(R.id.imageView)).isFocused())) {
            lastFocused = -1;
            return true;
        }
        if (others.size() > 0)
            for (int i = 0; i < others.size(); i++) {
                if (others.get(i) instanceof FocusableTVCardView && ((FocusableTVCardView) others.get(i)).saveFocus(findViewById(R.id.contentScrollView))) {
                    lastFocused = i;
                    return true;
                }
            }
        return super.hasFocus();
    }

    @Override
    public void restoreFocus(ScrollView sv) {
        if (lastFocused == -1 && findViewById(R.id.imageView) != null&&slaveView!=null)
            findViewById(R.id.imageView).requestFocus();
        else {
            if (others.size() > lastFocused) {
                if (others.get(lastFocused) instanceof FocusableTVCardView)
                    ((FocusableTVCardView) others.get(lastFocused)).restoreFocus((ScrollView) findViewById(R.id.contentScrollView));
                else if(slaveView!=null)
                    others.get(lastFocused).requestFocus();
            }
        }

    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setElevation(long elevation){
        super.setElevation(elevation);
    }
    //animation

    public void focus(final boolean isFocused) {
        if(slaveView!=null)
            slaveView.focus(isFocused);
        final Animation a;
        if (isFocused) {

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setElevation(10);
            }
            //how should we magnify it ?
            if(parentView!=null)
                parentView.bringToFront();
            boolean scale = true;
            int new_cardview_height = (int) MIN_FOCUSED_CARDVIEW_HEIGHT_COEFF*originalHeight;
            float coeff_image_view = (float) 1.1;
            if (others.size() > 0) {
                scale = false;
                coeff_image_view = (float) 0.6;
                int totalHeight=0;
                for(View v : others){
                    v.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                    totalHeight = v.getMeasuredHeight();
                }
                new_cardview_height=(int) ((totalHeight+originalHeight*coeff_image_view>originalHeight*2)?originalHeight*2:totalHeight+originalHeight*coeff_image_view);
		//we need new height to be smaller than screen size*
                DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
                
                int height = metrics.heightPixels;
                new_cardview_height= new_cardview_height+40<height?new_cardview_height:height-40;
            }
            if(new_cardview_height<MIN_FOCUSED_CARDVIEW_HEIGHT_COEFF*originalHeight)
                new_cardview_height= (int) (MIN_FOCUSED_CARDVIEW_HEIGHT_COEFF*originalHeight);

            a = new ExpandAnimation(
                    (int)(originalX+((float)(originalWidth-originalWidth*1.2))/2.0),
                    (int) (originalWidth*1.2), (int)( new_cardview_height),
                    scale?(float)(1.2):originalWidth*(float)(1.2),
                    scale?(float)(1.2):originalHeight*coeff_image_view, scale, 255, null);
            a.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                    for (final View v : others)
                        v.setVisibility(VISIBLE);
                    restoreFocus(null);

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    restoreFocus(null);
                    View v = (View) findViewById(R.id.topView);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

        } else {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setElevation(0);
            }
            saveFocus(null);
            boolean scale =others.size() == 0;
            a = new ExpandAnimation((int)originalX,originalWidth, originalHeight,scale?1:originalWidth, scale?1:originalHeight, scale, minAlpha, null);

            a.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    for (View v : others) 
                        v.setVisibility(GONE);
                }
                @Override
                public void onAnimationStart(Animation animation) {      }
                @Override
                public void onAnimationRepeat(Animation animation) {     }
            });
        }
        a.setDuration(200);
        startAnimation(a);
    }


    private class ExpandAnimation extends Animation {
        private final int mStartHeight;
        private final int mDeltaHeight;
        private final int mStartWidth;
        private final int mDeltaWidth;
        private final float mImageViewStartHeight;
        private final float mImageViewDeltaHeight;
        private final float mImageViewStartWidth;
        private final float mImageViewDeltaWidth;
        private final float originalX;
        private final float mDeltaX;
        private final boolean scale;
        private final float mStartScaleX;
        private final float mImageViewDeltaScaleX;
        private final float mStartScaleY;
        private final float mImageViewDeltaScaleY;
        private final float mStartAlpha;
        private final float mDeltaAlpha;
        //if scale, topview will use  float imageViewEndWidth, float imageViewEndHeight, as scale coefficients

        public ExpandAnimation(int endX,int endWidth, int endHeight, float imageViewEndWidth, float imageViewEndHeight, boolean scale, int endAlpha, Runnable post) {
            mStartHeight = getCurrentHeight();
            mStartWidth = getCurrentWidth();
            mDeltaHeight = endHeight - getCurrentHeight();
            mDeltaWidth = endWidth - getCurrentWidth();
            mImageViewStartHeight = getImageViewCurrentHeight();
            mImageViewStartWidth = getImageViewCurrentWidth();
            mImageViewDeltaHeight = imageViewEndHeight - mImageViewStartHeight;
            mImageViewDeltaWidth = imageViewEndWidth - mImageViewStartWidth;
            View v = (View) findViewById(R.id.topView);
            if (v != null&&scale) {
                mStartScaleX=v.getScaleX();
                mImageViewDeltaScaleX= (imageViewEndWidth)-mStartScaleX;
                mStartScaleY=v.getScaleY();
                mImageViewDeltaScaleY= (imageViewEndHeight)-mStartScaleY;
            }
            else{
                mStartScaleX=0;
                mImageViewDeltaScaleX=0;
                mStartScaleY=0;
                mImageViewDeltaScaleY=0;
            }
            if(Build.VERSION.SDK_INT>=21)
                mStartAlpha=getAlpha();
            else
                mStartAlpha =  ((ColorDrawable)TVCardView.this.getBackground()).getAlpha();
            if(Build.VERSION.SDK_INT>=21)
                mDeltaAlpha = (float) (endAlpha/255.0-mStartAlpha);
            else

                mDeltaAlpha = endAlpha-mStartAlpha;
            originalX = getX();
            mDeltaX = endX - getX();
            this.scale = scale;
        }

        @Override
        protected void applyTransformation(float interpolatedTime,
                Transformation t) {
            android.view.ViewGroup.LayoutParams lp = getLayoutParams();
            lp.height = (int) (mStartHeight + mDeltaHeight * interpolatedTime);
            lp.width = (int) (mStartWidth + mDeltaWidth * interpolatedTime);
            setLayoutParams(lp);
            if(Build.VERSION.SDK_INT>=21)
                TVCardView.this.setAlpha((mStartAlpha + mDeltaAlpha*interpolatedTime));
            else 
                TVCardView.this.getBackground().setAlpha((int)(mStartAlpha + mDeltaAlpha*interpolatedTime));
            setX(originalX + mDeltaX * interpolatedTime);
            if (!scale) {
                View v = (View) findViewById(R.id.topView);
                if (v != null) {
                    lp = v.getLayoutParams();
                    lp.height = (int) (mImageViewStartHeight + mImageViewDeltaHeight * interpolatedTime);
                    lp.width = (int) (mImageViewStartWidth + mImageViewDeltaWidth * interpolatedTime);
                    v.setLayoutParams(lp);
                }
            }
            else
            {
                View v = (View) findViewById(R.id.topView);
                if (v != null) {
                    v.setScaleX(mStartScaleX+mImageViewDeltaScaleX*interpolatedTime);
                    v.setScaleY(mStartScaleY+mImageViewDeltaScaleY*interpolatedTime);
                }
            }

        }

        @Override
        public boolean willChangeBounds() {
            // TODO Auto-generated method stub
            return true;
        }
    }


    public void setBackgroundAlpha(float f) {

    }

    @Override
    public void updateSlaveView() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public View createSlaveView() {
        // TODO Auto-generated method stub
        
        View v = (View)LayoutInflater.from(mContext).inflate(R.layout.card_layout, null);
        TVCardView tvcv =(TVCardView) v.findViewById(R.id.card_view);
        tvcv.setParentView(v);
        setSlaveView(tvcv);
        return v;
    }
    /* parent view is useful to be dynamically added to tvmenuadapter */
    public void setParentView(View v) {
        // TODO Auto-generated method stub
        this.parentView=v;
    }
    public View getParentView(){
        return parentView;
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
