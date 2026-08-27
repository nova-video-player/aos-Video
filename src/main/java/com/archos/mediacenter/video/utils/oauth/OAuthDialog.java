// Copyright 2017 Archos SA
// Copyright 2020 Courville Software
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

package com.archos.mediacenter.video.utils.oauth;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;

import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.ui.NovaProgressDialog;
import com.archos.mediacenter.video.utils.MiscUtils;
import com.archos.mediacenter.video.utils.NovaWebView;

/**
 * A full screen OAuth dialog which contains a webview. This takes an authorize url
 * and returns a filled OAuthData in the OAuthCallback.onFinished method.
 */
public class OAuthDialog extends Dialog {

	private static final Logger log = LoggerFactory.getLogger(OAuthDialog.class);

	private NovaProgressDialog mProgress;
	private LinearLayout mLayout;
	private NovaWebView mWebView;
	private OAuthCallback mListener;
	private OAuthClientRequest mReq;
	private OAuthData mdata;
	private static final FrameLayout.LayoutParams MATCH = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);

	/**
	 * @param context
	 * @param o The OAuth object which calls this dialog
	 * @param url The authorize url
	 */
	public OAuthDialog(Context context, OAuthCallback o,OAuthData oa, OAuthClientRequest req) {
		super(context);
		if (log.isDebugEnabled()) log.debug("OAuthDialog");
		mdata = oa;
		mReq = req;
		mListener=o;
	}
	
	/**
	 * 
	 * @return The used OAuthData
	 */
	public OAuthData getData() {
		if (log.isDebugEnabled()) log.debug("getData");
		return mdata;
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("SetJavaScriptEnabled")
	@Override
	/**
	 * When the dialog is created, we add the webview and load the authorize url.
	 */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (log.isDebugEnabled()) log.debug("onCreate");

		// get another progress dialog while loading the page in this dialog
		mProgress = NovaProgressDialog.show(getContext(), "", getContext().getResources().getString(R.string.loading), true);
		mProgress.setCancelable(true);
		mProgress.setCanceledOnTouchOutside(false);

				setContentView(R.layout.oauth_dialog);

		// Make window transparent until webview is loaded
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.alpha = 0.0f;
		getWindow().setAttributes(lp);

		getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

		mWebView = (NovaWebView) findViewById(R.id.webview);
		// Essential JavaScript for OAuth flow
		mWebView.getSettings().setJavaScriptEnabled(true);
		// Allow mixed content (for OAuth callback URL handling)
		mWebView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
		// Enhanced security settings
		mWebView.getSettings().setDomStorageEnabled(false);
		mWebView.getSettings().setDatabaseEnabled(false);
		// App cache is disabled by default in modern WebView, setAppCacheEnabled was deprecated
		mWebView.getSettings().setGeolocationEnabled(false);
		mWebView.getSettings().setAllowFileAccess(false);
		mWebView.getSettings().setAllowContentAccess(false);
		mWebView.getSettings().setAllowFileAccessFromFileURLs(false);
		mWebView.getSettings().setAllowUniversalAccessFromFileURLs(false);
		// Set user agent to avoid being served a mobile-optimized/minimal version
		String userAgent = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " +
				Build.BRAND + " " + Build.MODEL + ") AppleWebKit/537.36";
		mWebView.getSettings().setUserAgentString(userAgent);
		if (log.isDebugEnabled()) log.debug("onCreate: Using user agent: {}", userAgent);
		// TV-friendly display settings
		//mWebView.setVerticalScrollBarEnabled(false);
		//mWebView.setHorizontalScrollBarEnabled(false);
		// Use narrow columns for proper mobile/TV viewport (don't use wide viewport which renders as desktop)
		mWebView.getSettings().setUseWideViewPort(false);
		mWebView.getSettings().setLoadWithOverviewMode(false);
		// Scale content smaller for TV viewing
		mWebView.getSettings().setDefaultZoom(android.webkit.WebSettings.ZoomDensity.FAR);
		mWebView.setWebViewClient(new OAuthWebViewClient());
		mWebView.setWebChromeClient(new WebChromeClient());

		// Enable cookies for OAuth flow
		CookieManager cookieManager = CookieManager.getInstance();
		cookieManager.setAcceptCookie(true);
		cookieManager.setAcceptThirdPartyCookies(mWebView, true);

		mWebView.loadUrl(mReq.getLocationUri());

	}

	public NovaWebView getWebView(){
		if (log.isDebugEnabled()) log.debug("getWebView");
		return mWebView;
	}
	
	/**
	 * Set the callback when the authorization ends.
	 * 
	 * @param callback
	 */
	public void setOAuthCallback(OAuthCallback callback) {
		mListener = callback;
	}


	private class OAuthWebViewClient extends WebViewClient {

		/*
        **  Manage if the url should be load or not, and get the result of the request
        **
        */
		// this one is for Android API 21-23
        @SuppressWarnings("deprecation")
        @Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			if (log.isDebugEnabled()) log.debug("shouldOverrideUrlLoading API21-23 for url {}", url);
			String urldecode = null;
			try {
				urldecode = URLDecoder.decode(url, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				log.warn("OAuthWebViewClient:shouldOverrideUrlLoading: caught UnsupportedEncodingException");
			}
			Uri uri = Uri.parse(urldecode);
			if (!"localhost".equals(uri.getHost()) && !"auth".equals(uri.getHost())) {
				// Enhanced validation for OAuth callback - allow only Trakt domain, localhost, or nova.trakt://auth
				if (uri.getHost() != null && (uri.getHost().endsWith("trakt.tv") || uri.getHost().equals("localhost") || uri.getHost().equals("auth"))) {
					if (log.isDebugEnabled()) log.debug("shouldOverrideUrlLoading API21-23: allowing Trakt domain or custom auth host: {}", uri.getHost());
					return false; // Continue loading
				} else {
					log.warn("shouldOverrideUrlLoading API21-23: blocking non-Trakt domain: {}", uri.getHost());
					return true; // Block navigation to non-Trakt domains
				}
			}
			mdata.code = uri.getQueryParameter("code");
			OAuthDialog.this.dismiss();
			mListener.onFinished(mdata);

			return true;
		}

        // this one is for Android API 24+
        @Override
		public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
			String url = request.getUrl().toString();
			if (log.isDebugEnabled()) log.debug("shouldOverrideUrlLoading API24+ for url {}", url);
			String urldecode = null;
			try {
				urldecode = URLDecoder.decode(url, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				log.warn("OAuthWebViewClient:shouldOverrideUrlLoading: caught UnsupportedEncodingException");
			}
			Uri uri = Uri.parse(urldecode);
			if (!"localhost".equals(uri.getHost()) && !"auth".equals(uri.getHost())) {
				// Enhanced validation for OAuth callback - allow only Trakt domain, localhost, or nova.trakt://auth
				if (uri.getHost() != null && (uri.getHost().endsWith("trakt.tv") || uri.getHost().equals("localhost") || uri.getHost().equals("auth"))) {
					if (log.isDebugEnabled()) log.debug("shouldOverrideUrlLoading API24+: allowing Trakt domain or custom auth host: {}", uri.getHost());
					return false; // Continue loading
				} else {
					log.warn("shouldOverrideUrlLoading API24+: blocking non-Trakt domain: {}", uri.getHost());
					return true; // Block navigation to non-Trakt domains
				}
			}
			mdata.code = uri.getQueryParameter("code");
			OAuthDialog.this.dismiss();
			mListener.onFinished(mdata);

			return true;
		}


		/*
		**  Catch the error if an error occurs
		**
		*/
		// for Android 21-22
		@SuppressWarnings("deprecation")
		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
		{
			super.onReceivedError(view, errorCode, description, failingUrl);
			if (log.isDebugEnabled()) log.debug("onReceivedError API21,22 for url {}", failingUrl);
			if(mListener!=null)
				mListener.onFinished(mdata);
			OAuthDialog.this.dismiss();
			log.warn("onReceivedError: error code={}, description={}, failingUrl={}", errorCode, description, failingUrl);
			// ERR_FAILED (-1) often indicates SSL certificate issues or DNS resolution failures
			String errorMsg = "No Internet";
			if (errorCode == -1) {
				errorMsg = "Cannot reach Trakt (network error). Check internet, DNS, or proxy settings";
			}
			Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
		}

        // for Android 23+
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,  WebResourceError error)
        {
			if (log.isDebugEnabled()) log.debug("onReceivedError API23+");
			super.onReceivedError(view, request, error);
			log.warn("onReceivedError: error code={}, description={}, failingUrl={}, mainFrame={}", error.getErrorCode(), error.getDescription(), request.getUrl(), request.isForMainFrame());
			// Only abort the auth flow for main frame failures: sub-resource failures
			// (favicon, analytics, ORB-blocked assets, etc.) must not cancel a successfully
			// loaded sign-in page.
			if (!request.isForMainFrame()) return;
			if(mListener!=null)
                mListener.onFinished(mdata);
			OAuthDialog.this.dismiss();
			// ERR_FAILED (-1) often indicates SSL certificate issues or DNS resolution failures
			String errorMsg = "No Internet";
			if (error.getErrorCode() == -1) {
				errorMsg = "Cannot reach Trakt (network error). Check internet, DNS, or proxy settings";
			}
			Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
		}

		/*
		**  Handle SSL certificate errors
		**
		*/
		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error)
		{
			if (log.isDebugEnabled()) log.debug("onReceivedSslError");
			super.onReceivedSslError(view, handler, error);
			if(mListener!=null)
				mListener.onFinished(mdata);
			OAuthDialog.this.dismiss();
			log.error("onReceivedSslError: SSL error on url {}, primary error: {}", error.getUrl(), error.getPrimaryError());
			Toast.makeText(getContext(), "SSL Certificate Error - Cannot connect to Trakt", Toast.LENGTH_LONG).show();
		}

		/*
		**  Handle HTTP errors (API 23+)
		**
		*/
		@Override
		public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse)
		{
			super.onReceivedHttpError(view, request, errorResponse);
			// Log all HTTP errors for debugging
			String resourceType = request.isForMainFrame() ? "MAIN FRAME" : "RESOURCE";
			log.warn("onReceivedHttpError: HTTP {} for {} resource: {}", errorResponse.getStatusCode(), resourceType, request.getUrl());
			// Only dismiss dialog and show error for main frame HTTP errors
			if (request.isForMainFrame()) {
				log.error("onReceivedHttpError: Main frame failed with HTTP {}", errorResponse.getStatusCode());
				if(mListener!=null)
					mListener.onFinished(mdata);
				OAuthDialog.this.dismiss();
				// trakt.tv/auth/<provider> (Google, Facebook, Apple, etc.) is the handoff to a
				// third-party identity provider. Those providers commonly refuse to complete
				// sign-in inside an embedded WebView, which trakt.tv surfaces as an HTTP error
				// here. Give the user an actionable message instead of a generic network error.
				String host = request.getUrl().getHost();
				String path = request.getUrl().getPath();
				if (host != null && host.endsWith("trakt.tv") && path != null && path.startsWith("/auth/")) {
					Toast.makeText(getContext(), R.string.trakt_social_signin_unsupported, Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(getContext(), "HTTP Error " + errorResponse.getStatusCode() + " - Cannot connect to Trakt", Toast.LENGTH_LONG).show();
				}
			}
		}

        /*
        **  Display a dialog when the page start
        **
        */
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon)
        {
			if (log.isDebugEnabled()) log.debug("onPageStarted for url {}", url);
			super.onPageStarted(view, url, favicon);
			Activity activity = MiscUtils.getActivityFromContext(getContext());
			if (activity != null && ! activity.isFinishing() && ! activity.isDestroyed())
				mProgress.show();
        }

		/*
		**  Remove the dialog when the page finish loading
		**
		*/
		@Override
				public void onPageFinished(WebView view, String url)
		{
			if (log.isDebugEnabled()) log.debug("onPageFinished for url {}", url);
			super.onPageFinished(view, url);
				mWebView.resetDoItOnce();
				mProgress.dismiss();
				// Make window opaque since webview is loaded
				WindowManager.LayoutParams lp = getWindow().getAttributes();
				lp.alpha = 1.0f;
				getWindow().setAttributes(lp);
				injectCSS();
		}
	}

	//workaround to be accepted on amazon store
	private void injectCSS() {
		if (log.isDebugEnabled()) log.debug("injectCSS");
		try {
			String css = ".col-xs-4 a:focus .btn{ background-color:blue !important; }";
			getWebView().loadUrl("javascript:(function() {" +
					"var parent = document.getElementsByTagName('head').item(0);" +
					"var style = document.createElement('style');" +
					"style.type = 'text/css';" +
					"style.innerHTML=\"" + css + "\";" +
					"parent.appendChild(style);" +
					"})()");
		} catch (Exception e) {
			log.warn("injectCSS: caught Exception", e);
		}
	}

}
