// Copyright 2024 Courville Software
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

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.MiscUtils;
import com.archos.mediacenter.video.utils.oauth.OAuthCallback;
import com.archos.mediacenter.video.utils.oauth.OAuthData;
import com.archos.mediacenter.video.utils.oauth.OAuthDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

public class TraktDeviceAuthActivity extends AppCompatActivity {

    private static final Logger log = LoggerFactory.getLogger(TraktDeviceAuthActivity.class);
    private static final String PREFERENCE_PHONE_AUTH_STATE = "trakt_phone_auth_state";
    private static final String PREFERENCE_PHONE_AUTH_COMPLETED_STATE = "trakt_phone_auth_completed_state";

    private TextView mVerificationUrlText;
    private TextView mUserCodeText;
    private TextView mStatusText;
    private ProgressBar mProgressBar;
    private Button mCancelButton;

    private Trakt.deviceCode mDeviceCode;
    private Handler mPollHandler;
    private Runnable mPollRunnable;
    private long mExpirationTime;
    private boolean mIsPolling = false;
    private boolean mIsTv = false;
    private OAuthDialog mOAuthDialog;
    private String mPhoneAuthState;
    private String mPhoneAuthCallbackState;
    private boolean mPhoneAuthWaiting;

    private void resetDeviceCodeViews() {
        if (mVerificationUrlText != null) {
            mVerificationUrlText.setText("");
        }
        if (mUserCodeText != null) {
            mUserCodeText.setText("");
        }
    }

    /** Shows a real phone screen while the browser owns the OAuth interaction. */
    private void setupPhoneAuthView() {
        setContentView(R.layout.activity_trakt_phone_auth);
        mStatusText = findViewById(R.id.status_message);
        mProgressBar = findViewById(R.id.progress_bar);
        mCancelButton = findViewById(R.id.cancel_button);
        mStatusText.setText(R.string.trakt_device_auth_waiting);
        mCancelButton.setOnClickListener(v -> {
            clearPhoneAuthState();
            finish();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIsTv = MiscUtils.isAndroidTV(this);
        if (!mIsTv) {
            // This is also needed by a callback instance created by the browser. Without it,
            // Android displays the activity title over an otherwise empty window while the
            // authorization code is exchanged.
            setupPhoneAuthView();
            if (handlePhoneAuthRedirect(getIntent())) {
                return;
            }
            // Phone/tablet: use the installed browser for social sign-in. The WebView is
            // retained only when the device cannot open a browser at all.
            startPhoneAuth();
            return;
        }
        setContentView(R.layout.activity_trakt_device_auth);

        // Bind views
        mVerificationUrlText = findViewById(R.id.verification_url);
        mUserCodeText = findViewById(R.id.user_code);
        mStatusText = findViewById(R.id.status_message);
        mProgressBar = findViewById(R.id.progress_bar);
        mCancelButton = findViewById(R.id.cancel_button);
        resetDeviceCodeViews();

        mCancelButton.setOnClickListener(v -> {
            stopPolling();
            finish();
        });
        // Default focus to cancel so DPAD-OK works immediately
        mCancelButton.requestFocus();

        // Generate device code
        new GenerateDeviceCodeTask().execute();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!mIsTv && handlePhoneAuthRedirect(intent)) {
            return;
        }
        if (log.isDebugEnabled()) log.debug("onNewIntent: ignoring non-OAuth intent");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsTv && mPhoneAuthWaiting && consumeCompletedPhoneAuth()) {
            if (log.isDebugEnabled()) log.debug("onResume: completing phone authentication in original activity");
            mPhoneAuthWaiting = false;
            clearPhoneAuthState();
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (mOAuthDialog != null && mOAuthDialog.isShowing()) {
            mOAuthDialog.dismiss();
        }
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
                if (!mIsPolling) {
                    return;
                }

                // Check if code expired
                if (System.currentTimeMillis() >= mExpirationTime) {
                    if (log.isDebugEnabled()) log.debug("Device code expired");
                    onAuthenticationFailed(getString(R.string.trakt_device_auth_timeout));
                    return;
                }

                // Poll for token
                new ExchangeDeviceCodeTask().execute(mDeviceCode.device_code);
            }
        };

