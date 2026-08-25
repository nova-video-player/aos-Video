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
import com.archos.mediacenter.video.utils.MiscUtils;
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
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;

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
    private View                mRootView;
    private WindowManager       mWindow;
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
    private boolean isFirstTime = true;
    private Subtitle currentSubtitle = null;

    private boolean mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, mIsNavBarOnBottom, mIsGestureAreaShowing;
    private int mGestureAreaHeight, mControlBarHeight;

    Surface                     mUiSurface;
    private boolean mForbidWindow ;
    DispSubtitleThread mDispSubtitleThread = null;
    private static int mRoundCornerRadius = 0;
    private static boolean mFullScreenWithCutout = true;

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

    // WebVTT voice tag <v Voice Name>
    private static final Pattern VTT_VOICE_TAG_OPEN = Pattern.compile("<v\\s+([^>]+)>");
    private static final String HTML_VTT_VOICE_TAG_OPEN = "<b>$1:</b> ";
    private static final Pattern VTT_VOICE_TAG_CLOSE = Pattern.compile("</v>");

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
        if (log.isDebugEnabled()) log.debug("handleMessage: {}", msg.what);
        switch (msg.what) {
            case MSG_STOP_SUBTITLE:
                if (log.isDebugEnabled()) log.debug("handleMessage: MSG_STOP_SUBTITLE");
                mSubtitleTxtView.setVisibility(View.GONE);
                mSubtitleGfxView.setVisibility(View.GONE);
                break;
            case MSG_DISPLAY_SUBTITLE: {
                if (log.isDebugEnabled()) log.debug("handleMessage: MSG_DISPLAY_SUBTITLE");
                if (msg.obj == null)
                    break;
                displayView((Subtitle) msg.obj);
                break;
            }
            case MSG_REMOVE_SUBTITLE: {
                if (log.isDebugEnabled()) log.debug("handleMessage: MSG_REMOVE_SUBTITLE");
                if (msg.obj == null)
                    break;
                removeView((Subtitle) msg.obj);
                break;
            }
            case MSG_SET_STATUSBAR_EVADE: {
                // Handle status bar evade
                if (log.isDebugEnabled()) log.debug("handleMessage: MSG_SET_STATUSBAR_EVADE");
            }
        }
    }

    private void removeView(Subtitle subtitle) {
        if (log.isDebugEnabled()) log.debug("removeView");
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
        if (log.isDebugEnabled()) log.debug("displayView sub duration={}", subtitle.getDuration());

        if (subtitle.isText()) {
            if (mIsSubtitleGfx || isFirstTime) { // transition or first time we need to adjustView
                setScreenSize(mScreenWidth, mScreenHeight);
                mIsSubtitleGfx = false;
                isFirstTime = false;
                if (log.isDebugEnabled()) log.debug("displayView: Text, mIsSubtitleGfx=false adjustView");
                // reset the layout params to get full screen text subs since before it was gfx subs with different layout
                setScreenSize(mScreenWidth, mScreenHeight);
                adjustView(); // we need to adjust the view to reflect the change
            }

            subtitle.setAlignment(getAlignment(subtitle.getText()));

            if (log.isDebugEnabled()) log.debug("displayView: Text, mIsSubtitleGfx=false, alignment={} for text={}", subtitle.getAlignment(), subtitle.getText());

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
            if (log.isDebugEnabled()) log.debug("displayView: text={}", mSpannableStringBuilder.toString());
            // need to Invalidate View to force an update!
            mSubtitleTxtView.postInvalidate();
        } else if (subtitle.isBitmap()) {
            if (! mIsSubtitleGfx || isFirstTime) { // transition or first time we need to adjustView
                isFirstTime = false;
                mIsSubtitleGfx = true;
                if (log.isDebugEnabled()) log.debug("displayView: Bitmap, mIsSubtitleGfx=true adjustView");
                adjustView(); // we need to adjust the view because it was initialized with mIsSubtitleGfx=true
            }
            if (log.isDebugEnabled()) log.debug("displayView: Bitmap bounds={}, mIsSubtitleGfx=true", subtitle.getBounds());
            Rect bounds = subtitle.getBounds();
            mSubtitleGfxView.setSubtitle(subtitle.getBitmap(), bounds, subtitle.getFrameWidth(), subtitle.getFrameHeight());
        }
    }

    private void adjustSubtitlePosition(SubtitleAlignment alignment) {
        // Set the gravity based on the alignment for positioning
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

        // Get text justification based on horizontal alignment
        int textJustification = getTextJustification(alignment);

        // Set both positioning gravity and text justification
        mSubtitleTxtView.setGravity3D(gravity, textJustification);
    }

    /**
     * Get text justification based on subtitle alignment according to SRT standards
     * @param alignment The subtitle alignment
     * @return Gravity constant for text justification
     */
    private int getTextJustification(SubtitleAlignment alignment) {
        return switch (alignment) {
            // Left positions (1, 4, 7) - left justify
            case BOTTOM_LEFT, MID_LEFT, TOP_LEFT -> Gravity.START;
            // Right positions (3, 6, 9) - right justify
            case BOTTOM_RIGHT, MID_RIGHT, TOP_RIGHT -> Gravity.END;
            // Center positions (2, 5, 8) - center justify
            case BOTTOM_MID, MID_MID, TOP_MID -> Gravity.CENTER_HORIZONTAL;
        };
    }

    private int mColor;
    private boolean mOutline;
    private boolean mBackground;
    private int mBgOpacity;
    private int mUiMode;

    private void removeSubtitle(Subtitle subtitle) {
        if (log.isDebugEnabled()) log.debug("removeSubtitle");
        mHandler.removeMessages(MSG_DISPLAY_SUBTITLE);
        mHandler.removeMessages(MSG_REMOVE_SUBTITLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REMOVE_SUBTITLE, subtitle));
    }

    private void displaySubtitle(Subtitle subtitle) {
        if (log.isDebugEnabled()) log.debug("displaySubtitle");
        mHandler.removeMessages(MSG_REMOVE_SUBTITLE);
        mHandler.removeMessages(MSG_DISPLAY_SUBTITLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_DISPLAY_SUBTITLE, subtitle));
    }

    private static SubtitleAlignment getAlignment(final String input) {
        SubtitleAlignment alignment = SubtitleAlignment.BOTTOM_MID;
        Matcher subripAlignmentMatch = SUBRIP_ALIGNMENT_TAG.matcher(input);
        if (subripAlignmentMatch.find()) {
            int alignmentInt = Integer.parseInt(subripAlignmentMatch.group(1));
            if (log.isDebugEnabled()) log.debug("getAlignment: input={} -> alignmentInt={}", input, alignmentInt);
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

        // Fix concatenated lines that lost newlines during SRT parsing
        // Pattern: any character + sentence ending + capital letter = missing line break
        displayText = displayText.replaceAll("([^\\n\\r][.!?])([A-Z])", "$1<br />$2");

        // Protect <br /> tags during whitespace condensing
        displayText = displayText.replaceAll("<br\\s*/>", "§NEWLINE§");
        // condense whitespace to 1 space (but preserve our protected newlines)
        displayText = displayText.replaceAll("\\s+", " ");
        // Restore <br /> tags
        displayText = displayText.replaceAll("§NEWLINE§", "<br />");

        // check for .SSA subtitle tags {\ ... }
        // check for WebVTT voice tags <v ...> and </v>
        // Must be done before removing SSA_ANY_TAG, because { } could be inside <v> </v>
        StringBuffer sb = new StringBuffer(displayText.length());
        displayText = replaceAll(displayText, VTT_VOICE_TAG_OPEN, HTML_VTT_VOICE_TAG_OPEN, sb);
        displayText = replaceAll(displayText, VTT_VOICE_TAG_CLOSE, "", sb);
        Matcher ssaTagMatch = SSA_ANY_TAG.matcher(displayText);
        if (ssaTagMatch.find()) {
            // convert color Tag = {\c&H0F0F0F&} ......{\ to html tag
            sb.setLength(0);
            displayText = replaceAll(displayText, SSA_COLOR_TAG, HTML_COLOR_TAG, sb);
            displayText = replaceAll(displayText, SSA_ITALIC_TAG, HTML_ITALIC_TAG, sb);
            displayText = replaceAll(displayText, SSA_BOLD_TAG, HTML_BOLD_TAG, sb);
            displayText = replaceAll(displayText, SSA_UNDERLINE_TAG, HTML_UNDERLINE_TAG, sb);
            displayText = replaceAll(displayText, SSA_STRIKETHROUGH_TAG, HTML_STRIKETHROUGH_TAG, sb);
            displayText = replaceAll(displayText, SSA_ANY_TAG, "", sb);
        }
        if (log.isDebugEnabled()) log.debug("cleanText: [{}] -> [{}]", input, displayText);
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

    public boolean getBackgroundState() { 
        return mBackground; 
    }

    public void setBackgroundState(boolean background) {
        mBackground = background;
        if (mSubtitleTxtView != null) {
            mSubtitleTxtView.setBackgroundState(background);
        }
    }

    public int getBackgroundOpacity() { 
        return mBgOpacity; 
    }

    public void setBackgroundOpacity(int opacity) {
        mBgOpacity = opacity;
        if (mSubtitleTxtView != null) {
            mSubtitleTxtView.setBackgroundOpacity(opacity); 
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
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread quit");
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
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread started: set mSubtitleDisplayLeft=0");
            int mSubtitleDisplayLeft = 0;
            while (mRunning) {
                interrupted = false;
                synchronized (this) {
                    // wait() until we get a new Subtitle via addSubtitle() / player continues
                    while (mSuspended) {
                        if (log.isDebugEnabled()) log.debug("DispSubtitleThread wait()");
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            if (!mRunning) {
                                if (log.isDebugEnabled()) log.debug("DispSubtitleThread wait() - interrupted and not running, clear subtitle at {}!", System.currentTimeMillis());
                                clear();
                                return;
                            }
                            if (log.isDebugEnabled()) log.debug("DispSubtitleThread wait() - interrupted");
                        }
                    }
                }
                synchronized (this) {
                    // we don't have a subtitle, go back to wait()
                    if ((mCurrentSubtitle == null && mNextSubtitle == null) || (mCurrentSubtitle == null && mNextSubtitle != null && mNextSubtitle.getDuration() == 0)) {
                        if (log.isDebugEnabled()) log.debug("DispSubtitleThread no valid Subtitle, mNextSubtitle={}+{}ms",
                                mNextSubtitle != null ? mNextSubtitle.getPosition() : "null",
                                mNextSubtitle != null ? mNextSubtitle.getDuration() : "null");
                        if (mNextSubtitle != null) mNextSubtitle = null; // if mCurrentSubtitle is null, receiving zero subtitle has no effect
                        mSuspended = true;
                        continue;
                    }

                    // we have a subtitle that is not displayed yet
                    if (mCurrentSubtitle == null) { // new subtitle only considered if current one is not null
                        mCurrentSubtitle = mNextSubtitle; // the next subtitle has a duration > 0 other wise it would have been filtered out before
                        currentSubtitle = mCurrentSubtitle;
                        mNextSubtitle = null;
                        displaySubtitle(mCurrentSubtitle);
                        mSubtitleDisplayLeft = mCurrentSubtitle.getDuration();
                        if (log.isDebugEnabled()) log.debug("DispSubtitleThread displaying new (current=new) subtitle={}+{}ms, bounds={}, mSubtitleDisplayLeft={}", mCurrentSubtitle.getPosition(), mCurrentSubtitle.getDuration(), mCurrentSubtitle.getBounds(), mSubtitleDisplayLeft);
                    }
                }

                // outside of synchronized since sleep does NOT release the lock
                // go to sleep if we have still have mSubtitleDisplayLeft
                Subtitle currentSub = mCurrentSubtitle;
                if (mSubtitleDisplayLeft > 0 && currentSub != null) { // we have a subtitle to display
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread after displaying mCurrentSubtitle={}+{}ms, sleep for {}", currentSub.getPosition(), currentSub.getDuration(), mSubtitleDisplayLeft);
                    long sleepStart = System.currentTimeMillis();
                    try {
                        sleep(mSubtitleDisplayLeft);
                    } catch (InterruptedException e) { // wake up from sleep
                        interrupted = true;
                        long elapsedTime = System.currentTimeMillis() - sleepStart;
                        Subtitle curSub = mCurrentSubtitle;
                        Subtitle nxtSub = mNextSubtitle;
                        if (log.isDebugEnabled()) log.debug("DispSubtitleThread sleep interrupt, waking up after {}ms, mCurrentSubtitle={}+{}ms, mNextSubtitle={}+{}ms, old mSubtitleDisplayLeft={}",
                                elapsedTime,
                                curSub != null ? curSub.getPosition() : "null",
                                curSub != null ? curSub.getDuration() : "null",
                                nxtSub != null ? nxtSub.getPosition() : "null",
                                nxtSub != null ? nxtSub.getDuration() : "null",
                                mSubtitleDisplayLeft);
                        if (curSub != null && nxtSub != null) {
                            // woke up from sleep by interrupt because getting new subtitle
                            int currentPosition = curSub.getPosition() + (int) elapsedTime;
                            int realCurrentSubtitleDuration;
                            // need to correct time left only if the next subtitle starts before the current one ends
                            if (curSub.getPosition() + curSub.getDuration() > nxtSub.getPosition()) {
                                if (log.isDebugEnabled()) log.debug("DispSubtitleThread: cannot sleep after mNextSubtitle, adjust");
                                realCurrentSubtitleDuration = nxtSub.getPosition() - curSub.getPosition();
                                curSub.setDuration(realCurrentSubtitleDuration);
                                mSubtitleDisplayLeft = nxtSub.getPosition() - currentPosition;
                            } else {
                                realCurrentSubtitleDuration = curSub.getDuration();
                                mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                            }
                            if (log.isDebugEnabled()) log.debug("DispSubtitleThread sleep interrupt bcoz received new subtitle, recompute duration currentPosition={}, realCurrentSubtitleDuration={}, updated mSubtitleDisplayLeft={}", currentPosition, realCurrentSubtitleDuration, mSubtitleDisplayLeft);
                            if (nxtSub.getDuration() == 0) { // this is an empty subtitle that is used to provide the correct duration
                                if (log.isDebugEnabled()) log.debug("DispSubtitleThread sleep interrupt bcoz received empty Subtitle, dismiss mNextSubtitle");
                                mNextSubtitle = null; // remove the empty subtitle
                            }
                        } else {
                            mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                            if (log.isDebugEnabled()) log.debug("DispSubtitleThread sleep interrupt by seek/exit condition, updated mSubtitleDisplayLeft={}", mSubtitleDisplayLeft);
                        }
                    }
                    // if not interrupted update mSubtitleDisplayLeft (otherwise it is already done)
                    if (! interrupted) mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread now mSubtitleDisplayLeft={}", mSubtitleDisplayLeft);
                }
                // if we slept without interrupt or no display time is left remove the subtitle
                if (mSubtitleDisplayLeft <= 0) {
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread removing subtitle because mSubtitleDisplayLeft={}<0", mSubtitleDisplayLeft);
                    synchronized (this) {
                        if (mCurrentSubtitle != null) {
                            removeSubtitle(mCurrentSubtitle);
                            mCurrentSubtitle = null;
                            currentSubtitle = null;
                            mSubtitleDisplayLeft = 0;
                        }
                    }
                }
            }
            clear();
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread exited");
        }

        synchronized void addSubtitle(Subtitle subtitle) {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread addSubtitle isBitmap={} isText={} isTimed={} position={} duration={}", subtitle.isBitmap(), subtitle.isText(), subtitle.isTimed(), subtitle.getPosition(), subtitle.getDuration());
            mSuspended = false;

            if (subtitle.isTimed()) {
                mNextSubtitle = subtitle;
                if (!isAlive()) {
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread addSubtitle thread is not alive -> start");
                    super.start();
                } else {
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread addSubtitle thread is alive -> interrupt");
                    interrupt();
                }
            } else {
                if (log.isDebugEnabled()) log.debug("DispSubtitleThread addSubtitle not timed!");
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
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread show");
            // could setVisibility here
        }

        synchronized void clear() {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread clear");
            mSuspended = true;
            if (mCurrentSubtitle != null) {
                removeSubtitle(mCurrentSubtitle);
                mCurrentSubtitle = null;
                mNextSubtitle = null;
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_SUBTITLE));
        }

        synchronized void setSuspended(boolean suspended) {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread setSuspended");
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
        if (log.isDebugEnabled()) log.debug("setScreenSize: {}x{} mIsSubtitleGfx={}, mSubtitleLayout={}", displayWidth, displayHeight, mIsSubtitleGfx, (mSubtitleLayout == null ? "null" : "not null"));
        mScreenWidth = displayWidth;
        mScreenHeight = displayHeight;
        if (mSubtitleLayout != null) {
            // reset layout params to get full screen text subs since before it could have been gfx subs with different layout
            ViewGroup.LayoutParams lp = mSubtitleLayout.getLayoutParams();
            lp.width = mScreenWidth;
            lp.height = mScreenHeight;
            mPlayerView.updateViewLayout(mSubtitleLayout, lp);
        }
        if (currentSubtitle != null) displaySubtitle(currentSubtitle); // redisplay when changing screen size or video surface format
        if(mSubtitleTxtView!=null) mSubtitleTxtView.setScreenSize(displayWidth, displayHeight);
        setSize(mSubtitleSize);
        updateSubtitleLayout();
    }

    public void updateSubtitleLayout() {
        if (log.isDebugEnabled()) log.debug("updateSubtitleLayout");
        // surface change redisplay sub to adjust surface size
        if (! isFirstTime) adjustView();
        if (currentSubtitle != null) {
            displaySubtitle(currentSubtitle);
        }
    }
    
    public void setUIExternalSurface(Surface uiSurface) {
        if (log.isDebugEnabled()) log.debug("setUIExternalSurface {}", uiSurface);
        mUiSurface = uiSurface;
        if (mSubtitleGfxView != null) {
            mSubtitleGfxView.setRenderingSurface(uiSurface);
            if (log.isDebugEnabled()) log.debug("setUIExternalSurface setRenderingSurface for mSubtitleGfxView");
        }
        if (mSubtitleTxtView != null)
            mSubtitleTxtView.setRenderingSurface(uiSurface);
        if (mSubtitleSpacer != null)
            mSubtitleSpacer.setRenderingSurface(uiSurface);
    }

    // setOnSystemUiVisibilityChangeListener is the only reliable way to track transient bar visibility;
    // no WindowInsetsControllerCompat equivalent exists for this use case.
    @SuppressWarnings("deprecation")
    private void attachWindow() {
        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (mPreferences != null) mFullScreenWithCutout = mPreferences.getBoolean("enable_cutout_mode_short_edges", true);
        if (mSubtitleLayout != null) return;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSubtitleLayout = inflater.inflate(R.layout.subtitle_layout, mPlayerView, false);
        if (mSubtitleLayout == null) return;
        mSubtitleSpacer = (SubtitleSpacerView) mSubtitleLayout.findViewById(R.id.subtitle_spacer);
        mSubtitleGfxView = (SubtitleGfxView) mSubtitleLayout.findViewById(R.id.subtitle_gfx_view);
        mSubtitleTxtView = (Subtitle3DTextView) mSubtitleLayout.findViewById(R.id.subtitle_txt_view);
        if (mSubtitleSpacer == null || mSubtitleGfxView == null || mSubtitleTxtView == null) return;
        mSubtitleTxtView.setScreenSize(mScreenWidth, mScreenHeight);
        mSubtitleTxtView.setUIMode(mUiMode);
        mSubtitleTxtView.setBackgroundState(mBackground);
        mSubtitleTxtView.setBackgroundOpacity(mBgOpacity);
        mSubtitleSpacerParams = mSubtitleSpacer.getLayoutParams();
        if (log.isDebugEnabled()) log.debug("attachWindow: mSubtitleSpacerParams.height={}", mSubtitleSpacerParams.height);
        mSubtitleSpacerParams.height = mSubtitleEvadedVPos;
        setUIExternalSurface(mUiSurface);

        if (mSubtitleLayout != null) {
            mRootView = mSubtitleLayout.getRootView();
            // note OnApplyWindowInsetsListener does not update when navigation bar fades away, OnGlobalLayoutListener or addOnPreDrawListener are constantly triggering -> only setOnSystemUiVisibilityChangeListener works
            // however setOnSystemUiVisibilityChangeListener is unreliable on Android 6.0 thus use addOnLayoutChangeListener
            // in reality we need to do combination of setOnApplyWindowInsetsListener to get insets but not updated when UI mode changes and thus combine with setOnSystemUiVisibilityChangeListener

            // insets observer is needed for rotation
            mSubtitleLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                if (log.isDebugEnabled()) log.debug("attachWindow, onApplyWindowInsetsListener, mIsSubtitleGfx={}", mIsSubtitleGfx);
                if (! isFirstTime) adjustView();
                return insets;
            });

            // ui visibility listener is needed for UI mode changes
            // No WindowInsetsControllerCompat equivalent for transient bar visibility tracking;
            // setOnSystemUiVisibilityChangeListener remains the only reliable option here.
            //noinspection deprecation
            mRootView.setOnSystemUiVisibilityChangeListener(visibility -> {
                //noinspection deprecation
                mNavigationBarShowing = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                //noinspection deprecation
                mSystemBarShowing = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
                mActionBarShowing = PlayerController.isActionBarShowing();
                mIsNavBarOnBottom = MiscUtils.isNavigationBarOnBottom(mRootView, mContext);
                mIsGestureAreaShowing = MiscUtils.isGestureAreaDisplayed(mContext);
                mGestureAreaHeight = MiscUtils.getGestureAreaHeight(mContext);
                if (log.isDebugEnabled()) log.debug("attachWindow, setOnSystemUiVisibilityChangeListener: mNavigationBarShowing={}, mSystemBarShowing={}, mActionBarShowing={}, mControlBarShowing={}, mIsNavBarOnBottom={}, mIsGestureAreaShowing={}",
                        mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, PlayerController.isControlBarShowing(), mIsNavBarOnBottom, mIsGestureAreaShowing);
                // extra parameters injected for subtitles handling that need to be shifted up above controlBar of playerController if the mSubtitleEvadedVPos is not shifting them already above
                if (! isFirstTime) adjustView();
            });

        }

        mPlayerView.addView(mSubtitleLayout, mScreenWidth, mScreenHeight);
    }

    private void adjustView() {
        // strategy is videoView avoids cutout if not in fullscreen
        // adjust subtitle text height (bottom/top) to avoid system bars and playerController bar only if text subtitle but not left/right
        boolean avoidCutout = ! mFullScreenWithCutout;
        boolean isFloatingPlayer = Player.sPlayer != null && Player.sPlayer.isFloatingPlayer();
        // Player.sPlayer.getSurfaceControllerWidth(), Player.sPlayer.getSurfaceControllerHeight() is for the videoView but virtualScreen is larger
        // do not apply globalShift if in floating player mode
        if (log.isDebugEnabled()) log.debug("adjustView: mIsSubtitleGfx={}", mIsSubtitleGfx);
        mActionBarShowing = PlayerController.isActionBarShowing();
        MiscUtils.adjustViewLayoutForInsets(mContext, mRootView, mSubtitleLayout, "mSubtitleLayout",
                mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, PlayerController.isControlBarShowing(), mIsNavBarOnBottom, mIsGestureAreaShowing,
                (! mIsSubtitleGfx && PlayerController.isControlBarShowing() ? PlayerController.getControlBarCurrentHeight() : 0), (mIsSubtitleGfx ? 0 :mSubtitleEvadedVPos),
                false, ! mIsSubtitleGfx, false, ! mIsSubtitleGfx,
                avoidCutout, avoidCutout, avoidCutout, avoidCutout, ! mIsSubtitleGfx, mIsSubtitleGfx && ! isFloatingPlayer);
    }

    public void onControlBarVisibilityChanged() {
        if (! isFirstTime) adjustView();
    }

    private void detachWindow() {
        if (mSubtitleLayout == null)
            return;
        if (log.isDebugEnabled()) log.debug("detachWindow");
        mPlayerView.removeView(mSubtitleLayout);
        mSubtitleLayout = null;
    }

    public void start() {
        if (log.isDebugEnabled()) log.debug("start");

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
        if (log.isDebugEnabled()) log.debug("stop");

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
        if (log.isDebugEnabled()) log.debug("setSize: {}", size);
        mSubtitleSize = size;
        if (mSubtitleGfxView != null) {
            mSubtitleGfxView.setSize(size, mScreenWidth, mScreenHeight);
        }
        if (mSubtitleTxtView != null) {
            mSubtitleTxtView.setTextSize(calcTextSize(size));
        }
    }

    public void setColor(int color){
        if (log.isDebugEnabled()) log.debug("setColor: {}", color);
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
        if (log.isDebugEnabled()) log.debug("fadeSubtitlePositionHint: {}", fadeIn);
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
        if (log.isDebugEnabled()) log.debug("setShowSubtitlePositionHint: {}", show);
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
        if (log.isDebugEnabled()) log.debug("setVerticalPositionInternal: new Height {}", mSubtitleSpacerParams.height);
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
            if (log.isDebugEnabled()) log.debug("onSeekStart: clear");
            mDispSubtitleThread.clear();
            mDispSubtitleThread.interrupt();
        }
    }
}
