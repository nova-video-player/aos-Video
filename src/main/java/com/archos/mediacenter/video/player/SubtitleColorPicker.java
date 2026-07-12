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
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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
    private TextView mLabel;
    private boolean mSuppressHexWatcher = false;
    // Set right before handing focus from the hex field back to the grid (Down key), so
    // onFocusChanged() below knows not to just bounce focus straight back to the field.
    private boolean mFocusFromHexInput = false;

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
            // The "#" is a fixed label next to the field now, not part of the editable text.
            mHexInput.setText(String.format("%06X", color & 0xFFFFFF));
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
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (mCurrentlySelectedColor + ITEM_PER_LINE < colorBoxes.size()) {
                // Move selection down one row within the swatches
                colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
                mCurrentlySelectedColor += ITEM_PER_LINE;
                colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
                return true;
            }
            // Bottom row: fall through to the parent-delegation block below instead of
            // calling focusSearch() ourselves. TVCardDialog/TVCardView's own onKeyDown is
            // the only place that knows to skip a TVMenuSeparator if focusSearch() lands on
            // one — reimplementing that here missed that check, so focus could get stuck on
            // the separator instead of the row past it.
        }
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (mCurrentlySelectedColor - ITEM_PER_LINE >= 0) {
                // Move selection up one row
                colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
                mCurrentlySelectedColor -= ITEM_PER_LINE;
                colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
                return true;
            } else if (mHexInput != null) {
                // We are on the top row. Pressing UP moves focus into the Hex Input.
                mHexInput.requestFocus();
                return true;
            }
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
     * Applies the hex field the moment it holds a complete, valid 6-digit RRGGBB value —
     * called from the TextWatcher below on every keystroke. Silently no-ops otherwise
     * (empty, partial, or invalid characters), since partial typing is the normal state
     * while the user is still entering a value. The "#" is a fixed label, not part of
     * this field's text, so there's no prefix/shorthand handling needed here anymore.
     */
    private void tryApplyHexInput() {
        if (mHexInput == null) return;
        String text = mHexInput.getText().toString().trim();
        if (text.length() != 6) return; // wait for a complete RRGGBB

        try {
            int parsed = Color.parseColor("#" + text);
            mColor = parsed;
            mColorPickListener.onColorPicked(parsed);
        } catch (IllegalArgumentException e) {
            // invalid hex characters — ignore, keep whatever the user typed
        }
    }

    /**
     * Optional row label shown inline before the "#", e.g. "Text color:" — lets the host
     * dialog fold what used to be a separate label-only menu row into this one, so there's
     * one less stop to navigate past. Safe to skip if a row has no natural label.
     */
    public void setLabel(String label) {
        if (mLabel == null) return;
        mLabel.setText(label);
        mLabel.setVisibility(label != null && !label.isEmpty() ? VISIBLE : GONE);
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
        if (gainFocus) {
            if (!mFocusFromHexInput && mHexInput != null) {
                // Entered from outside this picker (the row above it in the settings menu) —
                // land on the hex field first instead of skipping straight to the swatch grid.
                mHexInput.requestFocus();
                return;
            }
            mFocusFromHexInput = false;
            colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_pressed));
        } else {
            colorBoxes.get(mCurrentlySelectedColor).setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
        }
    }
    private void init() {
        setFocusable(true);
        setOrientation(VERTICAL);

        // --- Hex input row: fixed "#" label + a plain 6-character RRGGBB field ---
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        LinearLayout hexRow = new LinearLayout(getContext());
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);

        mLabel = new TextView(getContext());
        mLabel.setPadding(pad, pad, 0, pad);
        mLabel.setVisibility(GONE); // shown once setLabel() is called
        hexRow.addView(mLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hashLabel = new TextView(getContext());
        hashLabel.setText("#");
        hashLabel.setPadding(pad, pad, 0, pad);
        hexRow.addView(hashLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mHexInput = new EditText(getContext());
        mHexInput.setHint("RRGGBB");
        mHexInput.setSingleLine(true);
        mHexInput.setPadding(pad, pad, pad, pad);
        mHexInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });
        mHexInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); // no autocorrect/caps
        // Standard "Done" action instead of leaving it to be inferred from the input type —
        // the existing API for exactly this, rather than guessing at raw key codes ourselves.
        mHexInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        hexRow.addView(mHexInput, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        addView(hexRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Single source of truth for applying the value: fires the moment all 6 digits are
        // valid, so there's nothing else that needs to "submit" it. mSuppressHexWatcher
        // guards against setCurrentColor()'s own setText() looping back into onColorPicked.
        mHexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!mSuppressHexWatcher) tryApplyHexInput();
            }
        });
        mHexInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // D-pad focus (unlike a touch tap) doesn't summon the IME on its own.
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(mHexInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        // Listener to escape the EditText on TV
        mHexInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        // Hand focus back to the grid; flagged so onFocusChanged() doesn't
                        // just bounce it straight back to this field.
                        mFocusFromHexInput = true;
                        SubtitleColorPicker.this.requestFocus();
                        return true;
                    }
                    if (TVUtils.isOKKey(keyCode)) {
                        // The value already applies live via the TextWatcher above. This
                        // just stops OK/Enter from leaking past the field to the player's
                        // global handler, which otherwise treats it as play/pause.
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        // Plain EditText has no "send to parent" chain like every other
                        // widget in this TV menu system, so left alone these vanish into
                        // the Activity's global onKeyDown() fallback instead of ever
                        // reaching TVCardDialog: Back can't close the dialog (so nothing
                        // gets saved — that only happens in TVCardDialog's own onKeyDown
                        // via exitDialog()), and Up has nowhere to go, stranding focus
                        // here. Delegate exactly like onKeyDown()'s own fallback below.
                        // Deliberately scoped to just these two — routing *every*
                        // unhandled key here also caught Backspace/Delete, which
                        // TVCardDialog has no case for and silently swallowed via its own
                        // unconditional `return true` fallback, breaking editing entirely.
                        ViewParent p;
                        View view = v;
                        while ((p = view.getParent()) != null) {
                            if (p instanceof TVCardView)
                                return ((TVCardView) p).onKeyDown(keyCode, event);
                            else if (p instanceof TVCardDialog)
                                return ((TVCardDialog) p).onKeyDown(keyCode, event);
                            else if (p instanceof View)
                                view = (View) p;
                            else
                                break;
                        }
                    }
                }
                // Everything else — Backspace/Delete, Left/Right cursor movement, typed
                // characters — falls through to EditText's own default handling untouched.
                return false;
            }
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
