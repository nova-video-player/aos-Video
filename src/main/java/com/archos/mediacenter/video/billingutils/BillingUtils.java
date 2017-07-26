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
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;
import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.video.R;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;



public class BillingUtils implements IabHelper.QueryInventoryFinishedListener, IabHelperInterface.OnIabPurchaseFinishedListener{
	IInAppBillingService mService;
	String base64EncodedPublicKey;
	IabHelperInterface mHelper;
	String ID = "premium";
	IsPaidCallback ispc=null;
	public static String PAID_STATUS_PREF="pref_paid_status";
	public static final int HAS_BEEN_PURCHASE = 0;
	public static final int CHECKING_IMPOSSIBLE = 1;
	public static final int HAS_NOT_BEEN_PURCHASE = 2;
	public static final int CHECKING_IMPOSSIBLE_PURCHASE_FLOW=3;
	
	private static final boolean DBG = true;

	public BillingUtils(Context ct){
		base64EncodedPublicKey = ct.getString(R.string.base64_encoded_publicKey);
		if(ArchosUtils.isAmazonApk()){
			mHelper = new AmazonIabHelper();
		}
		else {
			mHelper = new IabHelper(ct, base64EncodedPublicKey);
		}
	}
	public void checkPayement( final IsPaidCallback ispc){
		this.ispc=ispc;

		try{
			mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
				public void onIabSetupFinished(IabResult result) {
					if (!result.isSuccess()) {
						// Oh noes, there was a problem.
						ispc.hasBeenPaid(CHECKING_IMPOSSIBLE);
					}
					else{
						checkPayementAfterSetup(ispc);
					}

				}
			});
		}
		catch (IllegalStateException e){
			if(mHelper.isSetupDone())
				checkPayementAfterSetup(ispc);
		}
	}
	private void checkPayementAfterSetup( IsPaidCallback ispc){
		List<String> additionalSkuList = new ArrayList<String>();
		additionalSkuList.add(ID);
		mHelper.queryInventoryAsync(true,additionalSkuList,
				BillingUtils.this);
	}
	public void purchaseAfterSetup(Activity act, IsPaidCallback ispc){
		if(mHelper.isSetupDone() ){
			try{
				mHelper.launchPurchaseFlow(act, ID, 10001,   
						this, UUID.randomUUID().toString());
			} catch (IllegalStateException s){
				//we wait and we try again
				try {
					Thread.sleep(1000);
					try{
						mHelper.launchPurchaseFlow(act, ID, 10001,   
								this, UUID.randomUUID().toString());
					} catch (IllegalStateException s2){

					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	public void purchase(final Activity act, final IsPaidCallback ispc){
		if(mHelper.isSetupDone() ){
			purchaseAfterSetup(act, ispc);
		}
		else if(!mHelper.isAsyncInProgress()){
			try{
				mHelper.startSetup(new IabHelperInterface.OnIabSetupFinishedListener() {
					@Override
					public void onIabSetupFinished(IabResult result) {
						purchaseAfterSetup(act, ispc);
					}
				});
			} catch (IllegalStateException s){
				//an async task is still running, we can wait (the on
				try {
					Thread.sleep(500);
					purchaseAfterSetup(act, ispc);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}

	@Override
	public void onQueryInventoryFinished(com.archos.mediacenter.video.billingutils.IabResult result,
			Inventory inv) {
		if (result.isFailure()) {
			// handle error here
			if(ispc!=null)
				ispc.hasBeenPaid(CHECKING_IMPOSSIBLE);
		}
		else {
			if(DBG )Log.d("billing", "ok :"+(inv.hasDetails(ID)?"hasDetails":"!hasDetails"));
			// does the user have the premium upgrade?
			if(ispc!=null)
				ispc.hasBeenPaid(inv.hasPurchase(ID)?HAS_BEEN_PURCHASE:inv.hasDetails(ID)?HAS_NOT_BEEN_PURCHASE:CHECKING_IMPOSSIBLE);
		}

	}
	@Override
	public void onIabPurchaseFinished(IabResult result, Purchase info) {
		if (result.isFailure()) {
			ispc.hasBeenPaid(result.getResponse()==BillingResponse.BILLING_RESPONSE_RESULT_USER_CANCELED||result.getResponse()==BillingResponse.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED?HAS_BEEN_PURCHASE:CHECKING_IMPOSSIBLE_PURCHASE_FLOW);
			return;
		}      
		else if (info.getSku().equals(ID)) {
			// consume
			//checkPayement(ispc);
			ispc.hasBeenPaid(info.mPurchaseState==BillingResponse.BILLING_RESPONSE_RESULT_OK||info.mPurchaseState==BillingResponse.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED?HAS_BEEN_PURCHASE:HAS_NOT_BEEN_PURCHASE);
		}
		//ispc.hasBeenPaid(HAS_NOT_BEEN_PURCHASE);
	}
	public boolean handleActivityResult(int requestCode, int resultCode,
			Intent data) {
		return mHelper.handleActivityResult(requestCode, resultCode, data);

	}
}
