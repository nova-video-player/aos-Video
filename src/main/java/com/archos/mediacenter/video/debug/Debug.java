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

package com.archos.mediacenter.video.debug;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.archos.environment.ArchosUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by alexandre on 12/10/16.
 */

public class Debug {

    private static FileWriter fw;
    public static String ARCHOS_DEBUG_FOLDER_PATH = "/sdcard/archos_debug/";
    private static String DEBUG_LOG_FILE="logcat";

    public static void startLogcatRecording(){
        if(isDebuggable()) {
            new Thread() {
                public void run() {
                    try {
                        Process process = Runtime.getRuntime().exec("logcat -f " + getFilePath());


                    } catch (IOException e) {
                    }
                }
            }.start();
        }


    }

    private static boolean isDebuggable()
    {
        boolean debuggable = false;
        Context ctx = ArchosUtils.getGlobalContext();
        PackageManager pm = ctx.getPackageManager();
        try
        {
            ApplicationInfo appinfo = pm.getApplicationInfo(ctx.getPackageName(), 0);
            debuggable = (0 != (appinfo.flags & ApplicationInfo.FLAG_DEBUGGABLE));
        }
        catch(PackageManager.NameNotFoundException e)
        {
        /*debuggable variable will remain false*/
        }

        return debuggable;
    }

    private static void log(String s) {
        String path = getFilePath();
        if(fw==null) {
            File file = new File(path);
            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                fw = new FileWriter(path,true);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        try {
            fw.append(s+"\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static String getFilePath() {
        return ARCHOS_DEBUG_FOLDER_PATH+DEBUG_LOG_FILE+System.currentTimeMillis();
    }
}
