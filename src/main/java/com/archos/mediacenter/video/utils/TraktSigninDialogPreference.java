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

package com.archos.mediacenter.video.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.ui.NovaProgressDialog;
import com.archos.mediacenter.video.utils.oauth.OAuthCallback;
import com.archos.mediacenter.video.utils.oauth.OAuthData;
import com.archos.mediacenter.video.utils.oauth.OAuthDialog;

import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;

public class TraktSigninDialogPreference extends Preference {

    private final static boolean DBG = false;
    private static final String TAG = TraktSigninDialogPreference.class.getSimpleName();

	OAuthDialog od=null;
    private DialogInterface.OnDismissListener mOnDismissListener;

    public TraktSigninDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TraktSigninDialogPreference(Context context, AttributeSet attrs, int defStyle) {
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
            OAuthCallback codeCallBack = data -> {
                // TODO Auto-generated method stub
                if (data.code != null) {
                    if (DBG) Log.d(TAG,"onClick: data.code is not null");
                    NovaProgressDialog mProgress = NovaProgressDialog.show(getContext(), "", getContext().getResources().getString(R.string.connecting), true, true);
                    AsyncTask t1 = new AsyncTask() {
                        @Override
                        protected void onPreExecute() {
                            if (DBG) Log.d(TAG,"OAuthCallback.onPreExecute: show dialog");
                            mProgress.show();
                        }

                        @Override
                        protected Object doInBackground(Object... params) {
                            if (DBG) Log.d(TAG,"OAuthCallback.doInBackground: get trakt accessToken");
                            final Trakt.accessToken res = Trakt.getAccessToken(oa.code);
                            return res;
                        }

                        @Override
                        protected void onPostExecute(Object result) {
                            if (DBG) Log.d(TAG,"OAuthCallback.onPostExecute: store trakt accessToken and notify change");
                            mProgress.dismiss();
                            if (result != null && result instanceof Trakt.accessToken) {
                                Trakt.accessToken res = (Trakt.accessToken) result;
                                if (res.access_token != null) {
                                    Trakt.setAccessToken(getSharedPreferences(), res.access_token);
                                    Trakt.setRefreshToken(getSharedPreferences(), res.refresh_token);
                                    TraktSigninDialogPreference.this.notifyChanged();
                                }
                            }
                        }
                    };
                    t1.execute();
                } else {
                    if (DBG) Log.d(TAG,"onClick: data.code null!");
                    new AlertDialog.Builder(getContext())
                            .setNegativeButton(android.R.string.ok, null)
                            .setMessage(R.string.dialog_subloader_nonetwork_title)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
            };

            od = new OAuthDialog(getContext(), codeCallBack, oa, t);
            od.setCancelable(true);
            od.setCanceledOnTouchOutside(false);
            od.show();
            if (mOnDismissListener != null) {
                od.setOnDismissListener(mOnDismissListener);
                od.setOnCancelListener(dialogInterface -> {
                    mOnDismissListener.onDismiss(dialogInterface);
                });
            } else {
                od.setOnCancelListener(DialogInterface::cancel);
                od.setOnDismissListener(DialogInterface::dismiss);
            }
        } catch (OAuthSystemException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, "onClick: caught OAuthSystemException", e);
        }
        
    }
	public void dismissDialog() {
		if(od != null)
			od.dismiss();
	}

	public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener){
        mOnDismissListener = onDismissListener;
    }

	public void showDialog(boolean boolean1) {
		// TODO Auto-generated method stub
		if(boolean1) {
            if (DBG) Log.d(TAG, "showDialog: trigger onClick");
            this.onClick();
        } else {
            if (DBG) Log.d(TAG, "showDialog: dismiss dialog");
            dismissDialog();
        }
	}

}
