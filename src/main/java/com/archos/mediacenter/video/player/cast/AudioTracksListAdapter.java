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

package com.archos.mediacenter.video.player.cast;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.libraries.cast.companionlibrary.cast.tracks.ui.TracksListAdapter;

import java.util.List;

/**
 * Created by alexandre on 05/08/16.
 */
public class AudioTracksListAdapter extends TracksListAdapter {

    private final OnTrackSelectedListener mOnTrackSelectedListener;
    private OnMenuClickListener mOnMenuClickListener;

    public interface OnMenuClickListener{
        void onSoundSettingsClick();
        void onDelayAudioClick();
    }
    public AudioTracksListAdapter(Context context, int resource, List<MediaTrack> tracks, int activePosition, OnTrackSelectedListener onTrackSelectedListener) {
        super(context, resource, tracks, activePosition);
        mOnTrackSelectedListener = onTrackSelectedListener;
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        //warn listener
        mOnTrackSelectedListener.onTrackSelectionChanged();
    }

    public void setOnMenuClickListener(OnMenuClickListener onMenuClickListener){
        mOnMenuClickListener = onMenuClickListener;
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(ArchosVideoCastManager.getInstance().isRemoteDisplayConnected()&&position>=getCount()-2){
            if(convertView==null){
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                        Activity.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.tracks_row_layout, parent, false);
            }
            if(convertView.findViewById(R.id.radio)!=null)
            convertView.findViewById(R.id.radio).setVisibility(View.INVISIBLE);
            convertView.setBackgroundResource(R.drawable.transparent_ripple);
            TextView textView = (TextView) convertView.findViewById(R.id.text);
          if(position==getCount()-2)  {
              textView.setText(R.string.pref_audio_parameters_title);
              convertView.setOnClickListener(new View.OnClickListener() {
                  @Override
                  public void onClick(View view) {
                      mOnMenuClickListener.onSoundSettingsClick();
                  }
              });
          }
          else{
              textView.setText(R.string.player_pref_audio_delay_title);
              convertView.setOnClickListener(new View.OnClickListener() {
                  @Override
                  public void onClick(View view) {
                      mOnMenuClickListener.onDelayAudioClick();
                  }
              });
            }
            return convertView;
        }
        else {
            boolean shouldSetView = false;
            if(convertView!=null&& convertView.findViewById(R.id.radio)!=null) {
                convertView.findViewById(R.id.radio).setVisibility(View.VISIBLE);
                shouldSetView = convertView.getTag()!=null;
                //reinitialize background
                convertView.setBackground(null);
            }
            return super.getView(position, shouldSetView?convertView:null, parent);
        }
    }

    @Override
    public int getCount() {
        return super.getCount()+(ArchosVideoCastManager.getInstance().isRemoteDisplayConnected()?2:0);
    }

}
