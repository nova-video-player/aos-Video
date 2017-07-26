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
import android.support.v17.leanback.widget.ImageCardView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

/**
 * Created by alexandre on 22/12/15.
 */
public class CustomImageCardview extends ImageCardView {
    private FrameLayout mainRoot;
    public CustomImageCardview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mainRoot = new FrameLayout(getContext());


        removeView(getMainImageView());
        addView(mainRoot, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        mainRoot.addView(getMainImageView());
        //when removing ellipse, we have a problem in browser layout : this seems to fix it
        ((TextView)findViewById(R.id.content_text)).setEllipsize(TextUtils.TruncateAt.END);
    }

    public void addViewToRoot(View v){
        mainRoot.addView(v);
    }
    public CustomImageCardview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public CustomImageCardview(Context context) {
        super(context);
        init();
    }



    private void findChildrenViews() {


    }


}
