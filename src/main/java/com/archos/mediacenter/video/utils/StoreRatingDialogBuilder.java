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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.archos.mediacenter.video.R;

/**
 * Created by alexandre on 13/06/17.
 */

public class StoreRatingDialogBuilder{
    public static final String NUM_PLAYER_LAUNCHED = "num_player_launched";
    private static final int TRIGGER_VALUE = 12;

    public static void displayStoreRatingDialogIfNeeded(Context context){
        int current = PreferenceManager.getDefaultSharedPreferences(context).getInt(NUM_PLAYER_LAUNCHED, 0);
        if(current == TRIGGER_VALUE){
            showDialog(context);
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(NUM_PLAYER_LAUNCHED, current+1).commit();
    }

    private static void showDialog(final Context context) {

        String title ="";
        int icon=-1 ;
        icon= R.drawable.filetype_video;
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle(R.string.rate_us_title);

        alertDialog.setMessage(context.getText(R.string.rate_us));
        alertDialog.setCancelable(true);
        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                boolean googlePlay = MiscUtils.isGooglePlayServicesAvailable(context);
                try {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse((googlePlay ? "market://details?id=" : "amzn://apps/android?p=") +context.getPackageName())));
                } catch (ActivityNotFoundException e1) {
                    try {
                        // Breaks AndroidTV acceptance but required to open link in app instead of browser
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse((googlePlay ? "http://play.google.com/store/apps/details?id=" : "http://www.amazon.com/gp/mas/dl/android?p=") +context.getPackageName())));
                        //WebUtils.openWebLink(context, googlePlay ? "http://play.google.com/store/apps/details?id=" : "http://www.amazon.com/gp/mas/dl/android?p=" +context.getPackageName());
                    } catch (ActivityNotFoundException e2) {
                    }
                }
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel,null);
        alertDialog.show();


    }
}
