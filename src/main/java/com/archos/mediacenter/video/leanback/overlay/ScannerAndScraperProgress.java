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
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.ActiveOperationMonitor;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.NetworkScannerReceiver;
import com.archos.mediaprovider.video.NetworkScannerServiceVideo;
import com.archos.mediascraper.AllCollectionScrapeService;
import com.archos.mediascraper.AutoScrapeService;

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
    final private TextView mBadge;
    final private TextView mCount;
    final private int mDefaultTextColor;
    final private int mScannerTextColor;
    final private String mBadgeDomainLocal;
    final private String mBadgeDomainNetwork;
    final private String mBadgeOpScan;
    final private String mBadgeOpDelete;
    final private String mBadgeOpIdentify;
    final Handler mRepeatHandler = new Handler(Looper.getMainLooper());

    /** the visibility due to the general state of the fragment */
    private int mGeneralVisibility = View.GONE;

    /** the visibility due to the scanner and scraper state */
    private int mStatusVisibility = View.GONE;

    public ScannerAndScraperProgress(Context context, View overlayContainer) {
        mContext = context;
        mProgressGroup = overlayContainer.findViewById(R.id.progress_group);
        mProgressWheel = (ProgressBar) mProgressGroup.findViewById(R.id.progress);
        mBadge = (TextView) mProgressGroup.findViewById(R.id.badge);
        mCount = (TextView) mProgressGroup.findViewById(R.id.count);
        mDefaultTextColor = mCount.getCurrentTextColor();
        mScannerTextColor = ContextCompat.getColor(context, R.color.scanner_progress_text);
        mBadgeDomainLocal = context.getString(R.string.badge_domain_local);
        mBadgeDomainNetwork = context.getString(R.string.badge_domain_network);
        mBadgeOpScan = context.getString(R.string.badge_op_scan);
        mBadgeOpDelete = context.getString(R.string.badge_op_delete);
        mBadgeOpIdentify = context.getString(R.string.badge_op_identify);
        if (log.isDebugEnabled()) log.debug("ScannerAndScraperProgress: creation");
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
            boolean scanningOnGoing = NetworkScannerReceiver.isScannerWorking()
                    || LoaderUtils.getScrapeInProgress()
                    || AllCollectionScrapeService.isCollectionScrapeInProgress()
                    || ImportState.VIDEO.isInitialImport()
                    || ImportState.VIDEO.isRegularImport();
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

    /** update the counter TextView and badge */
    private void updateCount() {
        String badge = "";
        int count = 0;
        int textColor = mDefaultTextColor;

        if (NetworkScannerReceiver.isScannerWorking()) {
            if (NetworkScannerServiceVideo.isDeleting()) {
                badge = mBadgeDomainNetwork + ":" + mBadgeOpDelete;
                count = NetworkScannerServiceVideo.getRemainingDeletesCount();
            } else {
                badge = mBadgeDomainNetwork + ":" + mBadgeOpScan;
                count = NetworkScannerServiceVideo.getFilesFoundCount();
            }
            textColor = mScannerTextColor;
        } else if (ImportState.VIDEO.isInitialImport() || ImportState.VIDEO.isRegularImport()) {
            if (ImportState.VIDEO.isDeleting()) {
                badge = mBadgeDomainLocal + ":" + mBadgeOpDelete;
                count = ImportState.VIDEO.getNumberOfFilesRemainingToDelete();
            } else {
                badge = mBadgeDomainLocal + ":" + mBadgeOpScan;
                count = ImportState.VIDEO.getNumberOfFilesRemainingToImport();
            }
            textColor = mDefaultTextColor;
        } else if (LoaderUtils.getScrapeInProgress() || AutoScrapeService.getNumberOfFilesRemainingToProcess() > 0) {
            badge = mBadgeOpIdentify;
            count = AutoScrapeService.getNumberOfFilesRemainingToProcess();
            textColor = mDefaultTextColor;
        } else if (AllCollectionScrapeService.isCollectionScrapeInProgress()) {
            badge = mBadgeOpIdentify;
            count = AllCollectionScrapeService.getNumberOfCollectionsRemainingToProcess();
            textColor = mDefaultTextColor;
        }

        if (!badge.isEmpty()) {
            mBadge.setTextColor(textColor);
            mBadge.setText(badge);
            mBadge.setVisibility(View.VISIBLE);
            mCount.setTextColor(textColor);
            mCount.setText(String.valueOf(count));
            mCount.setVisibility(View.VISIBLE);
        } else {
            mBadge.setVisibility(View.GONE);
            mCount.setVisibility(View.INVISIBLE);
        }
    }
}
