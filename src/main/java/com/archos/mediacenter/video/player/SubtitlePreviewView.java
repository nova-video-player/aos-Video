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

package com.archos.mediacenter.video.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.archos.mediacenter.video.R;

/**
 * The "sample" strip of the subtitle settings panel. It reads the live values straight from
 * SubtitleManager (no copies), and draws them the way sync_styles() in sub_format_ssa.c feeds
 * libass when the user style is forced:
 * <ul>
 * <li>size, outline, shadow and box padding are all in "PlayRes 720" units, so every one of them
 *     becomes pixels by x (short side of the video surface / 720);</li>
 * <li>Off: outline + offset shadow (BorderStyle 1); Line: one box per line, padding = the outline
 *     width setting (BorderStyle 3); Box: one box for the text block, padding = the shadow width
 *     setting, plus the outline around the glyphs (BorderStyle 4).</li>
 * </ul>
 * It is an approximation, not libass: the real font comes from the fonts folder, and libass
 * sizes a font so ascent + descent equals the font size (approximated here with the default
 * font's metrics). When the file's own style is in force the preview cannot know it, so it says so.
 */
public class SubtitlePreviewView extends View {

    /** Short side, in pixels, of the surface the subtitles are rendered on. */
    public interface ShortSideProvider {
        int getShortSide();
    }

    private SubtitleManager mManager;
    private ShortSideProvider mShortSide;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBox = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCaption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final android.text.TextPaint mEllipsizePaint = new android.text.TextPaint();

    public SubtitlePreviewView(Context context) {
        this(context, null);
    }

    public SubtitlePreviewView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SubtitlePreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
        mCaption.setColor(ContextCompat.getColor(context, R.color.subtitle_panel_text_secondary));
        mCaption.setTextAlign(Paint.Align.CENTER);
        mCaption.setTextSize(android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, 13f, context.getResources().getDisplayMetrics()));
    }

    public void bind(SubtitleManager manager, ShortSideProvider shortSide) {
        mManager = manager;
        mShortSide = shortSide;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mManager == null) return;
        final int w = getWidth();
        final int h = getHeight();

        if (mManager.getEffectiveOverrideMode() != SubtitleManager.OVERRIDE_CUSTOM) {
            // File's style / Scale only: the look comes from the file, which we cannot know here.
            String caption = getContext().getString(R.string.subtitle_panel_preview_own_style);
            mEllipsizePaint.set(mCaption);
            caption = TextUtils.ellipsize(caption, mEllipsizePaint, w - 24f,
                    TextUtils.TruncateAt.END).toString();
            canvas.drawText(caption, w / 2f, h / 2f - (mCaption.ascent() + mCaption.descent()) / 2f, mCaption);
            return;
        }

        int shortSide = mShortSide != null ? mShortSide.getShortSide() : 0;
        if (shortSide <= 0) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            shortSide = Math.min(dm.widthPixels, dm.heightPixels);
        }
        final float k = shortSide / 720f;

        // libass sizes a font so that ascent + descent == FontSize; Android's text size is the em.
        final Typeface face = mManager.getBold() ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        mFill.setTypeface(face);
        mFill.setTextSize(100f);
        Paint.FontMetrics fm = mFill.getFontMetrics();
        final float cell = fm.descent - fm.ascent;
        final float fontPx = mManager.getFontSizePt() * k;
        final float textSize = cell > 0 ? fontPx * 100f / cell : fontPx;
        mFill.setTextSize(textSize);
        mStroke.setTypeface(face);
        mStroke.setTextSize(textSize);
        fm = mFill.getFontMetrics();

        final String text = getContext().getString(R.string.subtitle_sample_text);
        final float tw = mFill.measureText(text);
        final float x = (w - tw) / 2f;
        final float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
        final int textColor = mManager.getColor();
        final float outline = mManager.getOutlineWidth() * k;
        final float shadow = mManager.getShadowWidth() * k;

        switch (mManager.getBgMode()) {
            case SubtitleManager.BG_MODE_BOXED_LINE: {
                // BorderStyle 3: a box per line, padding = outline width, no outline, no shadow
                drawBox(canvas, x, baseline, tw, fm, outline);
                mFill.setColor(textColor);
                canvas.drawText(text, x, baseline, mFill);
                break;
            }
            case SubtitleManager.BG_MODE_BOXED_BLOCK: {
                // BorderStyle 4: one box for the block, padding = shadow width, outline on the glyphs
                drawBox(canvas, x, baseline, tw, fm, shadow);
                drawOutlined(canvas, text, x, baseline, outline, mManager.getOutlineColor(), textColor);
                break;
            }
            default: {
                // BorderStyle 1: outline + a shadow offset down and to the right, in the shadow color
                if (shadow > 0f) {
                    drawOutlined(canvas, text, x + shadow, baseline + shadow, outline,
                            mManager.getShadowColor(), mManager.getShadowColor());
                }
                drawOutlined(canvas, text, x, baseline, outline, mManager.getOutlineColor(), textColor);
                break;
            }
        }
    }

    private void drawBox(Canvas canvas, float x, float baseline, float tw, Paint.FontMetrics fm, float pad) {
        final int bg = mManager.getBackgroundColor();
        mBox.setColor(Color.argb(mManager.getBackgroundOpacity(), Color.red(bg), Color.green(bg), Color.blue(bg)));
        mRect.set(x - pad, baseline + fm.ascent - pad, x + tw + pad, baseline + fm.descent + pad);
        canvas.drawRect(mRect, mBox);
    }

    private void drawOutlined(Canvas canvas, String text, float x, float baseline, float outline,
                              int outlineColor, int fillColor) {
        if (outline > 0f) {
            // a stroke is centred on the glyph edge, libass's outline extends outward by its width
            mStroke.setStrokeWidth(outline * 2f);
            mStroke.setColor(outlineColor);
            canvas.drawText(text, x, baseline, mStroke);
        }
        mFill.setColor(fillColor);
        canvas.drawText(text, x, baseline, mFill);
    }
}
