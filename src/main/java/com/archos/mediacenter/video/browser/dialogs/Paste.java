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


import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.format.Formatter;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.FileManagerService;

public class Paste extends AlertDialog implements FileManagerService.ServiceListener {

    private static String TAG = "Paste";
    private static boolean DBG = false;

    static final private int MAX_PROGRESS = 100;

    private Context mContext;
    private TextView mMessage;
    private ProgressBar mProgress;
    private TextView mProgressText;
    private CheckBox mOpenFile;

    public Paste(Context context) {
        super(context, R.style.CustomDialogTheme);
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


        View customTitleView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_custom_title, null);
        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * customTitleView.getContext().getResources().getDisplayMetrics().density);
        customTitleView.setPadding(dpToPx.apply(12),dpToPx.apply(10),dpToPx.apply(16),dpToPx.apply(4));
        TextView titleText = customTitleView.findViewById(R.id.dialog_title);
        titleText.setText(R.string.copying);
        Typeface customFont = ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_95blk);
        titleText.setTypeface(customFont);
        titleText.setTextSize(24);
        ImageView iconView = customTitleView.findViewById(R.id.dialog_icon);
        iconView.setVisibility(View.GONE);

        setCustomTitle(customTitleView);
        if(FileManagerService.fileManagerService==null) {
            if (DBG) Log.w(TAG, "Paste: FileManagerService.fileManagerService==null, it should not!");
        } else {
            if (DBG) Log.d(TAG, "Paste: FileManagerService exists, addListener");
            FileManagerService.fileManagerService.addListener(this);
        }
        setButton(DialogInterface.BUTTON_NEGATIVE, mContext.getText(android.R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if(FileManagerService.fileManagerService==null) {
                    if (DBG)
                        Log.w(TAG, "Paste onClick: FileManagerService.fileManagerService==null, it should not!");
                } else {
                    FileManagerService.fileManagerService.stopPasting();
                }
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
        Typeface customFont = ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_95blk);
        Button positiveButton = getButton(AlertDialog.BUTTON_NEUTRAL);
        if (positiveButton != null) {
            positiveButton.setTypeface(customFont);
            Drawable ripple = ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple);
            positiveButton.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
            positiveButton.setBackground(ripple);
            positiveButton.setClipToOutline(true);
        }
        Button negativeButton = getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negativeButton != null) {
            negativeButton.setTypeface(customFont);
            Drawable ripple = ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple);
            negativeButton.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
            negativeButton.setBackground(ripple);
            negativeButton.setClipToOutline(true);
        }
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
        if(service == null) return;

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
