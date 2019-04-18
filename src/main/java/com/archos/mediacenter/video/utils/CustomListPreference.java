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

package com.archos.mediacenter.video.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;

/**
 * Created by alexandre on 16/11/15.
 */
public class CustomListPreference extends ListPreference {

    public CustomListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void  onClick(){
        ListAdapter listAdapter = new CustomArrayAdapter(getContext(),
                android.R.layout.simple_list_item_single_choice, getEntries());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setAdapter(listAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                getSharedPreferences().edit().putString(getKey(), ""+which).apply();
            }
        }).show();

    }

    public class CustomArrayAdapter extends ArrayAdapter<CharSequence> {

        public CustomArrayAdapter(Context context, int textViewResourceId,
                                  CharSequence[] objects) {
            super(context, textViewResourceId, objects);

        }

        public boolean areAllItemsEnabled() {
            return false;
        }

        public View getView(int position, View converView, ViewGroup viewGroup){
            View v = super.getView(position, converView, viewGroup);
            v.setEnabled(isEnabled(position));
            ((CheckedTextView)v).setChecked(Integer.valueOf(getSharedPreferences().getString(getKey(), "0")) == position);

            return v;
        }

        public boolean isEnabled(int position) {
            if (position >= 2&& Build.VERSION.SDK_INT< Build.VERSION_CODES.LOLLIPOP)
                return false;
            else
                return true;
        }


    }
}