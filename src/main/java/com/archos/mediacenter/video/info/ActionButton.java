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

package com.archos.mediacenter.video.info;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

/**
 * Created by alexandre on 26/01/16.
 */
public class ActionButton extends FrameLayout implements View.OnClickListener {
    private TextView mTextView;
    private ImageView mImageView;
    private View mRootView;
    private OnClickListener mOnClickListener;

    public ActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ActionButton(Context context) {
        super(context);
        init(null);
    }

    public ActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setFocusable(false);
        setClickable(false);

        mRootView = LayoutInflater.from(getContext()).inflate(R.layout.video_info2_action_button, this,false);

        addView(mRootView);
        mTextView = (TextView)mRootView.findViewById(R.id.text_view);
        mImageView = (ImageView)mRootView.findViewById(R.id.imageButton);
        //attrs must be in a increasing order
        int[] attrsArray = new int[] {
                android.R.attr.id, // 0
                android.R.attr.paddingLeft, //1
                android.R.attr.paddingTop, //2
                android.R.attr.paddingRight, //3
                android.R.attr.paddingBottom, //4
                android.R.attr.text,//5
                android.R.attr.drawableLeft, //6
                android.R.attr.drawablePadding, //7
        };


        TypedArray ta = getContext().obtainStyledAttributes(attrs, attrsArray);
        int id = ta.getResourceId(0 /* index of attribute in attrsArray */, View.NO_ID);
        Drawable background = ta.getDrawable(6);
        int leftPadding = ta.getDimensionPixelSize(1, 0);
        int rightPadding = ta.getDimensionPixelSize(3, 0);
        int topPadding = ta.getDimensionPixelSize(2, 0);
        int bottomPadding = ta.getDimensionPixelSize(4, 0);
        mRootView.setPadding(leftPadding,topPadding,rightPadding,bottomPadding );
        int left = ta.getDimensionPixelSize(7, 0);
        mTextView.setPadding(left, 0, 0, 0);
        mTextView.setText(ta.getText(5));
        Log.d("textdebug", "text " + ta.getText(5));
        mImageView.setImageDrawable(background);
        ta.recycle();

    }

    @Override
    public void setOnClickListener(OnClickListener onClickListener){
        mRootView.setOnClickListener(this);
        mOnClickListener = onClickListener;
    }

    @Override
    public void onClick(View view) {
        if(view == mRootView&&mOnClickListener!=null){
            mOnClickListener.onClick(this);
        }
    }



}
