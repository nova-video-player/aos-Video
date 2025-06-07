package com.archos.mediacenter.video.utils;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;

public class CustomTypefaceSpan extends TypefaceSpan {
    private final Typeface typeface;
    private final float textSizeSp; // size in SP
    private final int textColor;

    public CustomTypefaceSpan(String family, Typeface typeface, float textSizeSp, int textColor) {
        super(family);
        this.typeface = typeface;
        this.textSizeSp = textSizeSp;
        this.textColor = textColor;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        apply(paint);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        apply(paint);
    }

    private void apply(TextPaint paint) {
        paint.setTypeface(typeface);
        paint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, textSizeSp, Resources.getSystem().getDisplayMetrics()));
        paint.setColor(textColor);
    }
}