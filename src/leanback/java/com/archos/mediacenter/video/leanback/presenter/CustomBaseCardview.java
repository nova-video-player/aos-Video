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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.Context;
import android.os.Build;
import android.support.v17.leanback.widget.BaseCardView;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by alexandre on 22/12/15.
 */
public class CustomBaseCardview extends BaseCardView {
    public CustomBaseCardview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomBaseCardview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CustomBaseCardview(Context context) {
        super(context);
    }

    private void fixKitKatSizeIssue(){
        if(Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            measure(0, 0);
            if(getParent()!=null&&getParent() instanceof View) {
                ((View) getParent()).getLayoutParams().width = getMeasuredWidth();
                ((View) getParent()).getLayoutParams().height = getMeasuredHeight();
            }
        }
    }
    @Override
    public void onAttachedToWindow(){
        super.onAttachedToWindow();
        fixKitKatSizeIssue(); //first fix when added
    }
    @Override
    public void setActivated(boolean activated) {
       super.setActivated(activated);
        fixKitKatSizeIssue(); //resize when activated or not (info disappears)

    }






}
