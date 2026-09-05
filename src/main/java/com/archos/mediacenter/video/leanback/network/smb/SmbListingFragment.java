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

package com.archos.mediacenter.video.leanback.network.smb;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.utils.videodb.XmlDb;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.NonIndexedVideo;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingFragment;
import com.archos.mediacenter.video.leanback.network.NetworkListingFragment;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.VideoUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SmbListingFragment extends NetworkListingFragment {

    private static final Logger log = LoggerFactory.getLogger(SmbListingFragment.class);

    private boolean mCredentialsJustProvided = false;

    @Override
    protected  ListingFragment instantiateNewFragment() {
        return new SmbListingFragment();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // First orb is for credentials
        getTitleView().setOrb1IconResId(R.drawable.orb_cred);
        getTitleView().setOnOrb1ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askForCredentials();
            }
        });
        setConnectionDescription();
    }

    @Override
    public void onCredentialRequired(Exception e) {
        if (log.isDebugEnabled()) log.debug("onCredentialRequired: ask for credentials");
        if (mCredentialsJustProvided) {
            log.warn("onCredentialRequired: credentials just provided but authentication failed, breaking loop");
            mCredentialsJustProvided = false;
            if (getActivity() != null) {
                Toast.makeText(getActivity(), R.string.error_credentials, Toast.LENGTH_SHORT).show();
            }
            onListingFatalError(e, ListingEngine.ErrorEnum.ERROR_AUTHENTICATION);
            return;
        }
        askForCredentials();
    }

    @Override
    public void onListingUpdate(List<? extends MetaFile2> files) {
        mCredentialsJustProvided = false;
        super.onListingUpdate(files);
    }

    @Override
    protected void setListingEngineOptions(ListingEngine listingEngine) {
        listingEngine.setKeepHiddenFiles(true);
        if(!VideoPreferencesCommon.PreferenceHelper.shouldDisplayAllFiles(getActivity()))
            listingEngine.setFilter(VideoUtils.getVideoFilterMimeTypes(), new String[]{XmlDb.FILE_EXTENSION}); // display video files only but retrieve xml DB
    }

    @Override
    protected List<? extends MetaFile2> updateVideosMapAndFileList(List<? extends MetaFile2> mListedFiles, HashMap<String, Video> indexedVideosMap) {
        ArrayList<MetaFile2> newList = new ArrayList<>();
        HashMap<Uri, VideoDbInfo> resumes = new HashMap<>();
        for (MetaFile2 item : mListedFiles) {
            if (!item.getName().startsWith(".")&&(item.getExtension()==null||!item.getExtension().equals(XmlDb.FILE_EXTENSION))) //if not XML or hidden file
                newList.add(item);
            else if (item.isFile()&&item.getName().endsWith(XmlDb.FILE_NAME)) { //if is a resume DB
                // we check if we have a resume point
                if (item.isFile()) {
                    VideoDbInfo info = XmlDb.extractBasicVideoInfoFromXmlFileName(item.getUri());
                    if (info!=null && info.resume > 0 ) {
                        resumes.put(info.uri, info);
                    }
                }
            }
        }
        for (MetaFile2 item : newList) {
            VideoDbInfo info = null;
            if ((info = resumes.get(item.getUri())) != null) {
                Video video = indexedVideosMap.get(item.getUri().toString());
                if (video == null) {
                    video = new NonIndexedVideo(item.getStreamingUri(), item.getUri(), item.getName(), null);
                    indexedVideosMap.put(item.getUri().toString(), video);
                    video.setResumeMs(info.resume);
                    video.setDuration(info.duration<0?100:info.duration); //percent or complete duration
                } else {
                    if (video.getDurationMs() > 0) {
                        video.setResumeMs(info.duration<0?(int) ((float) info.resume / (float) 100 * (float) video.getDurationMs()):info.resume);
                    } else {
                        video.setResumeMs(info.resume);
                        video.setDuration(info.duration<0?100:info.duration); //percent or complete duration
                    }
                }
            }
        }
        return newList;
    }

    private void askForCredentials() {
        if (getParentFragmentManager().findFragmentByTag(SmbServerCredentialsDialog.class.getCanonicalName()) == null) {
            SmbServerCredentialsDialog dialog = new SmbServerCredentialsDialog();
            Bundle args = new Bundle();
            if (mUri != null) {
                NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mUri.toString());
                if (cred != null) {
                    args.putString(SmbServerCredentialsDialog.USERNAME, cred.getUsername());
                    args.putString(SmbServerCredentialsDialog.PASSWORD, cred.getPassword());
                }
                args.putParcelable(SmbServerCredentialsDialog.URI, mUri);
                dialog.setArguments(args);
            }
            dialog.setOnConnectClickListener(new SmbServerCredentialsDialog.onConnectClickListener() {
                @Override
                public void onConnectClick(String username, Uri path, String password, String domain) {
                    mUri = path;
                    mCredentialsJustProvided = true;
                    setConnectionDescription();
                    startListing(mUri);
                }
            });
            dialog.setOnCancelClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mCredentialsJustProvided = false;
                    onListingFatalError(null, ListingEngine.ErrorEnum.ERROR_AUTHENTICATION);
                }
            });
            dialog.show(getParentFragmentManager(), SmbServerCredentialsDialog.class.getCanonicalName());
        }
    }

    private void setConnectionDescription() {
        if (mUri != null) {
            String description = getString(R.string.network_guest);
            NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mUri.toString());
            if (cred != null) {
                String userName = cred.getUsername();
                if (userName != null && !userName.isEmpty()) {
                    description = userName;
                }
            }
            getTitleView().setOnOrb1Description(getString(R.string.network_connected_as, description));
        }
    }

}
