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

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.archos.mediacenter.video.R;

public class WebViewActivity extends AppCompatActivity {

    private static final String TAG = "WebViewActivity";
    private static final boolean DBG = false;

    private Uri mUri;
    private WebView mWebView;
    private LinearLayout mLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide(); // no title bar
        mUri = getIntent().getData();
        setContentView(R.layout.webview_activity);
        mWebView = findViewById(R.id.webview_activity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWebView.onResume();
        initWebView(mWebView, mUri);
        mWebView.requestFocus();
        mWebView.loadUrl(mUri.toString());
    }

    private static void initWebView(WebView webview, Uri uri) {
        webview.setFocusable(true);
        webview.setInitialScale(0); // imdb does not look good in fullscreen with anything but this
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new WebViewClient() {
            // this one is for Android API 21-23
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (DBG) Log.d(TAG, "shouldOverrideUrlLoading " + url);
                return false;
            }
            // this one is for Android API 24+
            @RequiresApi(Build.VERSION_CODES.M)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (DBG) Log.d(TAG, "shouldOverrideUrlLoading API24+ for url " + url);
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

    @Override
    public void onPause() {
        super.onPause();
        mWebView.onPause();
    }

    @Override
    public void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }

}
