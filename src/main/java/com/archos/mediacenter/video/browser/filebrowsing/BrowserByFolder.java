// Copyright 2017 Archos SA
// Copyright 2020 Courville Software
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


package com.archos.mediacenter.video.browser.filebrowsing;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.archos.filecorelibrary.FileExtendedInfo;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.filecoreextension.upnp2.ListingEngineFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpFile2;
import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.utils.videodb.XmlDb;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.BrowserByVideoObjects;
import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.video.browser.ThumbnailRequesterVideo;
import com.archos.mediacenter.video.browser.adapters.PresenterAdapterInterface;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.NonIndexedVideo;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.VideosInFolderLoader;
import com.archos.mediacenter.video.browser.presenter.CommonPresenter;
import com.archos.mediacenter.video.browser.presenter.Metafile2GridPresenter;
import com.archos.mediacenter.video.browser.presenter.Metafile2ListPresenter;
import com.archos.mediacenter.video.browser.presenter.VideoPresenter;
import com.archos.mediacenter.video.ui.NovaProgressDialog;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.VideoUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Observer;

abstract public class BrowserByFolder extends BrowserByVideoObjects implements
        Observer, LoaderManager.LoaderCallbacks<Cursor>, ListingEngine.Listener, VideoPresenter.ExtendedClickListener {

    private static final Logger log = LoggerFactory.getLogger(BrowserByFolder.class);

    private static final int DIALOG_LISTING = 4;

    public static final String CURRENT_DIRECTORY = "currentDirectory";
    public static final String TITLE = "title";
    protected static final String SHORTCUT_SELECTED = "shortcutSelected";
    protected static final String FILTER = "video||application/x-bittorrent";

    // Sort constants
    protected static final int MENU_ITEM_SORT = 0x2000;
    protected static final int MENU_ITEM_SORT_MASK = 0xF000;

    protected final static String SORT_BY_NAME_ASC = "name_asc";
    protected final static String SORT_BY_NAME_DESC = "name_desc";
    protected final static String SORT_BY_DATE_ASC = "date_asc";
    protected final static String SORT_BY_DATE_DESC = "date_desc";
    protected final static String SORT_BY_SIZE_ASC = "size_asc";
    protected final static String SORT_BY_SIZE_DESC = "size_desc";
    protected final static List<String> sortOrders = List.of(SORT_BY_NAME_ASC, SORT_BY_NAME_DESC, SORT_BY_DATE_ASC, SORT_BY_DATE_DESC, SORT_BY_SIZE_ASC, SORT_BY_SIZE_DESC);
    protected final static List<ListingEngine.SortOrder> sortOrdersListingEngine = List.of(
            ListingEngine.SortOrder.SORT_BY_NAME_ASC,
            ListingEngine.SortOrder.SORT_BY_NAME_DESC,
            ListingEngine.SortOrder.SORT_BY_DATE_ASC,
            ListingEngine.SortOrder.SORT_BY_DATE_DESC,
            ListingEngine.SortOrder.SORT_BY_SIZE_ASC,
            ListingEngine.SortOrder.SORT_BY_SIZE_DESC
    );

    static final public String DEFAULT_SORT = SORT_BY_NAME_ASC;
    static final String SORT_PARAM_KEY = BrowserByFolder.class.getSimpleName() + "_SORT";
    private String mSortOrder = DEFAULT_SORT;

    /**
     * Synchronization between the CursorLoader (thread) and the FileManagerCore
     * (thread). Both must be done to launch DirInfoTask.
     */
    private final static int READY_LISTING_DONE = 0x1;
    private final static int READY_DB_DONE = 0x10;
    public static final int RESULT_FILE_DELETED = 300;
    int mReady = 0;
    protected ListingEngine mListingEngine;

    protected final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // The video browser activity may have been paused since the request
            // for showing the dialog was sent (for instance when the device is beeing rotated)
            // => show the dialog only if the activity is still in the foreground 
            if (msg.what == DIALOG_LISTING && mIsActive && getParentFragmentManager() != null) {
                loading();
            }
        }
    };

    protected List<MetaFile2> mFileList;
    protected List<MetaFile2> mFullFileList;
    protected Cursor mCursor;
    private DialogListing mDialogListing;
    protected Uri mCurrentDirectory;
    protected boolean mShortcutSelected = false;
    private Menu mMenu;
    private boolean mIsActive = false;
    protected ListingAdapter mFilesAdapter;
    protected List<Object> mItemList = new ArrayList<>();
    private boolean mIsFirst = true;
    private String mTitle;
    protected int mFirstFileIndex;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Bundle b = bundle == null ? getArguments() : bundle;
        if (b != null) {
            mCurrentDirectory = b.getParcelable(CURRENT_DIRECTORY);
            mTitle = b.getString(TITLE, null);
            mShortcutSelected = b.getBoolean(SHORTCUT_SELECTED);
        }
        mSortOrder = mPreferences.getString(SORT_PARAM_KEY, DEFAULT_SORT);
        if (mCurrentDirectory == null)
            mCurrentDirectory = getDefaultDirectory();
        mFileList = new ArrayList();
        mFullFileList = new ArrayList<>();
        // close mCursor before initLoader
        if (mCursor != null && ! mCursor.isClosed()) {
            mCursor.close();
        }
        mCursor = null;
        LoaderManager.getInstance(this).initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        mIsActive = true;
        super.onResume();
        if (mFileList.isEmpty()) {
            listFiles(false);
        } else {
            bindAdapter();
            if (mMenu != null) // mMenu does not need to be populated here.
                hideSubMenu(mMenu);
            listFiles(true);
        }
    }

    @Override
    public void onActivityResult(int requesto, int result, Intent data){
        super.onActivityResult(requesto, result, data);
        if(result == RESULT_FILE_DELETED){
            if(mCurrentDirectory.equals(data.getData()))
                getActivity().onBackPressed();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(getActionBarTitle());
    }

    @Override
    public void onPause() {
        mIsActive = false;
        super.onPause();
    }

    @Override
    public void onStop() {
        if(mListingEngine!=null)
            mListingEngine.abort();
        // getActivity().unregisterReceiver(mReceiver);
        if (mDialogListing != null) {
            mDialogListing.dismissAllowingStateLoss();
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPreferences.edit()
                .putString(SORT_PARAM_KEY, mSortOrder)
                .apply();
        if(mListingEngine!=null)
            mListingEngine.abort();
        // close mCursor before destroyLoader
        if (mCursor != null && ! mCursor.isClosed()) {
            mCursor.close();
        }
        mCursor = null;
        LoaderManager.getInstance(this).destroyLoader(0);
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(CURRENT_DIRECTORY, mCurrentDirectory);
        state.putString(TITLE, mTitle);
        state.putBoolean(SHORTCUT_SELECTED, mShortcutSelected);
        state.putString(SORT_PARAM_KEY, mSortOrder);
    }

    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new VideosInFolderLoader(getContext(), getIndexableRootFolder()).getV4CursorLoader(true, false);
    }

    protected String getIndexableRootFolder() {
        return VideoUtils.getMediaLibCompatibleFilepathFromUri(mCurrentDirectory);
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (getActivity() == null) return;
        mCursor = cursor;
        updateAdapterIfReady();
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mMenu = menu;
        hideSubMenu(menu);
    }

    private void hideSubMenu(Menu menu) {
        // Check if there is at least one video file in the current folder
        boolean video = false;
        if (mItemList != null) {
            for (Object itemData : mItemList) {
                if (itemData instanceof Video) {
                    video = true;
                    break;
                }
            }
        }
        menu.setGroupVisible(MENU_SUBLOADER_GROUP, video);
    }

    @Override
    public File getFile(int position) {
        return null;
    }

    protected void listFiles(boolean discrete) {
        if(!discrete) {
            Message msg = mHandler.obtainMessage(DIALOG_LISTING);
            mHandler.sendMessageDelayed(msg, 500);
        }
        if(mListingEngine != null)
            mListingEngine.abort();
        try {
            mListingEngine = ListingEngineFactoryWithUpnp.getListingEngineForUrl(getActivity(), mCurrentDirectory);
            mListingEngine.setSortOrder(getSortOrder(mSortOrder));
            ArrayList<String> ext = new ArrayList<>(VideoUtils.getSubtitleExtensions());
            ext.add(XmlDb.FILE_EXTENSION);
            mListingEngine.setKeepHiddenFiles(true);
            if (!VideoPreferencesCommon.PreferenceHelper.shouldDisplayAllFiles(getActivity()))
                mListingEngine.setFilter(VideoUtils.getVideoFilterMimeTypes(), ext.toArray(new String[0])); // display video files only but retrieve xml DB + subs
            mListingEngine.setListener(this);
            mListingEngine.start();
        } catch (IllegalArgumentException e) {
            log.error("listFiles: caught IllegalArgumentException", e);
            // TODO MARC avoid endless spinning
            Toast.makeText(getActivity(), R.string.error_protocol_not_supported, Toast.LENGTH_SHORT).show();
            // call onListingFatalError
            if (mHandler.hasMessages(DIALOG_LISTING))
                mHandler.removeMessages(DIALOG_LISTING);
            displayFailPage();
            if (mListingEngine != null && mListingEngine.getListener() != null) {
                mListingEngine.getListener().onListingEnd();
            }
        }
    }

    @Override
    public void onListingStart() {
    }

    @Override
    public void onFolderRemoved(final Uri folder) {
        super.onFolderRemoved(folder);
        if(isAdded()) {
            getActivity().onBackPressed();
        }
    }

    @Override
    public void onListingUpdate(List<? extends MetaFile2> files) {
        if (mHandler.hasMessages(DIALOG_LISTING))
            mHandler.removeMessages(DIALOG_LISTING);

        if (mDialogListing != null) {
            mDialogListing.dismiss();
        }

        mFileList.clear();
        mFileList.addAll(files);
        //Do not updateAdapterIfReady if onLoadFinished not completed: make sure that the mCursor is null in this case
        if (mCursor != null) updateAdapterIfReady();

        mReady |= READY_LISTING_DONE;
        if(getActivity()==null)//too late
            return;
        bindAdapter();
    }

    @Override
    public void onListingEnd() {

    }

    @Override
    public void onListingTimeOut() {
        displayFailPage();
    }

    @Override
    public void onCredentialRequired(Exception e) {
        displayErrorAuthentification();
    }
    public void displayErrorAuthentification(){
        if (mHandler.hasMessages(DIALOG_LISTING))
            mHandler.removeMessages(DIALOG_LISTING);
        mArchosGridView.setVisibility(View.GONE);
        View emptyView = mRootView.findViewById(R.id.empty_view);
        if (emptyView instanceof ViewStub) {
            final ViewStub stub = (ViewStub) emptyView;
            emptyView = stub.inflate();
        }

        View loading = mRootView.findViewById(R.id.loading);
        if (loading != null)
            loading.setVisibility(View.GONE);

        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
            // Update the text of the empty view
            TextView emptyViewText = (TextView)emptyView.findViewById(R.id.empty_view_text);
            TextViewCompat.setTextAppearance(emptyViewText, android.R.style.TextAppearance_Large);
            emptyViewText.setText(R.string.error_credentials);
            // Check if a button is needed in the empty view
            Button emptyViewButton = (Button)emptyView.findViewById(R.id.empty_view_button);
            // Show the button and update its label
            emptyViewButton.setVisibility(View.VISIBLE);
            emptyViewButton.setText(getEmptyViewButtonLabel());
            emptyViewButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listFiles(false);
                }
            });
        }
    }
    @Override
    public void onListingFatalError(Exception e, ListingEngine.ErrorEnum errorCode) {
        if(errorCode == ListingEngine.ErrorEnum.ERROR_AUTHENTICATION || errorCode == ListingEngine.ErrorEnum.ERROR_NO_PERMISSION){
            displayErrorAuthentification();
        }
        else
            displayFailPage();
    }

    protected void refresh(){
        listFiles(false);
    }
    /**
     * After the first query or when cursor's content has changed, call this to
     * update data.
     */
    protected void updateVideoDB() {
    }

    // The user clicked on an item of the list
    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        if(mMultiplePositionEnabled) {
            if(mActionModeManager!=null)
                mActionModeManager.invalidateActionBar();
            return;
        }
        super.onItemClick(parent, v, position, id);
        if (mIsClickValid) {
            Object itemData = mBrowserAdapter.getItem(position);
            if(itemData instanceof MetaFile2) {
                if(((MetaFile2) itemData).isDirectory()) {
                    // Shortcut or Directory
                    enterDirectory((MetaFile2) itemData);
                }
                else{
                    PlayUtils.openAnyFile((MetaFile2) itemData, getActivity());
                }
            }
        }
    }

    @Override
    public FileExtendedInfo.FileType getFileType(int position) {
        // Must only be called for files/folders/shortcuts
        Object itemData = mItemList.get(position);
        if (itemData instanceof  MetaFile2 && ((MetaFile2)itemData).isDirectory()) {
            return FileExtendedInfo.FileType.Directory;
        }
        return FileExtendedInfo.FileType.File;
    }

    @Override
    public int getFileAndFolderSize() {
        return mItemList.size();
    }

    @Override
    public int getFirstFilePosition() {
        return mFirstFileIndex;
    }

    @Override
    public Uri getRealPathUriFromPosition(int position) {
        return Uri.parse(getFilePath(position));
    }
    @Override
    public String getFilePath(int position) {
        Object obj = mItemList.get(position);
        if(obj instanceof MetaFile2)
            return VideoUtils.getMediaLibCompatibleFilepathFromUri(((MetaFile2) obj).getUri());
        else if(obj instanceof Video)
            return super.getFilePath(position);
        return null;
    }

    @Override
    public Uri getUriFromPosition(int position) {
        return null;
    }

    @Override
    protected void setupThumbnail() {
        mThumbnailEngine.setThumbnailType(getThumbnailsType());
        mThumbnailRequester = new ThumbnailRequesterVideo(mThumbnailEngine, (ListingAdapter) mBrowserAdapter);
    }

    public static void setPresenters(Activity activity, CommonPresenter.ExtendedClickListener listener, PresenterAdapterInterface adapterInterface, int viewMode){
        BrowserByVideoObjects.setPresenters(activity, listener, adapterInterface, viewMode);
        CustomApplication application = (CustomApplication) activity.getApplication();
        if(viewMode==VideoUtils.VIEW_MODE_LIST) {
            adapterInterface.setPresenter(MetaFile2.class, new Metafile2ListPresenter(activity));
        }
        else if (viewMode == VideoUtils.VIEW_MODE_GRID_SHORT){
            adapterInterface.setPresenter(MetaFile2.class, new Metafile2GridPresenter(activity));
        }
        else if(viewMode==VideoUtils.VIEW_MODE_DETAILS){
            adapterInterface.setPresenter(MetaFile2.class, new Metafile2ListPresenter(activity));
        }
        else {
            adapterInterface.setPresenter(MetaFile2.class, new Metafile2GridPresenter(activity));
        }
    }

    @Override
    protected void setupAdapter(boolean createNewAdapter) {
        if (createNewAdapter || mBrowserAdapter == null) {
            mFilesAdapter = new ListingAdapter(getActivity().getApplicationContext(),
                    mItemList, mFullFileList) ;
            mBrowserAdapter = mFilesAdapter;
           setPresenters(getActivity(),this,mFilesAdapter, mViewMode);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        // Check if the current file requires a context menu
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            log.error("bad menuInfo", e);
            return;
        }
        // This can be null sometimes, don't crash...
        if (info == null) {
            log.error("bad menuInfo");
            return;
        }
        Object item = mFilesAdapter.getItem(info.position);
        if (item instanceof MetaFile2) {
            menu.setHeaderTitle(((MetaFile2) item).getName());
            if (((MetaFile2) item).canWrite())
                menu.add(0, R.string.delete, 0, R.string.delete);
            return;
        }
        super.onCreateContextMenu(menu,v, menuInfo);
    }

    public void onListingFileInfoUpdate(Uri uri, MetaFile2 metaFile2){
        MetaFile2 old = null;
        int indexFileList = -1;
        int i = 0;
        for(MetaFile2 metaFile : mFileList){
            if(metaFile.getUri().equals(metaFile2.getUri())){
                indexFileList = i;
                old = metaFile;
                break;
            }
            i++;
        }

        if(indexFileList == -1)
            return;
        mFileList.remove(indexFileList);
        mFileList.add(indexFileList, metaFile2);
        int indexInItemList = mItemList.indexOf(old);
        if(indexInItemList == -1)
            return;
        mItemList.remove(indexInItemList);
        mItemList.add(indexInItemList, metaFile2);
        mBrowserAdapter.notifyDataSetChanged();
    }

    protected void updateAdapterIfReady() {
        if (mCursor!=null && mFileList.size()>0) {
            mItemList.clear();
            VideoCursorMapper cursorMapper = new VideoCursorMapper();
            cursorMapper.bindColumns(mCursor);
            // Create a map of the indexed videos
            HashMap<String, Video> indexedVideosMap = new HashMap<>();

            mCursor.moveToFirst();
            while (!mCursor.isAfterLast()) {
                Video video = (Video)cursorMapper.bind(mCursor);
                String key = video.getFilePath();
                indexedVideosMap.put(key, video);
                mCursor.moveToNext();
            }
            // Must not close the cursor here, else it fails when recreating the fragment from backstack (i.e. when back from a sub-directory)

            int positionInAdapter = 0;
            mFirstFileIndex = -1;
            List<String>subList = new ArrayList<>();
            List<MetaFile2>newList = new ArrayList<>();
            HashMap<Uri, VideoDbInfo> resumes = new HashMap<>();
            for (MetaFile2 item : mFileList) {
                if (item.isDirectory()&&!item.getName().startsWith(".")) {
                    mItemList.add(item); // Add a regular folder
                    positionInAdapter++;
                    continue;
                }
                // not a directory case
                if (VideoUtils.getSubtitleExtensions().contains(item.getExtension())) {
                    subList.add(item.getNameWithoutExtension());
                    continue;
                }

                if (!item.getName().startsWith(".") && (item.getExtension() == null || !item.getExtension().equals(XmlDb.FILE_EXTENSION))) //if not XML or hidden file
                    newList.add(item);
                else if (item.isFile() && item.getName().endsWith(XmlDb.FILE_NAME)) { //if is a resume DB
                    // we check if we have a resume point
                    if (item.isFile()) {
                        VideoDbInfo info = XmlDb.extractBasicVideoInfoFromXmlFileName(item.getUri());
                        if (info != null && info.resume > 0) {
                            resumes.put(info.uri, info);
                        }
                    }
                }
            }

            for (MetaFile2 file : newList) {
                Object newObject = null;
                String key = VideoUtils.getMediaLibCompatibleFilepathFromUri(file.getUri());
                Video video = indexedVideosMap.get(key);
                if (video != null) {
                    video.setStreamingUri(file.getUri());
                    newObject = video; // Add an indexed video object
                }
                else {
                    String mimeType = file.getMimeType();
                    if (mimeType!=null) {
                        if (mimeType.startsWith("video/") ||mimeType.equals("application/x-bittorrent") ) {
                            Uri thumbnailUri = null;// Get upnp thumbnail if available
                            if (file instanceof UpnpFile2) {
                                thumbnailUri = ((UpnpFile2)file).getThumbnailUri();
                            }

                            video = new NonIndexedVideo(file.getStreamingUri(),file.getUri(), file.getName(), thumbnailUri); // Add a non-indexed video object
                            newObject = video;
                        }
                        else {
                            newObject = file; // Add a regular MetaFile2 object for now, maybe later we'll create a dedicated torrent object?
                        }
                    }
                    else {
                        // not adding file for which we have no mimetype
                    }
                }

                if (newObject!=null) {
                    mItemList.add(newObject);
                    if(mFirstFileIndex == -1)
                        mFirstFileIndex = positionInAdapter;
                    positionInAdapter++; // this increment must be done only when something is added or modified in the adapter (not when skipping a non-video file)

                }

                if(video!=null) {
                    VideoDbInfo info;
                    if ((info = resumes.get(file.getUri())) != null) {
                        if (video.getDurationMs() > 0) {
                            video.setRemoteResumeMs(info.duration < 0 ? (int) ((float) info.resume / (float) 100 * (float) video.getDurationMs()) : info.resume);
                        } else {
                            video.setRemoteResumeMs(info.resume);
                        }
                    }
                    if (!video.hasSubs()) {
                        for (String sub : subList) {

                            if (sub.startsWith(file.getNameWithoutExtension())) {
                                video.setHasSubs(true);
                                break;
                            }
                        }
                    }
                }
            }

            if(mIsFirst) {
                mIsFirst = false;
                bindAdapter();
            }
            else
                mBrowserAdapter.notifyDataSetChanged();

        }
    }

    protected boolean shouldForceVideoSelection() {
        return true;
    }

    protected void updateVideosMapAndFileList(List<MetaFile2> mListedFiles, HashMap<String, Video> indexedVideosMap) {
        ArrayList<MetaFile2> newList = new ArrayList<>();
        HashMap<Uri, VideoDbInfo> resumes = new HashMap<>();
        List<String> subList = new ArrayList<>();
        for (MetaFile2 item : mListedFiles) {
            if(VideoUtils.getSubtitleExtensions().contains(item.getExtension())){
                subList.add(item.getNameWithoutExtension());
                continue;
            }

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
            Video video = indexedVideosMap.get(VideoUtils.getMediaLibCompatibleFilepathFromUri(item.getUri()));
            if (video == null) {
                video = new NonIndexedVideo(item.getStreamingUri(), item.getUri(), item.getName(), null);
                //video.setHasSubs(true);
                indexedVideosMap.put(VideoUtils.getMediaLibCompatibleFilepathFromUri(item.getUri()),video);
            }
            if ((info = resumes.get(item.getUri())) != null) {
                if (video.getDurationMs() > 0) {
                    video.setResumeMs(info.duration<0?(int) ((float) info.resume / (float) 100 * (float) video.getDurationMs()):info.resume);
                } else {
                    video.setResumeMs(info.resume);
                    video.setDuration(info.duration<0?100:info.duration); //percent or complete duration

                }
            }
            if(!video.hasSubs()) {
                for (String sub : subList){
                    if(sub.startsWith(item.getNameWithoutExtension())) {
                        video.setHasSubs(true);
                        break;
                    }
                }
            }
        }
    }

    /**
     * @return the default directory when no one is specified in the intent.
     */
    abstract protected Uri getDefaultDirectory();

    @SuppressLint("ValidFragment") // XXX
    private class DialogListing extends DialogFragment {

        public DialogListing() {
            super();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            NovaProgressDialog npd = new NovaProgressDialog(getContext());
            npd.setTitle(mCurrentDirectory.getLastPathSegment());
            npd.setMessage(getString(R.string.loading));
            npd.setIcon(R.drawable.filetype_video_folder);
            npd.setIndeterminate(true);
            npd.setCancelable(true);
            npd.setCanceledOnTouchOutside(false);
            npd.show();
            return npd;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            if(mListingEngine!=null)
                mListingEngine.abort();
        }
    }

    protected void showToast(int textId) {
        Toast.makeText(getActivity().getApplicationContext(), textId, Toast.LENGTH_SHORT).show();
    }

    protected void showToast(String text) {
        Toast.makeText(getActivity().getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }

    protected void enterDirectory(MetaFile2 metaFile2) {
        Bundle args = new Bundle(2);
        args.putParcelable(CURRENT_DIRECTORY, metaFile2.getUri());
        args.putString(TITLE, metaFile2.getName());
        args.putBoolean(SHORTCUT_SELECTED, mShortcutSelected);
        Fragment f;
        try {
            f = getClass().getConstructor().newInstance();
            f.setArguments(args);
            BrowserCategory category = (BrowserCategory) getParentFragmentManager().findFragmentById(R.id.category);
            category.startContent(f);
        } catch (Exception e) {
            log.warn("enterDirectory: caught exception", e);
        }
    }
    @Override
    public boolean shouldDownloadRemoteSubtitle(int position){
        return false;
    }
    protected String getFirstSegment(String path) {
        // Remove any leading "/"
        String formattedPath;
        if (path.startsWith("/")) {
            formattedPath = path.substring(1);
        } else {
            formattedPath = path;
        }

        // Extract the part of the string before the first "/"
        int firstSlashPos = formattedPath.indexOf('/');
        if (firstSlashPos > 0) {
            return formattedPath.substring(0, firstSlashPos);
        }

        // Return the provided path if no "/" can be found
        return (path.length() > 0 ? path : "");
    }

    protected String getActionBarTitle() {
        if(mTitle!=null)
            return mTitle;
        if (mCurrentDirectory.equals(Uri.fromFile(Environment.getExternalStorageDirectory()))) {
            return  getResources().getString(R.string.root_storage);
        } else {
            return FileUtils.getName(mCurrentDirectory);
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty() && mSortModeSubmenu!=null) {
            // Add the "sort mode" item
            MenuItem sortMenuItem = menu.add(Browser.MENU_VIEW_MODE_GROUP, Browser.MENU_VIEW_MODE, Menu.NONE, R.string.sort_mode);
            sortMenuItem.setIcon(R.drawable.ic_menu_sort);
            sortMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            mSortModeSubmenu.attachMenuItem(sortMenuItem);

            mSortModeSubmenu.clear();
            mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_name_asc,MENU_ITEM_SORT+0);
            mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_name_desc,MENU_ITEM_SORT+1);
            mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_date_asc,MENU_ITEM_SORT+2);
            mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_date_desc,MENU_ITEM_SORT+3);
            mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_size_asc,MENU_ITEM_SORT+4);
            mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_size_desc,MENU_ITEM_SORT+5);
            // Init with the current value
            int initId = sortorder2itemid(mSortOrder);
            if (initId==-1) { // not found
                mSortModeSubmenu.selectSubmenuItem(0);
            }
            else {
                int position = mSortModeSubmenu.getPosition(initId);
                if (position<0) { // not found
                    position=0;
                }
                mSortModeSubmenu.selectSubmenuItem(position);
            }
        }
    }

    @Override
    public void onSubmenuItemSelected(ActionBarSubmenu submenu, int position, long itemId) {
        if (submenu==mSortModeSubmenu) {
            if ((itemId & MENU_ITEM_SORT_MASK)==MENU_ITEM_SORT) {
                mSortOrder = itemid2sortorder((int)itemId);
                log.debug("onSubmenuItemSelected: mSortOrder=" +mSortOrder + " setting listingEngine sortOrder");
                mListingEngine.setSortOrder(getSortOrder(mSortOrder));
                mPreferences.edit()
                        .putString(SORT_PARAM_KEY, mSortOrder)
                        .apply();
                // It's not enough to call notifyDataSetChanged() here to have the sort mode changed, must reset at Loader level.
                listFiles(false);
                LoaderManager.getInstance(this).restartLoader(0, null, this);
            }
        }
        else {
            super.onSubmenuItemSelected(submenu, position, itemId);
        }
    }

    private static String itemid2sortorder(int itemid) {
        String sortOrder = DEFAULT_SORT;
        itemid = itemid - MENU_ITEM_SORT;
        if (itemid >= 0 && itemid < sortOrders.size()) sortOrder = sortOrders.get(itemid);
        log.debug("itemid2sortorder: sortOrder="+sortOrder);
        return sortOrder;
    }

    /**
     * Returns -1 if given sortOrder can't be found in the menuid list
     * @param sortOrder
     * @return
     */
    private static int sortorder2itemid(String sortOrder) {
        return MENU_ITEM_SORT + sortOrders.indexOf(sortOrder);
    }

    protected static ListingEngine.SortOrder getSortOrder(String sortOrder) {
        final int index = sortOrders.indexOf(sortOrder);
        if (index <0) return ListingEngine.SortOrder.SORT_BY_NAME_ASC;
        else return sortOrdersListingEngine.get(index);
    }

}
