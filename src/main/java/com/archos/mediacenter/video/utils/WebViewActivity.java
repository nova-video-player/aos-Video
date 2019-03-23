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

package com.archos.mediacenter.video.utils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewFragment;

import com.archos.mediacenter.video.R;

public class WebViewActivity extends Activity {

    private static final String TAG = "WebViewActivity";
    private static final boolean DBG = false;

    private Uri mUri;
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUri = getIntent().getData();
        setContentView(R.layout.webview_activity);

        WebViewFragment wvf = (WebViewFragment)getFragmentManager().findFragmentById(R.id.webview_fragment);
        mWebView = wvf.getWebView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //WebViewFragment wvf = (WebViewFragment)getFragmentManager().findFragmentById(R.id.webview_fragment);
        initWebView(mWebView, mUri);

        mWebView.requestFocus();
        mWebView.loadUrl(mUri.toString());
    }

    private static void initWebView(WebView webview, Uri uri) {
        webview.setFocusable(true);
        webview.setInitialScale(0); // imdb does not look good in fullscreen with anything but this
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (DBG) Log.d(TAG, "shouldOverrideUrlLoading " + url);
                return false;
            }
        });
        // Remove 'Mobile' from the user agent to avoid phone-version of IMDB on TV screen...
        String userAgent = webview.getSettings().getUserAgentString();
        userAgent = userAgent.replace("Mobile", " ");
        webview.getSettings().setUserAgentString(userAgent);

        if (uri.toString().startsWith("https://www.youtube.com/tv#")) {
            webview.getSettings().setDomStorageEnabled(true);
            webview.getSettings().setMediaPlaybackRequiresUserGesture(false);
            webview.setWebChromeClient(new WebChromeClient() {
                @Override
                public Bitmap getDefaultVideoPoster() {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                }
            });
        }  
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && (mWebView.getUrl().startsWith("https://www.youtube.com/tv#/watch/ads/control")
                || mWebView.getUrl().startsWith("https://www.youtube.com/tv#/watch/video/control"))) {
            mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE));
        }
        else if (mWebView!=null && mWebView.canGoBack() && !mWebView.getUrl().startsWith("https://www.youtube.com/tv#")) {
            mWebView.goBack();
        }
        else {
            super.onBackPressed();;
        }
    }
}
