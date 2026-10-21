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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.tvmenu.TVSlaveView;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;

import java.util.ArrayList;

/**
 * The preset color grid, as it always was (8 per line, D-pad moves the cursor, OK picks, a tap picks),
 * used on the phone and on the TV. Two additions: the current color is marked (the dimmer highlight,
 * the cursor is the stronger one), and it is a TVSlaveView so the side-by-side 3D copy follows it.
 * It only reports the color picked; the settings model decides what it applies to.
 */
public class SubtitleColorPicker extends LinearLayout implements TVSlaveView {

    public interface ColorPickListener {
        void onColorPicked(int color);
    }

    private static final int DEFAULT_ITEMS_PER_LINE = 8;

    private ColorPickListener mColorPickListener;
    private int mItemsPerLine = DEFAULT_ITEMS_PER_LINE;
    private int mCursor = 0;
    private int mCurrentRgb = -1;          // RGB of the color in force, -1 until known
    private boolean mIsMirror;             // the 3D copy: shows the master's cursor, takes no input
    private boolean mMirrorFocusLook;
    private SubtitleColorPicker mSlave;
    private final ArrayList<View> mBoxes = new ArrayList<>();
    private final ArrayList<Integer> mColors = new ArrayList<>();

    public SubtitleColorPicker(Context context) {
        super(context);
        init();
    }

    public SubtitleColorPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SubtitleColorPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setColorPickListener(ColorPickListener listener) {
        mColorPickListener = listener;
    }

    /** The color in force (alpha ignored): marked in the grid, and where the cursor starts. */
    public void setCurrentColor(int color) {
        mCurrentRgb = color & 0x00FFFFFF;
        if (!isFocused()) { // never move the cursor under the user's finger / remote
            for (int i = 0; i < mColors.size(); i++) {
                if ((mColors.get(i) & 0x00FFFFFF) == mCurrentRgb) {
                    mCursor = i;
                    break;
                }
            }
        }
        updateMarks();
    }

    // ---- input --------------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (mCursor + 1 < mBoxes.size()) moveCursor(mCursor + 1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (mCursor - 1 >= 0) moveCursor(mCursor - 1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && mCursor + mItemsPerLine < mBoxes.size()) {
            moveCursor(mCursor + mItemsPerLine);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && mCursor - mItemsPerLine >= 0) {
            moveCursor(mCursor - mItemsPerLine);
            return true;
        } else if (TVUtils.isOKKey(keyCode)) {
            pick(mCursor);
            return true;
        }
        // else, we send it to the enclosing card (focus moves to the next row, Back, ...)
        return TVUtils.dispatchToCardParent(this, keyCode, keyEvent);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
        return true;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        updateMarks();
    }

    private void moveCursor(int index) {
        mCursor = index;
        updateMarks();
    }

    private void pick(int index) {
        mCursor = index;
        mCurrentRgb = mColors.get(index) & 0x00FFFFFF;
        updateMarks();
        if (mColorPickListener != null) mColorPickListener.onColorPicked(mColors.get(index));
    }

    // ---- drawing ------------------------------------------------------------------------------

    private void updateMarks() {
        final boolean look = mIsMirror ? mMirrorFocusLook : isFocused();
        final int cursorColor = ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed);
        final int currentColor = ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_focused);
        for (int i = 0; i < mBoxes.size(); i++) {
            final View box = mBoxes.get(i);
            if (look && i == mCursor) {
                box.setBackgroundColor(cursorColor);
            } else if ((mColors.get(i) & 0x00FFFFFF) == mCurrentRgb) {
                box.setBackgroundColor(currentColor);
            } else {
                box.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        if (mSlave != null) {
            mSlave.mCursor = mCursor;
            mSlave.mCurrentRgb = mCurrentRgb;
            mSlave.mMirrorFocusLook = look;
            mSlave.updateMarks();
        }
    }

    private void init() {
        setFocusable(true);
        setOrientation(VERTICAL);
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        LinearLayout line = null;
        int i = 0;
        for (final String color : getContext().getResources().getStringArray(R.array.color_picker_subtitle)) {
            if (i % mItemsPerLine == 0 || line == null) {
                line = new LinearLayout(getContext());
                line.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
                addView(line);
            }
            final View box = inflater.inflate(R.layout.subtitle_color_picker_box, line, false);
            final int argb = Color.parseColor(color);
            mBoxes.add(box);
            mColors.add(argb);
            box.findViewById(R.id.color).setBackgroundColor(argb);
            final int index = i;
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!mIsMirror) pick(index);
                }
            });
            line.addView(box);
            i++;
        }
        updateMarks();
    }

    // ---- TVSlaveView: the right-hand copy in side-by-side 3D -----------------------------------

    @Override
    public void updateSlaveView() {
    }

    @Override
    public View getSlaveView() {
        return mSlave;
    }

    @Override
    public View createSlaveView() {
        setSlaveView(new SubtitleColorPicker(getContext()));
        return mSlave;
    }

    @Override
    public void setSlaveView(View v) {
        if (v instanceof SubtitleColorPicker) {
            mSlave = (SubtitleColorPicker) v;
            mSlave.mIsMirror = true;
            mSlave.setFocusable(false);
            updateMarks();
        }
    }

    @Override
    public void removeSlaveView() {
        mSlave = null;
    }
}
