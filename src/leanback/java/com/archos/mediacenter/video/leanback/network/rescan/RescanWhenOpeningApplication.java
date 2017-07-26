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

package com.archos.mediacenter.video.leanback.network.rescan;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.NetworkAutoRefresh;

import java.util.List;

/**
 * Created by vapillon on 22/06/15.
 */
public class RescanWhenOpeningApplication extends GuidedStepFragment {

    public static boolean sGeneralSwitch = false;

    private static final int RESCAN_WHEN_OPENING_NO_ID = RescanFragment.RESCAN_WHEN_OPENING_ID + 1;
    private static final int RESCAN_WHEN_OPENING_YES_ID = RescanFragment.RESCAN_WHEN_OPENING_ID + 2;

    static public boolean isOn() {
        return sGeneralSwitch;
    }

    static public String getStringForCurrentValue(Context context) {
        return context.getString(NetworkAutoRefresh.autoRescanAtStart(context) ? R.string.yes : R.string.no);
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.rescan_when_opening_application),
                null, null,
                getResources().getDrawable(R.drawable.pref_nas_rescan));
    }
    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        sGeneralSwitch = NetworkAutoRefresh.autoRescanAtStart(getActivity());
        actions.add(new GuidedAction.Builder()
                .id(RESCAN_WHEN_OPENING_NO_ID)
                .checkSetId(RescanFragment.RESCAN_WHEN_OPENING_ID)
                .checked(!isOn())
                .title(getString(R.string.no))
                .build());
        actions.add(new GuidedAction.Builder()
                .id(RESCAN_WHEN_OPENING_YES_ID)
                .checkSetId(RescanFragment.RESCAN_WHEN_OPENING_ID)
                .checked(isOn())
                .title(getString(R.string.yes))
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId()==RESCAN_WHEN_OPENING_YES_ID) {
            sGeneralSwitch =true;
        }
        else if (action.getId()==RESCAN_WHEN_OPENING_NO_ID) {
            sGeneralSwitch =false;
        }
        NetworkAutoRefresh.setAutoRescanAtStart(getActivity(),sGeneralSwitch);
        getFragmentManager().popBackStack();
    }
}
