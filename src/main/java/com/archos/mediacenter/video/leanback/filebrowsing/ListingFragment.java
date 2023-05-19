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

package com.archos.mediacenter.video.leanback.filebrowsing;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;

import android.content.Intent;
import androidx.loader.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.core.app.ActivityOptionsCompat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.customizedleanback.app.MyVerticalGridFragment;
import com.archos.customizedleanback.widget.MyTitleView;
import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.upnp2.ListingEngineFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpFile2;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.NonIndexedVideo;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.VideosInFolderLoader;
import com.archos.mediacenter.video.leanback.DisplayMode;
import com.archos.mediacenter.video.leanback.details.VideoDetailsActivity;
import com.archos.mediacenter.video.leanback.details.VideoDetailsFragment;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.ListPresenter;
import com.archos.mediacenter.video.leanback.presenter.MetaFileListPresenter;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.presenter.VideoListPresenter;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.VideoUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * Created by vapillon on 17/04/15.
 */
public abstract class ListingFragment extends MyVerticalGridFragment implements ListingEngine.Listener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final Logger log = LoggerFactory.getLogger(ListingFragment.class);

    private static final String PREF_LISTING_DISPLAY_MODE = "PREF_LISTING_DISPLAY_MODE";

    public static final String ARG_URI = "URI";
    public static final String ARG_TITLE = "TITLE";
    public static final String ARG_IS_ROOT = "IS_ROOT";

    private DisplayMode mDisplayMode;
    private SharedPreferences mPrefs;
    private boolean mIsRoot;
    private Overlay mOverlay;

    public static final String SORT_PARAM_KEY = ListingFragment.class.getSimpleName() + "_SORT";
    private int mSortOrderItem;

    // Sort constants
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
    private String mSortOrder = DEFAULT_SORT;
    protected static CharSequence[] mSortOrderEntries;

    private ArrayObjectAdapter mFilesAdapter;
    private ListingEngine mListingEngine;
    private View mEmptyView;
    private View mProgressView;
    private TextView mErrorMessage;
    private TextView mErrorDetails;
    protected TextView mLongConnectionMessage;
    protected Button mActionButton;

    protected Uri mUri;

    private boolean mFileListReady;
    private List<? extends MetaFile2> mListedFiles;

    private boolean mDbQueryReady;
    private Cursor mCursor;

    private BackgroundManager bgMngr = null;

    /**
     * flag used to make the difference between first-creation and back-from-backstack
     */
    private boolean mRefreshOnNextResume;

    /**
     * Create a new fragment to browse into a folder
     */
    protected abstract ListingFragment instantiateNewFragment();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        log.debug("onCreate " + savedInstanceState);
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mDisplayMode = readDisplayModePref(mPrefs);

        mSortOrder = mPrefs.getString(SORT_PARAM_KEY, DEFAULT_SORT);
        mSortOrderItem = sortorder2itemid(mSortOrder);
        log.debug("onCreate: mSortOrder={} mSortOrderItem={}", mSortOrder, mSortOrderItem);

        updateBackground();

        setTitle(getArguments().getString(ARG_TITLE));
        mUri = getArguments().getParcelable(ARG_URI);
        mIsRoot = getArguments().getBoolean(ARG_IS_ROOT, false);

        setupEventListeners();

        // NOTE: onCreate is called only when the fragment is first created, not when it is back from backstack
        initGridOrList();

        mRefreshOnNextResume = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        log.debug("onCreateView " + this + " " + savedInstanceState);
        View v = super.onCreateView(inflater, container, savedInstanceState);

        mSortOrderEntries = new CharSequence[]{
                getResources().getString(R.string.sort_by_name_asc),
                getResources().getString(R.string.sort_by_name_desc),
                getResources().getString(R.string.sort_by_date_asc),
                getResources().getString(R.string.sort_by_date_desc),
                getResources().getString(R.string.sort_by_size_asc),
                getResources().getString(R.string.sort_by_size_desc),
        };

        // CAUTION: using a non public viewgroup ID here!
        // May be broken if leanback team changes it!
        FrameLayout browseFrame = (FrameLayout) v.findViewById(R.id.browse_grid_dock);
        if (browseFrame != null) {
            LayoutInflater.from(container.getContext()).inflate(R.layout.leanback_emptyview_and_progressview, browseFrame, true);
            mEmptyView = browseFrame.findViewById(R.id.message);
            mEmptyView.setVisibility(View.GONE);
            mProgressView = browseFrame.findViewById(R.id.progress);
            mProgressView.setVisibility(View.GONE);
            mErrorMessage = (TextView) browseFrame.findViewById(R.id.error);
            mErrorMessage.setVisibility(View.GONE);
            mErrorDetails = (TextView) browseFrame.findViewById(R.id.error_details);
            mErrorDetails.setVisibility(View.GONE);
            mActionButton = (Button) browseFrame.findViewById(R.id.action_button);
            mLongConnectionMessage = (TextView) browseFrame.findViewById(R.id.long_connection_message);
            mLongConnectionMessage.setVisibility(View.GONE);

        } else {
            log.error("no more R.id.browse_grid_dock FrameLayout in the VerticalGridFragment!");
            // caution mEmptyView and mProgressView will be null!
        }

        // Update the display mode if needed (i.e. if it has been changed in a child fragment)
        // (Note: onCreateView is called when back from backstack, hence we can update here)
        DisplayMode newDisplayMode = readDisplayModePref(mPrefs);
        if (newDisplayMode != mDisplayMode) {
            mDisplayMode = newDisplayMode;
            updateGridOrList();
        }

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        log.debug("onViewCreated");

        super.onViewCreated(view, savedInstanceState);

        mOverlay = new Overlay(this);

        setSecondOrbIcon();
        setSecondOrbAction();

        // Set orb color
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.lightblueA200));

        // set null listener to hide the first orb
        getTitleView().setOnOrb1ClickedListener(null);

        // set null listener to hide the third orb
        getTitleView().setOnOrb3ClickedListener(null);
        getTitleView().setOnOrb4ClickedListener(null);

        // Set third orb action for sort
        getTitleView().setOnOrb4Description(getString(R.string.sort_mode));
        getTitleView().setOrb4IconResId(R.drawable.orb_sort);
        log.debug("onViewCreated: mSortOrder={} mSortOrderItem={}", mSortOrder, mSortOrderItem);

        getTitleView().setOnOrb4ClickedListener(view1 -> new AlertDialog.Builder(getActivity())
                .setSingleChoiceItems(mSortOrderEntries, mSortOrderItem, (dialog, which) -> {
                    log.debug("onViewCreated:onClick mSortOrderItem {} -> {}", mSortOrderItem, which);
                    if (mSortOrderItem != which) {
                        mSortOrderItem = which;
                        mSortOrder = itemid2sortorder(mSortOrderItem);
                        // Save the sort mode
                        mPrefs.edit().putString(SORT_PARAM_KEY, mSortOrder).apply();
                        Bundle args = new Bundle();
                        args.putString("sort", mSortOrder);
                        initGridOrList(); // reinit all mFilesAdapter too
                        startListing(mUri);
                        //LoaderManager.getInstance(ListingFragment.this).restartLoader(0, args, ListingFragment.this);
                    }
                    dialog.dismiss();
                })
                .create().show());
    }

    @Override
    public void onDestroyView() {
        log.debug("onDestroyView");
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        log.debug("onDestroy");
        mPrefs.edit()
                .putString(SORT_PARAM_KEY, mSortOrder)
                .apply();
        if (mListingEngine != null) {
            mListingEngine.setListener(null);
            mListingEngine.abort();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        log.debug("onResume");
        super.onResume();
        mOverlay.resume();
        if (mRefreshOnNextResume) {
            startListing(mUri);
            mRefreshOnNextResume = false;
        }
        updateBackground();
        getTitleView().resetLastOrb();
    }

    @Override
    public void onPause() {
        log.debug("onPause");
        super.onPause();
        mOverlay.pause();
    }

    private void setSecondOrbIcon() {
        final MyTitleView titleView = getTitleView();

        switch(mDisplayMode) {
            case GRID:
                titleView.setOrb2IconResId(R.drawable.orb_list);
                titleView.setOnOrb2Description(getString(R.string.switch_to_list));
                break;
            case LIST:
                titleView.setOrb2IconResId(R.drawable.orb_grid);
                titleView.setOnOrb2Description(getString(R.string.switch_to_grid));
                break;
            default:
                throw new IllegalArgumentException("Invalid Display Mode! "+mDisplayMode);
        }
    }

    private void setSecondOrbAction() {
        getTitleView().setOnOrb2ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (mDisplayMode) {
                    case GRID:
                        mDisplayMode = DisplayMode.LIST;
                        break;
                    case LIST:
                        mDisplayMode = DisplayMode.GRID;
                        break;
                }
                // Save the new setting
                mPrefs.edit().putInt(PREF_LISTING_DISPLAY_MODE, mDisplayMode.ordinal()).commit();
                // Update the display mode
                updateGridOrList();
                // update the orb icon from list/grid to grid/list
                setSecondOrbIcon();
            }
        });
    }

    /**
     * Called at first onCreate only
     */
    private void initGridOrList() {
        mFilesAdapter = new ArrayObjectAdapter(buildFilePresenter());
        setAdapter(mFilesAdapter);

        setGridPresenter(buildGridPresenter());
    }

    /**
     * Called in case the display mode is changed
     * the updateGridViewHolder() method has been added to MyVerticalGridFragment to handle this case...
     */
    private void updateGridOrList() {

        // update the file presenter(s)
        mFilesAdapter.setPresenterSelector(buildFilePresenter());

        // Updating the grid presenter is not enough because a lot of things are done internally in VerticalGridFragment
        // at first setup... Added a new updateGridViewHolder() method to MyVerticalGridFragment to handle that
        setGridPresenter(buildGridPresenter());
        updateGridViewHolder();
    }

    private VerticalGridPresenter buildGridPresenter() {
        final int zoom;
        final boolean focusDimmer;
        final int numberOfColumns;
        final boolean shadowEnabled;

        switch (mDisplayMode) {
            case GRID:
                zoom = FocusHighlight.ZOOM_FACTOR_LARGE;
                focusDimmer = false;
                numberOfColumns = 6;
                shadowEnabled = true;
                break;
            case LIST:
                zoom = FocusHighlight.ZOOM_FACTOR_SMALL;
                focusDimmer = false;
                numberOfColumns = 1;
                shadowEnabled = true;
                break;
            default:
                throw new IllegalArgumentException("Invalid Display Mode! "+mDisplayMode);
        }

        VerticalGridPresenter vgp = new VerticalGridPresenter(zoom, focusDimmer);
        vgp.setNumberOfColumns(numberOfColumns);
        vgp.setShadowEnabled(shadowEnabled);
        return vgp;
    }

    private ClassPresenterSelector buildFilePresenter() {
        ClassPresenterSelector filePresenterSelector = new ClassPresenterSelector();
        switch (mDisplayMode) {
            case GRID:
                filePresenterSelector.addClassPresenter(MetaFile2.class, new PosterImageCardPresenter(getActivity()));
                filePresenterSelector.addClassPresenter(Video.class, new PosterImageCardPresenter(getActivity()));
                break;
            case LIST:
                filePresenterSelector.addClassPresenter(MetaFile2.class, new MetaFileListPresenter());
                filePresenterSelector.addClassPresenter(Video.class, new VideoListPresenter(true));
                break;
            default:
                throw new IllegalArgumentException("Invalid Display Mode! "+mDisplayMode);
        }
        return filePresenterSelector;
    }

    private static DisplayMode readDisplayModePref(SharedPreferences prefs) {
        int displayModeIndex = prefs.getInt(PREF_LISTING_DISPLAY_MODE, -1);
        if (displayModeIndex<0) {
            return DisplayMode.GRID; // default
        } else {
            return DisplayMode.values()[displayModeIndex];
        }
    }

    protected void startListing(Uri uri) {
        log.debug("startListing " + uri);
        // abort previous engine (in theory not needed)
        if (mListingEngine!=null) {
            mListingEngine.abort();
        }
        mUri = uri;

        // 1 - Get the list of files
        mFileListReady = false;
        mListingEngine = ListingEngineFactoryWithUpnp.getListingEngineForUrl(getActivity(), mUri);
        mListingEngine.setSortOrder(getSortOrder(mSortOrder));
        mListingEngine.setListener(this);
        setListingEngineOptions(mListingEngine);
        mListingEngine.setListingTimeOut(getListingTimeout()); // 15 seconds timeout
        mListingEngine.start();

        // 2 - Get DB data for the indexed videos that may be in this folder
        mDbQueryReady = false;
        LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    /**
     * this can be used when a fragment needs to set specific options
     * @param listingEngine
     */
    protected void setListingEngineOptions(ListingEngine listingEngine){
        if(!VideoPreferencesCommon.PreferenceHelper.shouldDisplayAllFiles(getActivity()))
            mListingEngine.setFilter(VideoUtils.getVideoFilterMimeTypes(), null); // display video files only
    }
    protected int getListingTimeout(){ //different timeout for ftp
        return 30000;
    }
    /**
     * @return true if the list is empty
     */
    protected boolean isEmpty() {
        return (mListedFiles==null||mListedFiles.isEmpty());
    }

    protected void setupEventListeners() {
        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof MetaFile2) {
                    MetaFile2 file = (MetaFile2) item;
                    if (file.isDirectory()) {
                        openDirectory(file);
                    } else {
                        String mimeType = MimeUtils.guessMimeTypeFromExtension(file.getExtension());
                        PlayUtils.openAnyFile(file, getActivity());

                    }
                } else if (item instanceof Video) {
                    openDetailsActivity((Video) item, itemViewHolder);
                } else {
                    throw new IllegalArgumentException("Click on an unexpected item type " + item);
                }
            }
        });
    }

    private void openDirectory(MetaFile2 directory) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, directory.getUri());
        args.putString(ARG_TITLE, directory.getName());
        ListingFragment newFragment = instantiateNewFragment();
        newFragment.setArguments(args);

        getParentFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.fragment_container, newFragment,"fragment_" + getParentFragmentManager().getBackStackEntryCount())
                .addToBackStack(directory.getUri().toString())
                .commit();
    }

    /**
     * Use this method to filter (again) listedfiles and to update video map :
     * for example hide DB xml files and change resume with remotes resumes
     * @param mListedFiles
     * @param indexedVideosMap
     */
    protected void updateVideosMapAndFileList(List<? extends MetaFile2> mListedFiles, HashMap<String, Video> indexedVideosMap) {
        // used by smblistingfragmnet
    }

    /**
     * Fill the adapter once both the file listing and the DB query are done
     * (both are launched in parallel)
     * This method also handle updates from the DB: file list is not updated, but we update the items with (new) data from the DB
     */
    private void updateAdapterIfReady() {
        log.debug("updateAdapterIfReady: mFileListReady={}, mDbQueryReady={}", mFileListReady, mDbQueryReady);
        if (mFileListReady && mDbQueryReady) {
            VideoCursorMapper cursorMapper = new VideoCursorMapper();
            cursorMapper.publicBindColumns(mCursor);

            // Create a map of the indexed videos
            HashMap<String, Video> indexedVideosMap = new HashMap<>();

            mCursor.moveToFirst();
            while (!mCursor.isAfterLast()) {
                Video video = (Video)cursorMapper.publicBind(mCursor);
                String key = video.getFilePath();
                indexedVideosMap.put(key, video);
                mCursor.moveToNext();
            }
            // Must not close the cursor here, else it fails when recreating the fragment from backstack (i.e. when back from a sub-directory)

            updateVideosMapAndFileList(mListedFiles, indexedVideosMap); // I'm sorry, but this is specially made for smb to update resume with remotes ones;

            int positionInAdapter = 0;
            for (MetaFile2 file : mListedFiles) {
                // Check if the file is already in the adapter and need just to be replaced
                Object existingObjectInAdapter = null;
                if (mFilesAdapter.size() > positionInAdapter) {
                    existingObjectInAdapter = mFilesAdapter.get(positionInAdapter);
                }
                boolean doReplace = areTheseTheSameFile(existingObjectInAdapter, file);
                log.debug("updateAdapterIfReady: processing {}, existingObjectInAdapter={}", file.getName(), doReplace);

                if (file.isDirectory()){
                    if (!doReplace) {
                        mFilesAdapter.add(file); // Add a regular folder
                    } else {
                        // a directory can not be "updated", hence nothing to do in the replace case
                    }
                    positionInAdapter++;
                }
                else {
                    // not a directory case
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
                            if (mimeType.startsWith("video/")||mimeType.equals("application/x-bittorrent")) {
                                Uri thumbnailUri = null;// Get upnp thumbnail if available
                                if (file instanceof UpnpFile2) {
                                    thumbnailUri = ((UpnpFile2)file).getThumbnailUri();
                                }

                                newObject = new NonIndexedVideo(file.getStreamingUri(),file.getUri(), file.getName(), thumbnailUri); // Add a non-indexed video object
                            }
                            else  {
                                newObject = file; // Add a regular MetaFile2 object for now, maybe later we'll create a dedicated torrent object?
                            }

                        }
                        else {
                            // not adding file for which we have no mimetype
                        }
                    }
                    if (newObject!=null) {
                        if (doReplace) {
                            log.debug("updateAdapterIfReady: replace " + positionInAdapter);
                            mFilesAdapter.replace(positionInAdapter, newObject);
                        } else {
                            log.debug("updateAdapterIfReady: remove {} and add {}", positionInAdapter, ((Video)newObject).getName());
                            mFilesAdapter.removeItems(positionInAdapter,1);
                            mFilesAdapter.add(newObject);
                        }
                        positionInAdapter++; // this increment must be done only when something is added or modified in the adapter (not when skipping a non-video file)
                    }
                }
            }
            //remove items
            if(mFilesAdapter.size()>mListedFiles.size()){
                log.debug("updateAdapterIfReady: mFilesAdapter.size()={}>mListedFiles.size()={}, remove above", mFilesAdapter.size(), mListedFiles.size());
                mFilesAdapter.removeItems(mListedFiles.size(), mFilesAdapter.size()-mListedFiles.size());
            }

        }
    }

    protected void updateResumes(List<? extends MetaFile2> mListedFiles) {
        //nothing to do most of the time
    }

    private boolean areTheseTheSameFile(Object obj, MetaFile2 file) {
        if (obj==null) {
            return false;
        }
        else if (obj instanceof Video) {
            Video video = (Video)obj; // works for both Video and NonIndexedVideo
            String metafilePath = VideoUtils.getMediaLibCompatibleFilepathFromUri(file.getUri());
            return metafilePath.equals(video.getFilePath());
        }
        else if (obj instanceof MetaFile2) {
            MetaFile2 f = (MetaFile2)obj;
            return f.getUri().equals(file.getUri());
        }
        else {
            throw new IllegalArgumentException("areTheseTheSameFile: should not have to handle this class! "+obj);
            //return false;
        }
    }

    @Override
    public void onListingStart() {
        log.debug("onListingStart:");
        // Delay the loading view to have it not show when the loading is quick
        mProgressView.setAlpha(0);
        mProgressView.setVisibility(View.VISIBLE);
        mProgressView.animate().alpha(1).setDuration(400).setStartDelay(200);
        // hide error in case there was one displayed
        mErrorMessage.setVisibility(View.GONE);
        mErrorDetails.setVisibility(View.GONE);
    }

    @Override
    public void onListingUpdate(List<? extends MetaFile2> files) {
        log.debug("onListingUpdate: mFileListReady->true");
        mListedFiles = files;
        mFileListReady = true;
        updateAdapterIfReady();
    }

    @Override
    public void onListingEnd() {
        log.debug("onListingEnd:");
        mProgressView.setVisibility(View.INVISIBLE);
        if (isEmpty() && (mErrorMessage.getVisibility()!=View.VISIBLE)) { // do not show empty view when there is an error displayed
            mEmptyView.setVisibility(View.VISIBLE);
        }
        else {
            mEmptyView.setVisibility(View.GONE);
        }
    }

    public void onListingFileInfoUpdate(Uri uri, MetaFile2 metaFile2){

    }

    @Override
    public void onListingTimeOut() {
        mProgressView.setVisibility(View.INVISIBLE);
        mErrorMessage.setText(R.string.error_time_out);
        mErrorMessage.setVisibility(View.VISIBLE);
        mErrorDetails.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);
        // Avoid getting some callbacks after the time out
        mListingEngine.setListener(null);
    }

    @Override
    public void onCredentialRequired(Exception e) {
        Toast.makeText(getActivity(), "TODO: Credential Required", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onListingFatalError(Exception e, ListingEngine.ErrorEnum errorCode) {
        mErrorMessage.setText(ListingEngine.getErrorStringResId(errorCode));
        mErrorMessage.setVisibility(View.VISIBLE);
        mActionButton.setVisibility(View.GONE);
        String exceptionMessage = (e!=null) ? e.getMessage() : null;
        if (exceptionMessage!=null && !exceptionMessage.isEmpty()) {
            mErrorDetails.setText(exceptionMessage);
            mErrorDetails.setVisibility(View.VISIBLE);
        }
    }

    // -----------------------------------------------------
    // implements LoaderManager.LoaderCallbacks<Cursor>
    // -----------------------------------------------------

    @Override
    public  Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // TODO For SMB we need to implement the BUCKET_ID stuff BrowserBySMB (do we?)
        return new VideosInFolderLoader(getActivity(), VideoUtils.getMediaLibCompatibleFilepathFromUri(mUri));
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor c) {
        log.debug("onLoadFinished: mDbQueryReady->true");
        mCursor = c;
        mDbQueryReady = true;
        updateAdapterIfReady();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }

    // -----------------------------------------------------

    private void openDetailsActivity(Video video, Presenter.ViewHolder itemViewHolder) {
        Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
        intent.putExtra(VideoDetailsFragment.EXTRA_VIDEO, video);
        intent.putExtra(VideoDetailsFragment.EXTRA_FORCE_VIDEO_SELECTION, true);
        View sourceView = null;
        if (itemViewHolder.view instanceof ImageCardView) {
            sourceView = ((ImageCardView) itemViewHolder.view).getMainImageView();
        } else if (itemViewHolder instanceof ListPresenter.ListViewHolder){
            sourceView = ((ListPresenter.ListViewHolder)itemViewHolder).getImageView();
        }

        Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                getActivity(),
                sourceView,
                VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();

        getActivity().startActivityForResult(intent, ListingActivity.REQUEST_INFO_ACTIVITY, bundle);
    }


    public void onFileDelete(Uri file) {

        if(file.toString().endsWith("/")&&!mUri.toString().endsWith("/")&&file.toString().equals(mUri.toString()+"/")|| mUri.equals(file)) { //if current listed uri
            if (isAdded()) getActivity().onBackPressed();
        }
        else{ //if parent uri
            Uri parent = FileUtils.getParentUrl(file);
            if(parent.toString().endsWith("/")&&!mUri.toString().endsWith("/")&&parent.toString().equals(mUri.toString()+"/") || mUri.equals(parent)){
                // we need to refresh
                if(isAdded())
                    startListing(mUri);
                else
                    mRefreshOnNextResume = true;
            }
        }
    }

    private void updateBackground() {
        bgMngr = BackgroundManager.getInstance(getActivity());
        if(!bgMngr.isAttached())
            bgMngr.attach(getActivity().getWindow());

        if (PrivateMode.isActive()) {
            bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.private_mode));
            bgMngr.setDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.private_background));
        } else {
            bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.leanback_background));
            bgMngr.setDrawable(new ColorDrawable(ContextCompat.getColor(getActivity(), R.color.leanback_background)));
        }
    }

    private static String itemid2sortorder(int itemid) {
        String sortOrder = DEFAULT_SORT;
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
        return sortOrders.indexOf(sortOrder);
    }

    protected static ListingEngine.SortOrder getSortOrder(String sortOrder) {
        final int index = sortOrders.indexOf(sortOrder);
        if (index <0) return ListingEngine.SortOrder.SORT_BY_NAME_ASC;
        else return sortOrdersListingEngine.get(index);
    }
}
