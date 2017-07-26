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

package com.archos.mediacenter.video.leanback.details;

import android.content.Context;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.reflect.Field;

/**
 * Created by alexandre on 01/03/17.
 */

public class ArchosHorizontalGridView extends HorizontalGridView {
    private ArchosDetailsOverviewRowPresenter mArchosDetailsOverviewRowPresenter;
    private static final String TAG = "ArchosHorizontalGridView";
    public ArchosHorizontalGridView(Context context) {
        super(context);
        init();
    }

    Runnable mUpdateChildViewsRunnable = new Runnable() {
        @Override
        public void run() {
            try{
                mOldUpdateChildViewsRunnable.run();
            }catch (Exception e){
                Log.d(TAG,"catching Exception "+e.toString());
            }
        }
    };
    Runnable mOldUpdateChildViewsRunnable;

    private void init() {


        try {
            Field f = RecyclerView.class.getDeclaredField("mUpdateChildViewsRunnable");
            f.setAccessible(true);
           ;
            mOldUpdateChildViewsRunnable = (Runnable) f.get(this);
            f.set(this, mUpdateChildViewsRunnable);
        }  catch (NoSuchFieldException e) {
            e.printStackTrace();

        } catch (IllegalAccessException e) {
            e.printStackTrace();

        }
    }

    public ArchosHorizontalGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ArchosHorizontalGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();

    }
    public ViewHolder getChildViewHolder(View child) {
        try {
            return super.getChildViewHolder(child);
        }
        catch (java.lang.IllegalArgumentException e){
            Log.d(TAG,"catching IllegalArgumentException "+e.toString());
            e.printStackTrace();
        }
        return new MyViewHolder(new FrameLayout(getContext()));
    }
    private class MyViewHolder extends  RecyclerView.ViewHolder{

        public MyViewHolder(View itemView) {
            super(itemView);
        }
    }

    public void setViewHolderCreator(ArchosDetailsOverviewRowPresenter archosDetailsOverviewRowPresenter) {
        mArchosDetailsOverviewRowPresenter = archosDetailsOverviewRowPresenter;
    }
}
