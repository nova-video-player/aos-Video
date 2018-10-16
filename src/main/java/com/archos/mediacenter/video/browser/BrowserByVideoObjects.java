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


package com.archos.mediacenter.video.browser;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.archos.environment.ArchosIntents;
import com.archos.environment.ArchosSettings;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.utils.videodb.XmlDb;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.lists.ListDialog;
import com.archos.mediacenter.video.browser.adapters.AdapterByVideoObjectsInterface;
import com.archos.mediacenter.video.browser.adapters.PresenterAdapterInterface;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.NonIndexedVideo;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.filebrowsing.network.BrowserByNetwork;
import com.archos.mediacenter.video.browser.presenter.CommonPresenter;
import com.archos.mediacenter.video.browser.presenter.ScrapedVideoDetailedPresenter;
import com.archos.mediacenter.video.browser.presenter.VideoGridPresenter;
import com.archos.mediacenter.video.browser.presenter.VideoGridShortPresenter;
import com.archos.mediacenter.video.browser.presenter.VideoListPresenter;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.utils.ExternalPlayerResultListener;
import com.archos.mediacenter.video.utils.ExternalPlayerWithResultStarter;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity;
import com.archos.mediacenter.video.utils.SubtitlesWizardActivity;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;

import java.util.ArrayList;
import java.util.List;

import httpimage.HttpImageManager;

public abstract class BrowserByVideoObjects extends Browser implements CommonPresenter.ExtendedClickListener, ExternalPlayerWithResultStarter {

    private static final int PLAY_ACTIVITY_REQUEST_CODE = 780;
    protected AdapterByVideoObjectsInterface mAdapterByVideoObjects;

    @Override
    protected void postBindAdapter() {
        super.postBindAdapter();

        mAdapterByVideoObjects = (AdapterByVideoObjectsInterface) mBrowserAdapter;
    }

    public Uri getRealPathUriFromPosition(int position){
        return Uri.parse(getFilePath(position));
    }

    @Override
    public String getFilePath(int pos){
        return mAdapterByVideoObjects.getVideoItem(pos).getFilePath();
    }



    public void displayInfo(int position){
        Video video = mAdapterByVideoObjects.getVideoItem(position);
        int firstFilePosition = getFirstFilePosition();
        ArrayList<Uri> urlList = new ArrayList<>();
        int j =0;
        int finalPos = 0;
        for (int i=position- VideoInfoActivity.MAX_VIDEO/2<firstFilePosition?firstFilePosition:position-VideoInfoActivity.MAX_VIDEO/2;i<getFileSize()+firstFilePosition;i++, j++) {
            urlList.add(j,getRealPathUriFromPosition(i));

            if(i == position)
                finalPos = j;
            if(j>VideoInfoActivity.MAX_VIDEO)
                break;
        }
        VideoInfoActivity.startInstance(getActivity(), this,video,finalPos,urlList,-1, shouldForceVideoSelection(), getPlaylistId());
    }

    protected long getPlaylistId(){
        return -1;
    }

    protected boolean shouldForceVideoSelection() {
        return false;
    }

