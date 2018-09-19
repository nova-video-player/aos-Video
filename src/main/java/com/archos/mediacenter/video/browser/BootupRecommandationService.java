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

package com.archos.mediacenter.video.browser;


import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.AppState;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;


public class BootupRecommandationService extends BroadcastReceiver {
	private static final String TAG = "BootupActivity";

	private static final AppState.OnForeGroundListener sForegroundListener = new AppState.OnForeGroundListener() {
		@Override
		public void onForeGroundState(Context applicationContext, boolean foreground) {
			if(!foreground)
				scheduleRecommendationUpdate(applicationContext);
		}
	};
	public static final String UPDATE_ACTION = "com.archos.mediacenter.video.browser.BootupRecommandationService.UPDATE_ACTION";

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "BootupActivity initiated");
		if (intent.getAction().endsWith(Intent.ACTION_BOOT_COMPLETED)||intent.getAction().equalsIgnoreCase(UPDATE_ACTION)) {
			scheduleRecommendationUpdate(context);
		}
	}

	private static void scheduleRecommendationUpdate(Context context) {
		if(ArchosFeatures.isAndroidTV(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && AppState.isForeGround()){
			Intent recommendationIntent = new Intent(context, UpdateRecommendationsService.class);
			context.startService(recommendationIntent);
		}
	}

	public static void init() {
		AppState.addOnForeGroundListener(sForegroundListener);
	}
}