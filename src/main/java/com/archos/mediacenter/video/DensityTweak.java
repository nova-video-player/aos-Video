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

package com.archos.mediacenter.video;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;

/**
 * Created by vapillon on 01/07/15.
 */
public class DensityTweak {

    final static public int USER_DEFINED_DENSITY_UNSET = -1;
    final static int DensityChoiceIds[] = new int[] {
            R.string.interface_size_very_small,
            R.string.interface_size_small,
            R.string.interface_size_standard,
            R.string.interface_size_large,
    };

    final CharSequence DensityChoices[];

    final static private String USER_DEFINED_DENSITY_KEY = "user_defined_density";
    final static private String USER_DEFINED_DENSITY_CONFIRMED_KEY = "user_defined_density_confirmed";
    final private Activity mActivity;
    final private SharedPreferences mPrefs;
    final private boolean mIsActualLeanbackDevice;

    public DensityTweak(Activity activity) {
        mActivity = activity;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        mIsActualLeanbackDevice = mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);

        DensityChoices = new CharSequence[DensityChoiceIds.length];
        for (int i=0; i<DensityChoiceIds.length; i++) {
            DensityChoices[i] = mActivity.getString(DensityChoiceIds[i]);
        }
    }

    /**
     * Apply the density set by the user to the current Context.
     * Can be called early in Activity.onCreate()
     * @return this, to allow chaining calls
     */
    public DensityTweak applyUserDensity() {

        // Do not do anything on actual leanback devices (the density is ok by default)
        if (mIsActualLeanbackDevice) {
            return this;
        }

        int userDefinedDensity =  getUserDefinedDensity();

        // do not touch anything if the user choose no density
        if (userDefinedDensity == USER_DEFINED_DENSITY_UNSET) {
            return this;
        }

        Resources res = mActivity.getResources();
        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        Configuration config = res.getConfiguration();
        displayMetrics.densityDpi = userDefinedDensity;
        config.densityDpi = userDefinedDensity;
        res.updateConfiguration(config, displayMetrics);
        return this;
    }

    /**
     * restores normal density without saving it for next start
     */
    public void temporaryRestoreDefaultDensity(){
        Resources res = mActivity.getResources();
        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        Configuration config = res.getConfiguration();
        displayMetrics.densityDpi = DisplayMetrics.DENSITY_DEFAULT;
        config.densityDpi = Configuration.DENSITY_DPI_UNDEFINED;
        res.updateConfiguration(config, displayMetrics);
    }

    /**
     * Display the custom density dialog to the user, if the device supports it and if the user did not already set it
     */
    public void showDensityChoiceIfNeeded() {
        if (needToAskUserToSetDensity()) {
            showDialog();
        }
    }

    /**
     * Reset the flag telling that the user choose a custom density
     */
    public void forceDensityDialogAtNextStart() {
        mPrefs.edit().putBoolean(USER_DEFINED_DENSITY_CONFIRMED_KEY, false).commit();
    }

    private int getTargetDensity() {
        // Did the user set a user-defined density
        int density = getUserDefinedDensity();

        // If not, return the current platform density
        if (density == USER_DEFINED_DENSITY_UNSET) {
            density = mActivity.getResources().getDisplayMetrics().densityDpi;
        }

        return density;
    }

    private int getUserDefinedDensity() {
        return mPrefs.getInt(USER_DEFINED_DENSITY_KEY, USER_DEFINED_DENSITY_UNSET);
    }

    private boolean needToAskUserToSetDensity() {
        // Do not do anything on actual leanback devices (the density is ok by default)
        if (mIsActualLeanbackDevice) {
            return false;
        }

        if (getUserDefinedDensity() == USER_DEFINED_DENSITY_UNSET ||
                mPrefs.getBoolean(USER_DEFINED_DENSITY_CONFIRMED_KEY, false) == false) {
            return true;
        }
        return false;
    }

    private void showDialog() {
        int currentSelection = getSelectedItemFromDensity(getTargetDensity());
        new AlertDialog.Builder(mActivity)
                .setTitle(R.string.interface_size)
                        //.setMessage("This TV interface was originally made for Android TV devices.\nYou may want to change the scale of the application on your device.")
                .setSingleChoiceItems(DensityChoices, currentSelection, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int density = getDensityFromSelectedItem(which);
                        // Change user density
                        PreferenceManager.getDefaultSharedPreferences(mActivity).edit()
                                .putInt(USER_DEFINED_DENSITY_KEY, density)
                                .commit();
                        // Quit and restart the calling activity
                        mActivity.finish();
                        mActivity.startActivity(new Intent(mActivity, mActivity.getClass()));
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PreferenceManager.getDefaultSharedPreferences(mActivity).edit()
                                        .putBoolean(USER_DEFINED_DENSITY_CONFIRMED_KEY, true)
                                        .commit();
                            }
                        }
                )
                .setCancelable(false) // user is forced to use the OK button to validate the choice
                .show();
    }

    /**
     *
     * @param density
     * @return -1 if the given density does not match any of the choices we have
     */
    private int getSelectedItemFromDensity(int density) {
        switch (density) {
            case DisplayMetrics.DENSITY_MEDIUM:
                return 0;
            case DisplayMetrics.DENSITY_HIGH:
                return 1;
            case DisplayMetrics.DENSITY_XHIGH:
                return 2;
            case DisplayMetrics.DENSITY_XXHIGH:
                return 3;
            default:
                return -1;
        }
    };

    private int getDensityFromSelectedItem(int selection) {
        switch (selection) {
            case 0:
                return DisplayMetrics.DENSITY_MEDIUM;
            case 1:
                return DisplayMetrics.DENSITY_HIGH;
            case 2:
            default: // default should never happen
                return DisplayMetrics.DENSITY_XHIGH;
            case 3:
                return DisplayMetrics.DENSITY_XXHIGH;
        }
    }
}
