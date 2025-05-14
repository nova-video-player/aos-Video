package com.archos.mediacenter.video.utils;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.ListPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;

import com.archos.mediacenter.video.R;

import java.util.Objects;

public class CustomListPreferenceDialogFragmentCompat extends ListPreferenceDialogFragmentCompat {

    public static CustomListPreferenceDialogFragmentCompat newInstance(String key) {
        final CustomListPreferenceDialogFragmentCompat fragment = new CustomListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context themedContext = new ContextThemeWrapper(requireContext(), R.style.CustomPreferenceDialogTheme);
        ListPreference preference = (ListPreference) getPreference();
        CharSequence[] entries = preference.getEntries();
        CharSequence[] entryValues = preference.getEntryValues();

        int selectedIndex = preference.findIndexOfValue(preference.getValue());

        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle(preference.getTitle())
                .setSingleChoiceItems(entries, selectedIndex, (dialogInterface, which) -> {
                    preference.setValue(entryValues[which].toString());
                    dialogInterface.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();

        // Apply custom background to the dialog window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.custom_dialog_background)
            );
        }

        // Customizing radio button
        ListView listView = dialog.getListView();
        if (listView != null) {
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }

        dialog.setOnShowListener(d -> {
            // Load your custom font
            Typeface typeface1 = ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_75bd);
            Typeface typeface2 = ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_65md);

            // Walk through dialog views and apply font
            ViewGroup root = dialog.findViewById(android.R.id.content);
            if (root != null && typeface1 != null && typeface2 != null) {
               applyFontToViewGroup(root, typeface1, typeface2);
            }
        });

        return dialog;
    }

    private void applyFontToViewGroup(ViewGroup viewGroup, Typeface typeface1, Typeface typeface2) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);

            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                int id = textView.getId();
                CharSequence text = textView.getText();
                Log.d("CustomDialogFont", "TextView text=\"" + text + "\", id=" + id); // get id
                Log.d("TitleId", getResources().getResourceEntryName(16908308)); // get title

                if (id == R.id.alertTitle) {
                    // Header
                    textView.setTypeface(typeface1);
                    textView.setTextSize(24);
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
                } else if (id == android.R.id.button2) {
                    // Cancel button
                    textView.setTypeface(typeface2);
                    textView.setTextSize(20);
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.green700));
                } else if (id == android.R.id.text1) {
                    // List item (text1)
                    textView.setTypeface(typeface2);
                    textView.setTextSize(20);
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                }else {
                    // other
                    textView.setTypeface(typeface2);
                    textView.setTextSize(20);
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.light_orange));
                }
            } else if (child instanceof ViewGroup) {
                applyFontToViewGroup((ViewGroup) child, typeface2, typeface2);
            }
        }
    }
}
