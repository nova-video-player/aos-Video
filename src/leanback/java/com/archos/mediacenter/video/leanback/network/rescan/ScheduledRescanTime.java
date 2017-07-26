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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.format.DateFormat;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.NetworkAutoRefresh;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Created by vapillon on 22/06/15.
 */
public class ScheduledRescanTime extends GuidedStepFragment {

    private static final int RESCAN_TIME_ID = RescanFragment.SCHEDULED_RESCAN_PERIOD_ID + 10;

    /**
     * The time of the first scan in the day
     * I prefer to store it in hours instead of by ID to be robust against ID changes
     */
    static int sTime = 0; //TODO get from prefs


    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.scheduled_rescan_time),
                null,
                getString(R.string.scheduled_rescan_period),
                getResources().getDrawable(R.drawable.pref_clock));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
// sTime should be equal to started time of pref only if selected period corresponds to the one in pref
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if(pref.getInt(NetworkAutoRefresh.AUTO_RESCAN_PERIOD,0)==ScheduledRescanPeriod.getPeriod()* 60 *60* 1000){

            sTime = pref.getInt(NetworkAutoRefresh.AUTO_RESCAN_STARTING_TIME_PREF,-1)/60/60/1000;
        }
        else
            sTime=0;
        SimpleDateFormat format;
        if (DateFormat.is24HourFormat(getActivity())) {
            format = new SimpleDateFormat("HH:mm");
        } else {
            format = new SimpleDateFormat("h:mm a");
        }

        Calendar cal = new GregorianCalendar();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        final int period = ScheduledRescanPeriod.getPeriod();
        final int increment = 24/period;

        for (int t=0; t<period; t++) {
            cal.set(Calendar.HOUR_OF_DAY, t);
            StringBuilder sb = new StringBuilder();

            for (int n=0; n<increment; n++) {
                // limit the list to 4 items
                if (n>=4) {
                    sb.append(" ...");
                    break;
                }

                cal.set(Calendar.HOUR_OF_DAY, t + n * period);
                if (n>0) {
                    sb.append("  /  "); // separator
                }
                sb.append(format.format(cal.getTime()));

            }
            Intent intent = new Intent();
            intent.putExtra("START",t);
            actions.add(new GuidedAction.Builder()
                    .id(RESCAN_TIME_ID + t)
                    .checkSetId(RESCAN_TIME_ID)
                    .checked(t == sTime) //TODO
                    .title(sb.toString())
                    .intent(intent)
                    .build());
        }

    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        int id = (int)action.getId();
        Intent intent = action.getIntent();
        sTime = intent.getIntExtra("START",-1); //just set sTime, alarm will be set in onResume on ScheduledRescanPeriod
        // go back two levels: > ScheduledRescanPeriod > RescanFragment
        getFragmentManager().popBackStack();
        getFragmentManager().popBackStack();
    }
}
