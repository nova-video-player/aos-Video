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

package com.archos.mediacenter.video.leanback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.search.VideoSearchActivity;
import com.archos.mediacenter.video.utils.TraktSigninDialogPreference;

public abstract class LeanbackActivity extends Activity {

    private BroadcastReceiver mTraktRelogBroadcastReceiver;
    private AlertDialog mTraktRelogAlertDialog;

    @Override
    protected void onCreate(Bundle saved){
        super.onCreate(saved);
        //in case we need to re-log in trakt
        mTraktRelogBroadcastReceiver = new BroadcastReceiver(){

            @Override
            public void onReceive(Context context, Intent intent) {
                if( System.currentTimeMillis() - Trakt.sLastTraktRefreshToken > Trakt.ASK_RELOG_FREQUENCY&&(mTraktRelogAlertDialog==null||!mTraktRelogAlertDialog.isShowing())) {
                    Trakt.sLastTraktRefreshToken = System.currentTimeMillis();
                    AlertDialog.Builder alert = new AlertDialog.Builder(LeanbackActivity.this);
                    alert.setTitle(R.string.trakt_signin_summary_logged_error)
                            .setMessage(R.string.trakt_relog_description)
                            .setPositiveButton(R.string.trakt_signin, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    TraktSigninDialogPreference dialog = new TraktSigninDialogPreference(LeanbackActivity.this, null);
                                    dialog.onClick();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null);
                    mTraktRelogAlertDialog = alert.create();
                    mTraktRelogAlertDialog.show();
                }
            }
        };
    }

    public void onResume(){
        super.onResume();
        registerReceiver(mTraktRelogBroadcastReceiver, new IntentFilter(Trakt.TRAKT_ISSUE_REFRESH_TOKEN));
    }

    public void onPause(){
        super.onPause();
        unregisterReceiver(mTraktRelogBroadcastReceiver);
    }

    /**
     * Catch the SEARCH key to launch our search instead of the Android TV general search
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
            Intent intent = new Intent(this, VideoSearchActivity.class);
            intent.putExtra(VideoSearchActivity.EXTRA_SEARCH_MODE, VideoSearchActivity.SEARCH_MODE_ALL);
            startActivity(intent);
            return true;
        }
        return super.onKeyDown(keyCode, event); // default
    }
}
