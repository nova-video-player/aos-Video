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

package com.archos.mediacenter.video.leanback.filebrowsing;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.KeyEvent;

import com.archos.mediacenter.video.leanback.SingleFragmentActivity;
import com.archos.mediacenter.video.leanback.network.ftp.FtpListingActivity;
import com.archos.mediacenter.video.leanback.network.smb.SmbListingActivity;
import com.archos.mediacenter.video.leanback.network.upnp.UpnpListingActivity;

public abstract  class ListingActivity extends SingleFragmentActivity {

    /**
     * android.net.Uri to start with
     */
    public static final String EXTRA_STARTING_URI = "STARTING_URI";

    /**
     * Name to be displayed for the starting level
     */
    public static final String EXTRA_STARTING_NAME = "STARTING_NAME";

    /**
     * android.net.Uri used as root.
     * goBackOneLevel() will return false if this root Uri is the current one
     * rootUri MUST be a parent of startingUri (or equal to startingUri)
     */
    public static final String EXTRA_ROOT_URI = "ROOT_URI";

    /**
     * Name to be displayed for the root level
     */
    public static final String EXTRA_ROOT_NAME = "ROOT_NAME";

    /**
     * Get the fragment to start with
     */
    abstract protected ListingFragment getStartingFragment();

    /**
     * if a file or folder has been deleted
     */
    public static final int RESULT_FILE_DELETED = 1;

    /**
     * when starting info activity in listing fragments
     */
    public static final int REQUEST_INFO_ACTIVITY = 1;



    /**
     * Return the best Activity class ofr a given Uri
     * @param uri
     * @return
     */
    public static Class getActivityForUri(Uri uri) {
        final String scheme = uri.getScheme();

        if ("file".equals(scheme)) {
            return LocalListingActivity.class;
        }
        else if ("smb".equals(scheme)) {
            return SmbListingActivity.class;
        }
        else if ("upnp".equals(scheme)) {
            return UpnpListingActivity.class;
        }
        else if (scheme!=null && scheme.contains("ftp")) { // ftp, sftp, ftps
            return FtpListingActivity.class;
        }
        else {
            throw new IllegalArgumentException("Found no Activity for "+uri);
        }
    }

    /**
     * Give Uri to start browsing from.
     * Also goBackOneLevel() will return false if this root Uri is the current one
     */
    protected Uri getStartingUri() {
        Uri uri = (Uri)getIntent().getParcelableExtra(EXTRA_STARTING_URI);
        if (uri==null) {
            // Default to the root
            return getRootUri();
        }
        return uri;
    }

    protected String getStartingName() {
        String name = getIntent().getStringExtra(EXTRA_STARTING_NAME);
        if (name==null) {
            // Default to the root
            return getRootName();
        }
        return name;
    }

    /**
     * goBackOneLevel() will return false if this root Uri is the current one
     */
    protected Uri getRootUri() {
        Uri uri = (Uri)getIntent().getParcelableExtra(EXTRA_ROOT_URI);
        if (uri==null) {
            throw new IllegalStateException("EXTRA_ROOT_URI Uri is mandatory in the fragment arguments!");
        }
        return uri;
    }

    /**
     * Name to be displayed for the root level
     */
    protected String getRootName() {
        String name = getIntent().getStringExtra(EXTRA_ROOT_NAME);
        if (name==null) {
            throw new IllegalStateException("EXTRA_ROOT_NAME String is mandatory in the fragment arguments!");
        }
        return name;
    }

    /**
     * Imlements SingleFragmentActivity
     * @return
     */
    public Fragment getFragmentInstance() {
        ListingFragment frag = getStartingFragment();
        Bundle args = new Bundle();
        args.putParcelable(ListingFragment.ARG_URI, (Parcelable)getStartingUri());
        args.putString(ListingFragment.ARG_TITLE, getStartingName());
        args.putBoolean(ListingFragment.ARG_IS_ROOT, true); // this is the first fragment in the activity
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onBackPressed() {
        MultiBackHintManager.getInstance(this).onBackPressed();

        boolean popped = getFragmentManager().popBackStackImmediate();
        if (!popped) {
            super.onBackPressed();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int result, Intent data){
        super.onActivityResult(requestCode, result, data);

        if(requestCode==REQUEST_INFO_ACTIVITY&&result== RESULT_FILE_DELETED){
            Uri file = data.getData();

            for(int i = 0; i<= getFragmentManager().getBackStackEntryCount(); i++){
                Fragment frag = getFragmentManager().findFragmentByTag("fragment_"+i);//tag specified in fragmenttransaction
                if(frag != null&&frag instanceof ListingFragment){ //send uri to refresh
                    ((ListingFragment)frag).onFileDelete(file);
                }
            }


        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {

        // Quit file browsing on BACK long press
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            MultiBackHintManager.getInstance(this).onBackLongPressed();
            finish();
            return true;
        }
        else return super.onKeyLongPress(keyCode, event);
    }
}
