// Copyright 2026 Courville Software
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

package com.archos.mediacenter.video.player.tvmenu;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.archos.mediacenter.video.R;

import java.util.ArrayList;

/**
 * A row of mutually exclusive choices drawn as a pill (a segment per option, the chosen one in the
 * accent color). It behaves like the other TV menu rows: same focus highlight as TVMenuItem, Left /
 * Right move the choice, OK cycles it, every other key goes to the enclosing card (Up / Down move
 * to the neighbouring row, Back closes), and it mirrors itself for side-by-side 3D (TVSlaveView).
 */
public class TVPillItem extends LinearLayout implements TVSlaveView {

    public interface OnSelectedListener {
        void onSelected(int index);
    }

    private final ArrayList<TextView> mSegments = new ArrayList<>();
    private OnSelectedListener mListener;
    private int mSelected;
    private float mTextSizePx;
    private CharSequence[] mLabels = new CharSequence[0];
    private TVPillItem mSlave;

    public TVPillItem(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setBackgroundResource(R.drawable.subtitle_pill_bg);
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                setFocusLook(hasFocus);
                if (mSlave != null) mSlave.setFocusLook(hasFocus);
            }
        });
    }

    /** @param textSizePx the size of the neighbouring menu labels, so the pill reads like the rest of the menu */
    public void setOptions(CharSequence[] labels, float textSizePx) {
        mLabels = labels;
        mTextSizePx = textSizePx;
        removeAllViews();
        mSegments.clear();
        final int padH = dp(12);
        final int padV = dp(8);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final TextView seg = new TextView(getContext());
            seg.setText(labels[i]);
            seg.setGravity(Gravity.CENTER);
            seg.setSingleLine(true);
            seg.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
            seg.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.subtitle_pill_text));
            seg.setBackgroundResource(R.drawable.subtitle_pill_segment);
            seg.setPadding(padH, padV, padH, padV);
            seg.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(index, true);
                }
            });
            addView(seg, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            mSegments.add(seg);
        }
        applySelection();
    }

    public void setOnSelectedListener(OnSelectedListener listener) {
        mListener = listener;
    }

    public int getSelectedIndex() {
        return mSelected;
    }

    /** Programmatic change (no listener call). */
    public void setSelectedIndex(int index) {
        select(index, false);
    }

    private void select(int index, boolean notify) {
        if (mSegments.isEmpty()) return;
        final int i = Math.max(0, Math.min(mSegments.size() - 1, index));
        final boolean changed = i != mSelected;
        mSelected = i;
        applySelection();
        if (notify && changed && mListener != null) mListener.onSelected(i);
    }

    private void applySelection() {
        for (int i = 0; i < mSegments.size(); i++) mSegments.get(i).setSelected(i == mSelected);
        if (mSlave != null) {
            mSlave.mSelected = mSelected;
            mSlave.applySelection();
        }
    }

    private void setFocusLook(boolean hasFocus) {
        if (hasFocus) {
            setBackgroundResource(R.color.video_info_next_prev_button_focused);
        } else {
            setBackgroundResource(R.drawable.subtitle_pill_bg);
        }
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    // ---- keys ---------------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                select(mSelected - 1, true);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                select(mSelected + 1, true);
                return true;
            default:
                break;
        }
        if (TVUtils.isOKKey(keyCode)) {
            if (!mSegments.isEmpty()) select((mSelected + 1) % mSegments.size(), true);
            return true;
        }
        // else, we send it to the enclosing card
        return TVUtils.dispatchToCardParent(this, keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || TVUtils.isOKKey(keyCode);
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
        setSlaveView(new TVPillItem(getContext()));
        return mSlave;
    }

    @Override
    public void setSlaveView(View v) {
        if (v instanceof TVPillItem) {
            mSlave = (TVPillItem) v;
            mSlave.setFocusable(false);
            mSlave.setOptions(mLabels, mTextSizePx);
            mSlave.mSelected = mSelected;
            mSlave.applySelection();
        }
    }

    @Override
    public void removeSlaveView() {
        mSlave = null;
    }
}
