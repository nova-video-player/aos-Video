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

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.DisplayCutout;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import static android.view.RoundedCorner.POSITION_BOTTOM_LEFT;
import static android.view.RoundedCorner.POSITION_BOTTOM_RIGHT;
import static android.view.RoundedCorner.POSITION_TOP_LEFT;
import static android.view.RoundedCorner.POSITION_TOP_RIGHT;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.Player;
import com.archos.mediacenter.video.player.PlayerActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by alexandre on 02/06/17.
 */

public class MiscUtils {

    private static final Logger log = LoggerFactory.getLogger(MiscUtils.class);

    public static boolean hasCutout = false;

    private static Handler handler;
    private static final int DELAY_MILLIS_NORMAL = 250; // Delay for applying relayout
    // AUTODIM_TIMEOUT_MS = 2250 in https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/SystemUI/src/com/android/systemui/navigationbar/views/NavigationBar.java
    private static final int DELAY_MILLIS_GESTURE_NAVIGATION = 2300; // Delay for applying relayout

    private static Runnable relayoutRunnable;

    private static int mCutoutLeft, mCutoutTop, mCutoutRight, mCutoutBottom;
    private static int mVideoWidth, mVideoHeight, mVideoAspect;

    public static Handler getHandler() {
        if (handler == null) {
            if (Looper.myLooper() == null) {
                Looper.prepare(); // prepare lopper if necessary
            }
            handler = new Handler(Looper.getMainLooper()); // use main lopper
        }
        return handler;
    }

    public static boolean isGooglePlayServicesAvailable(Context context){
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo("com.google.android.gms", 0);
            return packageInfo!=null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isOnTV(Context context) {
        return(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK));
    }

