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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TabHost;

import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.AudioDelayPickerAbstract;
import com.archos.mediacenter.video.player.AudioDelayPickerDialog;
import com.archos.mediacenter.video.player.AudioDelayPickerDialogInterface;
import com.archos.mediacenter.video.player.Player;
import com.archos.mediacenter.video.player.PlayerService;
import com.archos.mediacenter.video.player.SubtitleDelayPickerAbstract;
import com.archos.mediacenter.video.player.SubtitleDelayPickerDialog;
import com.archos.mediacenter.video.player.SubtitleDelayPickerDialogInterface;
import com.archos.mediacenter.video.player.SubtitleSettingsDialog;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alexandre on 22/06/16.
 *
 * our own implementation to manage both usual and display cast
 */
public class ArchosTracksChooserDialog extends DialogFragment implements SubtitleTracksListAdapter.OnMenuClickListener, AudioTracksListAdapter.OnMenuClickListener, OnTrackSelectedListener {



    public static ArchosTracksChooserDialog newInstance(MediaInfo mediaInfo) {
        ArchosTracksChooserDialog fragment = new ArchosTracksChooserDialog();
        Bundle bundle = new Bundle();
        bundle.putBundle(VideoCastManager.EXTRA_MEDIA, Utils.mediaInfoToBundle(mediaInfo));
        fragment.setArguments(bundle);
        return fragment;
    }





