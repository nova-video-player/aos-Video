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

/**
 * 
 */
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
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;

import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
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
        log.debug("OAuthDialog");
        mdata = oa;
		mReq = req;
		mListener=o;
	}
	
	/**
	 * 
	 * @return The used OAuthData
	 */
	public OAuthData getData() {
		log.debug("getData");
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
		log.debug("onCreate");

        // get another progress dialog while loading the page in this dialog
        mProgress = NovaProgressDialog.show(getContext(), "", getContext().getResources().getString(R.string.loading), true);
		mProgress.setCancelable(true);
		mProgress.setCanceledOnTouchOutside(false);

		setContentView(R.layout.oauth_dialog);

		getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

		mWebView = (NovaWebView) findViewById(R.id.webview);
		mWebView.getSettings().setJavaScriptEnabled(true);
		//mWebView.setVerticalScrollBarEnabled(false);
		//mWebView.setHorizontalScrollBarEnabled(false);
		// resize to fit content
		mWebView.getSettings().setUseWideViewPort(true);
		mWebView.getSettings().setLoadWithOverviewMode(true);

		mWebView.setWebViewClient(new OAuthWebViewClient());
		mWebView.setWebChromeClient(new WebChromeClient());
		mWebView.loadUrl(mReq.getLocationUri());

		CookieManager.getInstance().removeAllCookies(null);

	}

	public NovaWebView getWebView(){
		log.debug("getWebView");
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
			log.debug("shouldOverrideUrlLoading API21-23 for url " + url);
			String urldecode = null;
			try {
				urldecode = URLDecoder.decode(url, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				log.warn("OAuthWebViewClient:shouldOverrideUrlLoading: caught UnsupportedEncodingException");
			}
			Uri uri = Uri.parse(urldecode);
			if (!"localhost".equals(uri.getHost()) || !urldecode.contains("code=")) {
				log.debug("shouldOverrideUrlLoading: shouldOverrideUrlLoading false for host " + uri.getHost() + " and urldecode is " + urldecode);
				return false;
			}
			mdata.code = uri.getQueryParameter("code");
			OAuthDialog.this.dismiss();
			mListener.onFinished(mdata);

			return true;
		}

        // this one is for Android API 24+
        @RequiresApi(Build.VERSION_CODES.M)
        @Override
		public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
			String url = request.getUrl().toString();
			log.debug("shouldOverrideUrlLoading API24+ for url " + url);
			String urldecode = null;
			try {
				urldecode = URLDecoder.decode(url, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				log.warn("OAuthWebViewClient:shouldOverrideUrlLoading: caught UnsupportedEncodingException");
			}
			Uri uri = Uri.parse(urldecode);
			if (!"localhost".equals(uri.getHost()) || !urldecode.contains("code=")) {
				log.debug("shouldOverrideUrlLoading: shouldOverrideUrlLoading false for host " + uri.getHost() + " and urldecode is " + urldecode);
				return false;
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
			log.debug("onReceivedError API21,22 for url " + failingUrl);
        	if(mListener!=null)
        		mListener.onFinished(mdata);
            OAuthDialog.this.dismiss();
			log.warn("onReceivedError: error code=" + errorCode + ", meaning " + description);
			Toast.makeText(getContext(), "No Internet or " + description , Toast.LENGTH_LONG).show();
		}

        // for Android 23+
        @RequiresApi(Build.VERSION_CODES.M)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,  WebResourceError error)
        {
			log.debug("onReceivedError API23+");
			super.onReceivedError(view, request, error);
			if(mListener!=null)
                mListener.onFinished(mdata);
			OAuthDialog.this.dismiss();
			log.warn("onReceivedError: error code is " + error.getErrorCode() + ", description " + error.getDescription());
			Toast.makeText(getContext(), "No Internet or code " + + error.getErrorCode() + ", description " + error.getDescription() , Toast.LENGTH_LONG).show();
		}

        /*
        **  Display a dialog when the page start
        **
        */
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon)
        {
			log.debug("onPageStarted for url " + url);
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
			log.debug("onPageFinished for url " + url);
			super.onPageFinished(view, url);
			mWebView.resetDoItOnce();
			mProgress.dismiss();
			injectCSS();
		}
	}

	//workaround to be accepted on amazon store
	private void injectCSS() {
		log.debug("injectCSS");
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