        // Start first poll after interval
        mPollHandler.postDelayed(mPollRunnable, mDeviceCode.interval * 1000L);
    }

    private void onAuthenticationSuccess(Trakt.accessToken token) {
        if (log.isDebugEnabled()) log.debug("onAuthenticationSuccess");
        stopPolling();

        // Store tokens
        Trakt.setAccessToken(PreferenceManager.getDefaultSharedPreferences(this), token.access_token);
        Trakt.setRefreshToken(PreferenceManager.getDefaultSharedPreferences(this), token.refresh_token);
        Trakt.setAccountLocked(PreferenceManager.getDefaultSharedPreferences(this), false);
        // Disable collection sync to avoid hitting Trakt library limits
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(VideoPreferencesCommon.KEY_TRAKT_SYNC_COLLECTION, false)
                .apply();

        if (!mIsTv) {
            if (!mPhoneAuthWaiting && mPhoneAuthCallbackState != null) {
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putString(PREFERENCE_PHONE_AUTH_COMPLETED_STATE, mPhoneAuthCallbackState)
                        .apply();
            }
            clearPhoneAuthState();
            setResult(RESULT_OK);
            finish();
            return;
        }

        // Update UI
        mStatusText.setText(R.string.trakt_device_auth_success);
        mProgressBar.setVisibility(View.GONE);

        // Finish activity after short delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            setResult(RESULT_OK);
            finish();
        }, 1500);
    }

    private void onAuthenticationFailed(String message) {
        if (log.isDebugEnabled()) log.debug("onAuthenticationFailed: {}", message);
        stopPolling();
        mDeviceCode = null;

        if (!mIsTv) {
            clearPhoneAuthState();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mStatusText.setText(message);
        mProgressBar.setVisibility(View.GONE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Change button to retry
        mCancelButton.setText(R.string.trakt_signin);
        mCancelButton.setOnClickListener(v -> {
            // Retry
            resetDeviceCodeViews();
            mProgressBar.setVisibility(View.VISIBLE);
            mStatusText.setText(R.string.trakt_device_auth_waiting);
            mCancelButton.setText(android.R.string.cancel);
            mCancelButton.setOnClickListener(v2 -> {
                stopPolling();
                finish();
            });
            new GenerateDeviceCodeTask().execute();
        });
    }

    private void startPhoneAuth() {
        if (log.isDebugEnabled()) log.debug("startPhoneAuth: starting browser OAuth flow");
        try {
            String state = UUID.randomUUID().toString();
            mPhoneAuthState = state;
            mPhoneAuthWaiting = true;
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(PREFERENCE_PHONE_AUTH_STATE, state)
                    .apply();
            OAuthClientRequest request = Trakt.getAuthorizationRequestWithState(
                    PreferenceManager.getDefaultSharedPreferences(this), state);
            try {
                new CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(request.getLocationUri()));
                return;
            } catch (ActivityNotFoundException e) {
                log.warn("startPhoneAuth: no browser available; falling back to embedded WebView", e);
                startPhoneAuthInWebView(request, state);
            }
        } catch (Exception e) {
            log.error("startPhoneAuth: failed to start browser authentication", e);
            Toast.makeText(this, R.string.trakt_device_auth_error, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * The WebView fallback permits Trakt username/password authentication on devices without
     * a browser. Social identity providers such as Google may reject this embedded surface.
     */
    private void startPhoneAuthInWebView(OAuthClientRequest request, String state) {
        if (log.isDebugEnabled()) log.debug("startPhoneAuthInWebView: showing OAuth dialog");
        try {
            OAuthData oauthData = new OAuthData();
            oauthData.state = state;
            mOAuthDialog = new OAuthDialog(this, new OAuthCallback() {
                @Override
                public void onFinished(OAuthData data) {
                    if (data != null && data.code != null && state.equals(data.returnedState)) {
                        if (log.isDebugEnabled()) log.debug("startPhoneAuth: received auth code, exchanging");
                        new ExchangeAuthCodeTask().execute(data.code);
                    } else {
                        log.warn("startPhoneAuth: auth cancelled, no code returned, or state validation failed");
                        clearPhoneAuthState();
                        Toast.makeText(TraktDeviceAuthActivity.this, R.string.trakt_device_auth_error, Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }, oauthData, request);
            mOAuthDialog.show();
        } catch (Exception e) {
            log.error("startPhoneAuthInWebView: failed to start auth dialog", e);
            Toast.makeText(this, R.string.trakt_device_auth_error, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /** Receives nova.trakt://auth from the system browser after Trakt authorization. */
    private boolean handlePhoneAuthRedirect(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        Uri uri = intent.getData();
        if (uri == null || !"nova.trakt".equals(uri.getScheme()) || !"auth".equals(uri.getHost())) {
            return false;
        }

        String code = uri.getQueryParameter("code");
        String returnedState = uri.getQueryParameter("state");
        String expectedState = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(PREFERENCE_PHONE_AUTH_STATE, null);
        if (code == null || expectedState == null || !expectedState.equals(returnedState)) {
            log.warn("handlePhoneAuthRedirect: rejecting callback with missing code or invalid state");
            Toast.makeText(this, R.string.trakt_device_auth_error, Toast.LENGTH_LONG).show();
            finish();
            return true;
        }

        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .remove(PREFERENCE_PHONE_AUTH_STATE)
                .apply();
        mPhoneAuthCallbackState = expectedState;
        if (log.isDebugEnabled()) log.debug("handlePhoneAuthRedirect: received authorization code");
        new ExchangeAuthCodeTask().execute(code);
        return true;
    }

    private void clearPhoneAuthState() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .remove(PREFERENCE_PHONE_AUTH_STATE)
                .apply();
        mPhoneAuthState = null;
        mPhoneAuthCallbackState = null;
    }

    private boolean consumeCompletedPhoneAuth() {
        String completedState = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(PREFERENCE_PHONE_AUTH_COMPLETED_STATE, null);
        if (mPhoneAuthState == null || !mPhoneAuthState.equals(completedState)) {
            return false;
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .remove(PREFERENCE_PHONE_AUTH_COMPLETED_STATE)
                .apply();
        return true;
    }

    private class GenerateDeviceCodeTask {
        private boolean mAccountLocked = false;
        private boolean mForbidden = false;
        private boolean mServiceUnavailable = false;
        private boolean mMalformedResponse = false;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Handler handler = new Handler(Looper.getMainLooper());

        void execute() {
            executor.execute(() -> {
                try {
                    Trakt.deviceCode result = null;
                    try {
                        result = Trakt.generateDeviceCode();
                    } catch (Trakt.AccountLockedError e) {
                        mAccountLocked = true;
                    } catch (Trakt.ForbiddenError e) {
                        mForbidden = true;
                    } catch (Trakt.ServiceUnavailableError e) {
                        mServiceUnavailable = true;
                    } catch (Trakt.InvalidDeviceCodeResponseError e) {
                        mMalformedResponse = true;
                    }
                    final Trakt.deviceCode finalResult = result;
                    handler.post(() -> {
                        if (finalResult != null) {
                            mDeviceCode = finalResult;
                            if (log.isDebugEnabled()) log.debug("Device code generated: user_code={}", finalResult.user_code);
                            mVerificationUrlText.setText(finalResult.verification_url);
                            mUserCodeText.setText(finalResult.user_code);
                            startPolling();
                        } else {
                            resetDeviceCodeViews();
                            log.error("Failed to generate device code");
                            if (mAccountLocked) {
                                onAuthenticationFailed(getString(R.string.trakt_account_locked));
                            } else if (mForbidden) {
                                onAuthenticationFailed("HTTP Error 403 - Forbidden");
                            } else if (mServiceUnavailable) {
                                onAuthenticationFailed("HTTP Error 503 - Service Unavailable (Trakt down?)");
                            } else if (mMalformedResponse) {
                                onAuthenticationFailed(getString(R.string.trakt_device_auth_invalid_code));
                            } else {
                                onAuthenticationFailed(getString(R.string.trakt_device_auth_error));
                            }
                        }
                    });
                } finally {
                    executor.shutdown();
                }
            });
        }
    }

    private class ExchangeDeviceCodeTask {
        private boolean mAccountLocked = false;
        private boolean mForbidden = false;
        private boolean mServiceUnavailable = false;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Handler handler = new Handler(Looper.getMainLooper());

        void execute(String deviceCode) {
            executor.execute(() -> {
                try {
                    Trakt.accessToken result = null;
                    try {
                        result = Trakt.exchangeDeviceCodeForAccessToken(deviceCode);
                    } catch (Trakt.AccountLockedError e) {
                        mAccountLocked = true;
                    } catch (Trakt.ForbiddenError e) {
                        mForbidden = true;
                    } catch (Trakt.ServiceUnavailableError e) {
                        mServiceUnavailable = true;
                    }
                    final Trakt.accessToken finalResult = result;
                    handler.post(() -> {
                        if (finalResult != null) {
                            if (log.isDebugEnabled()) log.debug("Access token received");
                            onAuthenticationSuccess(finalResult);
                        } else {
                            if (mAccountLocked) {
                                onAuthenticationFailed(getString(R.string.trakt_account_locked));
                            } else if (mForbidden) {
                                onAuthenticationFailed("HTTP Error 403 - Forbidden");
                            } else if (mServiceUnavailable) {
                                onAuthenticationFailed("HTTP Error 503 - Service Unavailable (Trakt down?)");
                            } else if (mIsPolling && System.currentTimeMillis() < mExpirationTime) {
                                mPollHandler.postDelayed(mPollRunnable, mDeviceCode.interval * 1000L);
                            } else if (!mIsPolling) {
                                if (log.isDebugEnabled()) log.debug("Polling stopped");
                            } else {
                                if (log.isDebugEnabled()) log.debug("Code expired during polling");
                                onAuthenticationFailed(getString(R.string.trakt_device_auth_timeout));
                            }
                        }
                    });
                } finally {
                    executor.shutdown();
                }
            });
        }
    }

    private class ExchangeAuthCodeTask {
        private boolean mAccountLocked = false;
        private boolean mForbidden = false;
        private boolean mServiceUnavailable = false;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Handler handler = new Handler(Looper.getMainLooper());

        void execute(String code) {
            executor.execute(() -> {
                try {
                    Trakt.accessToken result = null;
                    try {
                        result = Trakt.getAccessToken(code);
                    } catch (Trakt.AccountLockedError e) {
                        mAccountLocked = true;
                    } catch (Trakt.ForbiddenError e) {
                        mForbidden = true;
                    } catch (Trakt.ServiceUnavailableError e) {
                        mServiceUnavailable = true;
                    }
                    final Trakt.accessToken finalResult = result;
                    handler.post(() -> {
                        if (finalResult != null) {
                            if (log.isDebugEnabled()) log.debug("ExchangeAuthCodeTask: access token received");
                            onAuthenticationSuccess(finalResult);
                        } else {
                            log.warn("ExchangeAuthCodeTask: token exchange failed");
                            if (mAccountLocked) {
                                Toast.makeText(TraktDeviceAuthActivity.this, R.string.trakt_account_locked, Toast.LENGTH_LONG).show();
                            } else if (mForbidden) {
                                Toast.makeText(TraktDeviceAuthActivity.this, "HTTP Error 403 - Forbidden", Toast.LENGTH_LONG).show();
                            } else if (mServiceUnavailable) {
                                Toast.makeText(TraktDeviceAuthActivity.this, "HTTP Error 503 - Service Unavailable (Trakt down?)", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(TraktDeviceAuthActivity.this, R.string.trakt_device_auth_error, Toast.LENGTH_LONG).show();
                            }
                            finish();
                        }
                    });
                } finally {
                    executor.shutdown();
                }
            });
        }
    }
}
