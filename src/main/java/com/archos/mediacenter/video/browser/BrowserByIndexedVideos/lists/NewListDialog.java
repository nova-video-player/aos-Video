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

package com.archos.mediacenter.video.browser.BrowserByIndexedVideos.lists;

import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;

/**
 * Created by alexandre on 22/05/17.
 */

public class NewListDialog extends DialogFragment {

    private View mView;

    @SuppressWarnings("deprecation") // getSerializable: API 33+ branch uses typed form; else branch suppressed
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        mView = LayoutInflater.from(getActivity()).inflate(R.layout.list_creator_layout, null);
        builder.setView(mView);
        builder.setTitle(R.string.list_title);
        builder.setPositiveButton(android.R.string.ok
                , new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                EditText text = (EditText)mView.findViewById(R.id.list_title);
                VideoStore.List.ListObj list = new VideoStore.List.ListObj(text.getText().toString(), -1, VideoStore.List.SyncStatus.STATUS_NOT_SYNC);
                Uri uri = getActivity().getContentResolver().insert(VideoStore.List.LIST_CONTENT_URI, list.toContentValues());
                Video video = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? getArguments().getSerializable(ListDialog.EXTRA_VIDEO, Video.class)
                        : (Video) getArguments().getSerializable(ListDialog.EXTRA_VIDEO);
                BaseTags metadata = video.getFullScraperTags(getActivity());
                boolean isEpisode = metadata instanceof EpisodeTags;
                VideoStore.VideoList.VideoItem videoItem  =
                        new VideoStore.VideoList.VideoItem(-1,!isEpisode?(int)metadata.getOnlineId():-1, isEpisode?(int)metadata.getOnlineId():-1, VideoStore.List.SyncStatus.STATUS_NOT_SYNC);

                getActivity().getContentResolver().insert(uri, videoItem.toContentValues());
                TraktService.sync(ArchosUtils.getGlobalContext(), TraktService.FLAG_SYNC_AUTO);

            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }
}
