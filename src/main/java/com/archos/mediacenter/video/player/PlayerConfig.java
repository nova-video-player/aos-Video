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

import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;

import com.archos.environment.SystemPropertiesProxy;

public class PlayerConfig {
    private static boolean sHasArchosEnhancement;
    private static boolean sHasHackedFullScreen;
    private static boolean sCanSystemBarHide;
    private static boolean sUseAvosPlayer;
    
    static {
        String hardware = SystemPropertiesProxy.get("ro.hardware", "null");

        sUseAvosPlayer = true;
        sHasArchosEnhancement = hardware.equals("archos");
        if (sHasArchosEnhancement) {
            sHasHackedFullScreen = true;
            sCanSystemBarHide = true;
        } else {
            if (Build.VERSION.SDK_INT >= 19 /* Android 4.4 */) {
                sHasHackedFullScreen = false;
                sCanSystemBarHide = true;
            } else {
                Object wm = null;
                try {
                    ClassLoader cl = ClassLoader.getSystemClassLoader();
                    Class ServiceManager = cl.loadClass("android.os.ServiceManager");
                    Class Stub = cl.loadClass("android.view.IWindowManager$Stub");

                    Class[] paramTypes = new Class[1];
                    paramTypes[0] = String.class;

                    Method getService = ServiceManager.getMethod("getService", paramTypes);

                    paramTypes[0] = IBinder.class;
                    Method asInterface = Stub.getMethod("asInterface", paramTypes);

                    wm = asInterface.invoke(Stub, getService.invoke(ServiceManager, Context.WINDOW_SERVICE));
                } catch (Exception e) {}

                //final IWindowManager wm = (IWindowManager.Stub.asInterface(ServiceManager.getService(Context.WINDOW_SERVICE)));
                boolean canStatusBarHide = false;
                boolean hasSystemNavBar = true;

                if (Build.VERSION.SDK_INT <= 15) {
                    try {
                        /* wm.getClass is "android.view.IWindowManager" */
                        Method method = wm.getClass().getMethod("canStatusBarHide");
                        canStatusBarHide = (boolean) method.invoke(wm);
                    } catch (Exception e) {
                    }
                } else {
                    try {
                        Method method = wm.getClass().getMethod("hasSystemNavBar");
                        hasSystemNavBar = (boolean) method.invoke(wm);
                    } catch (Exception e) {
                    }
                }

                if ((hardware.equals("rk30board") || hardware.equals("rk2928board")) && hasSystemNavBar) {
                    sHasHackedFullScreen = true;
                    sCanSystemBarHide = true;
                } else {
                    sHasHackedFullScreen = false;
                    sCanSystemBarHide = canStatusBarHide || !hasSystemNavBar;
                }
            }

            if (false)
                Log.d("PlayerConfig", "sdk_int: " + Build.VERSION.SDK_INT
                    + " / sHasHackedFullScreen: " + sHasHackedFullScreen
                    + " / sCanSystemBarHide: " + sCanSystemBarHide);
        }
    }

    public static boolean hasArchosEnhancement() {
        return sHasArchosEnhancement;
    }
    public static boolean hasHackedFullScreen() {
        return sHasHackedFullScreen;
    }
    public static boolean hasFullScreen() {
        return sHasHackedFullScreen || sCanSystemBarHide;
    }
    public static boolean canSystemBarHide() {
        return sCanSystemBarHide;
    }

    /*
     * Before Android JB, there is no way to get the system and status bar size when there are hidden.
     * So, we have to create a new window for the controller and the subtitles,
     * because the main window (video surface) is full screen and we don't want to have the UI above the system or status bar.
     * On Android JB and after, we can put the controller, the video and the subtitles in the same window,
     * and specify the size of the controller and subtitle layout according to the system and status bar size.
     */
    public static boolean useOneWindowPerView() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
    }

    public static boolean useAvosPlayer(String uri) {
        if (!sUseAvosPlayer) {
            int i = uri.lastIndexOf('.');
            if (i != -1) {
                String ext = uri.substring(i, uri.length());
                Log.d("lala", "ext: " + ext);
                if (ext.equalsIgnoreCase(".mp4") || ext.equalsIgnoreCase(".m4v"))
                    return false;
            }
        }
        return true;
    }
}
