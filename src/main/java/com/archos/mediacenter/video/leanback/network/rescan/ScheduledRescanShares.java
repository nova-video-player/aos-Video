// Copyright 2022 Courville Software
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

package com.archos.mediacenter.video.leanback.network.rescan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import android.text.format.DateFormat;
import android.util.Log;

import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.NetworkAutoRefresh;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class ScheduledRescanShares extends GuidedStepSupportFragment {

    private static final String TAG = "ScheduledRescanShares";
    protected final static boolean DBG = false;

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.rescan_select_shares),
                getString(R.string.rescan_select_shares_description),
                null,
                ContextCompat.getDrawable(getActivity(),R.drawable.filetype_new_folder_indexed));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        Cursor cursor = ShortcutDbAdapter.VIDEO.getAllShortcuts(getActivity(), null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                boolean rescan = (cursor.getInt(5) == 1) ? true : false;
                String shareName = cursor.getString(3);
                Integer id = cursor.getInt(0);
                Log.d(TAG, "onCreateActions: " + id + ", " + shareName + ", rescan=" + rescan);
                actions.add(new GuidedAction.Builder(getActivity())
                        .id(id)
                        .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID) // to have checklist and no radiobuttons
                        .checked(rescan)
                        .title(shareName)
                        .build());
            }
            cursor.close();
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        boolean rescan = action.isChecked();
        String shareName = action.getTitle().toString();
        Log.d(TAG, "onGuidedActionClicked: " +  shareName + ", set rescan=" + rescan);
        ShortcutDbAdapter.VIDEO.setRescanShortcut(getActivity(), rescan, shareName);
    }

}
