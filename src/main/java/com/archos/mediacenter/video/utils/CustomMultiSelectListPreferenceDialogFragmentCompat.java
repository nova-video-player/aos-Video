package com.archos.mediacenter.video.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat;

import com.archos.mediacenter.video.R;

import java.util.HashSet;
import java.util.Set;

public class CustomMultiSelectListPreferenceDialogFragmentCompat extends MultiSelectListPreferenceDialogFragmentCompat {

    private Set<String> selectedValues = new HashSet<>();

    public static CustomMultiSelectListPreferenceDialogFragmentCompat newInstance(String key) {
        final CustomMultiSelectListPreferenceDialogFragmentCompat fragment = new CustomMultiSelectListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = requireContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Inflate the custom layout
        View dialogView = inflater.inflate(R.layout.custom_multi_select_dialog, null);

        // Get references to views in the layout
        TextView title = dialogView.findViewById(R.id.custom_title);
        TextView message = dialogView.findViewById(R.id.custom_message);
        ListView listView = dialogView.findViewById(R.id.custom_list);

        // Get the preference
        MultiSelectListPreference preference = (MultiSelectListPreference) getPreference();
        CharSequence[] entries = preference.getEntries();
        CharSequence[] entryValues = preference.getEntryValues();
        Set<String> selectedValues = preference.getValues();

        // Set title and message
        title.setText(preference.getTitle());
        message.setText("Select one or more options."); // Customize if needed

        // Prepare the adapter for multiple choice
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                context,
                R.layout.preference_multi_select_item,
                R.id.text1,
                entries
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                CheckBox checkbox = view.findViewById(R.id.check_box);
                checkbox.setChecked(listView.isItemChecked(position));
                return view;
            }
        };
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        // Mark pre-selected values
        for (int i = 0; i < entryValues.length; i++) {
            if (selectedValues.contains(entryValues[i].toString())) {
                listView.setItemChecked(i, true);
            }
        }

        // 🔁 Handle item clicks to toggle checkbox state manually
        listView.setOnItemClickListener((parent, view, position, id) -> {
            CheckBox checkBox = view.findViewById(R.id.check_box);
            if (checkBox != null) {
                checkBox.setChecked(listView.isItemChecked(position));
            }
        });

        // Build the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Set<String> newValues = new HashSet<>();
                    for (int i = 0; i < entryValues.length; i++) {
                        if (listView.isItemChecked(i)) {
                            newValues.add(entryValues[i].toString());
                        }
                    }
                    if (preference.callChangeListener(newValues)) {
                        preference.setValues(newValues);
                    }
                });

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            Typeface typeface = ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_75bd);
            Drawable original = ContextCompat.getDrawable(context, R.drawable.custom_ripple);
            if (original != null) {
                Drawable rippleOk = original.getConstantState().newDrawable().mutate();
                Drawable rippleCancel = original.getConstantState().newDrawable().mutate();

                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

                positive.setTypeface(typeface);
                positive.setTextColor(ContextCompat.getColor(requireContext(), R.color.green700));
                positive.setTextSize(20);
                negative.setTypeface(typeface);
                negative.setTextColor(ContextCompat.getColor(requireContext(), R.color.green700));
                negative.setTextSize(20);

                if (positive != null) positive.setBackground(rippleOk);
                if (negative != null) negative.setBackground(rippleCancel);
            }

        });

        // Apply custom background to the dialog window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.custom_dialog_background)
            );
        }

        return dialog;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            MultiSelectListPreference preference = (MultiSelectListPreference) getPreference();
            if (preference.callChangeListener(selectedValues)) {
                preference.setValues(selectedValues);
            }
        }
    }
}