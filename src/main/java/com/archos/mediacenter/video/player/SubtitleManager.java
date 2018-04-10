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

package com.archos.mediacenter.video.player;

import com.archos.mediacenter.video.R;
import com.archos.medialib.Subtitle;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubtitleManager {
    private static final String TAG = "SubtitleManager";
    private static final boolean DBG = false;

    private Context             mContext;
    private ViewGroup           mPlayerView;
    private WindowManager              mWindow;
    private WindowManager.LayoutParams mLayoutParams = null;
    private Resources           mRes;
    private View                mSubtitleLayout = null;
    SubtitleGfxView             mSubtitleGfxView = null;
    Subtitle3DTextView          mSubtitleTxtView = null;
    private SubtitleSpacerView  mSubtitleSpacer = null;
    private LayoutParams        mSubtitleSpacerParams = null;
    private Drawable            mSubtitlePosHintDrawable;
    private int                 mScreenWidth;
    private int                 mScreenHeight;
    private int                 mSubtitleSize = 50;
    private int                 mSubtitleVPos = 10;
    private int                 mSubtitleVPosPixel;
    private int                 mSubtitleEvadedVPos;
    SpannableStringBuilder      mSpannableStringBuilder = null;
    TextShadowSpan              mTextShadowSpan = null;
    
    Surface                     mUiSurface;
    private boolean mForbidWindow ;
    DispSubtitleThread mDispSubtitleThread = null;

    public static final int SUBTITLE_TYPE_NONE = 0;
    public static final int SUBTITLE_TYPE_TEXT = 1;
    public static final int SUBTITLE_TYPE_GFX = 2;

    private static final int MSG_STOP_SUBTITLE = 0;
    private static final int MSG_DISPLAY_SUBTITLE = 1;
    private static final int MSG_REMOVE_SUBTITLE = 2;
    private static final int MSG_SET_STATUSBAR_EVADE = 3;

    // Range for TextView.setTextSize() (txt Subtitle)
    private static final int TXT_SIZE_MIN = 16;
    private static final int TXT_SIZE_MAX = 64;
    private static final float TXT_SIZE_RANGE = TXT_SIZE_MAX - TXT_SIZE_MIN;

    // matches a single "{\ ... }"
    private static final Pattern SSA_ANY_TAG = Pattern.compile("\\{\\\\.*?\\}");
    // matches "{\c&Hcolor&}text" until end of input or next color tag
    private static final Pattern SSA_COLOR_TAG = Pattern.compile("\\{\\\\c&[h,H]([0-9,A-F,a-f]+)&\\}(.*?)(?=\\{\\\\c|$)");
    // replacement for SSA_COLOR_TAG $1=color and $2=text
    private static final String HTML_COLOR_TAG = "<font color=\"#$1\">$2</font>";

    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_STOP_SUBTITLE:
                    mSubtitleTxtView.setVisibility(View.GONE);
                    mSubtitleGfxView.setVisibility(View.GONE);
                    break;
                case MSG_DISPLAY_SUBTITLE: {
                    if (msg.obj == null)
                        break;
                    displayView((Subtitle) msg.obj);
                    break;
                }
                case MSG_REMOVE_SUBTITLE: {
                    if (msg.obj == null)
                        break;
                    removeView((Subtitle) msg.obj);
                    break;
                }
                case MSG_SET_STATUSBAR_EVADE: {
                }
            }
        }

        private void removeView(Subtitle subtitle) {
            if (DBG) Log.d(TAG, "removeView");
            if (subtitle.isText()) {
                mSubtitleTxtView.setText("");
                mSubtitleTxtView.setVisibility(View.GONE);
                // need to Invalidate View to force an update!
                mSubtitleTxtView.postInvalidate();
            } else if (subtitle.isBitmap()) {
                mSubtitleGfxView.remove();
            }
        }

        private void displayView(Subtitle subtitle) {
            if (DBG) Log.d(TAG, "displayView");

            if (subtitle.isText()) {
                mSubtitleTxtView.setVisibility(View.VISIBLE);

                if (mSpannableStringBuilder == null) {
                    mSpannableStringBuilder = new SpannableStringBuilder();
                    float shadowRadius = mRes.getDimension(R.dimen.subtitles_shadow_radius);
                    float shadowDx = mRes.getDimension(R.dimen.subtitles_shadow_dx);
                    float shadowDy = mRes.getDimension(R.dimen.subtitles_shadow_dy);
                    int shadowColor = mRes.getColor(R.color.subtitles_shadow_color);
                    mTextShadowSpan = new TextShadowSpan(shadowRadius, shadowDx, shadowDy, shadowColor);
                }
                mSpannableStringBuilder.clear();
                mSpannableStringBuilder.append(Html.fromHtml(cleanText(subtitle.getText())));
                if (mSpannableStringBuilder.length() > 0) {
                    // Html.fromHtml override shadow style, so add a shadowSpan for whole text.
                    mSpannableStringBuilder.setSpan(mTextShadowSpan, 0, mSpannableStringBuilder.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                }
                mSubtitleTxtView.setText(mSpannableStringBuilder);
                // need to Invalidate View to force an update!
                mSubtitleTxtView.postInvalidate();
            } else if (subtitle.isBitmap()) {
                Rect bounds = subtitle.getBounds();
                mSubtitleGfxView.setSubtitle(subtitle.getBitmap(), bounds.right, bounds.bottom);
            }
        }
    };
    private int mColor;
    private boolean mOutline;
    private int mUiMode;

    private void removeSubtitle(Subtitle subtitle) {
        mHandler.removeMessages(MSG_DISPLAY_SUBTITLE);
        mHandler.removeMessages(MSG_REMOVE_SUBTITLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REMOVE_SUBTITLE, subtitle));
    }

    private void displaySubtitle(Subtitle subtitle) {
        mHandler.removeMessages(MSG_REMOVE_SUBTITLE);
        mHandler.removeMessages(MSG_DISPLAY_SUBTITLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_DISPLAY_SUBTITLE, subtitle));
    }

    private static String cleanText (final String input) {
        // remove space/new lines at end and beginning
        String displayText = input.trim();
        // convert \n or literal "\n" to <br>
        displayText = displayText.replaceAll("\\n|\\\\n", "<br />");
        // condense whitespace to 1 space
        displayText = displayText.replaceAll("\\s+", " ");

        // check for .SSA subtitle tags {\ ... }
        Matcher ssaTagMatch = SSA_ANY_TAG.matcher(displayText);
        if (ssaTagMatch.find()) {
            // convert color Tag = {\c&H0F0F0F&} ......{\ to html tag
            StringBuffer sb = new StringBuffer(displayText.length());
            displayText = replaceAll(displayText, SSA_COLOR_TAG, HTML_COLOR_TAG, sb);
            displayText = replaceAll(displayText, SSA_ANY_TAG, "", sb);
        }
        if (DBG) Log.d(TAG, String.format("cleaned Text [%s]", displayText));
        return displayText;
    }

    /**
     * Behaves like String.replaceAll() but takes Pattern and a StringBuffer rather than recreating them all the time
     * @param input String that needs replacements
     * @param pattern RegEx to find in input
     * @param replacement String (may contain $1, $2, ...) that replaces the match
     * @param buffer a StringBuffer this method may use
     * @return the resulting String
     */
    private static String replaceAll(String input, Pattern pattern, String replacement, StringBuffer buffer) {
        buffer.setLength(0);
        Matcher match = pattern.matcher(input);
        while (match.find()) {
            match.appendReplacement(buffer, replacement);
        }
        match.appendTail(buffer);
        return buffer.toString();
    }

    public int getColor() {
        return mColor;
    }

    public boolean getOutlineState() { return mOutline; }

    public void setOutlineState(boolean outline) {
        mOutline = outline;
        if (mSubtitleTxtView != null) {
            mSubtitleTxtView.setOutlineState(outline);
        }
    }

    public void setUIMode(int uiMode) {
        mUiMode = uiMode;
        if(mSubtitleTxtView!=null)
            mSubtitleTxtView.setUIMode(uiMode);
    }

    final class DispSubtitleThread extends Thread {
        private boolean mSuspended = true;
        private boolean mRunning = true;
        private Subtitle mCurrentSubtitle = null;
        private Subtitle mNextSubtitle = null;

        void quit() {
            if (DBG) Log.d(TAG, "DispSubtitleThread quit");
            mRunning = false;
            mDispSubtitleThread = null;
            interrupt();
            try {
                join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            if (DBG) Log.d(TAG, "DispSubtitleThread started");
            long displayTimeLeft = 0;
            while (mRunning) {
                synchronized (this) {
                    // wait() until we get a new Subtitle via addSubtitle() / player continues
                    while (mSuspended) {
                        if (DBG) Log.d(TAG, "DispSubtitleThread wait()");
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            if (!mRunning) {
                                clear();
                                return;
                            }
                            if (DBG) Log.d(TAG, "DispSubtitleThread wait() - interrupted");
                        }
                    }
                }
                synchronized (this) {
                    // we don't have a subtitle, go back to wait()
                    if (mCurrentSubtitle == null && mNextSubtitle == null) {
                        if (DBG) Log.d(TAG, "DispSubtitleThread no Subtitle");
                        mSuspended = true;
                        continue;
                    }

                    // we have a subtitle that is not displayed yet
                    if (mCurrentSubtitle == null) {
                        if (DBG) Log.d(TAG, "DispSubtitleThread displaying new Subtitle");
                        mCurrentSubtitle = mNextSubtitle;
                        mNextSubtitle = null;
                        displaySubtitle(mCurrentSubtitle);
                        displayTimeLeft = mCurrentSubtitle.getDuration();
                    }
                }
                // outside of synchronized since sleep does NOT release the lock
                // go to sleep if we have still have displaytime
                if (displayTimeLeft > 0) {
                    if (DBG) Log.d(TAG, "DispSubtitleThread sleep after displaying");
                    long sleepStart = System.currentTimeMillis();
                    try {
                        sleep(displayTimeLeft);
                    } catch (InterruptedException e) {
                        if (DBG) Log.d(TAG, "DispSubtitleThread sleep - interrupted");
                    }
                    displayTimeLeft-= System.currentTimeMillis()-sleepStart;
                }
                // if we sleeped without interrupt or no displaytime is left remove the subtitle
                if (displayTimeLeft <= 0) {
                    if (DBG) Log.d(TAG, "DispSubtitleThread removing subtitle");
                    synchronized (this) {
                        if (mCurrentSubtitle != null) {
                            removeSubtitle(mCurrentSubtitle);
                            mCurrentSubtitle = null;
                        }
                    }
                }
            }
            clear();
            if (DBG) Log.d(TAG, "DispSubtitleThread exited");
        }

        synchronized void addSubtitle(Subtitle subtitle) {
            if (DBG) Log.d(TAG, "DispSubtitleThread addSubtitle");
            mSuspended = false;

            if (subtitle.isTimed()) {
                mNextSubtitle = subtitle;
                if (!isAlive()) {
                    super.start();
                } else {
                    interrupt();
                }
            } else {
                if (mCurrentSubtitle != null) {
                    removeSubtitle(mCurrentSubtitle);
                    mCurrentSubtitle = null;
                }

                if (subtitle.getText() != null) {
                    mCurrentSubtitle = subtitle;
                    displaySubtitle(mCurrentSubtitle);
                }
            }
        }

        synchronized void show() {
            if (DBG) Log.d(TAG, "DispSubtitleThread show");
            // could setVisibility here
        }

        synchronized void clear() {
            if (DBG) Log.d(TAG, "DispSubtitleThread clear");
            mSuspended = true;
            if (mCurrentSubtitle != null) {
                removeSubtitle(mCurrentSubtitle);
                mCurrentSubtitle = null;
                mNextSubtitle = null;
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_SUBTITLE));
        }

        synchronized void setSuspended(boolean suspended) {
            if (DBG) Log.d(TAG, "DispSubtitleThread setSuspended");
            if (mSuspended == suspended)
                return;
            mSuspended = suspended;
            interrupt();
        }
    }

    public SubtitleManager(Context context, ViewGroup playerView, WindowManager window, boolean forbidWindow) {
        mContext = context;
        mPlayerView = playerView;
        mWindow = window;
        mRes = context.getResources();
        mForbidWindow = forbidWindow;
        mSubtitlePosHintDrawable = mRes.getDrawable(com.archos.mediacenter.video.R.drawable.subtitle_baseline);
    }

    public void setScreenSize(int displayWidth, int displayHeight) {
        mScreenWidth = displayWidth;
        mScreenHeight = displayHeight;
        if (mSubtitleLayout != null) {
            if (mLayoutParams != null) {
                mLayoutParams.width = mScreenWidth;
                mLayoutParams.height = mScreenHeight;
                mWindow.updateViewLayout(mSubtitleLayout, mLayoutParams);
            } else {
                ViewGroup.LayoutParams lp = mSubtitleLayout.getLayoutParams();
                lp.width = mScreenWidth;
                lp.height = mScreenHeight;
                mPlayerView.updateViewLayout(mSubtitleLayout, lp);
            }
        }
        if(mSubtitleTxtView!=null)
            mSubtitleTxtView.setScreenSize(displayWidth, displayHeight);
        setSize(mSubtitleSize);
    }
    
    public void setUIExternalSurface(Surface uiSurface) {
        mUiSurface = uiSurface;
        if (mSubtitleGfxView != null)
            mSubtitleGfxView.setRenderingSurface(uiSurface);
        if (mSubtitleTxtView != null)
            mSubtitleTxtView.setRenderingSurface(uiSurface);
        if (mSubtitleSpacer != null)
            mSubtitleSpacer.setRenderingSurface(uiSurface);
    }

    private void attachWindow() {
        if (mSubtitleLayout != null)
            return;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mSubtitleLayout = inflater.inflate(R.layout.subtitle_layout, null);
        mSubtitleSpacer = (SubtitleSpacerView) mSubtitleLayout.findViewById(R.id.subtitle_spacer);
        mSubtitleGfxView = (SubtitleGfxView) mSubtitleLayout.findViewById(R.id.subtitle_gfx_view);
        mSubtitleTxtView = (Subtitle3DTextView) mSubtitleLayout.findViewById(R.id.subtitle_txt_view);
        mSubtitleTxtView.setScreenSize(mScreenWidth, mScreenHeight);
        mSubtitleTxtView.setUIMode(mUiMode);
        mSubtitleSpacerParams = mSubtitleSpacer.getLayoutParams();
        mSubtitleSpacerParams.height = mSubtitleEvadedVPos;
        
        setUIExternalSurface(mUiSurface);

        if (PlayerConfig.useOneWindowPerView()&&!mForbidWindow) {
            mLayoutParams = new WindowManager.LayoutParams();

            mLayoutParams.gravity = Gravity.TOP;
            mLayoutParams.width = mScreenWidth;
            mLayoutParams.height = mScreenHeight;
            mLayoutParams.x = 0;
            mLayoutParams.y = 0;
            mLayoutParams.format = PixelFormat.TRANSLUCENT;
            mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
            mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_FULLSCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            mLayoutParams.token = null;
            mLayoutParams.windowAnimations = 0;

            mWindow.addView(mSubtitleLayout, mLayoutParams);
        } else {
            mPlayerView.addView(mSubtitleLayout, mScreenWidth, mScreenHeight);
        }
    }

    private void detachWindow() {
        if (mSubtitleLayout == null)
            return;
        if (DBG) Log.d(TAG, "detachWindow");
        if (PlayerConfig.useOneWindowPerView()) {
            try {
                mWindow.removeView(mSubtitleLayout);
                mLayoutParams = null;
            } catch (IllegalArgumentException ignored) {
            }
        } else {
            mPlayerView.removeView(mSubtitleLayout);
        }
        mSubtitleLayout = null;
    }

    public void start() {
        if (DBG) Log.d(TAG, "start");

        attachWindow();

        if (mDispSubtitleThread == null) {
            mDispSubtitleThread = new DispSubtitleThread();
            try {
                mDispSubtitleThread.start();
            } catch (IllegalThreadStateException e) {
                // thread has been started before
            }
        }

        show();
    }

    public void stop() {
        if (DBG) Log.d(TAG, "stop");

        if (mDispSubtitleThread != null) {
            mDispSubtitleThread.quit();
        }
        detachWindow();
    }

    public void show() {
        if (mDispSubtitleThread != null) {
            mDispSubtitleThread.show();
        }
    }

    public void clear() {
        if (mDispSubtitleThread != null) {
            mDispSubtitleThread.clear();
        }
    }

    public int getSize() {
        return mSubtitleSize;
    }

    public int getVerticalPosition() {
        return mSubtitleVPos;
    }
    
    /**
     * Translates size to a usable size for TextView.SetTextSize()
     * 
     * @param size 0..100 so we can use default slidebar values
     * @return float between TXT_SIZE_MIN and TXT_SIZE_MAX
     */
    public static float calcTextSize(int size) {
        int tmp = size;
        if (tmp > 100)
            tmp = 100;
        if (tmp < 0)
            tmp = 0;
        return (tmp / 100f) * TXT_SIZE_RANGE + TXT_SIZE_MIN;
    }

    /**
     * @param size expects Number 0..100
     */
    public void setSize(int size) {
        mSubtitleSize = size;
        if (mSubtitleGfxView != null) {
            mSubtitleGfxView.setSize(size, mScreenWidth, mScreenHeight);
        }
        if (mSubtitleTxtView != null) {
            mSubtitleTxtView.setTextSize(calcTextSize(size));
        }
    }

    public void setColor(int color){
        mColor = color;
        if (mSubtitleTxtView != null) {
            mSubtitleTxtView.setTextColor(color);
        }
    }

    /**
     * Animates the Alpha
     * @param fadeIn true to fade in, false to fade out
     */
    public void fadeSubtitlePositionHint (boolean fadeIn) {
        if (mSubtitleSpacer == null)
            return;
        if (fadeIn) {
            mSubtitleSpacer.animate().alpha(1).setDuration(100);
        } else {
            mSubtitleSpacer.animate().alpha(0).setDuration(500);
        }
    }

    /**
     * after you enable this you need to call fadeSubtitlePositionHint(true)
     * otherwise the Alpha of the Drawable stays at 0
     * @param show 
     */
    public void setShowSubtitlePositionHint (boolean show) {
        if (mSubtitleSpacer == null)
            return;
        mSubtitleSpacer.setAlpha(0);
        if (show) {
            mSubtitleSpacer.setBackgroundDrawable(mSubtitlePosHintDrawable);
        } else {
            mSubtitleSpacer.setBackgroundDrawable(null);
        }
    }

    /**
     * Whether Subtitle minimum Vertical Position should be above the StatusBar
     * or not. Only changes the Vertical Position if it is too low.
     *
     * @param evadeStatusBar <ul>
     *            <li><b>true</b> to place subtitles above the System StatusBar
     *            <li><b>false</b> to place Subtitles without considering the
     *            StatusBar
     *            </ul>
     */
    private void setBottomBarHeightInternal(int height) {
        if (height > 0) {
            int minPos = height;
            int newPos = Math.max(minPos, mSubtitleVPosPixel);
            setVerticalPositionInternal(newPos);
        } else {
            setVerticalPositionInternal(mSubtitleVPosPixel);
        }
    }

    private Runnable mSetStatusBarEvadeRunnable = new Runnable() {
        public void run() {
            setBottomBarHeightInternal(0);
        }
    };

    public void setBottomBarHeight(int height) {
        mHandler.removeCallbacks(mSetStatusBarEvadeRunnable);
        if (height > 0) {
            setBottomBarHeightInternal(height);
        } else {
            /*
             * wait a little: avoid a glitch
             * (subtitles being displayed under the system bar for 100ms)
             */
            mHandler.postDelayed(mSetStatusBarEvadeRunnable, 250);
        }
    }

    /**
     * Sets Subtitle Vertical Position by sizing an invisible view<br>
     * Space below Subtitle is max 1/3 of mScreenHeight.
     * @param pos 0..255.
     */
    public void setVerticalPosition(int pos) {
        mSubtitleVPos = pos;
        // note: Increased the Range from 0.100 to 0.255 to make it smoother
        // translate VPos 0..255 to 0..(1/3)DisplayHeight
        // mScreenHeight / 3 * pos / 255
        mSubtitleVPosPixel = (mScreenHeight * pos / 765) + 1;
        setVerticalPositionInternal(mSubtitleVPosPixel);
    }

    private void setVerticalPositionInternal (int pos) {
        mSubtitleEvadedVPos = pos;
        if (mSubtitleSpacer == null)
            return;
        mSubtitleSpacerParams.height = pos;
        if (DBG) Log.d(TAG, "New Height: " + mSubtitleSpacerParams.height);
        mSubtitleSpacer.setLayoutParams(mSubtitleSpacerParams);
        mSubtitleSpacer.requestLayout();
        mSubtitleSpacer.postInvalidate();
    }

    public void addSubtitle(Subtitle subtitle) {
        if (mDispSubtitleThread != null)
            mDispSubtitleThread.addSubtitle(subtitle);
    }

    public void onPlay() {
        if (mDispSubtitleThread != null)
            mDispSubtitleThread.setSuspended(false);
    }

    public void onPause() {
        if (mDispSubtitleThread != null)
            mDispSubtitleThread.setSuspended(true);
    }

    public void onSeekStart(int pos) {
        if (mDispSubtitleThread != null) {
            mDispSubtitleThread.clear();
            mDispSubtitleThread.interrupt();
        }
    }
}
