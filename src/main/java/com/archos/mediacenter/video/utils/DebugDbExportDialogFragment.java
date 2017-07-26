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

package com.archos.mediacenter.video.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.archos.filecorelibrary.zip.ZipUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.VideoOpenHelper;

import java.io.File;

/**
 * Created by vapillon on 13/08/15.
 */
public class DebugDbExportDialogFragment extends DialogFragment {

    private final static String TAG = "DebugDbExport";

    ProgressDialog mDialog = null;
    AsyncTask mExportTask = null;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.d(TAG, "onCreateDialog");
        mDialog = new ProgressDialog(getActivity());
        mDialog.setIndeterminate(true);
        mDialog.setCancelable(false);
        mDialog.setMessage(getString(R.string.zipping_library_before_mailing));
        mDialog.setButton(Dialog.BUTTON_NEGATIVE, getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismiss();
            }
        });
        return mDialog;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        if (mExportTask !=null) {
            mExportTask.cancel(true); // should not happen
        }
        mExportTask = new DbExportTask();
        mExportTask.execute();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        Log.d(TAG, "onDismiss");
        super.onDismiss(dialog);

        if (mExportTask !=null) {
            mExportTask.cancel(true);
            mExportTask = null;
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

        // Better hide the dialog in case of screen rotation, to avoid having to recreate a new zip
        // task while the previous one is maybe not yet fully finished...
        dismiss();
    }

    private class DbExportTask extends AsyncTask<Object, Void, File> {

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "onPreExecute ");
            super.onPreExecute();
        }

        @Override
        protected File doInBackground(Object... params) {
            Log.d(TAG, "doInBackground");
            File libFile = VideoOpenHelper.getDatabaseFile(getActivity());
            if (!libFile.exists()) {
                Toast.makeText(getActivity(), R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
                return null;
            }
            // Build zipped File
            File zipFileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            zipFileDir.mkdir(); // make the dir if it does not exist (very unlikely)
            File zipFile = new File(zipFileDir.getAbsolutePath()+"/video_db.zip");
            Log.d(TAG, "zipFile = "+zipFile);

            // Erase zipped library if there is already one
            if (zipFile.exists()) {
                zipFile.delete();
            }

            // Zip it!
            boolean zipSuccess = ZipUtils.compressFile(libFile, zipFile);
            Log.d(TAG, "Size uncompressed: " + libFile.length());
            Log.d(TAG, "Size compressed:   " + zipFile.length());
            return zipSuccess ? zipFile : null;
        }

        @Override
        protected void onPostExecute(File zipFile) {
            Log.d(TAG, "onPostExecute "+zipFile);
            if (zipFile!=null) {
                // Build and send the intent
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("text/plain");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"software+video_db@archos.com"});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.zipped_library_mail_subject));
                emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.zipped_library_mail_text));
                emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(zipFile));
                emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(emailIntent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(getActivity(), R.string.no_email_installed, Toast.LENGTH_SHORT).show();
                }
            }
            else {
                // Error
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.error)
                        .show();
            }

            mDialog.dismiss();
        }
    }

}
