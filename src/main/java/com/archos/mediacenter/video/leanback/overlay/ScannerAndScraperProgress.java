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

import android.app.Activity;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.ActiveOperationMonitor;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.video.NetworkScannerReceiver;
import com.archos.mediaprovider.video.NetworkScannerServiceVideo;
import com.archos.mediascraper.AutoScrapeService;
import com.archos.mediaprovider.video.LoaderUtils;
import androidx.core.content.ContextCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by vapillon on 16/06/15.
 */
public class ScannerAndScraperProgress {

    private static final Logger log = LoggerFactory.getLogger(ScannerAndScraperProgress.class);

    // For now i'm doing some basic polling...
    final static int REPEAT_PERIOD_MS = 1000;

    final private Context mContext;
    final private View mProgressGroup;
    final private ProgressBar mProgressWheel;
    final private TextView mCount;
    final private String mInitialScanMessage;
    final private int mDefaultTextColor;
    final private int mScannerTextColor;
    final Handler mRepeatHandler = new Handler(Looper.getMainLooper());


    /** the visibility due to the general state of the fragment */
    private int mGeneralVisibility = View.GONE;

    /** the visibility due to the scanner and scraper state */
    private int mStatusVisibility = View.GONE;

    public ScannerAndScraperProgress(Context context, View overlayContainer) {
        mContext = context;
        mProgressGroup = overlayContainer.findViewById(R.id.progress_group);
        mProgressWheel = (ProgressBar) mProgressGroup.findViewById(R.id.progress);
        mCount = (TextView) mProgressGroup.findViewById(R.id.count);
        mDefaultTextColor = mCount.getCurrentTextColor();
        mScannerTextColor = ContextCompat.getColor(context, R.color.scanner_progress_text);
        if (log.isDebugEnabled()) log.debug("ScannerAndScraperProgress: creation");
        mInitialScanMessage = context.getString(R.string.initial_scan);
        mRepeatHandler.post(mRepeatRunnable);
    }

    public void destroy() {
        if (log.isDebugEnabled()) log.debug("destroy");
        // all things that need to be stopped are stopped in pause() already
    }

    public void resume() {
        if (log.isDebugEnabled()) log.debug("resume: view visible");
        mGeneralVisibility = View.VISIBLE;
        updateCount();
        updateVisibility();
        mRepeatHandler.post(mRepeatRunnable);
    }

    public void pause() {
        if (log.isDebugEnabled()) log.debug("pause: view gone");
        mGeneralVisibility = View.GONE;
        updateVisibility();
        mRepeatHandler.removeCallbacks(mRepeatRunnable);
        if (mContext instanceof Activity) {
            ActiveOperationMonitor.clearKeepScreenOn((Activity) mContext);
        }
    }

    private Runnable mRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            boolean scanningOnGoing = NetworkScannerReceiver.isScannerWorking() || LoaderUtils.getScrapeInProgress() || ImportState.VIDEO.isInitialImport() || ImportState.VIDEO.isRegularImport();
            mStatusVisibility = scanningOnGoing ? View.VISIBLE : View.GONE;
            if (log.isTraceEnabled()) log.trace("mRepeatRunnable: visibility {} because scanningOngoing {} due to networkScanner {} due to autoScrapeService {} due to isInitialImport {} due to isRegularImport {}", mStatusVisibility, scanningOnGoing, NetworkScannerReceiver.isScannerWorking(), LoaderUtils.getScrapeInProgress(), ImportState.VIDEO.isInitialImport(), ImportState.VIDEO.isRegularImport());
            if (mContext instanceof Activity) {
                ActiveOperationMonitor.updateKeepScreenOn((Activity) mContext);
            }
            updateCount();
            updateVisibility();
            mRepeatHandler.postDelayed(this, REPEAT_PERIOD_MS);
        }
    };


    /** Compute the visibility of the progress group. Both mGeneralVisibility and mStatusVisibility must be VISIBLE for the view to be visible */
    private void updateVisibility() {
        if (log.isTraceEnabled()) log.trace("updateVisibility: (0 visible, 8 gone) mGeneralVisibility {}, mStatusVisibility {}", mGeneralVisibility, mStatusVisibility);
        if ((mGeneralVisibility == View.VISIBLE) && (mStatusVisibility == View.VISIBLE)) {
            mProgressGroup.setVisibility(View.VISIBLE);
        } else {
            mProgressGroup.setVisibility(View.GONE);
        }
    }

    /** update the counter TextView */
    private void updateCount() {
        int scanCount = 0;
        int scrapeCount = 0;
        int initialImportCount = 0;

        if (NetworkScannerReceiver.isScannerWorking()) {
            scanCount = NetworkScannerServiceVideo.getFilesFoundCount();
        }

        if (ImportState.VIDEO.isInitialImport()) {
            initialImportCount = ImportState.VIDEO.getNumberOfFilesRemainingToImport();
        } else {
            scrapeCount = AutoScrapeService.getNumberOfFilesRemainingToProcess();
        }

        if (scanCount > 0) {
            // Phase 1: Network scanning active - display increasing count in Dimmed Gray
            mCount.setTextColor(mScannerTextColor);
            mCount.setText(String.valueOf(scanCount));
            mCount.setVisibility(View.VISIBLE);
        } else if (initialImportCount > 0 || scrapeCount > 0) {
            // Phase 2: Metadata scraping / Import active - display decreasing count in Default White
            int count = initialImportCount > 0 ? initialImportCount : scrapeCount;
            String msg = initialImportCount > 0 ? mInitialScanMessage + "\n" : "";
            mCount.setTextColor(mDefaultTextColor);
            mCount.setText(msg + count);
            mCount.setVisibility(View.VISIBLE);
        } else {
            mCount.setVisibility(View.INVISIBLE);
        }
    }
}
