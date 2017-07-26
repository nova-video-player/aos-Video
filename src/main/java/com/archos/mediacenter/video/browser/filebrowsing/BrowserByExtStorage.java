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


package com.archos.mediacenter.video.browser.filebrowsing;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.archos.filecorelibrary.ExtStorageReceiver;
import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.contentstorage.DocumentUriBuilder;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BrowserActivity;
import com.archos.mediacenter.video.browser.BrowserCategory;

import java.io.File;

public class BrowserByExtStorage extends BrowserByLocalFolder {

    private static final int READ_REQUEST_CODE = 42;
    protected String currentMountPoint;

    private final BroadcastReceiver mSdCardReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // Remove "file://"
            final String path = intent.getDataString().substring(7);
            if (currentMountPoint.equals(path)) {
                // Sdcard has been ejected. Get out from the sdcard view.
                ((BrowserActivity) getActivity()).goHome();
            }
        }
    };
    private boolean mHasRegisteredReceiver;
    private boolean mHasAlreadyAsked = false;

    @Override
    public void onCreate(Bundle bundle) {
        Bundle b = bundle == null ? getArguments() : bundle;
        if (b != null) {
            currentMountPoint = b.getString(BrowserCategory.MOUNT_POINT, ExtStorageReceiver.VALUE_PATH_NONE);
        }
        super.onCreate(bundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        File mountPoint = null;
        if (!ExtStorageReceiver.VALUE_PATH_NONE.equals(currentMountPoint))
            mountPoint = new File(currentMountPoint);
        if ((mountPoint != null && !mountPoint.exists())&&!UriUtils.isContentUri(mCurrentDirectory)) {

            Uri uri = DocumentUriBuilder.getUriFromRootPath(getActivity(),currentMountPoint);
            if(uri!=null){
                mCurrentDirectory = uri;
                currentMountPoint = uri.toString();
                listFiles(false);
                getLoaderManager().restartLoader(0, null, this);

            }

        } else {
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            iFilter.addDataScheme("file");
            iFilter.addDataScheme(ExtStorageReceiver.ARCHOS_FILE_SCHEME);//new android nougat send UriExposureException when scheme = file
            getActivity().registerReceiver(mSdCardReceiver, iFilter);
            mHasRegisteredReceiver = true;
        }
    }

    @Override
    public void onListingFatalError(Exception e, ListingEngine.ErrorEnum errorCode) {
        if(!mHasAlreadyAsked&&UriUtils.isContentUri(mCurrentDirectory)&&(errorCode == ListingEngine.ErrorEnum.ERROR_AUTHENTICATION || errorCode == ListingEngine.ErrorEnum.ERROR_NO_PERMISSION)){
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.acquire_external_usb_permission).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(intent, READ_REQUEST_CODE);

                }
            }).setNegativeButton(android.R.string.cancel, null).show();
            displayFailPage();
            mHasAlreadyAsked = true;
        }

    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(BrowserCategory.MOUNT_POINT, currentMountPoint);
    }

    @Override
    public void onPause() {
        if(mHasRegisteredReceiver)
        getActivity().unregisterReceiver(mSdCardReceiver);
        mHasRegisteredReceiver = false;
        super.onPause();
    }

    protected String getIndexableRootFolder() {
        if(UriUtils.isContentUri(mCurrentDirectory))
            return DocumentUriBuilder.buildDocumentUriUsingTree(mCurrentDirectory).toString();
        else
            return super.getIndexableRootFolder();
    }

    @Override
    protected Uri getDefaultDirectory() {
        return Uri.parse(currentMountPoint);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(requestCode==READ_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                //Set directory as default in preferences
                Uri treeUri = intent.getData();
                //grant write permissions
                getActivity().getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                mCurrentDirectory = treeUri;
                listFiles(false);
                getLoaderManager().restartLoader(0, null, this);

            } else displayFailPage();
        }
    }

}
