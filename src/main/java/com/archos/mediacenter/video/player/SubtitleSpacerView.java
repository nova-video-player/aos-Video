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
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;

public class SubtitleSpacerView extends View {
    private String TAG = "SubtitleSpacerView";
   
    private Surface mExternalSurface = null;
    private Drawable mBackground = null;
   
    public SubtitleSpacerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mExternalSurface != null)  {
            try {
                Canvas c = mExternalSurface.lockCanvas(null);
                c.save();
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                c.restore();
                mExternalSurface.unlockCanvasAndPost(c);
            } catch (Exception e) {
            }
        }
    }
    
    public void setRenderingSurface(Surface s) {
        //mExternalSurface = s;
        setBackgroundDrawable(mBackground);
    }
    
    @Override
    public void setBackgroundDrawable(Drawable background) {
        mBackground=background;
        if (mExternalSurface == null)
            super.setBackgroundDrawable(mBackground);
        else
            super.setBackgroundDrawable(null);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        Canvas c = canvas;
        if (mExternalSurface != null) {
            try {
                Rect r = new Rect();
                r = canvas.getClipBounds();
                int [] location = new int[2];
                getLocationOnScreen(location);
                r.offsetTo(location[0],location[1]);
//                 Log.d(TAG, "Spacer "+r.toString());
                c = mExternalSurface.lockCanvas(null);
                c.save();
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                c.clipRect(r);
                c.translate(location[0],location[1]);
                if (mBackground != null)
                    mBackground.draw(c);
            } catch (Exception e) {
            }
        }
        super.onDraw(c);
        if (c != canvas) {
            c.restore();
            mExternalSurface.unlockCanvasAndPost(c);
        }
    }
}
