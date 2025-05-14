package com.archos.mediacenter.video.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.archos.mediacenter.video.R;

public class CustomArrayAdapter extends ArrayAdapter<CharSequence> {
    private final Typeface font;

    public CustomArrayAdapter(Context context, CharSequence[] items) {
        super(context, R.layout.custom_dialog_singlechoice, items);
        font = ResourcesCompat.getFont(context, R.font.nhaasgroteskdspro_65md);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        if (view instanceof TextView && font != null) {
            TextView tv = (TextView) view;
            tv.setTypeface(font);
            tv.setTextSize(20);
            tv.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
            tv.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple));
        }
        return view;
    }
}
