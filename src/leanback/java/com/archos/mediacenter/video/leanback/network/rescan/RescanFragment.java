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

import android.app.AlertDialog;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediaprovider.video.NetworkAutoRefresh;
import com.archos.mediaprovider.video.NetworkScannerServiceVideo;

import java.util.Date;
import java.util.List;

/**
 * Created by vapillon on 22/06/15.
 */
public class RescanFragment extends GuidedStepFragment implements NetworkScannerServiceVideo.ScannerListener {

    public static final int MANUAL_RESCAN_ID = 100;
    public static final int SCHEDULED_RESCAN_PERIOD_ID = 200;
    public static final int RESCAN_WHEN_OPENING_ID = 300;
    public static final int LAST_RESCAN_ID = 400;

    private Overlay mOverlay;

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.rescan),
                getString(R.string.rescan_description), "",
                getResources().getDrawable(R.drawable.pref_nas_rescan));
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView(){
        mOverlay.destroy();
        NetworkScannerServiceVideo.removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {

        actions.add(new GuidedAction.Builder()
                .id(MANUAL_RESCAN_ID)
                .title(getString(R.string.rescan_now))
                .description(getString(R.string.rescan_now_description))
                .build());

        actions.add(new GuidedAction.Builder()
                .id(SCHEDULED_RESCAN_PERIOD_ID)
                .title(getString(R.string.scheduled_rescan))
                .description("") //updated in onCreateView()
                .hasNext(true)
                .build());

        actions.add(new GuidedAction.Builder()
                .id(RESCAN_WHEN_OPENING_ID)
                .title(getString(R.string.rescan_when_opening_application))
                .description("")  //updated in onCreateView()
                .hasNext(true)
                .build());

        actions.add(new GuidedAction.Builder()
                .id(LAST_RESCAN_ID)
                .title(getString(R.string.last_rescan_occured))
                .description("") //updated in refreshLastRescanAction()
                .build());

    }
    private String getTimeFormat(long millis){
        if(millis==0)
            return getString(R.string.last_rescan_never);
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getTimeFormat(getActivity());
        return  dateFormat.format(new Date(millis));

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        //refresh when scan state changes
        NetworkScannerServiceVideo.addListener(this);
        // Way I found to update the damn values...

        getActionById(SCHEDULED_RESCAN_PERIOD_ID).setLabel2(ScheduledRescanPeriod.getStringForCurrentValue(getActivity()));

        getActionById(RESCAN_WHEN_OPENING_ID).setLabel2(RescanWhenOpeningApplication.getStringForCurrentValue(getActivity()));
        refreshManualRescanAction();
        refreshLastRescanAction();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mOverlay.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if(action.getId()==MANUAL_RESCAN_ID){
            NetworkAutoRefresh.forceRescan(getActivity());
        }
        if(action.getId()==LAST_RESCAN_ID){
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.rescan_error_wifi_info_standby).setPositiveButton(android.R.string.ok, null);
            builder.create().show();
        }
        else if (action.getId()==SCHEDULED_RESCAN_PERIOD_ID) {
            add(getFragmentManager(), new ScheduledRescanPeriod());
        }
        else if (action.getId()==RESCAN_WHEN_OPENING_ID) {
            add(getFragmentManager(), new RescanWhenOpeningApplication());
        }
    }

    private GuidedAction getActionById(int id) {
        for (GuidedAction action : getActions()) {
            if (action.getId() == id) {
                return action;
            }
        }
        return null;
    }

    @Override
    public void onScannerStateChanged() {
        refreshManualRescanAction();
        refreshLastRescanAction();

    }

    private void refreshLastRescanAction() {
        String message;
        boolean clickable = false;
        switch (NetworkAutoRefresh.getLastError(getActivity())){
            case  NetworkAutoRefresh.AUTO_RESCAN_ERROR_NO_WIFI:
                message = getString(R.string.rescan_error_wifi);
                int wifiPolicy = Settings.System.getInt(getActivity().getContentResolver(),
                        Settings.Global.WIFI_SLEEP_POLICY,
                        Settings.Global.WIFI_SLEEP_POLICY_NEVER);
                if(wifiPolicy!=Settings.Global.WIFI_SLEEP_POLICY_NEVER&&wifiPolicy!=Settings.Global.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED) {
                    message += " (" + getString(R.string.rescan_error_wifi_click_for_more_info) + ")";
                    clickable = true;
                }

                break;
            case NetworkAutoRefresh.AUTO_RESCAN_ERROR_UNABLE_TO_REACH_HOST:
                message = getString(R.string.rescan_error_server);
                break;
            default:
                message = getTimeFormat(PreferenceManager.getDefaultSharedPreferences(getActivity()).getLong(NetworkAutoRefresh.AUTO_RESCAN_LAST_SCAN, 0));
        }
        getActionById(LAST_RESCAN_ID).setLabel2(message);
        getActionById(LAST_RESCAN_ID).setEnabled(clickable);
        if(getGuidedActionsStylist().getActionsGridView()!=null&&getGuidedActionsStylist()!=null) //depending on when it is called
            getGuidedActionsStylist().getActionsGridView().getAdapter().notifyDataSetChanged();
    }

    private void refreshManualRescanAction() {
        if(NetworkScannerServiceVideo.isScannerAlive()) {
            GuidedAction act = getActionById(MANUAL_RESCAN_ID);
            act.setLabel2(getString(R.string.scan_running));
            act.setEnabled(false);
        }
        else{
            GuidedAction act = getActionById(MANUAL_RESCAN_ID);
            act.setLabel2(getString(R.string.rescan_now_description));
            act.setEnabled(true);
        }
        //refresh view
        if(getGuidedActionsStylist().getActionsGridView()!=null&&getGuidedActionsStylist()!=null) //depending on when it is called
            getGuidedActionsStylist().getActionsGridView().getAdapter().notifyDataSetChanged();

    }
}
