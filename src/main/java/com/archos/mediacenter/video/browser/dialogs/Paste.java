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


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.FileManagerService;

public class Paste extends AlertDialog implements FileManagerService.ServiceListener {




    static final private int MAX_PROGRESS = 100;

    private Context mContext;
    private TextView mMessage;
    private ProgressBar mProgress;
    private TextView mProgressText;
    private CheckBox mOpenFile;

    public Paste(Context context) {
        super(context);
        mContext = context;
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(com.archos.filecorelibrary.R.layout.paste, null, false);
        setView(view);
        mOpenFile = (CheckBox)view.findViewById(com.archos.filecorelibrary.R.id.open_file);
        mOpenFile.setVisibility(View.GONE);
        mMessage = (TextView) view.findViewById(com.archos.filecorelibrary.R.id.message);
        mMessage.setMovementMethod(new ScrollingMovementMethod());

        mProgress = (ProgressBar) view.findViewById(com.archos.filecorelibrary.R.id.progress);
        mProgress.setMax(MAX_PROGRESS);
        mProgress.setEnabled(false);
        //mIndeterminateProgress = (ProgressBar) view.findViewById(R.id.progress_small);
        mProgressText = (TextView) view.findViewById(com.archos.filecorelibrary.R.id.progress_text);

        setTitle(R.string.copying);
        FileManagerService.fileManagerService.addListener(this);
        setButton(DialogInterface.BUTTON_NEGATIVE, mContext.getText(android.R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                FileManagerService.fileManagerService.stopPasting();
            }
        });
        setButton(DialogInterface.BUTTON_NEUTRAL, mContext.getText(R.string.run_in_background), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dismiss();
            }
        });
        setCanceledOnTouchOutside(false);

    }
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (FileManagerService.fileManagerService!=null)
            FileManagerService.fileManagerService.deleteObserver(Paste.this);
    }

    public void setMessage(CharSequence message) {
        mMessage.setText(message);
    }



    @Override
    public void onActionStart() {

    }

    @Override
    public void onActionStop() {
        dismiss();
    }

    @Override
    public void onActionError() {
        dismiss();
    }

    @Override
    public void onActionCanceled() {
        dismiss();
    }

    @Override
    public void onProgressUpdate() {
        FileManagerService service = FileManagerService.fileManagerService;

            if(service.getPasteTotalSize()>0)
                mProgress.setProgress((int) (MAX_PROGRESS *  service.getPasteTotalProgress() / service.getPasteTotalSize()));
            if( service.getPasteTotalProgress() != service.getPasteTotalSize()){
                if(service.getFilesToPaste().size()>0) {
                    setMessage(service.getFilesToPaste().get(service.getCurrentFile()).getName());
                    if (service.getFilesToPaste().size() > 1) {
                        mProgressText.setText(mContext.getResources().getString(com.archos.filecorelibrary.R.string.pasting_copy_many,
                                service.getCurrentFile()+1, service.getFilesToPaste().size(),
                                Formatter.formatShortFileSize(mContext, service.getPasteTotalProgress()), Formatter.formatShortFileSize(mContext, service.getPasteTotalSize())));
                    } else {
                        mProgressText.setText(mContext.getResources().getString(com.archos.filecorelibrary.R.string.pasting_copy_one,
                                Formatter.formatShortFileSize(mContext, service.getPasteTotalProgress()), Formatter.formatShortFileSize(mContext, service.getPasteTotalSize())));
                    }
                }
            }
            else
                mProgressText.setText(mContext.getResources().getString(com.archos.filecorelibrary.R.string.pasting_done));


    }
}
