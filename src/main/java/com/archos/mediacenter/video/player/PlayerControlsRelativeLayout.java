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
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

public class PlayerControlsRelativeLayout extends RelativeLayout{
	int mState;
	int mPos;
	LayoutInflater mInflater;

	public PlayerControlsRelativeLayout(Context context) {
		super(context);
		mPos=0;
		mState=0;
		mInflater = LayoutInflater.from(context);
	}

	public PlayerControlsRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mInflater = LayoutInflater.from(context);
	}

	public PlayerControlsRelativeLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		mInflater = LayoutInflater.from(context);
	}

	@Override
	public void onDraw(Canvas c){
		super.onDraw(c);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed,l,t,r,b);
		if(mState==1)
			setSideBySide(true, mPos);
		else if(mState==2)
			setTopBottom(true, mPos);
		else
			setTopBottom(false, mPos);
	}

	public void setTopBottom(boolean side, int pos){
		mPos = pos;
		mState=0;
		setX(0);
		this.setScaleX(1);
		this.setScaleY(1);
		setY(0);
		if(side){
			mState=2;
			//this.setPivotScaleX(0);

			if(pos==1)
				setY(-getHeight()/4);
			else
				setY(getHeight()/4);
			this.setScaleY((float)0.5);
		}
	}

	public void setSideBySide(boolean side, int pos){
		mPos = pos;
		mState=0;
		setY(0);
		this.setScaleY(1);
		this.setScaleX(1);
		setX(0);
		if(side){
			mState = 1;
			//this.setPivotScaleX(0);
			this.setScaleX((float)0.5);
			if(pos==1)
				setX(-getWidth()/4);
			else
				setX(getWidth()/4);
		}
	}
}