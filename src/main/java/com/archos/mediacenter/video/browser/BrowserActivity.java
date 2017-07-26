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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;

import com.archos.mediacenter.cover.Cover;
import com.archos.mediacenter.cover.CoverRoll3D;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.dialogs.Paste;


/**
 * The goal of this class is to have all common code for the management of the
 * MediaCenter browser and specially code management for the main screen with
 * cover flow, category list and content list.
 */

abstract public class BrowserActivity extends AppCompatActivity {
    protected final static String TAG = "BrowserActivity";
    protected final static boolean DBG = false;

    public final static String FRAGMENT_ARGS = "args";
    public final static String FRAGMENT_NAME = "name";

    // one minute. Avoid stopping the HDD while browsing music or video content
    public final static int HDD_TIMEOUT_IN_SECONDS = 60;

    protected BrowserLayout mBrowserLayout;
    protected CoverRoll3D mCoverRoll;
    protected Paste mPasteDialog;

    /**
     * Basic class used to save more than one object in
     * onRetainNonConfigurationInstance().
     */
    protected static class NonConfigurationInstance {
        public Object mCoverRoll;
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        setContentView(getLayoutID());
        setSupportActionBar((Toolbar) findViewById(R.id.main_toolbar));
        ViewCompat.setElevation(findViewById(R.id.main_toolbar), getResources().getDimension(R.dimen.toolbar_default_elevation));

        // CoverRoll will trigger the info menu by itself but the
        // onPrepareDialog/onCreateDialog are to be handled here...
        if (mCoverRoll != null) {
            final NonConfigurationInstance nci = (NonConfigurationInstance) getLastCustomNonConfigurationInstance();
            if (nci != null) {
                mCoverRoll.setLastNonConfigurationInstance(nci.mCoverRoll);
            }
            mCoverRoll.setActivity(this);
            mCoverRoll.setLoaderManager(getLoaderManager());
            mCoverRoll.onStartGL();

            // The content of the cover roll may be defined by the intent extra
            // on what is in the intent data.
            // Do that as soon as possible because it must be done before OpenGL
            // layer is ready.
            String coverRollInitContentId = getIntent().getDataString();
            mCoverRoll.setContentId(coverRollInitContentId);
        }
    }

    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Cover.LAUNCH_CONTENT_BROWSER_INTENT);
        registerReceiver(mCoverLaunchListener, filter);

        updateGlobalResume();

            // Resume the CoverRoll here. It's resumed by the onVisibilityChanged callback anyway, but better be symmetric with the onPause() below
        if (mCoverRoll != null) mCoverRoll.onResumeGLDisplay();
    }

    public void onPause() {
        // CoverRoll must be pause ASAP, can't wait for the onVisibilityChanged callback, see #1566
        if (mCoverRoll != null) mCoverRoll.onPauseGLDisplay();

        unregisterReceiver(mCoverLaunchListener);

        super.onPause();
    }

    public void onDestroy() {
        // There a things to free in the CoverRoll (cursors)
        if (mCoverRoll != null) {
            mCoverRoll.onStopGL();
            mCoverRoll.onDestroy(this);
        }

        super.onDestroy();
    }

    public Object onRetainCustomNonConfigurationInstance() {
        NonConfigurationInstance nci = new NonConfigurationInstance();
        if (mCoverRoll != null) 
            nci.mCoverRoll = mCoverRoll.onRetainNonConfigurationInstance();
        return nci;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret = super.onOptionsItemSelected(item);


        return ret;
    }

    private final BroadcastReceiver mCoverLaunchListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Cover.LAUNCH_CONTENT_BROWSER_INTENT)) {
                Fragment f = Fragment.instantiate(BrowserActivity.this,
                        intent.getStringExtra(FRAGMENT_NAME), intent.getBundleExtra(FRAGMENT_ARGS));

                BrowserCategory category = (BrowserCategory) getSupportFragmentManager().findFragmentById(
                        R.id.category);
                category.startContent(f);
            }
        }
    };

    /**
     * Go back to the MediaCenter's main screen.
     */
    public void goHome() {
        // Clear the back stack
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);


        BrowserCategory category = (BrowserCategory) getSupportFragmentManager().findFragmentById(
                         R.id.category);
        category.clearCheckedItem();
        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD); // reset the ActionBar navigation mode that may have been changed by a Browser fragment
        getSupportActionBar().setTitle(getTitleID());
        invalidateOptionsMenu();
    }

    /**
     * Return the layout id of main browser activity.
     */
    abstract public int getLayoutID();

    /**
     * This method updates the view offering a quick access to the last media
     * played or in progress.
     */
    abstract protected void updateGlobalResume();

    /**
     * Return the title's application string id.
     */
    abstract public int getTitleID();

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (DBG)
            Log.d(TAG, "onCreateContextMenu");
        if ((mCoverRoll != null) && v.equals(mCoverRoll)) {
            mCoverRoll.createContextMenu(this, menu);
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }
}
