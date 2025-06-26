package com.archos.mediacenter.video.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;

import com.archos.mediacenter.video.R;

public class CustomEditTextPreferenceDialogFragmentCompat extends EditTextPreferenceDialogFragmentCompat {

    public static CustomEditTextPreferenceDialogFragmentCompat newInstance(String key) {
        final CustomEditTextPreferenceDialogFragmentCompat fragment = new CustomEditTextPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        if (dialog instanceof AlertDialog) {
            AlertDialog alertDialog = (AlertDialog) dialog;

            // Replace the title with a custom view
            TextView customTitle = new TextView(requireContext());
            customTitle.setText(getPreference().getDialogTitle());
            customTitle.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_95blk));
            customTitle.setTextSize(22);
            customTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.red)); // or any color
            customTitle.setPadding(48, 48, 48, 24); // adjust as needed

            alertDialog.setCustomTitle(customTitle);
        }

        return dialog;
    }

    @Override
    protected View onCreateDialogView(Context context) {
        return LayoutInflater.from(context).inflate(R.layout.custom_edittext_dialog, null);
    }


    @Override
    public void onBindDialogView(View view) {
        super.onBindDialogView(view);

        EditText editText = view.findViewById(android.R.id.edit);
        if (editText != null) {
            editText.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_95blk));
            editText.setTextSize(16);
            editText.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        }
    }

    private TextView findMessageTextView(View root, String expectedText) {
        if (root instanceof TextView) {
            TextView tv = (TextView) root;

            int viewId = tv.getId();
            String viewIdName = null;

            if (viewId != View.NO_ID) {
                try {
                    viewIdName = getResources().getResourceEntryName(viewId);
                } catch (Resources.NotFoundException e) {
                    // ignore invalid ID
                }
            }

            if ("message".equals(viewIdName) && expectedText.equals(tv.getText().toString())) {
                return tv;
            }
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = findMessageTextView(group.getChildAt(i), expectedText);
                if (result != null) return result;
            }
        }

        return null;
    }


    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            View root = dialog.getWindow().getDecorView();

            // Find the real message TextView by ID and text content
            CharSequence dialogMessage = getPreference().getDialogMessage();
            String messageText = dialogMessage != null ? dialogMessage.toString() : "";
            TextView messageView = findMessageTextView(root, messageText);

            if (messageView != null) {
                messageView.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_75bd));
                messageView.setTextColor(Color.LTGRAY); // or your custom color
                messageView.setTextSize(14); // optional
            }

            // Style buttons
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            Typeface font = ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_95blk);

            if (positive != null) {
                positive.setTypeface(font);
                positive.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
                Drawable ripple = ContextCompat.getDrawable(requireContext(), R.drawable.custom_ripple);
                positive.setBackground(ripple);
                positive.setClipToOutline(true);
            }
            if (negative != null) {
                negative.setTypeface(font);
                negative.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
                Drawable ripple = ContextCompat.getDrawable(requireContext(), R.drawable.custom_ripple);
                negative.setBackground(ripple);
                negative.setClipToOutline(true);
            }

            // Optional: dialog background
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.menu_bg);
        }
    }
}
