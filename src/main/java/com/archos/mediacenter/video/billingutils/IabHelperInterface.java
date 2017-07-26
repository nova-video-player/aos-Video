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

package com.archos.mediacenter.video.billingutils;

import android.app.Activity;
import android.content.Intent;

import java.util.List;

/**
 * Created by alexandre on 29/11/16.
 */

public interface IabHelperInterface { // Item types
    public static final String ITEM_TYPE_INAPP = "inapp";
    public static final String ITEM_TYPE_SUBS = "subs";

    boolean isSetupDone();

    boolean isAsyncInProgress();

    /**
     * Callback for setup process. This listener's {@link #onIabSetupFinished} method is called
     * when the setup process is complete.
     */
    public interface OnIabSetupFinishedListener {
        /**
         * Called to notify that setup is complete.
         *
         * @param result The result of the setup process.
         */
        public void onIabSetupFinished(IabResult result);
    }
    public void startSetup(final OnIabSetupFinishedListener listener);
    public void dispose();
    public boolean subscriptionsSupported();
    /**
     * Callback that notifies when a purchase is finished.
     */
    public interface OnIabPurchaseFinishedListener {
        /**
         * Called to notify that an in-app purchase finished. If the purchase was successful,
         * then the sku parameter specifies which item was purchased. If the purchase failed,
         * the sku and extraData parameters may or may not be null, depending on how far the purchase
         * process went.
         *
         * @param result The result of the purchase.
         * @param info The purchase information (null if purchase failed)
         */
        public void onIabPurchaseFinished(IabResult result, Purchase info);
    }
    public void launchPurchaseFlow(Activity act, String sku, int requestCode, IabHelperInterface.OnIabPurchaseFinishedListener listener);
    public void launchPurchaseFlow(Activity act, String sku, int requestCode,
                                   IabHelperInterface.OnIabPurchaseFinishedListener listener, String extraData);
    public void launchSubscriptionPurchaseFlow(Activity act, String sku, int requestCode,
                                               IabHelperInterface.OnIabPurchaseFinishedListener listener);
    public void launchSubscriptionPurchaseFlow(Activity act, String sku, int requestCode,
                                               IabHelperInterface.OnIabPurchaseFinishedListener listener, String extraData);
    public void launchPurchaseFlow(Activity act, String sku, String itemType, int requestCode,
                                   IabHelperInterface.OnIabPurchaseFinishedListener listener, String extraData);
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data);
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreSkus)  throws IabException ;
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus,
                                    List<String> moreSubsSkus) throws IabException;
    public void queryInventoryAsync(final boolean querySkuDetails,
                                    final List<String> moreSkus,
                                    final IabHelper.QueryInventoryFinishedListener listener);

    }
