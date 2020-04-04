// Copyright 2017 Archos SA
// Copyright 2020 Courville Software
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

package com.archos.mediacenter.video.browser.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.util.Log;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.ui.NovaProgressDialog;

/**
 * Created by alexandre on 17/06/15.
 */
public class DialogRetrieveSubtitles extends DialogFragment {

    private SubtitleManager mEngine;
    private static boolean isShowing = false;
    private final static boolean DBG = false;
    private final static String TAG = "DialogRetrieveSubtitles";

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NovaProgressDialog npd = new NovaProgressDialog(getContext());
        npd.setMessage(getString(R.string.dialog_subloader_copying));
        npd.setIcon(R.drawable.filetype_video);
        npd.setIndeterminate(true);
        npd.setCancelable(true);
        npd.setCanceledOnTouchOutside(false);
        isShowing = true;
        if (DBG) Log.d(TAG,"dialog created");
        return npd;
    }

    public void setDownloader(SubtitleManager engine) { mEngine = engine; }
    public boolean isShowing() { return isShowing; }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        if(mEngine!=null)
            mEngine.abort();
        dialog.cancel();
        isShowing = false;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mEngine != null)
            mEngine.abort();
        dialog.cancel();
        isShowing = false;
    }
}
