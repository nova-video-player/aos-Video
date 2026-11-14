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

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.util.Log;

import com.archos.mediacenter.video.R;

import java.util.List;

public class FontsFolderSafPreference extends Preference {

    private static final String TAG = "FontsFolderSafPreference";
    private static final boolean DBG = false;

    private ActivityResultLauncher<Intent> mTreePickerLauncher;

    public void setTreePickerLauncher(ActivityResultLauncher<Intent> launcher) {
        mTreePickerLauncher = launcher;
    }

    /**
     * AOSP Android TV (and the Google TV builds derived from it) ship
     * FrameworkPackageStubs, whose Stubs$DocumentsStub declares an
     * ACTION_OPEN_DOCUMENT_TREE intent-filter at priority 99. That stub only shows the
     * "You don't have an app that can do this" toast and finishes, but it still resolves,
     * so resolveActivity() alone cannot tell a real folder picker from the TV placeholder.
     */
    private static boolean isStubFolderPicker(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.activityInfo == null) return false;
        String packageName = resolveInfo.activityInfo.packageName;
        if (packageName == null) return false;
        if (packageName.equals("com.android.tv.frameworkpackagestubs")
                || packageName.equals("com.google.android.tv.frameworkpackagestubs")
                || packageName.endsWith(".frameworkpackagestubs")) {
            return true;
        }
        String className = resolveInfo.activityInfo.name;
        return className != null && className.endsWith("$DocumentsStub");
    }

    /**
     * Returns the best real (non-stub) handler for ACTION_OPEN_DOCUMENT_TREE, or null if
     * only the TV stub and/or no handler is installed.
     */
    private static ResolveInfo resolveRealFolderPicker(Context context) {
        if (context == null) return null;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        PackageManager pm = context.getPackageManager();
        ResolveInfo defaultPicker = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (defaultPicker != null && !isStubFolderPicker(defaultPicker)) return defaultPicker;
        List<ResolveInfo> candidates = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (candidates != null) {
            for (ResolveInfo candidate : candidates) {
                if (!isStubFolderPicker(candidate)) return candidate;
            }
        }
        return null;
    }

    /**
     * Whether some app can actually show a folder picker for ACTION_OPEN_DOCUMENT_TREE.
     * Some Android TV / Google TV builds only ship the FrameworkPackageStubs placeholder
     * (which shows "You don't have an app that can do this"), so that case is treated as
     * unavailable. The matching <intent> is declared in the manifest <queries> so
     * third-party pickers remain visible under Android 11+ package visibility filtering.
     */
    public static boolean isFolderPickerAvailable(Context context) {
        return resolveRealFolderPicker(context) != null;
    }

    private void showNoFolderPickerMessage() {
        Toast.makeText(getContext(), R.string.no_folder_picker_available, Toast.LENGTH_LONG).show();
    }

    public FontsFolderSafPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (DBG) Log.d(TAG, "FontsFolderSafPreference");
    }

    public FontsFolderSafPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (DBG) Log.d(TAG, "FontsFolderSafPreference");
    }

    public void notifyChanged() {
        super.notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder v) {
        super.onBindViewHolder(v);
    }

    @Override
    public void onClick() {
        if (getOnPreferenceClickListener() != null) return;
        if (mTreePickerLauncher == null) {
            // No registered launcher (e.g. preference bound before onCreatePreferences()
            // finished wiring it up) -- fail silently rather than crash; there's no
            // legacy startActivityForResult fallback here the way FolderPicker's callers
            // had, since ACTION_OPEN_DOCUMENT_TREE's result (the tree URI + persisted
            // permission) MUST be handled via a proper activity-result callback to call
            // takePersistableUriPermission() correctly -- there isn't a meaningful
            // degraded path for this one.
            Log.w(TAG, "onClick: no tree picker launcher registered, ignoring");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        ResolveInfo picker = resolveRealFolderPicker(getContext());
        if (picker == null) {
            showNoFolderPickerMessage();
            return;
        }
        // If the TV stub would win default resolution, target the real picker explicitly
        // so the stub cannot intercept the launch and show its toast instead.
        ResolveInfo defaultPicker = getContext().getPackageManager()
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (defaultPicker == null || isStubFolderPicker(defaultPicker)) {
            ActivityInfo activityInfo = picker.activityInfo;
            if (activityInfo != null) {
                intent.setComponent(new ComponentName(activityInfo.packageName, activityInfo.name));
            }
        }
        // Best-effort hint to start the system picker at the last-picked folder, if any.
        // The Files app is free to ignore this; it's a hint, not a guarantee, unlike
        // FolderPicker's own EXTRA_CURRENT_SELECTION which that app fully honored.
        String savedUriString = getSharedPreferences().getString(
                VideoPreferencesCommon.KEY_SUBTITLE_FONTS_FOLDER_URI, null);
        if (savedUriString != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(savedUriString));
                }
            } catch (Exception ignored) {
                // Malformed/stale saved URI -- not worth failing the whole picker launch over.
            }
        }
        try {
            mTreePickerLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            // Picker disappeared between the resolveActivity() check and the launch.
            Log.w(TAG, "onClick: no activity handles ACTION_OPEN_DOCUMENT_TREE");
            showNoFolderPickerMessage();
        }
    }

    public CharSequence getSummary() {
        if (!isFolderPickerAvailable(getContext())) {
            return getContext().getString(R.string.subtitle_fonts_folder_summary_no_picker);
        }
        String savedUriString = getSharedPreferences().getString(
                VideoPreferencesCommon.KEY_SUBTITLE_FONTS_FOLDER_URI, null);
        if (savedUriString == null) {
            return getContext().getString(R.string.subtitle_fonts_folder_summary);
        }
        try {
            Uri uri = Uri.parse(savedUriString);
            String docId = uri.getLastPathSegment();
            if (docId == null) return savedUriString;
            int colon = docId.indexOf(':');
            if (colon < 0) return docId; // not the expected "<volume>:<path>" shape -- show as-is
            String relativePath = docId.substring(colon + 1);
            return relativePath.isEmpty() ? docId.substring(0, colon) : relativePath;
        } catch (Exception e) {
            return savedUriString;
        }
    }
}
