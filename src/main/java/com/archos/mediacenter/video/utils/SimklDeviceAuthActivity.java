// Copyright 2026 Nova Video Player Project
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

package com.archos.mediacenter.video.utils;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.simkl.Simkl;
import com.archos.mediacenter.video.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimklDeviceAuthActivity extends AppCompatActivity {

    private static final Logger log = LoggerFactory.getLogger(SimklDeviceAuthActivity.class);

    private TextView mVerificationUrlText;
    private TextView mUserCodeText;
    private TextView mStatusText;
    private ProgressBar mProgressBar;
    private Button mCancelButton;

    private Simkl.DeviceCode mDeviceCode;
    private Handler mPollHandler;
    private Runnable mPollRunnable;
    private long mExpirationTime;
    private boolean mIsPolling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simkl_device_auth);

        mVerificationUrlText = findViewById(R.id.verification_url);
        mUserCodeText = findViewById(R.id.user_code);
        mStatusText = findViewById(R.id.status_message);
        mProgressBar = findViewById(R.id.progress_bar);
        mCancelButton = findViewById(R.id.cancel_button);

        mCancelButton.setOnClickListener(v -> {
            stopPolling();
            finish();
        });
        mCancelButton.requestFocus();

        new GenerateDeviceCodeTask().execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }

    private void stopPolling() {
        mIsPolling = false;
        if (mPollHandler != null && mPollRunnable != null) {
            mPollHandler.removeCallbacks(mPollRunnable);
        }
    }

    private void startPolling() {
        if (mDeviceCode == null) {
            log.error("startPolling: device code is null");
            return;
        }

        mIsPolling = true;
        mExpirationTime = System.currentTimeMillis() + (mDeviceCode.expires_in * 1000L);
        mPollHandler = new Handler(Looper.getMainLooper());

        mPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mIsPolling) return;

                if (System.currentTimeMillis() >= mExpirationTime) {
                    if (log.isDebugEnabled()) log.debug("Simkl device code expired");
                    onAuthenticationFailed(getString(R.string.simkl_device_auth_timeout));
                    return;
                }

                new ExchangeDeviceCodeTask().execute(mDeviceCode.device_code);
            }
        };

        mPollHandler.postDelayed(mPollRunnable, mDeviceCode.interval * 1000L);
    }

    private void onAuthenticationSuccess(Simkl.AccessToken token) {
        if (log.isDebugEnabled()) log.debug("onAuthenticationSuccess");
        stopPolling();

        Simkl.setAccessToken(PreferenceManager.getDefaultSharedPreferences(this), token.access_token);
        if (token.refresh_token != null) {
            Simkl.setRefreshToken(PreferenceManager.getDefaultSharedPreferences(this), token.refresh_token);
        }

        mStatusText.setText(R.string.simkl_device_auth_success);
        mProgressBar.setVisibility(View.GONE);

        // Fetch user profile in background
        new Thread(() -> {
            String username = Simkl.fetchUserProfile(token.access_token);
            if (username != null && !username.isEmpty()) {
                Simkl.setUser(PreferenceManager.getDefaultSharedPreferences(SimklDeviceAuthActivity.this), username);
            }
        }).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            setResult(RESULT_OK);
            finish();
        }, 1500);
    }

    private void onAuthenticationFailed(String message) {
        if (log.isDebugEnabled()) log.debug("onAuthenticationFailed: {}", message);
        stopPolling();

        mStatusText.setText(message);
        mProgressBar.setVisibility(View.GONE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        mCancelButton.setText(R.string.simkl_signin);
        mCancelButton.setOnClickListener(v -> {
            mProgressBar.setVisibility(View.VISIBLE);
            mStatusText.setText(R.string.simkl_device_auth_waiting);
            mCancelButton.setText(android.R.string.cancel);
            mCancelButton.setOnClickListener(v2 -> {
                stopPolling();
                finish();
            });
            new GenerateDeviceCodeTask().execute();
        });
    }

    private class GenerateDeviceCodeTask extends AsyncTask<Void, Void, Simkl.DeviceCode> {
        @Override
        protected void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
            mStatusText.setText(R.string.simkl_device_auth_waiting);
        }

        @Override
        protected Simkl.DeviceCode doInBackground(Void... voids) {
            return Simkl.generateDeviceCode();
        }

        @Override
        protected void onPostExecute(Simkl.DeviceCode deviceCode) {
            if (isFinishing() || isDestroyed()) return;

            if (deviceCode != null && deviceCode.user_code != null) {
                mDeviceCode = deviceCode;
                mVerificationUrlText.setText(deviceCode.verification_url);
                mUserCodeText.setText(deviceCode.user_code);
                startPolling();
            } else {
                onAuthenticationFailed(getString(R.string.simkl_device_auth_timeout));
            }
        }
    }

    private class ExchangeDeviceCodeTask extends AsyncTask<String, Void, Simkl.AccessToken> {
        @Override
        protected Simkl.AccessToken doInBackground(String... params) {
            return Simkl.exchangeDeviceCodeForAccessToken(params[0]);
        }

        @Override
        protected void onPostExecute(Simkl.AccessToken token) {
            if (isFinishing() || isDestroyed()) return;

            if (token != null && token.access_token != null) {
                onAuthenticationSuccess(token);
            } else if (token != null && "access_denied".equalsIgnoreCase(token.error)) {
                onAuthenticationFailed(getString(R.string.simkl_device_auth_denied));
            } else if (token != null && "expired_token".equalsIgnoreCase(token.error)) {
                onAuthenticationFailed(getString(R.string.simkl_device_auth_timeout));
            } else {
                // Authorization pending or slow_down: schedule next polling check
                if (token != null && "slow_down".equalsIgnoreCase(token.error)) {
                    if (mDeviceCode != null) {
                        mDeviceCode.interval += 5;
                    }
                }
                long delay = (mDeviceCode != null ? mDeviceCode.interval : 5) * 1000L;
                if (mIsPolling && mPollHandler != null && mPollRunnable != null) {
                    mPollHandler.postDelayed(mPollRunnable, delay);
                }
            }
        }
    }
}
