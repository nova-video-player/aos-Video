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
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;

/**
 * Created by alexandre on 17/06/15.
 */
public class DialogRetrieveSubtitles extends DialogFragment {

    private SubtitleManager mEngine;
    private static boolean isShowing = false;
    private final static boolean DBG = false;
    private final static String TAG = "DialogRetrieveSubtitles";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.progressbar_dialog, container, false);
        Dialog dialog = getDialog();
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        setCancelable(true);
        TextView textView = view.findViewById(R.id.textView);
        textView.setText(R.string.dialog_subloader_copying);
        ProgressBar progressBar = view.findViewById(R.id.progressBar);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
        isShowing = true;

        if (DBG) Log.d(TAG,"dialog created");

        return view;
    }

    public void setDownloader(SubtitleManager engine){
        mEngine = engine;
    }

    public boolean isShowing(){
        return isShowing;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if(mEngine!=null)
            mEngine.abort();
        dialog.cancel();
        isShowing = false;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mEngine != null)
            mEngine.abort();
        dialog.cancel();
        isShowing = false;
    }

}