    private VideoCastManager mCastManager;
    private ArchosVideoCastManager mArchosCastManager;
    private long[] mActiveTracks = null;
    private MediaInfo mMediaInfo;
    private SubtitleTracksListAdapter mTextAdapter;
    private AudioTracksListAdapter mAudioVideoAdapter;
    private List<MediaTrack> mTextTracks = new ArrayList<>();
    private List<MediaTrack> mAudioTracks = new ArrayList<>();
    public static final long TEXT_TRACK_NONE_ID = -1;
    private int mSelectedTextPosition = 0;
    private int mSelectedAudioPosition = -1;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        // since dialog doesn't expose its root view at this point (doesn't exist yet), we cannot
        // attach to the unknown eventual parent, so we need to pass null for the rootView parameter
        // of the inflate() method
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.chromecast_tracks_dialog_layout, null);
        setUpView(view);

        builder.setView(view)
                .setPositiveButton(getString(R.string.ccl_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });

        return builder.create();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        Bundle mediaWrapper = getArguments().getBundle(VideoCastManager.EXTRA_MEDIA);
        mMediaInfo = Utils.bundleToMediaInfo(mediaWrapper);
        mCastManager = VideoCastManager.getInstance();
        mArchosCastManager = ArchosVideoCastManager.getInstance();
        mActiveTracks = mArchosCastManager.getSelectedTracks();
        List<MediaTrack> allTracks = mMediaInfo.getMediaTracks();
        if (allTracks == null || allTracks.isEmpty()) {
            Utils.showToast(getActivity(), R.string.ccl_caption_no_tracks_available);
            dismiss();
        }
    }

    /**
     * This is to get around the following bug:
     * https://code.google.com/p/android/issues/detail?id=17423
     */
    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }

    private void setUpView(View view) {
        ListView listView1 = (ListView) view.findViewById(R.id.listview1);
        ListView listView2 = (ListView) view.findViewById(R.id.listview2);
        LinearLayout textEmptyMessageContainer = (LinearLayout) view.findViewById(R.id.text_empty_container);
        LinearLayout audioEmptyMessageContainer = (LinearLayout) view.findViewById(R.id.audio_empty_container);
        View switchToremoteDisplayText = view.findViewById(R.id.switch_remote_empty_message_text);
        View switchToremoteDisplayAudio = view.findViewById(R.id.switch_remote_empty_message_audio);

        partitionTracks();

        mTextAdapter = new SubtitleTracksListAdapter(getActivity(), R.layout.tracks_row_layout,
                mTextTracks, mSelectedTextPosition, this);
        mTextAdapter.setOnMenuClickListener(this);
        mAudioVideoAdapter = new AudioTracksListAdapter(getActivity(), R.layout.tracks_row_layout,
                mAudioTracks, mSelectedAudioPosition, this);
        mAudioVideoAdapter.setOnMenuClickListener(this);
        listView1.setAdapter(mTextAdapter);
        listView2.setAdapter(mAudioVideoAdapter);

        TabHost tabs = (TabHost) view.findViewById(R.id.tabhost);
        tabs.setup();

        // create tab 1
        switchToremoteDisplayText.setVisibility(View.GONE);
        switchToremoteDisplayAudio.setVisibility(View.GONE);
        TabHost.TabSpec tab1 = tabs.newTabSpec("tab1");
        if (mTextTracks == null || mTextTracks.isEmpty()) {
            listView1.setVisibility(View.INVISIBLE);
            tab1.setContent(R.id.text_empty_container);
            if(!mArchosCastManager.isRemoteDisplayConnected()){
                switchToremoteDisplayText.setVisibility(View.VISIBLE);
            }
        } else {
            textEmptyMessageContainer.setVisibility(View.INVISIBLE);
            tab1.setContent(R.id.listview1);

        }
        tab1.setIndicator(getString(R.string.ccl_caption_subtitles));
        tabs.addTab(tab1);

        // create tab 2
        TabHost.TabSpec tab2 = tabs.newTabSpec("tab2");
        if (mAudioTracks == null || mAudioTracks.isEmpty()) {
            listView2.setVisibility(View.INVISIBLE);
            tab2.setContent(R.id.audio_empty_container);
            if(!mArchosCastManager.isRemoteDisplayConnected()){
                switchToremoteDisplayAudio.setVisibility(View.VISIBLE);
            }
        } else {
            audioEmptyMessageContainer.setVisibility(View.INVISIBLE);
            tab2.setContent(R.id.listview2);
        }
        tab2.setIndicator(getString(R.string.ccl_caption_audio));
        tabs.addTab(tab2);
    }

    private MediaTrack buildNoneTrack() {
        return new MediaTrack.Builder(TEXT_TRACK_NONE_ID, MediaTrack.TYPE_TEXT)
                .setName(getString(R.string.ccl_none))
                .setSubtype(MediaTrack.SUBTYPE_CAPTIONS)
                .setContentId("").build();
    }

    /**
     * This method loops through the tracks and partitions them into a group of Text tracks and a
     * group of Audio tracks, and skips over the Video tracks.
     */
    private void partitionTracks() {
        List<MediaTrack> allTracks = mMediaInfo.getMediaTracks();
        mAudioTracks.clear();
        mTextTracks.clear();
        mTextTracks.add(buildNoneTrack());
        mSelectedTextPosition = 0;
        mSelectedAudioPosition = -1;
        if (allTracks != null) {
            int textPosition = 1; /* start from 1 since we have a NONE selection at the beginning */
            int audioPosition = 0;
            for (MediaTrack track : allTracks) {
                switch (track.getType()) {
                    case MediaTrack.TYPE_TEXT:
                        mTextTracks.add(track);
                        if (mActiveTracks != null) {
                            for (long mActiveTrack : mActiveTracks) {
                                if (mActiveTrack == track.getId()) {
                                    mSelectedTextPosition = textPosition;
                                }
                            }
                        }
                        textPosition++;
                        break;
                    case MediaTrack.TYPE_AUDIO:
                        mAudioTracks.add(track);
                        if (mActiveTracks != null) {
                            for (long mActiveTrack : mActiveTracks) {
                                if (mActiveTrack == track.getId()) {
                                    mSelectedAudioPosition = audioPosition;
                                }
                            }
                        }
                        audioPosition++;
                        break;
                }
            }
        }
    }


    @Override
    public void onSubSettingsClick() {
        SubtitleSettingsDialog dialog = new SubtitleSettingsDialog(getContext(), CastPlayerService.sCastPlayerService.getSubtitleManager());
        dialog.show();
        dismiss();
    }

    @Override
    public void onDelaySubClick() {
        final VideoDbInfo videoDbInfo = PlayerService.sPlayerService.getVideoInfo();
        VideoMetadata.SubtitleTrack track = Player.sPlayer.getVideoMetadata().getSubtitleTrack(videoDbInfo.subtitleTrack);
        if (track == null)
            return;
        boolean hasRatio = track.isExternal;
        SubtitleDelayPickerDialog dialog = new SubtitleDelayPickerDialog(getContext(), new SubtitleDelayPickerDialogInterface.OnDelayChangeListener() {
            @Override
            public void onDelayChange(SubtitleDelayPickerAbstract mSubtitleDelayPicker, int delay, int ratio) {
                boolean delayChanged = delay != videoDbInfo.subtitleDelay;
                boolean ratioChanged = ratio != videoDbInfo.subtitleRatio;
                videoDbInfo.subtitleDelay = delay;
                videoDbInfo.subtitleRatio = ratio;
                if (delayChanged || ratioChanged) {
                    CastPlayerService.sCastPlayerService.getSubtitleManager().clear();
                    Player.sPlayer.setSubtitleDelay(videoDbInfo.subtitleDelay);
                    Player.sPlayer.setSubtitleRatio(videoDbInfo.subtitleRatio);
                }
            }
        }, PlayerService.sPlayerService.getVideoInfo().subtitleDelay, PlayerService.sPlayerService.getVideoInfo().subtitleRatio, hasRatio);
        dialog.show();
        dismiss();
    }

    @Override
    public void onSoundSettingsClick() {
        AlertDialog.Builder adb = new AlertDialog.Builder(getContext());
        adb.setTitle(R.string.pref_audio_parameters_title);
        final ArrayList<RadioButton> rbs = new  ArrayList<RadioButton>();
        adb.setAdapter(new ArrayAdapter<View>(getContext(), R.layout.menu_item_layout) {
                           @Override
                           public View getView(final int position, View convertView, ViewGroup parent) {
                               if (position > 0) {
                                   Switch tb = new Switch(getContext());
                                   tb.setText(R.string.pref_audio_filt_title);
                                   tb.setPadding(20,20, 20, 20);
                                   tb.setChecked( PlayerService.sPlayerService.mAudioFilt>0);
                                   tb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                       @Override
                                       public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                           PlayerService.sPlayerService.setAudioFilt(isChecked ? 3 : 0);
                                       }
                                   });
                                   return tb;
                               }
                               else {
                                   Switch tb = new Switch(getContext());
                                   tb.setText(R.string.pref_audio_filt_night_mode);
                                   tb.setPadding(20,20, 20, 20);
                                   tb.setChecked(PlayerService.sPlayerService.mNightModeOn);
                                   tb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                       @Override
                                       public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                           PlayerService.sPlayerService.setNightMode(isChecked);
                                       }
                                   });
                                   return tb;
                               }
                           }
                           @Override
                           public int getCount() {
                               return 2;
                           }
                       }
                , new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        adb.setCancelable(true);
        adb.create().show();
        dismiss();
    }

    @Override
    public void onDelayAudioClick() {
        AudioDelayPickerDialog dialog;
        AudioDelayPickerDialogInterface.OnAudioDelayChangeListener onAudioDelayChangeListener = new AudioDelayPickerDialogInterface.OnAudioDelayChangeListener() {
            @Override
            public void onAudioDelayChange(AudioDelayPickerAbstract delayPicker, int delay) {
                PlayerService.sPlayerService.setAudioDelay(delay,false);
            }
        };
        if(PlayerService.sPlayerService!=null)
            dialog = new AudioDelayPickerDialog(getContext(),onAudioDelayChangeListener , PlayerService.sPlayerService.getAudioDelay());
        else
            dialog = new AudioDelayPickerDialog(getContext(),onAudioDelayChangeListener, PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(getString(R.string.save_delay_setting_pref_key), 0));
        dialog.show();
        dismiss();
    }

    @Override
    public void onTrackSelectionChanged() {
        List<MediaTrack> selectedTracks = new ArrayList<>();
        MediaTrack textTrack = mTextAdapter.getSelectedTrack();
        if (textTrack.getId() != TEXT_TRACK_NONE_ID) {
            selectedTracks.add(textTrack);
        }
        MediaTrack audioVideoTrack = mAudioVideoAdapter.getSelectedTrack();
        if (audioVideoTrack != null) {
            selectedTracks.add(audioVideoTrack);
        }
        mCastManager.notifyTracksSelectedListeners(selectedTracks);
    }
}

