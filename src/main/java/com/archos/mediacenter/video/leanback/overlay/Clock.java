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

package com.archos.mediacenter.video.leanback.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by vapillon on 16/06/15.
 */
public class Clock {

    private static final String TAG = "Clock";

    final Context mContext;
    final private TextView mClockTextView;
    final private SimpleDateFormat mDateFormat;

    public Clock(Context context, View overlayContainer) {
        mContext = context;

        if (DateFormat.is24HourFormat(mContext)) {
            mDateFormat = new SimpleDateFormat("HH:mm");
        } else {
            mDateFormat = new SimpleDateFormat("h:mm");
        }

        mClockTextView = (TextView)overlayContainer.findViewById(R.id.clock);

        // No clock when not on an actual leanback device
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            mClockTextView.setVisibility(View.GONE);
        }

        mContext.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    public void destroy() {
        mContext.unregisterReceiver(mReceiver);
    }

    public void resume() {
        updateClock();
    }

    public void pause() {
        // nothing to do here
        // We do not change the visibility of the clock here to have a smooth transition between fragments with clock
    }

    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive " + intent);
            updateClock();
        }
    };

    private void updateClock() {
        mClockTextView.setText(mDateFormat.format(new Date()));
    }
}
