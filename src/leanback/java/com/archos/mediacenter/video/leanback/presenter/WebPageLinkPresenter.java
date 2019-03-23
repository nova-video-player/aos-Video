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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.Presenter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.WebPageLink;

/**
 * Created by vapillon on 10/04/15.
 */
public class WebPageLinkPresenter extends Presenter {

    private static final String TAG = "WebPageLinkPresenter";
    private static final boolean DBG = false;

    public class WebPageLinkViewHolder extends ViewHolder {
        BaseCardView mCard;
        View mPlaceholder;
        WebView mWebView;
        View mProgress;
        AsyncTask mWebViewDelayedInitTask;
        String mUrl = null;

        public WebPageLinkViewHolder(ViewGroup parent) {
            super(new BaseCardView(parent.getContext()));
            mCard = (BaseCardView)view;
            mCard.setFocusable(true);
            mCard.setFocusableInTouchMode(true);

            Context c = parent.getContext();
            mPlaceholder = new View(c);
            mPlaceholder.setBackgroundColor(c.getResources().getColor(R.color.lb_basic_card_bg_color));
            BaseCardView.LayoutParams lp = new BaseCardView.LayoutParams(
                    c.getResources().getDimensionPixelSize(R.dimen.details_weblink_width),
                    c.getResources().getDimensionPixelSize(R.dimen.details_weblink_height));
            lp.viewType = BaseCardView.LayoutParams.VIEW_TYPE_MAIN;
            mCard.addView(mPlaceholder, lp);

            if(DBG) Log.d(TAG, "Launching delayed creation of webview in " + mCard + " for " + this);
            mWebViewDelayedInitTask = new WebViewDelayedInitTask(mCard, mPlaceholder).execute();
        }

        void setUrl(String url) {
            mUrl = url;
            // Load in webview if it is already ready
            if (mWebView!=null) {
                mWebView.loadUrl(mUrl);
            }
            // Relaunch webview init if not on-going (it means it has been interrupted before finishing after ViewHolder creation)
            else if (mWebViewDelayedInitTask==null) {
                mWebViewDelayedInitTask = new WebViewDelayedInitTask(mCard, mPlaceholder).execute();
            }
        }

        public void stopLoading() {
            if (mWebView!=null)
                mWebView.stopLoading();
        }

        void abortWebviewInit() {
            if (mWebViewDelayedInitTask!=null) {
                if(DBG) Log.d(TAG, "Canceling delayed init for WebView " + this);
                mWebViewDelayedInitTask.cancel(false);
                mWebViewDelayedInitTask = null; // way to remember it is over
            }
        }

        private void initWebView() {
            mWebView.setFocusable(false);
            mWebView.setInitialScale(80);
            mWebView.getSettings().setJavaScriptEnabled(true);
            mWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if(DBG) Log.d(TAG, "shouldOverrideUrlLoading " + url);
                    //view.loadUrl(url);
                    return false;
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    mProgress.setVisibility(View.VISIBLE);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    mProgress.setVisibility(View.GONE);
                }
            });
            // Remove 'Mobile' from the user agent to avoid phone-version of IMDB on TV screen...
            String userAgent = mWebView.getSettings().getUserAgentString();
            userAgent = userAgent.replace("Mobile", " ");
            mWebView.getSettings().setUserAgentString(userAgent);
        }

        /**
         * Unconventional way to use the AsyncTask: the threaded part is just a delay done with a sleep
         * All the work need to be done on the UI thread, hence in onPostExecute()
         */
        class WebViewDelayedInitTask extends AsyncTask<View, Void, Boolean> {
            final ViewGroup mParent;
            final View mPlaceholder;

            public WebViewDelayedInitTask(ViewGroup parent, View placeholder) {
                mParent = parent;
                mPlaceholder = placeholder;
            }

            @Override
            protected Boolean doInBackground(View... views) {
                try { Thread.sleep(500); } catch (InterruptedException e) {}
                return Boolean.valueOf(!isCancelled());
            }

            @Override
            protected void onPostExecute(Boolean doit) {
                if (!doit.booleanValue() || isCancelled()) {
                    if(DBG) Log.d(TAG, "WebViewDelayedInitTask canceled");
                    return;
                }
                if(DBG) Log.d(TAG, "starting creation of WebView in " + mParent);

                Context c = mParent.getContext();
                View content = LayoutInflater.from(c).inflate(R.layout.leanback_weblink_cardview_content, mParent, false);
                mProgress = content.findViewById(R.id.progress);
                mWebView = (WebView)content.findViewById(R.id.webview);
                initWebView();

                mParent.removeView(mPlaceholder);
                mParent.addView(content);

                // Load the url if it has been setup already
                if (mUrl!=null) {
                    mWebView.loadUrl(mUrl);
                }
                mWebViewDelayedInitTask = null; // way to remember it is over
            }
        }


    }

    @Override
    public WebPageLinkViewHolder onCreateViewHolder(ViewGroup parent) {
        return new WebPageLinkViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        final WebPageLinkViewHolder vh = (WebPageLinkViewHolder)viewHolder;
        final WebPageLink link = (WebPageLink)item;
        if(DBG) Log.d(TAG, "onBindViewHolder "+viewHolder+" "+link.getUrl());
        vh.setUrl(link.getUrl());
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        WebPageLinkViewHolder vh = (WebPageLinkViewHolder)viewHolder;
        if(DBG) Log.d(TAG, "onBindViewHolder "+viewHolder);
        vh.stopLoading();
        vh.setUrl("about:blank");
        vh.abortWebviewInit();
    }
}
