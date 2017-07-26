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

package com.archos.mediacenter.video.widget;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.archos.mediacenter.video.R;

public class VideoWidgetConfigure extends Activity {
    private static final String TAG = "VideoWidgetConfigure";

    int mAppWidgetId = -1;
    int mContentType = -1;

    private Button mValidateButton;

    public class OnContentItemSelectedListener implements AdapterView.OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            // Check the name rather than the index so that it still works if the order of the items is changed
            String selectedOption = parent.getItemAtPosition(pos).toString();
            if (selectedOption.equals(getString(R.string.content_recently_added))) {
                mContentType = WidgetProviderVideo.MODE_RECENTLY_ADDED;
            }
            else if (selectedOption.equals(getString(R.string.content_recently_played))) {
                mContentType = WidgetProviderVideo.MODE_RECENTLY_PLAYED;
            }
            else if (selectedOption.equals(getString(R.string.all_movies))) {
                mContentType = WidgetProviderVideo.MODE_MOVIES;
            }
            else if (selectedOption.equals(getString(R.string.all_tv_shows))) {
                mContentType = WidgetProviderVideo.MODE_TVSHOWS;
            }
            else {
                mContentType = WidgetProviderVideo.MODE_ALL_VIDEOS;
            }
        }

        public void onNothingSelected(AdapterView parent) {
            // Do nothing.
        }
    }

 
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Retrieve the widget id provided by the intent
        mAppWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);


        // Set the Activity result to RESULT_CANCELED immediately : thus the App Widget host will always be notified
        // in case of error or if the user cancels the configuration and the App Widget will not be added
        notifyResult(RESULT_CANCELED);

        if (mAppWidgetId == -1) {
            // Error, no id provided
            finish();
        }

        setContentView(R.layout.widget_configuration);
 
        // Content spinner setup
        Spinner contentSpinner = (Spinner)findViewById(R.id.content_spinner);
        ArrayAdapter<CharSequence> contentAdapter = ArrayAdapter.createFromResource(this, R.array.video_content_array, android.R.layout.simple_spinner_item);
        contentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        contentSpinner.setAdapter(contentAdapter);
        contentSpinner.setOnItemSelectedListener(new OnContentItemSelectedListener());

        mValidateButton = (Button)findViewById(R.id.validate_button);
        mValidateButton.setOnClickListener(mValidateButtonClickListener);
   }

    private View.OnClickListener mValidateButtonClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            // Configure the widget
            WidgetProviderVideo.configure(VideoWidgetConfigure.this, mAppWidgetId, mContentType);

            // Ask to update the widget because the initial update was skipped
            // (the configuration was not yet available when it was received by the provider)
            Intent intent = new Intent(VideoWidgetConfigure.this, WidgetProviderVideo.class);
            intent.setAction(WidgetProviderVideo.INITIAL_UPDATE_ACTION);
            intent.setData(Uri.parse(String.valueOf(mAppWidgetId)));    // Fill data with a dummy value to avoid the "extra beeing ignored" optimization of the PendingIntent
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(VideoWidgetConfigure.this, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 100, pendingIntent);

            // Tell the AppWidgetHost that the configuration is done for this widget id
            notifyResult(Activity.RESULT_OK);

            // Configuration done => exit the activity
            finish();
        }
    };

    private void notifyResult(int resultCode) {
        if (mAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Normal case : set the result and provide back the widget id
            Intent intent = new Intent();
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            setResult(resultCode, intent);
        }
        else {
            // Error, unknown widget id => just set the result code
            setResult(resultCode);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }    
}
