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

package com.archos.mediacenter.video.browser.dialogs;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;

/**
 * Created by alexandre on 17/06/15.
 */
public class DialogRetrieveSubtitles extends DialogFragment {
    private ProgressDialog pd;
    private SubtitleManager mEngine;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        pd = new ProgressDialog(getActivity());
        pd.setMessage(getString(R.string.dialog_subloader_copying));
        pd.setIndeterminate(true);
        pd.setCancelable(true);

        return pd;
    }
    public void setDownloader(SubtitleManager engine){
        mEngine = engine;
    }
    public boolean isShowing(){
        return pd!=null&&pd.isShowing();
    }
    @Override
    public void onCancel(DialogInterface dialog) {
        if(mEngine!=null)
            mEngine.abort();
    }
}