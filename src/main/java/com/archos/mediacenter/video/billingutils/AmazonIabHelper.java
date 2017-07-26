/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.archos.mediacenter.video.billingutils;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.FulfillmentResult;
import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;
import com.archos.environment.ArchosUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;


/**
 * Provides convenience methods for in-app billing. You can create one instance of this
 * class for your application and use it to process in-app billing operations.
 * It provides synchronous (blocking) and asynchronous (non-blocking) methods for
 * many common in-app billing operations, as well as automatic signature
 * verification.
 *
 * After instantiating, you must perform setup in order to start using the object.
 * To perform setup, call the {@link #startSetup} method and provide a listener;
 * that listener will be notified when setup is complete, after which (and not before)
 * you may call other methods.
 *
 * After setup is complete, you will typically want to request an inventory of owned
 * items and subscriptions. See {@link #queryInventory}, {@link #queryInventoryAsync}
 * and related methods.
 *
 * When you are done with this object, don't forget to call {@link #dispose}
 * to ensure proper cleanup. This object holds a binding to the in-app billing
 * service, which will leak unless you dispose of it correctly. If you created
 * the object on an Activity's onCreate method, then the recommended
 * place to dispose of it is the Activity's onDestroy method.
 *
 * A note about threading: When using this object from a background thread, you may
 * call the blocking versions of methods; when using from a UI thread, call
 * only the asynchronous versions and handle the results via callbacks.
 * Also, notice that you can only call one asynchronous operation at a time;
 * attempting to start a second asynchronous operation while the first one
 * has not yet completed will result in an exception being thrown.
 *
 * @author Bruno Oliveira (Google)
 *
 */
public class AmazonIabHelper implements IabHelperInterface {
    private static final String TAG = "IabHelper";
    private static final int MSG_SETUP_TIMEOUT = 0;
    // Is debug logging enabled?
    boolean mDebugLog = true;
    private String mExtraData;
    private OnIabPurchaseFinishedListener mOnIabPurchaseFinishedListener;
    private PurchasingObserver mPurchasingObserver;
    private IabHelper.QueryInventoryFinishedListener mQueryInventoryFinishedListener;
    private boolean mIsSetupDone;
    private Object mLock = new Object();
    private Inventory mInventory;
    private IabResult mLastResult;
    private String mLastOperationSKU;
    private OnIabSetupFinishedListener mOnIabSetupFinishedListener;
    private Handler mHandler;
    private boolean mHasSetupTimeout;
    private static final int SETUP_TIMEOUT = 3000;

