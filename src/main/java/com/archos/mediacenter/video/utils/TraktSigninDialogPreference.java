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

import static com.archos.mediacenter.utils.trakt.Trakt.getAuthorizationRequest;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.AttributeSet;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TraktSigninDialogPreference extends Preference {

    private static final Logger log = LoggerFactory.getLogger(TraktSigninDialogPreference.class);

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
        Context context = getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                // Activity is not in a state to show dialogs, so return early
                return;
            }
        }
        try {
            log.debug("onClick: TraktSigninDialogPreference");
            OAuthClientRequest t = getAuthorizationRequest(getSharedPreferences());
            log.debug("onClick: t=" + t.getLocationUri());
            final OAuthData oa = new OAuthData();
            OAuthCallback codeCallBack = data -> {
                // TODO Auto-generated method stub
                if (data.code != null) {
                   log.debug("onClick: data.code is not null");
                    if (context instanceof Activity) {
                        Activity activity = (Activity) context;
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            // check again before displaying dialog
                            return;
                        }
                    }
                    NovaProgressDialog mProgress = NovaProgressDialog.show(getContext(), "", getContext().getResources().getString(R.string.connecting), true, true);

                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        try {
                            mProgress.show();
                            Trakt.accessToken result = Trakt.getAccessToken(oa.code);
                            mProgress.dismiss();
                            if (result != null && result.access_token != null) {
                                log.debug("onClick: trakt access token is {}", result.access_token);
                                Trakt.setAccessToken(getSharedPreferences(), result.access_token);
                                Trakt.setRefreshToken(getSharedPreferences(), result.refresh_token);
                                TraktSigninDialogPreference.this.notifyChanged();
                            }
                        } catch (Exception e) {
                            log.error("onClick: Error during Trakt access token retrieval", e);
                            mProgress.dismiss();
                        }
                    });
                } else {
                    log.debug("onClick: data.code null!");
                    if (!(context instanceof Activity) || ((Activity) context).isFinishing() || ((Activity) context).isDestroyed()) {
                        return;
                    }
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
            log.error("onClick: caught OAuthSystemException", e);
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
            log.debug("showDialog: trigger onClick");
            this.onClick();
        } else {
            log.debug("showDialog: dismiss dialog");
            dismissDialog();
        }
	}

}
