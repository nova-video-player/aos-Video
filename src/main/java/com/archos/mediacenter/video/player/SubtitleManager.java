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
import com.archos.medialib.Subtitle.SubtitleAlignment;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;

import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubtitleManager {

    private static final Logger log = LoggerFactory.getLogger(SubtitleManager.class);

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
    private boolean mIsSubtitleGfx = false;

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

    // some ssa syntax https://sweetkaraoke.pagesperso-orange.fr/Tutoriels/Tutoriel4_1b.html#Chapitre_1_:_les_styles_ASS_et_SSA_:
    // matches a single "{\ ... }"
    private static final Pattern SSA_ANY_TAG = Pattern.compile("(?:\\\\)?\\{(?:\\{\\})?\\\\.*?\\}"); // capture any ssa tag {\tag} but also this format \{{}\an8}
    // matches "{\c&Hcolor&}text" until end of input or next color tag, matches also "\<1/2/3/4>c&H<hex code>&" and "*c&H<hex code>&"
    private static final Pattern SSA_COLOR_TAG = Pattern.compile("\\{\\\\?[1-4\\*]?\\\\c&[h,H]([0-9A-Fa-f]+)&\\}(.*?)(?=\\{\\\\c|$)");
    // replacement for SSA_COLOR_TAG $1=color and $2=text
    private static final String HTML_COLOR_TAG = "<font color=\"#$1\">$2</font>";
    // bold, italic, underline, slanted ssa tags
    private static final Pattern SSA_BOLD_TAG = Pattern.compile("\\{\\\\b1\\}(.*?)(?=\\{\\\\b0|$)");
    private static final String HTML_BOLD_TAG = "<b>$1</b>";
    private static final Pattern SSA_ITALIC_TAG = Pattern.compile("\\{\\\\i1\\}(.*?)(?=\\{\\\\i0|$)");
    private static final String HTML_ITALIC_TAG = "<i>$1</i>";
    private static final Pattern SSA_UNDERLINE_TAG = Pattern.compile("\\{\\\\u1\\}(.*?)(?=\\{\\\\u0|$)");
    private static final String HTML_UNDERLINE_TAG = "<u>$1</u>";
    private static final Pattern SSA_STRIKETHROUGH_TAG = Pattern.compile("\\{\\\\s1\\}(.*?)(?=\\{\\\\s0|$)");
    private static final String HTML_STRIKETHROUGH_TAG = "<s>$1</s>";

    // alignment tag can contain a Word Joiner (WJ) \u2060 unicode character and be of the form \{{}\\u2060an8} or simply {\an8} or \{\\an8\}
    // 1 is BOTTOM_LEFT, 2 is BOTTOM_MID, 3 is BOTTOM_RIGHT, 4 is MID_LEFT, 5 is MID_MID, 6 is MID_RIGHT, 7 is TOP_LEFT, 8 is TOP_MID, 9 is TOP_RIGHT
    private static final Pattern SUBRIP_ALIGNMENT_TAG = Pattern.compile("\\\\?\\{(?:\\{\\})?\\\\?\\\\(?:\\u2060)?an([1-9])\\\\?\\}");

    private static class SubtitleHandler extends Handler {
        private final WeakReference<SubtitleManager> mSubtitleManager;

        SubtitleHandler(SubtitleManager subtitleManager) {
            super(Looper.getMainLooper());
            mSubtitleManager = new WeakReference<>(subtitleManager);
        }

        @Override
        public void handleMessage(Message msg) {
            SubtitleManager subtitleManager = mSubtitleManager.get();
            if (subtitleManager != null) {
                subtitleManager.handleMessage(msg);
            }
        }
    }

    private final Handler mHandler = new SubtitleHandler(this);

    private void handleMessage(Message msg) {
        log.debug("handleMessage: {}", msg.what);
        switch (msg.what) {
            case MSG_STOP_SUBTITLE:
                log.debug("handleMessage: MSG_STOP_SUBTITLE");
                mSubtitleTxtView.setVisibility(View.GONE);
                mSubtitleGfxView.setVisibility(View.GONE);
                break;
            case MSG_DISPLAY_SUBTITLE: {
                log.debug("handleMessage: MSG_DISPLAY_SUBTITLE");
                if (msg.obj == null)
                    break;
                displayView((Subtitle) msg.obj);
                break;
            }
            case MSG_REMOVE_SUBTITLE: {
                log.debug("handleMessage: MSG_REMOVE_SUBTITLE");
                if (msg.obj == null)
                    break;
                removeView((Subtitle) msg.obj);
                break;
            }
            case MSG_SET_STATUSBAR_EVADE: {
                // Handle status bar evade
                log.debug("handleMessage: MSG_SET_STATUSBAR_EVADE");
            }
        }
    }

    private void removeView(Subtitle subtitle) {
        log.debug("removeView");
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
        log.debug("displayView sub duration={}", subtitle.getDuration());

        if (subtitle.isText()) {
            mIsSubtitleGfx = false;

            subtitle.setAlignment(getAlignment(subtitle.getText()));

            log.debug("displayView: Text, mIsSubtitleGfx=false, alignment={} for text={}", subtitle.getAlignment(), subtitle.getText());

            mSubtitleTxtView.setVisibility(View.VISIBLE);

            // Adjust the position based on the alignment
            adjustSubtitlePosition(subtitle.getAlignment());

            if (mSpannableStringBuilder == null) {
                mSpannableStringBuilder = new SpannableStringBuilder();
                float shadowRadius = mRes.getDimension(R.dimen.subtitles_shadow_radius);
                float shadowDx = mRes.getDimension(R.dimen.subtitles_shadow_dx);
                float shadowDy = mRes.getDimension(R.dimen.subtitles_shadow_dy);
                int shadowColor = ContextCompat.getColor(mContext, R.color.subtitles_shadow_color);
                mTextShadowSpan = new TextShadowSpan(shadowRadius, shadowDx, shadowDy, shadowColor);
            }
            mSpannableStringBuilder.clear();
            mSpannableStringBuilder.append(HtmlCompat.fromHtml(cleanText(subtitle.getText()), HtmlCompat.FROM_HTML_MODE_LEGACY));
            if (mSpannableStringBuilder.length() > 0) {
                // HtmlCompat.fromHtml override shadow style, so add a shadowSpan for whole text.
                mSpannableStringBuilder.setSpan(mTextShadowSpan, 0, mSpannableStringBuilder.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }
            mSubtitleTxtView.setText(mSpannableStringBuilder);
            log.debug("displayView: text={}", mSpannableStringBuilder.toString());
            // need to Invalidate View to force an update!
            mSubtitleTxtView.postInvalidate();
        } else if (subtitle.isBitmap()) {
            mIsSubtitleGfx = true;
            log.debug("displayView: Bitmap bounds={}, mIsSubtitleGfx=true", subtitle.getBounds());
            Rect bounds = subtitle.getBounds();
            mSubtitleGfxView.setSubtitle(subtitle.getBitmap(), bounds, subtitle.getFrameWidth(), subtitle.getFrameHeight());
        }
    }

    private void adjustSubtitlePosition(SubtitleAlignment alignment) {
        // Set the gravity based on the alignment
        int gravity = switch (alignment) {
            case BOTTOM_LEFT -> Gravity.BOTTOM | Gravity.START;
            case BOTTOM_MID -> Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            case BOTTOM_RIGHT -> Gravity.BOTTOM | Gravity.END;
            case MID_LEFT -> Gravity.CENTER_VERTICAL | Gravity.START;
            case MID_MID -> Gravity.CENTER;
            case MID_RIGHT -> Gravity.CENTER_VERTICAL | Gravity.END;
            case TOP_LEFT -> Gravity.TOP | Gravity.START;
            case TOP_MID -> Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            case TOP_RIGHT -> Gravity.TOP | Gravity.END;
        }; // Default to bottom center

        // Set the gravity to the Subtitle3DTextView
        mSubtitleTxtView.setGravity3D(gravity);
    }

    private int mColor;
    private boolean mOutline;
    private int mUiMode;

    private void removeSubtitle(Subtitle subtitle) {
        log.debug("removeSubtitle");
        mHandler.removeMessages(MSG_DISPLAY_SUBTITLE);
        mHandler.removeMessages(MSG_REMOVE_SUBTITLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REMOVE_SUBTITLE, subtitle));
    }

    private void displaySubtitle(Subtitle subtitle) {
        log.debug("displaySubtitle");
        mHandler.removeMessages(MSG_REMOVE_SUBTITLE);
        mHandler.removeMessages(MSG_DISPLAY_SUBTITLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_DISPLAY_SUBTITLE, subtitle));
    }

    private static SubtitleAlignment getAlignment(final String input) {
        SubtitleAlignment alignment = SubtitleAlignment.BOTTOM_MID;
        Matcher subripAlignmentMatch = SUBRIP_ALIGNMENT_TAG.matcher(input);
        if (subripAlignmentMatch.find()) {
            int alignmentInt = Integer.parseInt(subripAlignmentMatch.group(1));
            log.debug("getAlignment: input={} -> alignmentInt={}", input, alignmentInt);
            alignment = switch (alignmentInt) {
                case 1 -> SubtitleAlignment.BOTTOM_LEFT;
                case 2 -> SubtitleAlignment.BOTTOM_MID;
                case 3 -> SubtitleAlignment.BOTTOM_RIGHT;
                case 4 -> SubtitleAlignment.MID_LEFT;
                case 5 -> SubtitleAlignment.MID_MID;
                case 6 -> SubtitleAlignment.MID_RIGHT;
                case 7 -> SubtitleAlignment.TOP_LEFT;
                case 8 -> SubtitleAlignment.TOP_MID;
                case 9 -> SubtitleAlignment.TOP_RIGHT;
                default -> alignment;
            };
        }
        return alignment;
    }

    private static String cleanText(final String input) {
        // remove space/new lines at end and beginning
        String displayText = input.trim();
        // convert \n or literal "\n" to <br>
        displayText = displayText.replaceAll("(?i)\\n|\\\\n", "<br />");
        // condense whitespace to 1 space
        displayText = displayText.replaceAll("\\s+", " ");

        // check for .SSA subtitle tags {\ ... }
        Matcher ssaTagMatch = SSA_ANY_TAG.matcher(displayText);
        if (ssaTagMatch.find()) {
            // convert color Tag = {\c&H0F0F0F&} ......{\ to html tag
            StringBuffer sb = new StringBuffer(displayText.length());
            displayText = replaceAll(displayText, SSA_COLOR_TAG, HTML_COLOR_TAG, sb);
            displayText = replaceAll(displayText, SSA_ITALIC_TAG, HTML_ITALIC_TAG, sb);
            displayText = replaceAll(displayText, SSA_BOLD_TAG, HTML_BOLD_TAG, sb);
            displayText = replaceAll(displayText, SSA_UNDERLINE_TAG, HTML_UNDERLINE_TAG, sb);
            displayText = replaceAll(displayText, SSA_STRIKETHROUGH_TAG, HTML_STRIKETHROUGH_TAG, sb);
            displayText = replaceAll(displayText, SSA_ANY_TAG, "", sb);
        }
        log.debug("cleanText: [{}] -> [{}]", input, displayText);
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
        private boolean interrupted = false;

        void quit() {
            log.debug("DispSubtitleThread quit");
            mRunning = false;
            mDispSubtitleThread = null;
            interrupt();
            try {
                join();
            } catch (InterruptedException e) {
                log.error("DispSubtitleThread quit - interrupted", e);
            }
        }

        @Override
        public void run() {
            log.debug("DispSubtitleThread started: set mSubtitleDisplayLeft=0");
            int mSubtitleDisplayLeft = 0;
            while (mRunning) {
                interrupted = false;
                synchronized (this) {
                    // wait() until we get a new Subtitle via addSubtitle() / player continues
                    while (mSuspended) {
                        log.debug("DispSubtitleThread wait()");
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            if (!mRunning) {
                                log.debug("DispSubtitleThread wait() - interrupted and not running, clear subtitle at {}!", System.currentTimeMillis());
                                clear();
                                return;
                            }
                            log.debug("DispSubtitleThread wait() - interrupted");
                        }
                    }
                }
                synchronized (this) {
                    // we don't have a subtitle, go back to wait()
                    if ((mCurrentSubtitle == null && mNextSubtitle == null) || (mCurrentSubtitle == null && mNextSubtitle != null && mNextSubtitle.getDuration() == 0)) {
                        log.debug("DispSubtitleThread no valid Subtitle, mNextSubtitle={}+{}ms",
                                mNextSubtitle != null ? mNextSubtitle.getPosition() : "null",
                                mNextSubtitle != null ? mNextSubtitle.getDuration() : "null");
                        if (mNextSubtitle != null) mNextSubtitle = null; // if mCurrentSubtitle is null, receiving zero subtitle has no effect
                        mSuspended = true;
                        continue;
                    }

                    // we have a subtitle that is not displayed yet
                    if (mCurrentSubtitle == null) { // new subtitle only considered if current one is not null
                        mCurrentSubtitle = mNextSubtitle; // the next subtitle has a duration > 0 other wise it would have been filtered out before
                        mNextSubtitle = null;
                        displaySubtitle(mCurrentSubtitle);
                        mSubtitleDisplayLeft = mCurrentSubtitle.getDuration();
                        log.debug("DispSubtitleThread displaying new (current=new) subtitle={}+{}ms, bounds={}, mSubtitleDisplayLeft={}", mCurrentSubtitle.getPosition(), mCurrentSubtitle.getDuration(), mCurrentSubtitle.getBounds(), mSubtitleDisplayLeft);
                    }
                }

                // outside of synchronized since sleep does NOT release the lock
                // go to sleep if we have still have mSubtitleDisplayLeft
                if (mSubtitleDisplayLeft > 0) { // we have a subtitle to display and at this point mCurrentSubtitle != null
                    log.debug("DispSubtitleThread after displaying mCurrentSubtitle={}+{}ms, sleep for {}", mCurrentSubtitle.getPosition(), mCurrentSubtitle.getDuration(), mSubtitleDisplayLeft);
                    long sleepStart = System.currentTimeMillis();
                    try {
                        sleep(mSubtitleDisplayLeft);
                    } catch (InterruptedException e) { // wake up from sleep thus mCurrentSubtitle exists
                        interrupted = true;
                        long elapsedTime = System.currentTimeMillis() - sleepStart;
                        log.debug("DispSubtitleThread sleep interrupt, waking up after {}ms, mCurrentSubtitle={}+{}ms, mNextSubtitle={}+{}ms, old mSubtitleDisplayLeft={}",
                                elapsedTime,
                                mCurrentSubtitle != null ? mCurrentSubtitle.getPosition() : "null",
                                mCurrentSubtitle != null ? mCurrentSubtitle.getDuration() : "null",
                                mNextSubtitle != null ? mNextSubtitle.getPosition() : "null",
                                mNextSubtitle != null ? mNextSubtitle.getDuration() : "null",
                                mSubtitleDisplayLeft);
                        if (mCurrentSubtitle != null && mNextSubtitle != null) {
                            // woke up from sleep by interrupt because getting new subtitle
                            int currentPosition = mCurrentSubtitle.getPosition() + (int) elapsedTime;
                            int realCurrentSubtitleDuration;
                            // need to correct time left only if the next subtitle starts before the current one ends
                            if (mCurrentSubtitle.getPosition()+mCurrentSubtitle.getDuration() > mNextSubtitle.getPosition()) {
                                log.debug("DispSubtitleThread: cannot sleep after mNextSubtitle, adjust");
                                realCurrentSubtitleDuration = mNextSubtitle.getPosition() - mCurrentSubtitle.getPosition();
                                mCurrentSubtitle.setDuration(realCurrentSubtitleDuration);
                                mSubtitleDisplayLeft = mNextSubtitle.getPosition() - currentPosition;
                            } else {
                                realCurrentSubtitleDuration = mCurrentSubtitle.getDuration();
                                mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                            }
                            log.debug("DispSubtitleThread sleep interrupt bcoz received new subtitle, recompute duration currentPosition={}, realCurrentSubtitleDuration={}, updated mSubtitleDisplayLeft={}", currentPosition, realCurrentSubtitleDuration, mSubtitleDisplayLeft);
                            if (mNextSubtitle.getDuration() == 0) { // this is an empty subtitle that is used to provide the correct duration
                                log.debug("DispSubtitleThread sleep interrupt bcoz received empty Subtitle, dismiss mNextSubtitle");
                                mNextSubtitle = null; // remove the empty subtitle
                            }
                        } else {
                            mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                            log.debug("DispSubtitleThread sleep interrupt by seek/exit condition, updated mSubtitleDisplayLeft={}", mSubtitleDisplayLeft);
                        }
                    }
                    // if not interrupted update mSubtitleDisplayLeft (otherwise it is already done)
                    if (! interrupted) mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                    log.debug("DispSubtitleThread now mSubtitleDisplayLeft={}", mSubtitleDisplayLeft);
                }
                // if we slept without interrupt or no display time is left remove the subtitle
                if (mSubtitleDisplayLeft <= 0) {
                    log.debug("DispSubtitleThread removing subtitle because mSubtitleDisplayLeft={}<0", mSubtitleDisplayLeft);
                    synchronized (this) {
                        if (mCurrentSubtitle != null) {
                            removeSubtitle(mCurrentSubtitle);
                            mCurrentSubtitle = null;
                            mSubtitleDisplayLeft = 0;
                        }
                    }
                }
            }
            clear();
            log.debug("DispSubtitleThread exited");
        }

        synchronized void addSubtitle(Subtitle subtitle) {
            log.debug("DispSubtitleThread addSubtitle isBitmap={} isText={} isTimed={} position={} duration={}", subtitle.isBitmap(), subtitle.isText(), subtitle.isTimed(), subtitle.getPosition(), subtitle.getDuration());
            mSuspended = false;

            if (subtitle.isTimed()) {
                mNextSubtitle = subtitle;
                if (!isAlive()) {
                    log.debug("DispSubtitleThread addSubtitle thread is not alive -> start");
                    super.start();
                } else {
                    log.debug("DispSubtitleThread addSubtitle thread is alive -> interrupt");
                    interrupt();
                }
            } else {
                log.debug("DispSubtitleThread addSubtitle not timed!");
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
            log.debug("DispSubtitleThread show");
            // could setVisibility here
        }

        synchronized void clear() {
            log.debug("DispSubtitleThread clear");
            mSuspended = true;
            if (mCurrentSubtitle != null) {
                removeSubtitle(mCurrentSubtitle);
                mCurrentSubtitle = null;
                mNextSubtitle = null;
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_SUBTITLE));
        }

        synchronized void setSuspended(boolean suspended) {
            log.debug("DispSubtitleThread setSuspended");
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
        mSubtitlePosHintDrawable = ContextCompat.getDrawable(context, com.archos.mediacenter.video.R.drawable.subtitle_baseline);
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
        log.debug("setUIExternalSurface {}", uiSurface);
        mUiSurface = uiSurface;
        if (mSubtitleGfxView != null) {
            mSubtitleGfxView.setRenderingSurface(uiSurface);
            log.debug("setUIExternalSurface setRenderingSurface for mSubtitleGfxView");
        }
        if (mSubtitleTxtView != null)
            mSubtitleTxtView.setRenderingSurface(uiSurface);
        if (mSubtitleSpacer != null)
            mSubtitleSpacer.setRenderingSurface(uiSurface);
    }

    private void attachWindow() {
        if (mSubtitleLayout != null)
            return;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSubtitleLayout = inflater.inflate(R.layout.subtitle_layout, mPlayerView, false);
        mSubtitleSpacer = (SubtitleSpacerView) mSubtitleLayout.findViewById(R.id.subtitle_spacer);
        mSubtitleGfxView = (SubtitleGfxView) mSubtitleLayout.findViewById(R.id.subtitle_gfx_view);
        mSubtitleTxtView = (Subtitle3DTextView) mSubtitleLayout.findViewById(R.id.subtitle_txt_view);
        mSubtitleTxtView.setScreenSize(mScreenWidth, mScreenHeight);
        mSubtitleTxtView.setUIMode(mUiMode);
        mSubtitleSpacerParams = mSubtitleSpacer.getLayoutParams();
        mSubtitleSpacerParams.height = mSubtitleEvadedVPos;
        setUIExternalSurface(mUiSurface);
        mPlayerView.addView(mSubtitleLayout, mScreenWidth, mScreenHeight);
    }

    private void detachWindow() {
        if (mSubtitleLayout == null)
            return;
        log.debug("detachWindow");
        mPlayerView.removeView(mSubtitleLayout);
        mSubtitleLayout = null;
    }

    public void start() {
        log.debug("start");

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
        log.debug("stop");

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
        log.debug("setSize: {}", size);
        mSubtitleSize = size;
        if (mSubtitleGfxView != null) {
            mSubtitleGfxView.setSize(size, mScreenWidth, mScreenHeight);
        }
        if (mSubtitleTxtView != null) {
            mSubtitleTxtView.setTextSize(calcTextSize(size));
        }
    }

    public void setColor(int color){
        log.debug("setColor: {}", color);
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
        log.debug("fadeSubtitlePositionHint: {}", fadeIn);
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
        log.debug("setShowSubtitlePositionHint: {}", show);
        if (mSubtitleSpacer == null)
            return;
        mSubtitleSpacer.setAlpha(0);
        if (show) {
            mSubtitleSpacer.setBackground(mSubtitlePosHintDrawable);
        } else {
            mSubtitleSpacer.setBackground(null);
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
        log.debug("setBottomBarHeightInternal: {}", height);
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
        if (mIsSubtitleGfx && SubtitleGfxView.RECT_COORDINATES)
            mSubtitleVPos = 0;
        else
            mSubtitleVPos = pos;
        // note: Increased the Range from 0.100 to 0.255 to make it smoother
        // translate VPos 0..255 to 0..(1/3)DisplayHeight
        // mScreenHeight / 3 * pos / 255
        mSubtitleVPosPixel = (mScreenHeight * pos / 765) + 1;
        setVerticalPositionInternal(mSubtitleVPosPixel);
    }

    private void setVerticalPositionInternal (int pos) {
        if (mIsSubtitleGfx && SubtitleGfxView.RECT_COORDINATES) mSubtitleEvadedVPos = 0;
        else mSubtitleEvadedVPos = pos;
        if (mSubtitleSpacer == null) return;
        mSubtitleSpacerParams.height = mSubtitleEvadedVPos;
        log.debug("setVerticalPositionInternal: new Height " + mSubtitleSpacerParams.height);
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

    public boolean isSubtitleGfx() {
        // this method is unreliable since it needs to have a sub displayed to know if it is gfx or not
        return mIsSubtitleGfx;
    }
}
