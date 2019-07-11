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

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.video.NetworkScannerReceiver;
import com.archos.mediascraper.AutoScrapeService;

/**
 * Created by vapillon on 16/06/15.
 */
public class ScannerAndScraperProgress {

    // For now i'm doing some basic polling...
    final static int REPEAT_PERIOD_MS = 1000;

    final private View mProgressGroup;
    final private ProgressBar mProgressWheel;
    final private TextView mCount;
    final private String mInitialScanMessage;
    final Handler mRepeatHandler = new Handler();


    /** the visibility due to the general state of the fragment */
    private int mGeneralVisibility = View.GONE;

    /** the visibility due to the scanner and scraper state */
    private int mStatusVisibility = View.GONE;

    public ScannerAndScraperProgress(Context context, View overlayContainer) {
        mProgressGroup = overlayContainer.findViewById(R.id.progress_group);
        mProgressWheel = (ProgressBar) mProgressGroup.findViewById(R.id.progress);
        mCount = (TextView) mProgressGroup.findViewById(R.id.count);

        mInitialScanMessage = context.getString(R.string.initial_scan);

        mRepeatHandler.post(mRepeatRunnable);
    }

    public void destroy() {
        // all things that need to be stopped are stopped in pause() already
    }

    public void resume() {
        mGeneralVisibility = View.VISIBLE;
        updateCount();
        updateVisibility();
        mRepeatHandler.post(mRepeatRunnable);
    }

    public void pause() {
        mGeneralVisibility = View.GONE;
        updateVisibility();
        mRepeatHandler.removeCallbacks(mRepeatRunnable);
    }

    private Runnable mRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            boolean scanningOnGoing = NetworkScannerReceiver.isScannerWorking() || AutoScrapeService.isScraping() || ImportState.VIDEO.isInitialImport();
            mStatusVisibility = scanningOnGoing ? View.VISIBLE : View.GONE;
            updateCount();
            updateVisibility();
            mRepeatHandler.postDelayed(this, REPEAT_PERIOD_MS);
        }
    };


    /** Compute the visibility of the progress group. Both mGeneralVisibility and mStatusVisibility must be VISIBLE for the view to be visible */
    private void updateVisibility() {
        if ((mGeneralVisibility == View.VISIBLE) && (mStatusVisibility == View.VISIBLE)) {
            mProgressGroup.setVisibility(View.VISIBLE);
        }
        else {
            mProgressGroup.setVisibility(View.GONE);
        }
    }

    /** update the counter TextView */
    private void updateCount() {
        String msg = String.valueOf("");
        int count = 0;

        // First check initial import count
        if (ImportState.VIDEO.isInitialImport()) {
            msg = mInitialScanMessage+"\n";
            count = ImportState.VIDEO.getNumberOfFilesRemainingToImport();
        }
        // If not initial import count, check autoscraper count
        if (count==0) {
            count = AutoScrapeService.getNumberOfFilesRemainingToProcess();
        }

        // Display count only if greater than zero
        if (count > 0) {
            mCount.setText(msg+Integer.toString(count));
            mCount.setVisibility(View.VISIBLE);
        } else {
            mCount.setVisibility(View.INVISIBLE);
        }
    }
}