    // This will display the menu with enabled actions for the file.
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterContextMenuInfo info;
        try {
            info = (AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }
        // This can be null sometimes, don't crash...
        if (info == null) {
            Log.e(TAG, "bad menuInfo");
            return;
        }

        final int position = info.position;
        final Video video = mAdapterByVideoObjects.getVideoItem(position);


        if (!isItemClickable(position)) {
            return;
        }
        if(video instanceof Episode){
            menu.setHeaderTitle(((Episode) video).getShowName()+": "+video.getName());
        }
        else
             menu.setHeaderTitle(video.getName());
        String entryPath = video.getFilePath();

        final int resumePosition = video.getResumeMs();
        final boolean resume = resumePosition > 0;
        final boolean delete = !FileUtils.isSlowRemote(Uri.parse(entryPath));
        final boolean markAsTrakt = Trakt.isTraktV2Enabled(mContext, mPreferences);
        final boolean isNetwork = !FileUtils.isLocal(video.getFileUri());
        menu.add(0, R.string.play_from_beginning, 0, R.string.play_selection);
        if (resume && resumePosition != PlayerActivity.LAST_POSITION_END) {
            menu.findItem(R.string.play_from_beginning).setTitle(R.string.play_from_beginning);
            String resumeString = mContext.getString(R.string.resume) + " (" + MediaUtils.formatTime(resumePosition) + ")";
            menu.add(0, R.string.resume, 0, resumeString);
        }

        if (delete)
            menu.add(0, R.string.delete, 0, R.string.delete);
        if (resume)
            menu.add(0, R.string.delete_resume, 0, R.string.delete_resume);

        menu.add(0, R.string.info, 0, R.string.info);
        if(!(video instanceof NonIndexedVideo)) {
            if (markAsTrakt) {
                if (!video.isWatched())
                    menu.add(0, R.string.mark_as_watched, 0, R.string.mark_as_watched);
                else
                    menu.add(0, R.string.mark_as_not_watched, 0, R.string.mark_as_not_watched);
            } else {
                if (resumePosition != PlayerActivity.LAST_POSITION_END)
                    menu.add(0, R.string.mark_as_watched, 0, R.string.mark_as_watched);
                else
                    menu.add(0, R.string.mark_as_not_watched, 0, R.string.mark_as_not_watched);
            }
        }
        final int f_position = position;
        final Activity activity = getActivity();
        // Subtitles wizard
        if(!isNetwork)
            menu.add(R.string.get_subtitles_on_drive).setOnMenuItemClickListener(
                    new OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.setClass(mContext, SubtitlesWizardActivity.class);
                            intent.setData(video.getFileUri());
                            activity.startActivity(intent);
                            return true;
                        }
                    });


        // Subloader
        menu.add(0, R.string.get_subtitles_online, 0, R.string.get_subtitles_online);

        if(video.hasScraperData()){
            menu.add(0, R.string.add_to_list, 0, R.string.add_to_list);

        }

        // Propose to remove from DB the files that are indexed
        if (video.getId()>0) {
            menu.add(0, R.string.video_browser_unindex_file, 0, R.string.video_browser_unindex_file);
        }
        else
            menu.add(0, R.string.video_browser_index_file, 0, R.string.video_browser_index_file);
        if(isNetwork){
            menu.add(0, R.string.copy_on_device, 0, R.string.copy_on_device);

        }
    }

    public void startVideo(int video, int resume) {

    }
    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        super.onItemClick(parent, v, position, id);
        if (mIsClickValid) {
            Object itemData = mBrowserAdapter.getItem(position);
            if (itemData instanceof Video) {
                // File
                displayInfo(position);
                //startVideo((Video) itemData, PlayerActivity.RESUME_FROM_LAST_POS);
            }
        }
    }

    public static void setPresenters(Activity activity, CommonPresenter.ExtendedClickListener listener, PresenterAdapterInterface adapterInterface, int viewMode){
        CustomApplication application = (CustomApplication) activity.getApplication();
        HttpImageManager imageManager = application.getHttpImageManager();
        if(viewMode== VideoUtils.VIEW_MODE_LIST) {
            adapterInterface.setPresenter(Video.class, new VideoListPresenter(activity, listener,imageManager));
        }
        else if (viewMode == VideoUtils.VIEW_MODE_GRID_SHORT){
            adapterInterface.setPresenter(Video.class, new VideoGridShortPresenter(activity, listener, imageManager));

        }
        else if(viewMode==VideoUtils.VIEW_MODE_DETAILS){
            adapterInterface.setPresenter(NonIndexedVideo.class, new VideoListPresenter(activity, listener,imageManager));
            adapterInterface.setPresenter(Video.class, new VideoListPresenter(activity, listener,imageManager));
            adapterInterface.setPresenter(Episode.class, new ScrapedVideoDetailedPresenter(activity, listener,imageManager));
            adapterInterface.setPresenter(Movie.class, new ScrapedVideoDetailedPresenter(activity, listener,imageManager));
        }
        else {
            adapterInterface.setPresenter(Video.class, new VideoGridPresenter(activity, listener,imageManager));
        }
    }

    public void startVideo(Video video, int resume) {
        PlayUtils.startVideo(getActivity(),
                video,
                resume,
                true, -1, this, -1);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {


        super.onCreateOptionsMenu(menu, inflater);
        if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty()) {
            // Add the "load subtitles" item
            menu.add(MENU_SUBLOADER_GROUP, MENU_SUBLOADER_ALL_FOLDER, Menu.NONE, R.string.menu_subloader_allfolder).setIcon(R.drawable.ic_menu_subtitles).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // NOTE: ignore the MENU_VIEW_MODE item which is
        // already handled internally in ActionBarSubmenu

        if (item.getItemId() == MENU_SUBLOADER_ALL_FOLDER) {
            ArrayList<String> videoNoSubs = new ArrayList<>();
            ArrayList<String> videoPaths = new ArrayList<>();

            Video video;
            List<Video> videos = new ArrayList<>();
            for (int i = 0; i<mBrowserAdapter.getCount(); i++){
                video = mAdapterByVideoObjects.getVideoItem(i);
                if(video!=null) {
                    videos.add(video);
                    if(video.hasSubs())
                        videoNoSubs.add(video.getFilePath());
                    videoPaths.add(video.getFilePath());
                }
            }

            getMissingSubtitles(false, videoNoSubs, videoPaths);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        boolean ret = true;
        int index = item.getItemId();
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        Video video = mAdapterByVideoObjects.getVideoItem(info.position);
        switch (index) {

            case R.string.play_from_beginning:
                // play action
                startVideo(video, PlayerActivity.RESUME_NO);
                break;

            case R.string.resume:
                // resume action
                startVideo(video, PlayerActivity.RESUME_FROM_LAST_POS);
                break;

            case R.string.info:
                displayInfo(info.position);
                break;

            case R.string.video_browser_index_file:
                VideoStore.requestIndexing(video.getFileUri(), getActivity());
                break;

            case R.string.delete_resume:
                updateDbXml(info.position, UpdateDbXmlType.RESUME, -1);
                if(Trakt.isTraktV2Enabled(getActivity(), mPreferences)){
                    new TraktService.Client(mContext, null, false).watchingStop(video.getId(), 0);
                }
                break;

            case R.string.delete:
                // delete action
                // Forbid deleting in DemoMode
                if (ArchosSettings.isDemoModeActive(getActivity())) {
                    getActivity().startService(
                            new Intent(ArchosIntents.ACTION_DEMO_MODE_FEATURE_DISABLED));
                } else {
                    List<Uri> toDelete = new ArrayList<>();
                    toDelete.add(getRealPathUriFromPosition(info.position));
                    // We need the position for BrowserByFolder.
                    mDeletedPosition = info.position;
                    showConfirmDeleteDialog(false, toDelete);
                }
                break;
            case R.string.mark_as_watched:
                markAsRead(info.position, true, mPreferences.getBoolean(BrowserByNetwork.KEY_NETWORK_BOOKMARKS, true));
                break;

            case R.string.mark_as_not_watched:
                markAsNotRead(info.position, true, mPreferences.getBoolean(BrowserByNetwork.KEY_NETWORK_BOOKMARKS, true));
                break;

            case R.string.get_subtitles_online:
                Intent subIntent = new Intent(Intent.ACTION_MAIN);
                subIntent.setClass(mContext, SubtitlesDownloaderActivity.class);
                subIntent.putExtra(SubtitlesDownloaderActivity.FILE_URL,video.getFilePath());
                getActivity().startActivity(subIntent);
                break;

            case R.string.video_browser_unindex_file:
                updateDbXml(info.position, UpdateDbXmlType.HIDE, 1);

                break;

            case R.string.copy_on_device:
                List<Uri> toCopy = new ArrayList<>();
                toCopy.add(video.getFileUri());
                startDownloadingVideo(toCopy);

                break;
            case R.string.add_to_list:
                Bundle bundle = new Bundle();
                bundle.putSerializable(ListDialog.EXTRA_VIDEO, video);
                ListDialog dialog = new ListDialog();
                dialog.setArguments(bundle);
                dialog.show(getActivity().getFragmentManager(), "list_dialog");
                break;
            default:
                ret = super.onContextItemSelected(item);
                Log.e(TAG, "Unexpected default case! " + index);
        }

        return ret;
    }

    protected void syncTrakt(final int position) {
        Video video = mAdapterByVideoObjects.getVideoItem(position);
        int flags = TraktService.FLAG_SYNC_TO_TRAKT_WATCHED|TraktService.FLAG_SYNC_NOW;

        if (video instanceof Episode)
            flags |= TraktService.FLAG_SYNC_SHOWS;
        else if(video instanceof Movie)
            flags |= TraktService.FLAG_SYNC_MOVIES;

        new TraktService.Client(mContext, null, false).sync(flags);
    }


    @Override
    protected boolean updateDbXml(int position, UpdateDbXmlType type, int value) {
        boolean dbUpdated = false;
        Object item = mBrowserAdapter.getItem(position);
        long id = -1;
        if(item instanceof Video)
            id = ((Video)item).getId();
        if ( id != -1) {
            String whereR = VideoStore.Video.VideoColumns._ID + " = "
                    + (int) id;
            final ContentValues cvR = new ContentValues(1);
            String col;

            switch (type) {
                case HIDE:
                    col = VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER;
                    break;
                case BOOKMARK:
                    col = VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK;
                    break;
                case RESUME:
                    col = VideoStore.Video.VideoColumns.BOOKMARK;
                    break;
                case TRAKT_RESUME:
                    col = VideoStore.Video.VideoColumns.ARCHOS_TRAKT_RESUME;
                    break;
                case TRAKT_SEEN:
                    col = VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN;
                    break;
                default:
                    return dbUpdated;
            }
            cvR.put(col, value);
            getActivity().getContentResolver().update(
                    VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cvR, whereR, null);
            dbUpdated = true;
            VideoDbInfo info  = VideoDbInfo.fromId(getActivity().getContentResolver(),id );
            XmlDb xmlDb = XmlDb.getInstance();
            xmlDb.writeXmlRemote(info);
        }
        return dbUpdated;
    }

    @Override
    public Uri getUriFromPosition(int position) {
        return ((AdapterByVideoObjectsInterface)mBrowserAdapter).getVideoItem(position).getDbUri();
    }


    public void onExtendedClick(View image,Object v, int positionInAdapter){
        displayInfo(positionInAdapter);
    }


    @Override
    public void startActivityWithResultListener(Intent intent) {
        startActivityForResult(intent, PLAY_ACTIVITY_REQUEST_CODE);
    }

    @Override
    public void  onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == PLAY_ACTIVITY_REQUEST_CODE){
            ExternalPlayerResultListener.getInstance().onActivityResult(requestCode,resultCode,data);
        }
        else super.onActivityResult(requestCode,resultCode,data);
    }
}
