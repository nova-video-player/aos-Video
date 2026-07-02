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
import androidx.core.content.ContextCompat;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.tvmenu.TVCardDialog;
import com.archos.mediacenter.video.player.tvmenu.TVCardView;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;

import java.util.ArrayList;

/**
 * Created by alexandre on 13/10/15.
 */
public class SubtitleColorPicker extends LinearLayout  {

    /**
     * Which style property this shared picker instance is currently editing.
     * Set by the host dialog right before calling setVisibility(VISIBLE) on a swatch tap;
     * read back inside the dialog's single ColorPickListener.onColorPicked() to route the
     * chosen color to the right SubtitleManager setter. Purely a routing hint — this class
     * has no opinion about what the target means.
     */
    public enum Target {
        TEXT, OUTLINE, SHADOW, BACKGROUND
    }

    private Target mTarget = Target.TEXT;

    public Target getTarget() {
        return mTarget;
    }

    public void setTarget(Target target) {
        mTarget = target;
    }

    private ColorPickListener mColorPickListener;
    private int mColor;
    private int mSize;
    private static final int ITEM_PER_LINE = 8;
    private int mCurrentlySelectedColor = 0;
    private ArrayList<View> colorBoxes = new ArrayList<>();
    private ArrayList<Integer> colors = new ArrayList<>();
    private EditText mHexInput;
    private boolean mSuppressHexWatcher = false;

    /**
     * Sets the hex field to reflect a color without re-triggering onColorPicked — used
     * when the host dialog opens the picker for a swatch that already has a color set.
     */
    public void setCurrentColor(int color) {
        mColor = color;
        if (mHexInput != null) {
            mSuppressHexWatcher = true;
            // Show RGB only (no alpha) since that's what users expect to type/read;
            // alpha/opacity is controlled separately by the background opacity slider.
            mHexInput.setText(String.format("#%06X", color & 0xFFFFFF));
            mSuppressHexWatcher = false;
        }
    }

    public SubtitleColorPicker(Context context) {
        super(context);
        init();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
        if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
            if(mCurrentlySelectedColor+1<colorBoxes.size()) {
                colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
                mCurrentlySelectedColor++;
                colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
            }
            return true;
        }
        else if(keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {

            if (mCurrentlySelectedColor - 1 >= 0){
                colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
                mCurrentlySelectedColor--;
                colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
            }
            return true;
        }
        else if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN&&mCurrentlySelectedColor+ITEM_PER_LINE<colorBoxes.size()){
            colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
            mCurrentlySelectedColor += ITEM_PER_LINE;
            colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
            return true;
        }
       else if(keyCode== KeyEvent.KEYCODE_DPAD_UP&&mCurrentlySelectedColor-ITEM_PER_LINE>=0){
            colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
            mCurrentlySelectedColor-=ITEM_PER_LINE;
            colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
            return true;
        }
        else if (TVUtils.isOKKey(keyCode)) {
            mColor = colors.get(mCurrentlySelectedColor);
            mColorPickListener.onColorPicked(mColor);
            return true;
        }
        //else, we send it to parent
        ViewParent p;
        View v = this;
        while((p=v.getParent())!=null){
            if(p instanceof TVCardView)
                return ((TVCardView)p).onKeyDown(keyCode, keyEvent);
            else if(p instanceof TVCardDialog)
                return ((TVCardDialog)p).onKeyDown(keyCode, keyEvent);
            else if(p instanceof View)
                v=(View)p;
            else
                break;
        }
        return false;
    }
    public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
        return true;
    }
    public interface ColorPickListener{
        void onColorPicked(int color);
    }

    /**
     * Parses the hex field (accepts "#RRGGBB", "RRGGBB", or shorthand "#RGB") and, if
     * valid, dispatches it exactly like a swatch tap. Silently no-ops on invalid/empty
     * input rather than erroring, since this fires on every focus-loss and partial typing
     * is expected while the user is still entering a value.
     */
    private void tryApplyHexInput() {
        if (mHexInput == null) return;
        String text = mHexInput.getText().toString().trim();
        if (text.isEmpty()) return;
        if (!text.startsWith("#")) text = "#" + text;

        // Expand shorthand #RGB to #RRGGBB
        if (text.length() == 4) {
            StringBuilder sb = new StringBuilder("#");
            for (int i = 1; i < 4; i++) {
                char c = text.charAt(i);
                sb.append(c).append(c);
            }
            text = sb.toString();
        }

        if (text.length() != 7) return; // not a complete #RRGGBB yet, wait for more input

        try {
            int parsed = Color.parseColor(text);
            mColor = parsed;
            mColorPickListener.onColorPicked(parsed);
        } catch (IllegalArgumentException e) {
            // invalid hex string (bad characters) — ignore, keep whatever text the user typed
        }
    }

    public void setColorPickListener(ColorPickListener listener){
        mColorPickListener = listener;
    }
    public SubtitleColorPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SubtitleColorPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus,direction, previouslyFocusedRect);
        if(gainFocus)
            colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
        else
            colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));

    }
    private void init() {
        setFocusable(true);
        setOrientation(VERTICAL);

        // --- Hex input row ---
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        mHexInput = new EditText(getContext());
        mHexInput.setHint("#RRGGBB");
        mHexInput.setSingleLine(true);
        mHexInput.setPadding(pad, pad, pad, pad);
        mHexInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(7) });
        mHexInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); // no autocorrect/caps
        addView(mHexInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mHexInput.setOnEditorActionListener((v, actionId, event) -> {
            tryApplyHexInput();
            return false; // let the IME close normally too
        });
        mHexInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) tryApplyHexInput();
        });

        LinearLayout line = null;
        int i = 0;

        LayoutInflater inflater= LayoutInflater.from(getContext());
        mSize = getContext().getResources().getStringArray(R.array.color_picker_subtitle).length;
        for(final String color : getContext().getResources().getStringArray(R.array.color_picker_subtitle)){

            if(i%ITEM_PER_LINE == 0||line==null) {
                line = new LinearLayout(getContext());
                line.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
                addView(line);
            }
            View box = inflater.inflate(R.layout.subtitle_color_picker_box, null);
            colorBoxes.add(box);
            final int finalPos = i;
            box.findViewById(R.id.color).setBackgroundColor(Color.parseColor(color));
            colors.add(Color.parseColor(color));
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mCurrentlySelectedColor = finalPos;
                    mColor = Color.parseColor(color);
                    mColorPickListener.onColorPicked(mColor);
                }
            });
            line.addView(box);
            i++;
        }

    }
}