    public static boolean isAndroidTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        // Check if the UiModeManager object is null
        if (uiModeManager != null) {
            return (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION);
        } else {
            // UiModeManager is not available on this device
            return false;
        }
    }
    
    public static boolean isEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }

    public static void dumpBundle(Bundle bundle, String TAG, Boolean isDebug) {
        if (isDebug) {
            if (bundle != null) {
                Set<String> keys = bundle.keySet();
                Iterator<String> it = keys.iterator();
                log.info(TAG + " bundle dump start");
                while (it.hasNext()) {
                    String key = it.next();
                    log.info(TAG + " [" + key + "=" + bundle.get(key) + "]");
                }
                log.info(TAG + " bundle dump stop");
            }
        }
    }

    public static int dp2Px(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static float px2Dp(float px) {
        return px / Resources.getSystem().getDisplayMetrics().density;
    }

    // retrieve activity from context
    public static Activity getActivityFromContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static int getNavigationBarHeight(Context context) {
        int navigationBarHeight = 0;
        Resources resources = context.getResources();
        int resourceIdNavBarHeight = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        // check if navigation bar is displayed because chromeos reports a navigation_bar_height of 84 but there is none displayed
        if (resourceIdNavBarHeight > 0 && hasNavigationBar(resources))
            navigationBarHeight = resources.getDimensionPixelSize(resourceIdNavBarHeight);
        log.debug("getNavigationBarHeight: navigationBarHeight=" + navigationBarHeight);
        return navigationBarHeight;
    }

    public static boolean isGestureAreaDisplayed(Context context) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
                Insets insets = windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures());
                return insets.bottom > 0;
            }
        }
        return false;
    }

    public static int getGestureAreaHeight(Context context) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
                Insets insets = windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures());
                return insets.bottom;
            }
        }
        return 0;
    }

    public static int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0)
            result = context.getResources().getDimensionPixelSize(resourceId);
        return result;
    }

    private static boolean hasNavigationBar(Resources resources) {
        int navBarId = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        log.debug("hasNavigationBar: navBarId=" + navBarId + ", hasNavBar=" + resources.getBoolean(navBarId));
        return navBarId > 0 && resources.getBoolean(navBarId);
    }

    public static boolean isNavBarAtBottom(Context context) {
        // detect navigation bar orientation https://stackoverflow.com/questions/21057035/detect-android-navigation-bar-orientation
        final boolean isNavAtBottom = (context.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
                || (context.getResources().getConfiguration().smallestScreenWidthDp >= 600);
        log.debug("isNavBarAtBottom: NavBarAtBottom=" + isNavAtBottom);
        return isNavAtBottom;
    }

    public static boolean isSystemBarOnBottom(Context mContext) {
        Resources res=mContext.getResources();
        Configuration cfg=res.getConfiguration();
        DisplayMetrics dm=res.getDisplayMetrics();
        boolean canMove=(dm.widthPixels != dm.heightPixels &&
                cfg.smallestScreenWidthDp < 600);
        return(!canMove || dm.widthPixels < dm.heightPixels);
    }

    public static boolean isNavigationBarOnBottom(View rootView, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use WindowInsets API for API 30+
            WindowInsets insets = rootView.getRootWindowInsets();
            if (insets != null) {
                Insets navigationBarInsets = insets.getInsets(WindowInsets.Type.navigationBars());
                int bottomInset = navigationBarInsets.bottom;
                int rightInset = navigationBarInsets.right;
                int leftInset = navigationBarInsets.left;

                // Navigation bar is on the bottom if the bottom inset is greater than 0
                return bottomInset > 0;
            }
        } else {
            // Fallback for API < 30
            Resources resources = context.getResources();
            Configuration config = resources.getConfiguration();
            DisplayMetrics dm = resources.getDisplayMetrics();

            // Check if the navigation bar can move (e.g., on phones)
            boolean canMove = (dm.widthPixels != dm.heightPixels && config.smallestScreenWidthDp < 600);

            // Navigation bar is on the bottom if:
            // - It cannot move, or
            // - The screen is in portrait orientation
            return !canMove || dm.widthPixels < dm.heightPixels;
        }

        // Default to true if unable to determine
        return true;
    }

    public static boolean hasNavBar(Resources resources) {
        int id = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        if (id > 0) return resources.getBoolean(id);
        else return false;
    }

    public static int getSystemBarHeight(Resources resources) {
        if (!hasNavBar(resources))
            return 0;
        int orientation = resources.getConfiguration().orientation;
        //Only phone between 0-599 has navigationbar can move
        boolean isSmartphone = resources.getConfiguration().smallestScreenWidthDp < 600;
        if (isSmartphone && Configuration.ORIENTATION_LANDSCAPE == orientation) return 0;
        int id = resources
                .getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_height_landscape", "dimen", "android");
        if (id > 0) return resources.getDimensionPixelSize(id);
        return 0;
    }

    public static int getSystemBarWidth(Resources resources) {
        if (hasNavBar(resources)) return 0;
        int orientation = resources.getConfiguration().orientation;
        //Only phone between 0-599 has navigationbar can move
        boolean isSmartphone = resources.getConfiguration().smallestScreenWidthDp < 600;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && isSmartphone) {
            int id = resources.getIdentifier("navigation_bar_width", "dimen", "android");
            if (id > 0) return resources.getDimensionPixelSize(id);
        }
        return 0;
    }

    public static int getActionBarHeight(Context context) {
        int actionBarHeight = 0;
        final TypedArray styledAttributes = context.getTheme().obtainStyledAttributes(
                new int[] { android.R.attr.actionBarSize }
        );
        actionBarHeight = (int) styledAttributes.getDimension(0, 0);
        styledAttributes.recycle();
        return actionBarHeight;
    }

    public static View getActionBarView(Window window) {
        View v = window.getDecorView();
        return v.findViewById(R.id.action_bar_container);
    }

    public static int getRoundCornersRadius(WindowInsets insets) {
        // determine round edges radius
        // needed to shift subs when using left/right top/bottom positions
        int radius = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final RoundedCorner topRight = insets.getRoundedCorner(POSITION_TOP_RIGHT);
            final int radiusTopRight = (topRight != null ? topRight.getRadius() : 0);
            final RoundedCorner topLeft = insets.getRoundedCorner(POSITION_TOP_LEFT);
            final int radiusTopLeft = (topLeft != null ? topLeft.getRadius() : 0);
            final RoundedCorner bottomRight = insets.getRoundedCorner(POSITION_BOTTOM_RIGHT);
            final int radiusBottomRight = (bottomRight != null ? bottomRight.getRadius() : 0);
            final RoundedCorner bottomLeft = insets.getRoundedCorner(POSITION_BOTTOM_LEFT);
            final int radiusBottomLeft = (bottomLeft != null ? bottomLeft.getRadius() : 0);
            radius = Math.max(Math.max(radiusTopLeft, radiusTopRight), Math.max(radiusBottomLeft, radiusBottomRight));
        }
        return radius;
    }

    // Design principles
    // videoView does not avoid round edges
    // videoView can avoid cutout conditionally resulting in shifting videoView of screen size minus cutout safe insets
    // subtitleTextView avoids round edges to avoid text clipping
    // subtitleTextView and subtitleGfxView are centered in the videoView
    // subtitleTextView a
    // subtitleGfxView has a fixed size and is centered in the videoView and does not avoid round edges to preserve aspect ratio
    // playerControllerView always avoids cutouts and navigation bar or gesture navigation area
    // -> videoViewLeftMargin=cutoutLeft, videoViewRightMargin=cutoutRight, videoViewTopMargin=cutoutTop, videoViewBottomMargin=cutoutBottom
    // -> subViewleftMargin=(cl-cr)/2-max[cl+cr,max(cl,r)+max(cr,r)]/2, subViewRightMargin=(cr-cl)/2+max[cl+cr,max(cl,r)+max(cr,r)]/2, subViewTopMargin=(ct-cb)/2-max[ct+cb,max(ct,r)+max(cb,r)]/2, subViewBottomMargin=(ct-cb)/2+max[ct+cb,max(ct,r)+max(cb,r)]/2
    // In case of subtitleGfxView, it needs to be scaled along with the videoView (surfaceControllerView) that can be larger than the screen size. Thus an additional negative margin needs to be applied to match with virtual screen size
    // Player.sPlayer.getSurfaceControllerWidth(), Player.sPlayer.getSurfaceControllerHeight() is for the video surface used

    // Calculus to center subtitle view inside video view when cutout margins are applied on video view and subtitle view avoids round edges
    // Screen width w by height h in sizes. video window and subtitle window.
    // Both windows are of screen height h.
    // The video window due to cutout in the screen need to occupy between abscissa [cl, w-cr] (i.e. cl left margin and cr right margin).
    // Because of round edges to avoid text clipping, subtitle window needs to occupy between abscissa [max(cr, r), w-max(cl,r)] where r is the round edge radius.
    // The video window is set but I want to adjust size of the subtitle window to be centered inside the video window but in respecting the [max(cr, r), w-max(cl,r)] abscissa boundary.
    // Below is the calculus to derive left and right margin to apply to this subtitle window
    // Wv=w-cl-cr, Ws=w-max(cl,r)-max(cr,r)
    // subtitle new width for centering W=min(Wv,Ws)=w-max[cl+cr,max(cl,r)+max(cr,r)]
    // both centers coincides c=cl+Wv/2=w/2+(cl-cr)/2 (computed only with Wv)
    // subtitleLeftMargin slm=c-W/2=w/2+(cl-cr)/2-w/2+max[cl+cr,max(cl,r)+max(cr,r)]/2=(cl-cr)/2+max[cl+cr,max(cl,r)+max(cr,r)]/2
    // subtitleRightMargin srm=w-c-W/2=w-w/2-(cl-cr)/2-w/2+max[cl+cr,max(cl,r)+max(cr,r)]/2=(cr-cl)/2+max[cl+cr,max(cl,r)+max(cr,r)]/2

    public static int calcMarginAvoidEdge(int left, int right, int radius) {
        // this is for left margin but holds with permutations
        return Math.round((left - right) / 2.0f + Math.max(left + right, Math.max(left, radius) + Math.max(right, radius)) / 2.0f);
    }


    // this adjust margins but not view size
    public static void adjustViewLayoutForInsets(Context context, View rootView, View viewLayout, String viewName, boolean navigationBarShowing, boolean systemBarShowing, boolean actionBarShowing,
                                                 boolean controlBarShowing, boolean isNavBarOnBottom, boolean isGestureAreaShowing,
                                                 int additionalBottomMargin, int alreadyAppliedBottomMargin,
                                                 boolean adjustLeft, boolean adjustTop, boolean adjustRight, boolean adjustBottom,
                                                 boolean avoidCutoutLeft, boolean avoidCutoutTop, boolean avoidCutoutRight, boolean avoidCutoutBottom,
                                                 boolean avoidRoundEdges, boolean applyGlobalShift) {
        // additionalBottomMargin is the margin to apply to the bottom of the view (captures for subtitleView the height of playerController control bar being an external component if displayed)
        // alreadyAppliedBottomMargin is the margin already applied to the bottom of the view (captures the vertical position of subtitleView set in subtitles settings)
        // globalShiftLeft and globalShiftUp are to shift the view globally in the screen and needed for subtitleGfxView to match the videoView (surfaceControllerView) that can be larger than the screen size
        if (Player.sPlayer == null) {
            log.debug("adjustViewLayoutForInsets: {} Player.sPlayer is null, aborting", viewName);
            return;
        }
        if (viewLayout == null || rootView == null) {
            log.debug("adjustViewLayoutForInsets: {} viewLayout or rootView is null, aborting", viewName);
            return;
        }
        log.debug("adjustViewLayoutForInsets: {} navigationBarShowing={}, systemBarShowing={}, actionBarShowing={}, controlBarShowing={}, isNavBarOnBottom={}, isGestureAreaShowing={}, additionalBottomMargin={}, alreadyAppliedBottomMargin={}",
                viewName, navigationBarShowing, systemBarShowing, actionBarShowing, controlBarShowing, isNavBarOnBottom, isGestureAreaShowing, additionalBottomMargin, alreadyAppliedBottomMargin);
        log.debug("adjustViewLayoutForInsets: {} getNavigationBarHeight()={}, getGestureAreaHeight()={}, getStatusBarHeight()={}, getActionBarHeight()={}, getSystemBarHeight()={}",
                viewName, MiscUtils.getNavigationBarHeight(context), MiscUtils.getGestureAreaHeight(context), MiscUtils.getStatusBarHeight(context), MiscUtils.getActionBarHeight(context), MiscUtils.getSystemBarHeight(context.getResources()));
        log.debug("adjustViewLayoutForInsets: {} additionalBottomMargin={}, alreadyAppliedBottomMargin={}", viewName, additionalBottomMargin, alreadyAppliedBottomMargin);
        log.debug("adjustViewLayoutForInsets: {} adjust=({},{},{},{}), avoidCutout=({},{},{},{}), avoidRoundEdges={}, applyGlobalShift={}",
                viewName, adjustLeft, adjustTop, adjustRight, adjustBottom, avoidCutoutLeft, avoidCutoutTop, avoidCutoutRight, avoidCutoutBottom, avoidRoundEdges, applyGlobalShift);
        log.debug("adjustViewLayoutForInsets: videoSurface=({},{}), ar={}, screen=({},{})", Player.sPlayer.getVideoWidth(), Player.sPlayer.getVideoHeight(), Player.sPlayer.getVideoAspect(), PlayerActivity.getScreenWidth(), PlayerActivity.getScreenHeight());
        int left, top, right, bottom;
        left = top = right = bottom = 0;
        int systemBarLeft, systemBarTop, systemBarRight, systemBarBottom;
        boolean navAreaPresentOnBottom = (isGestureAreaShowing || (isNavBarOnBottom && navigationBarShowing));
        int rotation = (PlayerActivity.isRotationLocked() ? PlayerActivity.getLockedRotation(): ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation());

        WindowInsets insets = rootView.getRootWindowInsets();
        if (insets == null) return;
        setCutoutMetrics(insets, rootView, null);
        if (avoidCutoutLeft) left = mCutoutLeft;
        if (avoidCutoutTop) top = mCutoutTop;
        if (avoidCutoutRight) right = mCutoutRight;
        if (avoidCutoutBottom) bottom = mCutoutBottom;
        int screenVideoAvailableWidth = PlayerActivity.getScreenWidth() - left - right; // screen width minus cutout insets if applied which provides width of area for videoView
        int screenVideoAvailableHeight = PlayerActivity.getScreenHeight() - top - bottom; // screen height minus cutout insets if applied which provides height of area for videoView
        if (Player.sPlayer.getSurfaceControllerWidth() == 0 || Player.sPlayer.getSurfaceControllerHeight() == 0) {
            log.debug("adjustViewLayoutForInsets: {} Player.sPlayer.getSurfaceControllerWidth() or getSurfaceControllerHeight() is 0, aborting", viewName);
            return;
        }
        // when not null this is the shift to apply to recenter the subtitleView when larger then the screen
        int globalShiftLeft = Math.min(Math.round((screenVideoAvailableWidth - Player.sPlayer.getSurfaceControllerWidth()) / 2.0f), 0);
        int globalShiftUp = Math.min(Math.round((screenVideoAvailableHeight - Player.sPlayer.getSurfaceControllerHeight()) / 2.0f), 0);

        // when not null this is the margin to apply to center the subtitleView in the allowed video space
        int centerLeftMargin = Math.max(Math.round((screenVideoAvailableWidth - Player.sPlayer.getSurfaceControllerWidth()) / 2.0f), 0);
        int centerTopMargin = Math.max(Math.round((screenVideoAvailableHeight - Player.sPlayer.getSurfaceControllerHeight()) / 2.0f), 0);

        int radius = 0; // round edges radius needed to shift subs when using l/r/t/b positions to be determined below
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());
            Insets navigationBarInsets = insets.getInsets(WindowInsets.Type.navigationBars());
            Insets statusBarInsets = insets.getInsets(WindowInsets.Type.statusBars());
            radius = getRoundCornersRadius(insets);
            log.debug("adjustViewLayoutForInsets: {} LTRB systemBarsInsets=({},{},{},{}), navigationBarInsets=({},{},{},{}), statusBarInsets=({},{},{},{}), cutout=({},{},{},{}), roundedCornerRadius={}",
                    viewName, systemBarsInsets.left, systemBarsInsets.top, systemBarsInsets.right, systemBarsInsets.bottom,
                    navigationBarInsets.left, navigationBarInsets.top, navigationBarInsets.right, navigationBarInsets.bottom,
                    statusBarInsets.left, statusBarInsets.top, statusBarInsets.right, statusBarInsets.bottom,
                    mCutoutLeft, mCutoutTop, mCutoutRight, mCutoutBottom, radius);
            systemBarLeft = systemBarsInsets.left;
            systemBarTop = systemBarsInsets.top;
            systemBarRight = systemBarsInsets.right;
            systemBarBottom = systemBarsInsets.bottom;
        } else {
            log.debug("adjustViewLayoutForInsets: {} LTRB insets=({},{},{},{}), cutout=({},{},{},{})", viewName, insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom(),
                    mCutoutLeft, mCutoutTop, mCutoutRight, mCutoutBottom);
            radius = 0;
            systemBarLeft = insets.getSystemWindowInsetLeft();
            systemBarTop = insets.getSystemWindowInsetTop();
            systemBarRight = insets.getSystemWindowInsetRight();
            systemBarBottom = insets.getSystemWindowInsetBottom();
        }
        // avoidRoundEdges is false for gfx subtitleView
        if (avoidRoundEdges) { // at this point left/top/right/bottom is already set to cutout insets if applied
            // this centers the subtitle view inside the video view avoiding text clipping due to round edges
            // avoiding round edges is only needed for left/right margins, benefit to limit to left/right is to not shift too high subs in landscape mode
            left = calcMarginAvoidEdge(left, right, radius);
            top = calcMarginAvoidEdge(top, bottom, 0);
            right = calcMarginAvoidEdge(right, left, radius);
            bottom = calcMarginAvoidEdge(bottom, top, 0);
        }
        int uncompressibleBottom = bottom; // keep it for later since it represents the bottom margin that cannot be compressed i.e. not influenced by OSD playerController or system bars
        // only shift if not already overlapping
        if (adjustLeft && systemBarShowing && left < systemBarLeft) left += systemBarLeft - left;
        if (adjustTop && systemBarShowing && top < systemBarTop) top += systemBarTop - top;
        if (adjustRight && systemBarShowing && right < systemBarRight) right += systemBarRight - right;
        if (adjustBottom && navAreaPresentOnBottom && bottom < systemBarBottom) bottom += systemBarBottom - bottom; // bottom margin is 0 if no navigation bar
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) viewLayout.getLayoutParams();
        int prevLeft, prevTop, prevRight, prevBottom;
        prevLeft = layoutParams.leftMargin; prevTop = layoutParams.topMargin; prevRight = layoutParams.rightMargin; prevBottom = layoutParams.bottomMargin;
        if (applyGlobalShift) { // debias the previous margins
            prevLeft -= globalShiftLeft + centerLeftMargin;
            prevTop -= globalShiftUp + centerTopMargin;
        }
        log.debug("adjustViewLayoutForInsets, {} orientation is {}({}), isRotationLocked={}", viewName, PlayerActivity.getHumanReadableRotation(rotation), rotation, PlayerActivity.isRotationLocked());
        // extra logic for subtitles handling that need to be shifted up above controlBar of playerController if the subtitleVposPixel is not shifting them already above
        // Whether Subtitle minimum Vertical Position should be above the StatusBar or not. Only changes the Vertical Position if it is too low.
        int shiftBottom = Math.max(bottom + additionalBottomMargin - alreadyAppliedBottomMargin, 0);
        log.debug("adjustViewLayoutForInsets: {} layoutParams ({},{},{},{})->({},{},{},{}), applyGlobalShift={}, globalShift=({},{}), centerShift=({},{})",
                viewName, prevLeft, prevTop, prevRight, prevBottom,
                left, top, right, shiftBottom, applyGlobalShift, globalShiftLeft, globalShiftUp, centerLeftMargin, centerTopMargin);
        // do not delay when having a gfx subtitle or floating player hence ! applyGlobalShift
        if (! applyGlobalShift && prevBottom > uncompressibleBottom && shiftBottom == uncompressibleBottom && navAreaPresentOnBottom) {
            log.debug("adjustViewLayoutForInsets: Delaying relayout due to give time to navigation bar to fade out");
            // Schedule the delayed relayout
            if (relayoutRunnable != null) {
                log.debug("adjustViewLayoutForInsets: Cancel previous delayed relayout");
                getHandler().removeCallbacks(relayoutRunnable);
            }
            final int finalLeft, finalTop, finalRight, finalShiftBottom;
            finalLeft = left; finalTop = top; finalRight = right; finalShiftBottom = shiftBottom;
            relayoutRunnable = () -> {
                layoutParams.leftMargin = finalLeft;
                if (applyGlobalShift) layoutParams.leftMargin += globalShiftLeft + centerLeftMargin;
                layoutParams.topMargin = finalTop;
                if (applyGlobalShift) layoutParams.topMargin += globalShiftUp + centerTopMargin;
                layoutParams.rightMargin = finalRight;
                layoutParams.bottomMargin = finalShiftBottom;
                if (applyGlobalShift) {
                    layoutParams.height = Player.sPlayer.getSurfaceControllerHeight();
                    layoutParams.width = Player.sPlayer.getSurfaceControllerWidth();
                }
                viewLayout.setLayoutParams(layoutParams);
                viewLayout.forceLayout();
                viewLayout.requestLayout();
                log.debug("adjustViewLayoutForInsets: Delayed relayout applied");
            };
            int delay = 0;
            if (isGestureAreaShowing) {
                delay = DELAY_MILLIS_GESTURE_NAVIGATION;
            } else if (isNavBarOnBottom && navigationBarShowing) {
                delay = DELAY_MILLIS_NORMAL;
            }
            // wait a little: avoid a glitch (subtitles being displayed under the system bar for x ms), note that gesture bar fades away slowly
            getHandler().postDelayed(relayoutRunnable, delay);
        } else {
            // Apply the relayout immediately
            layoutParams.leftMargin = left;
            if (applyGlobalShift) layoutParams.leftMargin += globalShiftLeft + centerLeftMargin;
            layoutParams.topMargin = top;
            if (applyGlobalShift) layoutParams.topMargin += globalShiftUp + centerTopMargin;
            layoutParams.rightMargin = right;
            layoutParams.bottomMargin = shiftBottom;
            if (applyGlobalShift) {
                layoutParams.height = Player.sPlayer.getSurfaceControllerHeight();
                layoutParams.width = Player.sPlayer.getSurfaceControllerWidth();
            }
            viewLayout.setLayoutParams(layoutParams);
            viewLayout.forceLayout();
            viewLayout.requestLayout();
            log.debug("adjustViewLayoutForInsets: Immediate relayout applied");
        }
        log.debug("adjustViewLayoutForInsets: {} finalLayoutMargins=({},{},{},{}), wh=({},{})", viewName, layoutParams.leftMargin, layoutParams.topMargin, layoutParams.rightMargin, layoutParams.bottomMargin, layoutParams.width, layoutParams.height);
    }

    public interface CutoutMetricsSetter {
        void setCutoutMetrics(int left, int top, int right, int bottom);
    }

    public static void setCutoutMetrics(WindowInsets insets, View rootView, CutoutMetricsSetter setter) {
        DisplayCutout cutout = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cutout = insets.getDisplayCutout();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cutout = rootView.getRootWindowInsets().getDisplayCutout();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cutout != null) {
            mCutoutLeft = cutout.getSafeInsetLeft();
            mCutoutTop = cutout.getSafeInsetTop();
            mCutoutRight = cutout.getSafeInsetRight();
            mCutoutBottom = cutout.getSafeInsetBottom();
            if (setter != null) {
                setter.setCutoutMetrics(mCutoutLeft, mCutoutTop, mCutoutRight, mCutoutBottom);
            }
        } else {
            mCutoutLeft = mCutoutTop = mCutoutRight = mCutoutBottom = 0;
            if (setter != null) {
                setter.setCutoutMetrics(0, 0, 0, 0);
            }
        }
    }

    private static final boolean ENABLE_MULTITHREAD_EXECUTOR = true;

    public static boolean isEnableMultithreadExecutor() {
        return ENABLE_MULTITHREAD_EXECUTOR;
    }

    public static int getNumberOfThreads() {
        int numberOfThreads = ENABLE_MULTITHREAD_EXECUTOR ? Runtime.getRuntime().availableProcessors() : 1;
        // limit to 4 threads
        if (numberOfThreads > 4) numberOfThreads = 4;
        log.debug("getNumberOfThreads: numberOfThreads={}", numberOfThreads);
        return numberOfThreads;
    }
}
