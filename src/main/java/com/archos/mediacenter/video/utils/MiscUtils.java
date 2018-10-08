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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.app.UiModeManager;
import android.content.res.Configuration;

import static android.content.Context.UI_MODE_SERVICE;

/**
 * Created by alexandre on 02/06/17.
 */

public class MiscUtils {

    public static boolean isGooglePlayServicesAvailable(Context context){
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo("com.google.android.gms", 0);
            return packageInfo!=null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isOnTV(Context context) {
        return(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK));
    }

    public static boolean isAndroidTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        return (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION);
    }

}
