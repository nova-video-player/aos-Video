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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

public abstract class IsPaidCallback {
	public static final boolean DEBUG = false;
	private Context mContext;
	public IsPaidCallback(Context ct){
		mContext = ct; //used to debug
	}
	public boolean checkPayement(int isPaidResponse){
		boolean hasBeenPaid = isPaidResponse==BillingUtils.HAS_BEEN_PURCHASE;
    	if(isPaidResponse==1) { //Network error, we check on pref and take last value
    		hasBeenPaid = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(BillingUtils.PAID_STATUS_PREF, false);
    	}
    	return hasBeenPaid;
	}
	public  void hasBeenPaid(int isPaid){
		//if has been paid  or not (not network error) we save status)
		if(isPaid==0 || isPaid==2){
			PreferenceManager.getDefaultSharedPreferences(mContext).edit().putBoolean(BillingUtils.PAID_STATUS_PREF,isPaid==0).commit();
		
		}	
		if(DEBUG){
			String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());

			BufferedWriter bW;
			ConnectivityManager cm =
					(ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

			NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
			boolean isConnected = activeNetwork != null &&
					activeNetwork.isConnectedOrConnecting();
			try {
				bW = new BufferedWriter(new FileWriter("/sdcard/archosbillingdebug.txt", true));
				bW.write(currentDateTimeString+ ": " + "is paid : "+isPaid+ " network: "+String.valueOf(isConnected));
				bW.newLine();
				bW.flush();
				bW.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
}
