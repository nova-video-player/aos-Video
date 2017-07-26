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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.archos.mediacenter.video.R;

/**
 * Created by alexandre on 18/05/15.
 */
@SuppressLint("ValidFragment")
public class DeleteDialog extends DialogFragment  {
    public final static String TODELETE = "todelete";
    private DialogInterface.OnClickListener mOnCancelListener;

    public void setOnCancelListener(DialogInterface.OnClickListener onCancelListener){
        mOnCancelListener = onCancelListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title ="";
        int icon=-1 ;
        icon= R.drawable.filetype_video;
        ProgressDialog pd = new ProgressDialog(getActivity());
        pd.setTitle(title);
        if(icon >=0)
            pd.setIcon(icon);
        pd.setMessage(getText(R.string.deleting));
        pd.setIndeterminate(true);
        pd.setCancelable(true);

        return pd;

    }
    @Override
    public void onCancel(DialogInterface dialog) {
        if(mOnCancelListener!=null)
            mOnCancelListener.onClick(dialog,0);
    }
}
