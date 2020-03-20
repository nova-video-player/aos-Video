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

import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.utils.oauth.*;

import com.archos.mediacenter.video.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import android.util.AttributeSet;

public class TraktSigninDialogPreference extends Preference {
	OAuthDialog od=null;
    private DialogInterface.OnDismissListener mOnDismissListener;

    public TraktSigninDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        
    }
    public TraktSigninDialogPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle); 
        this.setKey(Trakt.KEY_TRAKT_USER);
    }
    public boolean isDialogShowing(){
    	return od!=null&&od.isShowing();
    }


    public SharedPreferences getSharedPreferences(){
        if(super.getSharedPreferences()==null)
            return PreferenceManager.getDefaultSharedPreferences(getContext()); //when used by non-preference activity
        else
            return super.getSharedPreferences();
    }
    @Override
    public void onClick() {
        try {
            OAuthClientRequest t = Trakt.getAuthorizationRequest(getSharedPreferences());
            final OAuthData oa = new OAuthData();
            OAuthCallback codeCallBack = new OAuthCallback() {
                @Override
                public void onFinished(final OAuthData data) {
                    // TODO Auto-generated method stub
                    if(data.code!=null){
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setCancelable(false);
                        builder.setView(R.layout.progressbar_dialog);
                        final AlertDialog mProgressBarAlertDialog = builder.create();
                        AsyncTask t = new AsyncTask(){
                            @Override
                            protected void onPreExecute() {
                                mProgressBarAlertDialog.show();
                            }
                            @Override
                            protected Object doInBackground(Object... params) {
                                final Trakt.accessToken res = Trakt.getAccessToken(oa.code);
                                return res;
                            }
                            @Override
                            protected void onPostExecute(Object result) {
                                mProgressBarAlertDialog.dismiss();
                                if (result!=null&&result instanceof Trakt.accessToken){
                                    Trakt.accessToken res = (Trakt.accessToken) result;
                                    if(res.access_token!=null){
                                        Trakt.setAccessToken(getSharedPreferences(), res.access_token);
                                        Trakt.setRefreshToken(getSharedPreferences(), res.refresh_token);
                                        TraktSigninDialogPreference.this.notifyChanged();
                                    }
                                }   
                            }
                        };
                        t.execute();
                    }
                    else{
                    	new AlertDialog.Builder(getContext())
                    	.setNegativeButton(android.R.string.ok, null)
                    	.setMessage(R.string.dialog_subloader_nonetwork_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                         .show();
                    }
                    	
                }
            };

            od= new OAuthDialog(getContext(),codeCallBack, oa, t);
            od.show();
            od.setOnDismissListener(mOnDismissListener);
            od.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    if (mOnDismissListener != null) {
                        mOnDismissListener.onDismiss(dialogInterface);
                    }
                }
            });
        } catch (OAuthSystemException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
	public void dismissDialog() {
		if(od!=null)
			od.dismiss();
	}

	public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener){
        mOnDismissListener = onDismissListener;
    }

	public void showDialog(boolean boolean1) {
		// TODO Auto-generated method stub
		if(boolean1)
			this.onClick();
		else
			dismissDialog();
	}

}
