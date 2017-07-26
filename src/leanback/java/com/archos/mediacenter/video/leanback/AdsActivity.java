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

package com.archos.mediacenter.video.leanback;

import android.app.Activity;
import android.os.Bundle;

import com.archos.mediacenter.video.AdsHelper;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.billingutils.BillingUtils;
import com.archos.mediacenter.video.billingutils.IsPaidCallback;
import com.google.android.gms.ads.AdListener;


public class AdsActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.androidtv_ads_activity);

        AdsHelper.createInterstitialAd(this);
        AdsHelper.updateAdListener(new AdListener() {
            @Override
            public void onAdClosed () {
                super.onAdClosed();
                reloadAndExit();
            }
            @Override
            public void onAdLeftApplication () {
                super.onAdLeftApplication();
                reloadAndExit();
            }
        });

        // At this point we can turn-on the ads if needed
        BillingUtils inappUtil = new BillingUtils(this);
        inappUtil.checkPayement(new IsPaidCallback(this) {
            @Override
            public void hasBeenPaid(int isPaid) {
                super.hasBeenPaid(isPaid);
                boolean shown = false;
                if (!checkPayement(isPaid)) {
                    shown = AdsHelper.showAd();
                }
                if (!shown) {
                    reloadAndExit();
                }
            }
        });

    }

    private void reloadAndExit() {
        AdsHelper.requestNewAd();
        setResult(Activity.RESULT_OK);
        finish();
    }

}
