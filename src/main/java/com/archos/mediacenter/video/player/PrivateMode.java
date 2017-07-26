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

package com.archos.mediacenter.video.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.CheckBox;

import com.archos.mediacenter.video.R;


public class PrivateMode {

    private static String DONT_SHOW_PRIVATE_MODE_DIALOG = "dont_show_private_mode_dialog";
    private static boolean DONT_SHOW_PRIVATE_MODE_DIALOG_DEFAULT = false;

    private static boolean mActivated = false;

    public static void setActive(boolean activate) {
        mActivated = activate;
    }

    public static boolean isActive() {
        return mActivated;
    }

    public static void toggle() {
        mActivated = !mActivated;
    }

    public static boolean canShowDialog(Context context) {
        return !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DONT_SHOW_PRIVATE_MODE_DIALOG, DONT_SHOW_PRIVATE_MODE_DIALOG_DEFAULT);
    }

    private static void dontShowDialog(Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(DONT_SHOW_PRIVATE_MODE_DIALOG, true);
        editor.commit();
    }

    public static void showDialog(final Activity activity) {
        View customView = activity.getLayoutInflater().inflate(R.layout.private_mode_dialog, null);
        final CheckBox dontShowAgain =  (CheckBox)customView.findViewById(R.id.dont_show_again);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.private_mode)
                .setView(customView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (dontShowAgain.isChecked()) {
                            dontShowDialog(activity);
                        }
                    }
                })
                .create().show();
    }

    public static void resetDontShowDialog(Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(DONT_SHOW_PRIVATE_MODE_DIALOG, false);
        editor.commit();
    }

}
