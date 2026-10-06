// Copyright 2026 Courville Software
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

package com.archos.mediacenter.video.utils.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import com.archos.mediacenter.video.R;

/**
 * Receives the outcome of a PackageInstaller session. Declared in the sponsor-only
 * manifest so it exists only in builds that feature the updater.
 */
public class UpdateInstallReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_STATUS = "com.archos.mediacenter.video.UPDATE_INSTALL_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_INSTALL_STATUS.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        boolean success = status == PackageInstaller.STATUS_SUCCESS;
        UpdateManager manager = UpdateManager.getInstance();
        // When the settings UI is alive it reports failures itself (with the message), so
        // only show the receiver Toast when there is no pending callback to avoid a second,
        // overlapping message.
        if (success || !manager.hasPendingInstallCallback()) {
            CharSequence text = success
                    ? context.getString(R.string.updater_install_success)
                    : context.getString(R.string.updater_install_failed) + " " + (message != null ? message : "");
            Toast.makeText(context, text, Toast.LENGTH_LONG).show();
        }
        manager.deliverInstallResult(status, message);
    }
}
