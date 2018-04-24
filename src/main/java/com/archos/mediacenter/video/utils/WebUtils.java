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
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.List;

/**
 * Created by vapillon on 22/06/15.
 */
public class WebUtils {

    public static void openWebLink(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (MiscUtils.isAndroidTV(context)) {
            // On AndroidTV, only webview is allowed
            intent.setClass(context, WebViewActivity.class);
        } else if (!isThereAnActivityToOpenWebLinks(context))
            // Force our basic WebView activity only in case the is no other web browser
            intent.setClass(context, WebViewActivity.class);
        context.startActivity(intent);
    }

    private static boolean isThereAnActivityToOpenWebLinks(Context context) {
        Intent pureIntent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://www.archos.com"));
        List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities(pureIntent, PackageManager.MATCH_DEFAULT_ONLY);

        for (ResolveInfo ri : activities) {
            ResolveInfo info = activities.get(0);
            if (info!=null) { // better safe than sorry
                ActivityInfo activityInfo =info.activityInfo;
                if (activityInfo!=null) { // better safe than sorry

                    // Do not take the stupid Android TV Stubs$BrowserStub into account...
                    if ("com.google.android.tv.frameworkpackagestubs.Stubs$BrowserStub".equals(activityInfo.name)) {
                        continue; // This Stub activity does nothing! don't count it as valid!
                    }

                    // Do not take mx player integrated web browser into account because it is not exported!
                    else if ("com.mxtech.videoplayer.ActivityWebBrowser".equals(activityInfo.name)) {
                        continue;
                    }
                    else
                        return true;
                }
            }
        }
        return false;
    }
}
