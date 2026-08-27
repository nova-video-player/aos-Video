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
import android.view.View;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

import com.archos.mediacenter.video.player.Player;
import com.archos.mediacenter.video.player.PlayerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by vapillon on 16/06/15.
 */
public class Clock {

    private static final Logger log = LoggerFactory.getLogger(Clock.class);

    public static String formatTimeWithArrow(String startText, String endText) {
        return startText + " → " + endText;
    }

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
    }

    public void destroy() {
    }

    public void resume() {
        if (log.isDebugEnabled()) log.debug("resume");
        mContext.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        updateClock();
    }

    public void pause() {
        if (log.isDebugEnabled()) log.debug("pause");
        // We do not change the visibility of the clock here to have a smooth transition between fragments with clock
        mContext.unregisterReceiver(mReceiver);
    }

    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (log.isDebugEnabled()) log.debug("onReceive: {}", intent);
            updateClock();
        }
    };

    private void updateClock() {
        long now = System.currentTimeMillis();
        String currentClockText = mDateFormat.format(new Date(now));
        boolean hasFloatingPlayer = PlayerService.sPlayerService != null
                && Player.sPlayer != null
                && Player.sPlayer.isFloatingPlayer()
                && Player.sPlayer.isPlaying();

        if (log.isDebugEnabled()) {
            log.debug("updateClock: hasFloatingPlayer={}, sPlayerService={}, sPlayer={}, isPlaying={}",
                    hasFloatingPlayer,
                    PlayerService.sPlayerService != null,
                    Player.sPlayer != null,
                    Player.sPlayer != null ? Player.sPlayer.isPlaying() : false);
        }

        if (hasFloatingPlayer) {
            int duration = Player.sPlayer.getDuration();
            int position = Player.sPlayer.getCurrentPosition();
            float speed = PlayerService.sPlayerService.getAudioSpeed();
            if (speed <= 0f) speed = 1.0f;

            if (duration > 0 && duration > position) {
                long remainingMs = (long) ((duration - position) / speed);
                String endClockText = mDateFormat.format(new Date(now + remainingMs));
                mClockTextView.setText(formatTimeWithArrow(currentClockText, endClockText));
                return;
            }
        }
        mClockTextView.setText(currentClockText);
    }
}
