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

package com.archos.mediacenter.video;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.amazon.device.ads.Ad;
import com.amazon.device.ads.AdError;
import com.amazon.device.ads.AdProperties;
import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

public class AdsHelper {

    private static InterstitialAd googleInterstitial;
    private static com.amazon.device.ads.InterstitialAd amazonInterstitial;
    private static AdListener sAdListener;

    public static void createInterstitialAd(Context context) {
        if (googleInterstitial == null&&amazonInterstitial==null) {
            if(ArchosUtils.isAmazonApk()) {
                // Create the interstitial.
                amazonInterstitial = new com.amazon.device.ads.InterstitialAd(context);

                // Set the listener to use the callbacks below.
                amazonInterstitial.setListener(new com.amazon.device.ads.AdListener() {
                    @Override
                    public void onAdLoaded(Ad ad, AdProperties adProperties) {
                        if(sAdListener!=null)
                            sAdListener.onAdLoaded();
                    }

                    @Override
                    public void onAdFailedToLoad(Ad ad, AdError adError) {

                    }

                    @Override
                    public void onAdExpanded(Ad ad) {

                    }

                    @Override
                    public void onAdCollapsed(Ad ad) {

                    }

                    @Override
                    public void onAdDismissed(Ad ad) {
                        if(sAdListener!=null)
                            sAdListener.onAdClosed();
                    }
                });

            }else {
                // use text and images only ads on FreeBox 4K to avoid crashing it
                String adUnitId = context.getString(Build.BRAND.equals("Freebox")
                        ? R.string.interstitiel_ad_anticrash_freebox_unit_id
                        : R.string.interstitiel_ad_unit_id);
                googleInterstitial = new InterstitialAd(context);
                googleInterstitial.setAdUnitId(adUnitId);
                googleInterstitial.setAdListener(new AdListener() {
                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        if(sAdListener!=null)
                            sAdListener.onAdClosed();
                    }

                    @Override
                    public void onAdFailedToLoad(int errorCode) {
                        super.onAdFailedToLoad(errorCode);
                    }

                    @Override
                    public void onAdLeftApplication() {
                        super.onAdLeftApplication();
                    }

                    @Override
                    public void onAdOpened() {
                        super.onAdOpened();
                    }

                    @Override
                    public void onAdLoaded() {
                        super.onAdLoaded();
                        if(sAdListener!=null)
                            sAdListener.onAdLoaded();
                    }
                });
            }
        }
    }


    public static void updateAdListener(AdListener listener) {
        sAdListener = listener;

    }

    public static void requestNewAd() {
        if (googleInterstitial != null) {
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice(VideoUtils.TEST_ADS_DEVICE_ID)
                    .build();
            googleInterstitial.loadAd(adRequest);
        } else if(amazonInterstitial!=null){
            amazonInterstitial.loadAd();
        }
    }

    public static boolean showAd() {
        if (googleInterstitial != null && googleInterstitial.isLoaded()) {
            googleInterstitial.show();
            return true;
        }
        else if(amazonInterstitial !=null && amazonInterstitial.isReady()){
            return amazonInterstitial.showAd();
        }
        return false;
    }

}