    public AmazonIabHelper(){
        mHandler = new android.os.Handler(){
            public void handleMessage(Message msg) {
                if(msg.what==MSG_SETUP_TIMEOUT){
                    mHasSetupTimeout= true;
                    if(mOnIabSetupFinishedListener!=null)
                        mOnIabSetupFinishedListener.onIabSetupFinished(new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ERROR, "Unable to start setup"));
                }
            }
        };
    }
    @Override
    public boolean isSetupDone() {
        return mIsSetupDone;
    }

    @Override
    public boolean isAsyncInProgress() {
        return false;
    }

    @Override
    public void startSetup(OnIabSetupFinishedListener listener) {
        mOnIabSetupFinishedListener = listener;
        if (mPurchasingObserver == null) {
            mPurchasingObserver = new PurchasingObserver();
        }
        mHandler.removeMessages(MSG_SETUP_TIMEOUT);
        mHasSetupTimeout = false;
        mHandler.sendEmptyMessageAtTime(MSG_SETUP_TIMEOUT, SETUP_TIMEOUT);

        PurchasingService.registerListener(ArchosUtils.getGlobalContext(), mPurchasingObserver);
        PurchasingService.getUserData();
    }

    @Override
    public void dispose() {

    }

    @Override
    public boolean subscriptionsSupported() {
        return false;
    }

    @Override
    public void launchPurchaseFlow(Activity act, String sku, int requestCode, OnIabPurchaseFinishedListener listener) {

    }

    @Override
    public void launchPurchaseFlow(Activity act, String sku, int requestCode, OnIabPurchaseFinishedListener listener, String extraData) {
        mExtraData = extraData;
        mLastOperationSKU = sku;
        mOnIabPurchaseFinishedListener = listener;
        PurchasingService.purchase(sku);
    }

    @Override
    public void launchSubscriptionPurchaseFlow(Activity act, String sku, int requestCode, OnIabPurchaseFinishedListener listener) {

    }

    @Override
    public void launchSubscriptionPurchaseFlow(Activity act, String sku, int requestCode, OnIabPurchaseFinishedListener listener, String extraData) {

    }

    @Override
    public void launchPurchaseFlow(Activity act, String sku, String itemType, int requestCode, OnIabPurchaseFinishedListener listener, String extraData) {

    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }

    @Override
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreSkus) throws IabException {
        Log.d(TAG,"queryInventory");


        try {
            mInventory = new Inventory();
            Log.d(TAG,"getPurchaseUpdates");

            synchronized(mLock) {
                PurchasingService.getPurchaseUpdates(true);
                mLock.wait(10000);
            }

            Log.d(TAG,"getProductData");
            synchronized(mLock) {
                PurchasingService.getProductData(new HashSet<String>(moreSkus));
                mLock.wait(10000);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return mInventory;
    }

    @Override
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        return null;
    }

    @Override
    public void queryInventoryAsync(final boolean querySkuDetails, final List<String> moreSkus, IabHelper.QueryInventoryFinishedListener listener) {
        mQueryInventoryFinishedListener = listener;
        Log.d(TAG,"queryInventoryAsync");

        new Thread(){
            public void run(){
                try {
                    queryInventory(querySkuDetails,moreSkus);
                    Log.d(TAG,"call mQueryInventoryFinishedListener");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG,"call mQueryInventoryFinishedListener2");

                            mQueryInventoryFinishedListener.onQueryInventoryFinished(mLastResult!=null?mLastResult:new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE,""), mInventory);
                        }
                    });

                } catch (IabException e) {
                    e.printStackTrace();
                }
            }
        }.start();


    }


    private class PurchasingObserver implements PurchasingListener {

        /**
         * see parent (or https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs/quick-start)
         */
        @Override
        public void onProductDataResponse(ProductDataResponse productDataResponse) {
            IabResult result = null;
            switch (productDataResponse.getRequestStatus()) {

                case SUCCESSFUL:

                    String unskus = "";
                    for (final String s : productDataResponse.getUnavailableSkus()) {
                        unskus += s + "/";
                    }
                    if (!TextUtils.isEmpty(unskus)) {
                        Log.d(TAG, "(onItemDataResponse) The following skus were unavailable: " + unskus);
                    }

                    final Map<String, Product> products = productDataResponse.getProductData();
                    for (final String key : products.keySet()) {
                        Product product = products.get(key);
                        String currencyCode = "";
                        long priceMicros = 0;

                        SkuDetails skuDetails = new SkuDetails(ITEM_TYPE_INAPP,
                                product.getSku(), product.getPrice(), product.getTitle(), product.getDescription(), priceMicros, currencyCode);
                        mInventory.addSkuDetails(skuDetails);
                                Log.d(TAG, String.format("Product: %s\n Type: %s\n SKU: %s\n Price: %s\n Description: %s\n",
                                product.getTitle(), product.getProductType(), product.getSku(), product.getPrice(), product.getDescription()));
                    }
                    result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_OK,"");
                    break;

                case FAILED: // Fail gracefully on failed responses.
                    result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ERROR,
                            "Couldn't complete refresh operation.");
//                    Log.v(TAG, "ItemDataRequestStatus: FAILED");
                    break;
            }
            mLastResult = result;
            synchronized(mLock) {
                mLock.notify();
            }


        }

        /**
         * see parent (or https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs/quick-start)
         */
        @Override
        public void onPurchaseResponse(final PurchaseResponse response) {
            switch (response.getRequestStatus()) {
                case SUCCESSFUL:
                    boolean fulfilled = false;
                    try {
                        Receipt receipt = response.getReceipt();
//                        Item.ItemType itemType = receipt.getItemType();
                        String sku = receipt.getSku();
                        String purchaseToken = receipt.getReceiptId();

                        // according to: https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs-v2/faq-for-iap-2.0
                        // notifyFulfillment must be called on all types of IAPs.
                        // Calling it here to reduce the chance of failures.
                        PurchasingService.notifyFulfillment(purchaseToken, FulfillmentResult.FULFILLED);
                        fulfilled = true;

                        Purchase purchase = new Purchase(ITEM_TYPE_INAPP, sku, purchaseToken, response.getRequestId().toString(), 0, response.getUserData().getUserId());
                        purchase.setDeveloperPayload(AmazonIabHelper.this.mExtraData);
                        mOnIabPurchaseFinishedListener.onIabPurchaseFinished(new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_OK, ""),purchase);
                        AmazonIabHelper.this.mExtraData = "";
                    }
                    catch (Exception e) {
                        // Make sure to fulfill the purchase if there's a crash
                        // so the items will not return in restoreTransactions
                        if (!fulfilled) {
                            PurchasingService.notifyFulfillment(response.getReceipt().getReceiptId(), FulfillmentResult.FULFILLED);
                        }
                        IabResult result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ERROR, "The purchase has failed. No message.");
                        mOnIabPurchaseFinishedListener.onIabPurchaseFinished(result,null);

                    }
                    break;
                case INVALID_SKU:
                    String msg = "The purchase has failed. Invalid sku given.";
                    Log.d(TAG, msg);
                    IabResult result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE, msg);
                    mOnIabPurchaseFinishedListener.onIabPurchaseFinished(result,null);
                    break;
                case ALREADY_PURCHASED:

                    Purchase purchase = new Purchase(ITEM_TYPE_INAPP, mLastOperationSKU, "", response.getRequestId().toString(), 0);

                    msg = "The purchase has failed. Product already purchased.";
                    Log.d(TAG, msg);
                    result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED, msg);
                    mOnIabPurchaseFinishedListener.onIabPurchaseFinished(result,purchase);
                    break;
                default:
                    msg = "The purchase has failed. No message.";
                    Log.d(TAG, msg);
                    result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ERROR, msg);
                    mOnIabPurchaseFinishedListener.onIabPurchaseFinished(result,null);
                    break;
            }
        }

        @Override
        public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse purchaseUpdatesResponse) {

            if (mCurrentUserData.getUserId() != null && !mCurrentUserData.getUserId().equals(purchaseUpdatesResponse.getUserData().getUserId())) {
                Log.d(TAG, "The updates is not for the current user id.");
                IabResult result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ERROR,
                        "Couldn't complete restore purchases operation.");
                mLastResult = result;
                synchronized(mLock) {
                    mLock.notify();
                }
                return;
            }

            Log.d(TAG,"update " +
                    "purchaseUpdatesResponse.getRequestStatus() "+purchaseUpdatesResponse.getRequestStatus());
            switch (purchaseUpdatesResponse.getRequestStatus()) {
                case SUCCESSFUL:
                    if (mInventory == null) {
                        mInventory = new Inventory();
                    }
                    Log.d(TAG,"purchaseUpdatesResponse.getReceipts() "+purchaseUpdatesResponse.getReceipts().size());
                    // Process receipts
                    for (final Receipt receipt : purchaseUpdatesResponse.getReceipts()) {
                        Log.d(TAG,"update " +
                                "add sku "+receipt.getSku());
                        // Canceled purchases still get here but they are
                        // flagged as canceled
                        // https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs-v2/migrate-iapv1-apps-to-iapv2
                        if (receipt.isCanceled()) {
                            continue;
                        }

                        String sku = receipt.getSku();
                       PurchasingService.notifyFulfillment(receipt.getReceiptId(), FulfillmentResult.FULFILLED);


                        Purchase purchase = new Purchase(ITEM_TYPE_INAPP,
                                sku, "NO TOKEN",
                                purchaseUpdatesResponse.getRequestId().toString(), 0);
                        Log.d(TAG,"update " +
                                "add sku "+sku);
                        mInventory.addPurchase(purchase);
                    }

                    if (purchaseUpdatesResponse.hasMore()) {
                        Log.d(TAG, "Initiating Another Purchase Updates");
                        PurchasingService.getPurchaseUpdates(false);
                    } else {
                        mLastResult = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_OK,"");
                    }

                    break;

                case FAILED:
                    Log.d(TAG, "There was an error while trying to restore purchases. " +
                            "Finishing with those that were accumulated until now.");
                    if (mInventory != null) {
                        mLastResult = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_OK,"");
                    } else {
                        IabResult result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ERROR,
                                "Couldn't complete restore purchases operation.");
                        mLastResult = result;
                    }
                    break;
            }
            synchronized(mLock) {
                mLock.notify();
            }
        }
        /** Private Members */

        private static final String TAG = "PurchasingObserver";

        private UserData mCurrentUserData = null;




        @Override
        public void onUserDataResponse(UserDataResponse userDataResponse) {
            Log.d(TAG,"onUserDataResponse");
            
            mHandler.removeMessages(MSG_SETUP_TIMEOUT);
            mHasSetupTimeout = false;
            if(mHasSetupTimeout)
                return;
            if (userDataResponse.getRequestStatus() == UserDataResponse.RequestStatus.SUCCESSFUL) {
                mCurrentUserData = userDataResponse.getUserData();
                mIsSetupDone = true;
                IabResult result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_OK, null);
                mOnIabSetupFinishedListener.onIabSetupFinished(result);
            } else {
                String msg = "Unable to get userId";
                Log.d(TAG, msg); Log.d(TAG, msg);
                IabResult result = new IabResult(BillingResponse.BILLING_RESPONSE_RESULT_ERROR, msg);
                mOnIabSetupFinishedListener.onIabSetupFinished(result);

                mIsSetupDone = false;
            }
        }
    }




}
