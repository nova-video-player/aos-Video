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
import android.widget.ScrollView;

public class TVScrollView extends ScrollView {

    private TVOnScrollListener tv;
    public TVScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }
    public TVScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
    }

    public TVScrollView(Context context) {
        super(context);
       
        // TODO Auto-generated constructor stub
    }
    public void setOnScrollListener(TVOnScrollListener tv){
        this.tv=tv;
    }
    @Override
    protected void  onScrollChanged(int l, int t, int oldl,int oldt){
        super.onScrollChanged(l, t, oldl, oldt);
        if(tv!=null)
            tv.onScrollChanged();
    }

}
