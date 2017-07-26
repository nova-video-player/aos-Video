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


package com.archos.mediacenter.video.browser;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewStub;
import android.view.animation.DecelerateInterpolator;

import com.archos.mediacenter.utils.GlobalResumeView;
import com.archos.medialib.R;

public class BrowserLayout extends android.support.v4.widget.DrawerLayout {

    public interface Callback {
        /** Called when animation is done. */
        public void onLayoutChanged();

        /** Called when category view is dragged. */
        public void onGoHome();


    }

    private static final int ANIMATION_DURATION = 300;

    /* The layout has never been initialized */
    private static final int STATE_UNINITIALIZED = -1;
    /* The layout shows the cover and the category */
    private static final int STATE_COVER = 0;
    /* The layout shows the category and the content */
    private static final int STATE_CONTENT = 1;

    /* Property name for the cover's animation */
    private static final String PROP_COVER_LEFT = "coverLeftAnim";
    private static final String TAG = "BrowserLayout";

    private static final TimeInterpolator INTERPOLATOR = new DecelerateInterpolator(1.5f);


    private Callback mCallback;
    private GlobalResumeView mGlobalResumeView;
    private View mCategoryView;
    private View mContentView;
    private ViewStub mGlobalResumeViewStub;

    // Arrays of invisible and visible views according the orientation and the
    // layout's state.
    // The first index is STATE_*
    // The second index is the list of the views.
    private View[][] mInvisibleViews, mVisibleViews;

    public BrowserLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public BrowserLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BrowserLayout(Context context) {
        super(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // TODO This should be in the xml.
        mCategoryView = findViewById(R.id.category);
        mGlobalResumeViewStub = (ViewStub) findViewById(R.id.global_resume_stub);

        mContentView = findViewById(R.id.content);

        mCategoryView.setOnKeyListener(mOnKeyListener);
    }

    private final OnKeyListener mOnKeyListener = new OnKeyListener() {
        // This listener is used to check the keypresses when the categories or global resume have the focus
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.getAction() == KeyEvent.ACTION_DOWN) {
               mCallback.onGoHome();
                return true;
            }

            return false;
        }
    };


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

    }

    private static float getCoverRollWidthRatio(Configuration config, boolean isAndroidTV) {
        final int w = config.screenWidthDp;

        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // LANDSCAPE
        	
            if (w<700)
                return 0.50f; // phones
            else if (w<900&& !isAndroidTV)
                return 0.69f; // Kindle Fire HD (853dp)
            else if (w<1000&& !isAndroidTV)
                return 0.69f; // Nexus 7 (961dp)
            else if (w<1200&& !isAndroidTV)
                return 0.66f; // Archos 80 (1024dp)
            else
                return 0.71f; // Archos 10 (1280dp)
        }
        else {
            // PORTRAIT
            if (w<500)
                return 0; // phones
            else
                return 0.5f; // tablets
        }
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public View getCategoryView() {
        return mCategoryView;
    }



    public View getContentView() {
        return mContentView;
    }

    /**
     * Only call this when you really need to show the global resume view.
     */
    public GlobalResumeView getGlobalResumeView() {
        if (mGlobalResumeView == null) {
            mGlobalResumeView = (GlobalResumeView) mGlobalResumeViewStub.inflate();
            mGlobalResumeView.setOnKeyListener(mOnKeyListener);
           
        }
        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        if(!mPreferences.getBoolean("display_resume_box",true))
            mGlobalResumeView.setVisibility(View.GONE);
        else
            mGlobalResumeView.setVisibility(View.VISIBLE);
        return mGlobalResumeView;
    }







}
