// Copyright 2026 Courville Software
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
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.LinkedHashMap;
import java.util.WeakHashMap;

public final class SystemBarUtils {

    private static final String NAVIGATION_BAR_OVERLAY_TAG = "nova.navigationBarOverlay";

    private static final WeakHashMap<Activity, LinkedHashMap<Object, Integer>> sNavigationBarColors =
            new WeakHashMap<>();

    private SystemBarUtils() {
    }

    public static void setNavigationBarOverlay(Activity activity, int color, Object owner) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
        LinkedHashMap<Object, Integer> colors = sNavigationBarColors.get(activity);
        if (colors == null) {
            colors = new LinkedHashMap<>();
            sNavigationBarColors.put(activity, colors);
        }
        colors.remove(owner);
        colors.put(owner, color);
        getOrCreateOverlay(activity).setBackgroundColor(color);
    }

    public static void clearNavigationBarOverlay(Activity activity, Object owner) {
        LinkedHashMap<Object, Integer> colors = sNavigationBarColors.get(activity);
        if (colors != null) {
            colors.remove(owner);
            if (!colors.isEmpty()) {
                Integer lastColor = null;
                for (Integer value : colors.values()) {
                    lastColor = value;
                }
                View overlay = findOverlay(activity);
                if (overlay != null && lastColor != null) {
                    overlay.setBackgroundColor(lastColor);
                }
                return;
            }
            sNavigationBarColors.remove(activity);
        }
        removeOverlay(activity);
    }

    public static void clearNavigationBarOverlay(Activity activity) {
        sNavigationBarColors.remove(activity);
        removeOverlay(activity);
    }

    private static View getOrCreateOverlay(Activity activity) {
        View overlay = findOverlay(activity);
        if (overlay != null) {
            return overlay;
        }
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        overlay = new View(activity);
        overlay.setTag(NAVIGATION_BAR_OVERLAY_TAG);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (view, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null && params.height != bottom) {
                params.height = bottom;
                view.setLayoutParams(params);
            }
            return insets;
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM);
        decorView.addView(overlay, params);
        ViewCompat.requestApplyInsets(overlay);
        return overlay;
    }

    private static View findOverlay(Activity activity) {
        return activity.getWindow().getDecorView().findViewWithTag(NAVIGATION_BAR_OVERLAY_TAG);
    }

    private static void removeOverlay(Activity activity) {
        View overlay = findOverlay(activity);
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
    }
}
