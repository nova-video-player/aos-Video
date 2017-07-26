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
import android.preference.PreferenceManager;
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
public class ScheduledRescanPeriod extends GuidedStepFragment {

    private static final int DO_NOT_ID = RescanFragment.SCHEDULED_RESCAN_PERIOD_ID + 1;
    private static final int ONCE_A_DAY_ID = RescanFragment.SCHEDULED_RESCAN_PERIOD_ID + 2;
    private static final int TWICE_A_DAY_ID = RescanFragment.SCHEDULED_RESCAN_PERIOD_ID + 3;
    private static final int EVERY_6_HOURS_ID = RescanFragment.SCHEDULED_RESCAN_PERIOD_ID + 4;
    private static final int EVERY_2_HOURS_ID = RescanFragment.SCHEDULED_RESCAN_PERIOD_ID + 5;
    private static final int EVERY_HOUR_ID = RescanFragment.SCHEDULED_RESCAN_PERIOD_ID + 6;

    /**
     * The actual period, in hours
     * I prefer to store it in hours instead of by ID to be robust against ID changes
     * If equals zero there is no scheduled rescan
     */
    private static int sPeriod = 24; //TODO get from prefs
    private boolean mHasClickedAction;

    static int getPeriod() {
        return sPeriod;
    }

    /**
     * In case the different possible periods have changed, the value stored in the preference may
     * not exactly match the defined IDs.
     * @param periodInHours
     * @return
     */
    static private int getIdFromPeriod(int periodInHours) {
        if (periodInHours>=24) {
            return ONCE_A_DAY_ID;
        }
        else if (periodInHours>=12) {
            return TWICE_A_DAY_ID;
        }
        else if (periodInHours>=6) {
            return EVERY_6_HOURS_ID;
        }
        else if (periodInHours>=2) {
            return EVERY_2_HOURS_ID;
        }
        else if (periodInHours>=1) {
            return EVERY_HOUR_ID;
        }
        else {
            return DO_NOT_ID;
        }
    }

    /**
     * @return the description string matching a period id, "" if the period id is undefined
     */
    static private String getStringForPeriodId(Context context, int periodId) {
        switch (periodId) {
            case DO_NOT_ID:
                return context.getString(R.string.scheduled_rescan_period_no);
            case ONCE_A_DAY_ID:
                return context.getString(R.string.scheduled_rescan_period_once_a_day);
            case TWICE_A_DAY_ID:
                return context.getString(R.string.scheduled_rescan_period_twice_a_day);
            case EVERY_6_HOURS_ID:
                return context.getString(R.string.scheduled_rescan_period_every_six_hours);
            case EVERY_2_HOURS_ID:
                return context.getString(R.string.scheduled_rescan_period_every_two_hours);
            case EVERY_HOUR_ID:
                return context.getString(R.string.scheduled_rescan_period_every_hour);
            default:
                return "";
        }
    }

    static public String getStringForCurrentValue(Context context) {
        return getStringForPeriodId(context, getIdFromPeriod(NetworkAutoRefresh.getRescanPeriod(context)/60/60/1000));
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.scheduled_rescan),
                null,
                getString(R.string.rescan),
                getResources().getDrawable(R.drawable.pref_clock));
    }
    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        sPeriod = PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt(NetworkAutoRefresh.AUTO_RESCAN_PERIOD,0)/60/60/1000;
        final int currentId = getIdFromPeriod(sPeriod);

        for (long id : new long[] {DO_NOT_ID, ONCE_A_DAY_ID, TWICE_A_DAY_ID, EVERY_6_HOURS_ID, EVERY_2_HOURS_ID, EVERY_HOUR_ID}) {
            actions.add(new GuidedAction.Builder()
                    .id(id)
                    .checkSetId(RescanFragment.SCHEDULED_RESCAN_PERIOD_ID)
                    .checked(id == currentId)
                    .title(getStringForPeriodId(getActivity(), (int) id))
                    .build());
        }
    }
    @Override
    public void onResume(){
        super.onResume();
        if(mHasClickedAction){ //needed when going to ScheduledRescanTime then coming back by pressing "back". popBackStack will also get there.
            NetworkAutoRefresh.scheduleNewRescan(getActivity(),ScheduledRescanTime.sTime*60*60*1000, getPeriod()* 60 *60* 1000,true);
            mHasClickedAction = false;
        }
    }
    @Override
    public void onGuidedActionClicked(GuidedAction action) {

        boolean goBack = false;
        mHasClickedAction = true;
        int id = (int)action.getId();
        switch (id) {
            case DO_NOT_ID:
                sPeriod = 0;
                goBack = true; // no need for ScheduledRescanTime in that case
                break;
            case ONCE_A_DAY_ID:
                sPeriod = 24;
                break;
            case TWICE_A_DAY_ID:
                sPeriod = 12;
                break;
            case EVERY_6_HOURS_ID:
                sPeriod = 6;
                break;
            case EVERY_2_HOURS_ID:
                sPeriod = 2;
                break;
            case EVERY_HOUR_ID:
                sPeriod = 1;
                goBack = true; // no need for ScheduledRescanTime in that case
                break;
        }


        if (goBack) {
            NetworkAutoRefresh.scheduleNewRescan(getActivity(),0, getPeriod()* 60 *60* 1000,true);
            getFragmentManager().popBackStack();
        } else {
            add(getFragmentManager(), new ScheduledRescanTime());
        }
    }
}
