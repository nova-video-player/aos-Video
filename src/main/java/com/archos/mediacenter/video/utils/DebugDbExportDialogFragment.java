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

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.archos.filecorelibrary.zip.ZipUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.ui.NovaProgressDialog;
import com.archos.mediaprovider.video.VideoOpenHelper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by vapillon on 13/08/15.
 */
public class DebugDbExportDialogFragment extends DialogFragment {

    private final static boolean DBG = false;
    private final static String TAG = "DebugDbExport";

    NovaProgressDialog mDialog = null;
    DbExportTask mExportTask = null;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (DBG) Log.d(TAG, "onCreateDialog");
        mDialog = new NovaProgressDialog(getActivity());
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
        if (DBG) Log.d(TAG, "onResume");
        super.onResume();

        if (mExportTask != null) {
            mExportTask.cancel(); // should not happen
        }
        mExportTask = new DbExportTask();
        mExportTask.execute();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (DBG) Log.d(TAG, "onDismiss");
        super.onDismiss(dialog);

        if (mExportTask != null) {
            mExportTask.cancel();
            mExportTask = null;
        }
    }

    @Override
    public void onPause() {
        if (DBG) Log.d(TAG, "onPause");
        super.onPause();

        // Better hide the dialog in case of screen rotation, to avoid having to recreate a new zip
        // task while the previous one is maybe not yet fully finished...
        dismiss();
    }

    private class DbExportTask {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private volatile boolean isCancelled = false;

        void execute() {
            executor.execute(() -> {
                if (isCancelled) return;
                if (DBG) Log.d(TAG, "doInBackground");
                File zipFile = null;
                if (getActivity() != null) {
                    File libFile = VideoOpenHelper.getDatabaseFile(getActivity());
                    if (libFile.exists()) {
                        File zipFileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        zipFileDir.mkdir();
                        zipFile = new File(zipFileDir.getAbsolutePath() + "/video_db.zip");
                        if (DBG) Log.d(TAG, "zipFile = " + zipFile);
                        if (zipFile.exists()) zipFile.delete();
                        boolean zipSuccess = ZipUtils.compressFile(libFile, zipFile);
                        if (DBG) Log.d(TAG, "Size uncompressed: " + libFile.length());
                        if (DBG) Log.d(TAG, "Size compressed:   " + zipFile.length());
                        if (!zipSuccess) zipFile = null;
                    }
                }
                final File finalZipFile = zipFile;
                handler.post(() -> {
                    if (isCancelled) return;
                    if (DBG) Log.d(TAG, "onPostExecute " + finalZipFile);
                    if (finalZipFile != null) {
                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("text/plain");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"software+video_db@courville.org"});
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.zipped_library_mail_subject));
                        emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.zipped_library_mail_text));
                        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalZipFile));
                        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(emailIntent);
                        } catch (ActivityNotFoundException ex) {
                            if (getActivity() != null)
                                Toast.makeText(getActivity(), R.string.no_email_installed, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (getActivity() != null)
                            new AlertDialog.Builder(getActivity()).setMessage(R.string.error).show();
                    }
                    mDialog.dismiss();
                });
                executor.shutdown();
            });
        }

        void cancel() {
            isCancelled = true;
            executor.shutdownNow();
        }
    }

}
