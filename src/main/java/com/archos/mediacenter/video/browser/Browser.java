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


import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.filecorelibrary.MetaFile.FileType;
import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.utils.ActionBarSubmenu.ActionBarSubmenuListener;
import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.utils.ThumbnailRequest;
import com.archos.mediacenter.utils.ThumbnailRequester;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.autoscraper.AutoScraperActivity;
import com.archos.mediacenter.video.browser.dialogs.DeleteDialog;
import com.archos.mediacenter.video.browser.dialogs.DialogRetrieveSubtitles;
import com.archos.mediacenter.video.browser.dialogs.Paste;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.browser.tools.MultipleSelectionManager;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;
import com.archos.mediacenter.video.utils.ExternalPlayerResultListener;
import com.archos.mediacenter.video.utils.ExternalPlayerWithResultStarter;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity;
import com.archos.mediacenter.video.utils.SubtitlesWizardActivity;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.ImportState;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public abstract class Browser extends Fragment implements AbsListView.OnScrollListener,
        View.OnTouchListener,
        AdapterView.OnItemClickListener,
        Observer,
        ThumbnailEngine.Listener,
        ActionBarSubmenuListener, OnItemLongClickListener, Delete.DeleteListener, ExternalPlayerWithResultStarter {


    private static final boolean DBG = false;

    // Options menu items
    protected static final int MENU_VIEW_MODE_GROUP = 2;
    protected final static int MENU_SUBLOADER_GROUP = 3;
    protected static final int MENU_HIDE_WATCHED_GROUP = 4;
    protected static final int MENU_VIEW_MODE_LIST = 11;
    protected static final int MENU_VIEW_MODE_GRID = 12;
    protected static final int MENU_VIEW_MODE_DETAILS = 13;
    protected static final int MENU_VIEW_MODE = 14;
    protected static final int MENU_VIEW_HIDE_SEEN = 15;

    protected final static int MENU_SUBLOADER_ALL_FOLDER = 21;

    private final static int SUBMENU_ITEM_LIST_INDEX = 0;
    private final static int SUBMENU_ITEM_GRID_INDEX = 1;
    private final static int SUBMENU_ITEM_DETAILS_INDEX = 2;

    static final protected String EMPTY_STRING = "";
    static final private String FIRST_VISIBLE_POSITION = "firstVisiblePosition";
    static final private String LAST_POSITION = "lastPosition";
    static final protected String RESUME = "resume";
    static final private String SELECTED_POSITION = "selectedPosition";
    protected static final String TAG = "Browser";
    static final private String TIME_HOUR = "%kh%M'";
    static final private String TIME_MINUTE = "%M'%S''";
    static final private String TIME_SECOND = "%S''";
    protected static final String COPY_NAME = "copy_name";
    protected static final String COPY_LENGTH = "copy_length";
    protected static final String COPY_DIALOG = "copy_dialog";
    private static final String LIST_STATE_KEY = "list_state_key";
    private static final int PLAY_ACTIVITY_REQUEST_CODE = 880;
    protected boolean mDownloadSubs = false;
    protected Paste mPasteDialog;
    protected AlertDialog cancelDialog;
    protected String mCopyName;
    protected long mCopyLength = 0;
    protected int mCopyDialogID = -1;
    protected boolean mHideWatched;
    protected boolean mHideOption = false;
    protected int LOADING_FINISHED = 2;
    protected final Handler mHandler = new Handler();

    protected int mDeletedPosition;
    protected int mFirstVisiblePosition;
    protected int mSelectedPosition;
    protected int mViewMode;
    protected AbsListView  mArchosGridView;
    protected BaseAdapter mBrowserAdapter;
    private boolean mCommonDefaultInvalidate = true;
    protected Context mContext;
    private static AlertDialog mDialogDelete;
    private static DeleteDialog mDialogDeleting;
    private DialogForceDlSubtitles mDialogForceDlSubtitles;
    protected DialogRetrieveSubtitles mDialogRetrieveSubtitles;
    protected SharedPreferences mPreferences;
    protected ThumbnailEngineVideo mThumbnailEngine;
    protected ThumbnailRequester mThumbnailRequester;
    protected int mTouchX;
    protected int mTouchY;
    protected boolean mIsClickValid;
    protected View mRootView;
    protected ActionBarSubmenu mDisplayModeSubmenu;
    protected ActionBarSubmenu mSortModeSubmenu;
    private View mMenuAnchor;
    protected int mScroll =0;
    protected int mOffset=0;
    static final String CURRENT_SCROLL = "currentscroll";
    private Parcelable mListState;


    /**
     * Subclasses must create a new BrowserAdapter and according to the adapter
     * class, create a new ThumbnailRequester.
     */
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mSelectedPosition=0;
        mContext = getActivity().getApplicationContext();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        mThumbnailEngine = ThumbnailEngineVideo.getInstance(mContext);
        mThumbnailEngine.setThumbnailSize(
                getResources().getDimensionPixelSize(R.dimen.video_details_poster_width),
                getResources().getDimensionPixelSize(R.dimen.video_details_poster_height));
        Bundle posBundle = bundle!=null?bundle:getArguments();

        if (posBundle != null) {
            mFirstVisiblePosition = posBundle.getInt(FIRST_VISIBLE_POSITION);
            mSelectedPosition = posBundle.getInt(SELECTED_POSITION);
            mScroll = posBundle.getInt(CURRENT_SCROLL);
            mListState= posBundle.getParcelable(LIST_STATE_KEY);
        }
        if (bundle != null) {
            mCopyName = bundle.getString(COPY_NAME);
            mCopyLength = bundle.getLong(COPY_LENGTH);
            mCopyDialogID = bundle.getInt(COPY_DIALOG);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        mThumbnailEngine.setListener(this, mHandler);
        // Check if we need some thumbnails
        if (mThumbnailRequester != null)
            mThumbnailRequester.refresh(mArchosGridView);
        super.onResume();
    }

    @Override
    public void onPause() {
        //Posted in mArchosGridView to retrieve these values when the GridView is rendered.
        //risk of null values otherwise
        mArchosGridView.post(new Runnable() {
            @Override
            public void run() {
                setPosition();
            }
        });
        mListState = mArchosGridView.onSaveInstanceState();
        // If view mode has changed, save it for the next time.
        int viewMode = mPreferences.getInt(getClass().getName(), -1);
        if (mViewMode!= VideoUtils.VIEW_MODE_GRID_SHORT && (viewMode == -1 || viewMode != mViewMode)) {
            Editor ed = mPreferences.edit();
            ed.putInt(getClass().getName(), mViewMode);
            ed.commit();
        }

        if (mDialogDeleting != null)
            mDialogDeleting.dismiss();

        if (mDialogForceDlSubtitles != null)
            mDialogForceDlSubtitles.dismiss();

        if (mDialogRetrieveSubtitles != null)
            mDialogRetrieveSubtitles.dismiss();

        if (mPasteDialog != null)
            mPasteDialog.dismiss();
        mThumbnailEngine.cancelPendingRequestsForThisListener(this);
        mThumbnailEngine.setListener(null, null);

        super.onPause();
    }

    public void onStart(){
        super.onStart();
    }

    public void onStop(){
        super.onStop();
        mCommonDefaultInvalidate = true;
    }
    @Override
    public void onDestroy() {
        mThumbnailEngine.cancelPendingRequestsForThisListener(this);

        if (mArchosGridView != null)
            unregisterForContextMenu(mArchosGridView);
        if(mActionModeManager!=null) {
            mActionModeManager.destroyActionBar();
        }
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (mArchosGridView != null) {
            state.putInt(FIRST_VISIBLE_POSITION, mArchosGridView.getFirstVisiblePosition());
            state.putInt(SELECTED_POSITION, mArchosGridView.getSelectedItemPosition());
            View v = mArchosGridView.getChildAt(mSelectedPosition-mFirstVisiblePosition);
            mScroll = (v == null) ? 0 : v.getTop();
            state.putInt(CURRENT_SCROLL, mScroll);
            state.putParcelable(LIST_STATE_KEY, mListState);
        }
        state.putString(COPY_NAME, mCopyName);
        state.putLong(COPY_LENGTH, mCopyLength);
        state.putInt(COPY_DIALOG, mCopyDialogID);
        if (mDialogForceDlSubtitles != null)
            mDialogForceDlSubtitles.dismiss();
    }

    /**
     * May be overriden by child classes to have the ActionBar in NAVIGATION_MODE_LIST, for example
     * @return
     */
    protected int getActionBarNavigationMode() {
        return ActionBar.NAVIGATION_MODE_STANDARD;
    }




    /*      request.getListPosition() return position in adapter.
           meaning that with new headergridview, the first item would be 0+ headercount*columnscount
           this is the value we need to get real itemView. However, adapter needs position without header

     */
    public void onThumbnailReady(ThumbnailRequest request, ThumbnailEngine.Result result) {
        int requestListPosition = request.getListPosition();
        int adapterPosition = request.getListPosition();
        if(mArchosGridView instanceof HeaderGridView){
            requestListPosition += ((HeaderGridView) mArchosGridView).getOffset();
        }
        else if(mArchosGridView instanceof ListView){
            requestListPosition += ((ListView) mArchosGridView).getHeaderViewsCount()   ;
        }
        final int firstVisible = mArchosGridView.getFirstVisiblePosition();
        final int lastVisible = mArchosGridView.getLastVisiblePosition();

        // Check item is visible and make sure that the request is still
        // matching the content of the list (it may have been changed since the
        // request was sent).
        if (firstVisible <= requestListPosition && requestListPosition <= lastVisible
                && mThumbnailRequester.isRequestStillValid(request)) {
            // Get the view at requested position.
            View itemView = mArchosGridView.getChildAt(requestListPosition - firstVisible);
            if (itemView != null) {
                // As itemView isn't null, getView won't call newView so parent
                // can be null, it doesn't matter.
                mBrowserAdapter.getView(adapterPosition, itemView, null);
            }
        }
    }

    public void onAllRequestsDone() {
        // The thumbnail requester is handling that
        if (mThumbnailRequester != null)
            mThumbnailRequester.onAllRequestsDone();
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                         int totalItemCount) {


        // Thumbnail engine must get the OnScroll updates from the list view
        if (mThumbnailRequester != null)
            mThumbnailRequester.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {

        // Thumbnail engine must get the OnScroll updates from the list view
        if(mThumbnailRequester != null)
            mThumbnailRequester.onScrollStateChanged(view, scrollState);
    }

    /**
     * According the view mode, get the common object for the adapter.
     */
    private void initList(final AbsListView list){
        list.setOnItemClickListener(this);
        list.setOnItemLongClickListener(this);
        // register for context menu.
        list.setOnCreateContextMenuListener(this);
        list.setOnScrollListener(this);
        list.setOnTouchListener(this);
        list.setChoiceMode(AbsListView.CHOICE_MODE_NONE);

        // Setup key listener to open extender with right key or L1 or R1 or "i"
        list.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // All is done on action down because it also moves the focus, hence we can't wait for action up

                 
                 /*
                  * Work around to handle issues on focus for android 4.1
                  * 
                  * Sometimes it is impossible to down with the pad.
                  * what we know :
                  * before going to the onkey method, list should have scrolled (except when and issue occured)
                  * so, if we are going down, the last visible item has to be focused item +1 (focus is done after the function, when  return false)
                  * if last visible item == selected item even if this isn't the last item of the list, then the list won't scroll anymore.
                  * So we scroll it manually.
                  * 
                  */
                if (event.getAction() == KeyEvent.ACTION_DOWN && mArchosGridView instanceof ListView) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && mArchosGridView.getFirstVisiblePosition() == mArchosGridView.getSelectedItemPosition() && mArchosGridView.getSelectedItemPosition() > 0) {

                        mArchosGridView.setSelection(mArchosGridView.getSelectedItemPosition() - 1);
                        return true;


                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && mArchosGridView.getLastVisiblePosition() == mArchosGridView.getSelectedItemPosition()
                            && mArchosGridView.getSelectedItemPosition() < mArchosGridView.getCount()) {
                        View v2 = mArchosGridView.getChildAt(mArchosGridView.getSelectedItemPosition() - mArchosGridView.getFirstVisiblePosition());
                        int scrollTo = (v2 == null) ? 0 : v2.getTop();
                        //we want the selected item to be at the end of the list
                        if (mArchosGridView instanceof ListView)
                            ((ListView) mArchosGridView).setSelectionFromTop(mArchosGridView.getSelectedItemPosition() + 1,
                                    scrollTo);
                        return true;


                    }

                }
                if (event.getAction() != KeyEvent.ACTION_DOWN)
                    return false;
                if ((keyCode == KeyEvent.KEYCODE_BUTTON_L1) || (keyCode == KeyEvent.KEYCODE_BUTTON_R1) ||   // Nice for LUDO (even if an hidden feature)
                        (keyCode == KeyEvent.KEYCODE_I) ||                                                // Nice for any keyboard, LUDO included
                        ((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && (list instanceof GridView && (((GridView) list).getNumColumns() < 2) || list instanceof ListView)))// Right arrow is only used in list mode, can't be in grid mode
                {
                    View selectedView = list.getSelectedView();
                    if (selectedView != null) {
                        // Check that there is a visible "expander" in the selected list item
                        View expandView = selectedView.findViewById(R.id.expanded);
                        if ((expandView != null) && (expandView.getVisibility() == View.VISIBLE)) {
                            if (getFileType(list.getSelectedItemPosition()) == FileType.SmbDir)
                                return false;
                            expandView.requestFocus(); //test
                            displayInfo(list.getSelectedItemPosition());
                            return true;
                        }
                    }
                }
                return false;
            }
        });

    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mRootView = inflater.inflate(R.layout.browser_content_video, container, false);
        if(mViewMode== VideoUtils.VIEW_MODE_GRID){
            mArchosGridView = (AbsListView) mRootView.findViewById(R.id.archos_grid_view);
            mRootView.findViewById(R.id.archos_list_view).setVisibility(View.GONE);
        }
        else
        {
            mArchosGridView = (AbsListView) mRootView.findViewById(R.id.archos_list_view);
            mRootView.findViewById(R.id.archos_grid_view).setVisibility(View.GONE);
        }
        initList((AbsListView) mRootView.findViewById(R.id.archos_list_view));
        initList((AbsListView)mRootView.findViewById(R.id.archos_grid_view));
        // Try to restore the last view mode selected by the user for this
        // category or use the default one.
        int viewMode = mPreferences.getInt(getClass().getName(), getDefaultViewMode());
        setViewMode(viewMode!=VideoUtils.VIEW_MODE_GRID_SHORT?viewMode:VideoUtils.VIEW_MODE_GRID);
        setViewMode(viewMode);

        mMenuAnchor = mRootView.findViewById(R.id.menu_anchor);

        mDisplayModeSubmenu = new ActionBarSubmenu(mContext, inflater, mMenuAnchor);
        mDisplayModeSubmenu.setListener(this);

        mSortModeSubmenu = new ActionBarSubmenu(mContext, inflater, mMenuAnchor);
        mSortModeSubmenu.setListener(this);
        return mRootView;
    }

    /**
     * Return the default view mode. Override it when needed.
     */
    public int getDefaultViewMode() {
        return VideoUtils.VIEW_MODE_LIST;
    }

    protected void setViewMode(int mode) {
        Resources res = getResources();
        if (mode != mViewMode)
            mCommonDefaultInvalidate = true;
        mViewMode = mode;
        int verticalSpacing;
        int stretchMode;

        switch (mode) {
            case VideoUtils.VIEW_MODE_LIST:
            case VideoUtils.VIEW_MODE_DETAILS:
            default:
                mArchosGridView = (AbsListView) mRootView.findViewById(R.id.archos_list_view);
                mRootView.findViewById(R.id.archos_grid_view).setVisibility(View.GONE);
                mArchosGridView.setVisibility(View.VISIBLE);
                if(mArchosGridView instanceof GridView)
                    ((GridView)mArchosGridView).setNumColumns(1);
                verticalSpacing = res.getDimensionPixelSize(R.dimen.content_list_vertical_spacing_between_items);
                stretchMode = GridView.STRETCH_COLUMN_WIDTH;
                break;
            case VideoUtils.VIEW_MODE_GRID_SHORT:
            case VideoUtils.VIEW_MODE_GRID:

                mArchosGridView = (AbsListView) mRootView.findViewById(R.id.archos_grid_view);
                mRootView.findViewById(R.id.archos_list_view).setVisibility(View.GONE);
                mArchosGridView.setVisibility(View.VISIBLE);
                if(mArchosGridView instanceof GridView)
                    ((GridView)mArchosGridView).setNumColumns(GridView.AUTO_FIT);
                verticalSpacing = res.getDimensionPixelSize(R.dimen.content_grid_vertical_spacing_between_items);
                // setHorizontalSpacing() doesn't allow to have well-centered column. Having larger
                // columns and making sure the items are centered in it makes it.
                if(mArchosGridView instanceof GridView&&mode==VideoUtils.VIEW_MODE_GRID)
                    ((GridView)mArchosGridView).setColumnWidth(res.getDimensionPixelSize(R.dimen.video_grid_column_width) +
                            res.getDimensionPixelSize(R.dimen.content_grid_horizontal_minimal_spacing_between_items));
                else if(mArchosGridView instanceof GridView&&mode==VideoUtils.VIEW_MODE_GRID_SHORT)
                    ((GridView)mArchosGridView).setColumnWidth(res.getDimensionPixelSize(R.dimen.video_info_grid_column_width));
                stretchMode = GridView.STRETCH_SPACING_UNIFORM;
                break;
        }

        if(mArchosGridView instanceof GridView){
            ((GridView)mArchosGridView).setVerticalSpacing(verticalSpacing);
            ((GridView)mArchosGridView).setStretchMode(stretchMode);
        }

        // View has changed => be sure to request every thumbnail again.
        if (mThumbnailRequester != null) {
            mThumbnailRequester.reset();
        }
    }

    public int getThumbnailsType() {
        return ThumbnailEngineVideo.TYPE_FILE;
    }

    protected abstract void setupAdapter(boolean createNewAdapter);

    protected abstract void setupThumbnail();

    public void notifyDataSetChanged() {
        //setPosition();
        if (mBrowserAdapter != null)
            mBrowserAdapter.notifyDataSetChanged();

    }

    /**
     * Create a new adapter according the browser mode and link the thumbnail
     * requester to this adapter. This method must call postBindAdapter after
     * setting the adapter.
     */
    public final void bindAdapter() {
        boolean newAdapter = mBrowserAdapter == null;
        if (mCommonDefaultInvalidate) {
            newAdapter = true;
            mCommonDefaultInvalidate = false;
        }
        if (DBG) Log.d(TAG, "bindAdapter: " + newAdapter);
        setupAdapter(newAdapter);
        if (mArchosGridView.getAdapter() != mBrowserAdapter)
            mArchosGridView.setAdapter(mBrowserAdapter);
        if (newAdapter) {
            setupThumbnail();
        } else {
            notifyDataSetChanged();
        }
        mArchosGridView.clearChoices();
        postBindAdapter();
    }

    public void loading(){
        // Hide content, show message
        if(mArchosGridView!=null && mRootView!=null){
            mArchosGridView.setVisibility(View.GONE);
            View emptyView = mRootView.findViewById(R.id.empty_view);
            if (emptyView instanceof ViewStub) {
                final ViewStub stub = (ViewStub) emptyView;
                emptyView = stub.inflate();
            }
            if (emptyView != null) {
                emptyView.setVisibility(View.VISIBLE);
                // Update the text of the empty view
                TextView emptyViewText = (TextView)emptyView.findViewById(R.id.empty_view_text);
                emptyViewText.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
                emptyViewText.setText(R.string.loading);
                // Check if a button is needed in the empty view
                Button emptyViewButton = (Button)emptyView.findViewById(R.id.empty_view_button);
                if (emptyViewButton != null)
                    emptyViewButton.setVisibility(View.GONE);
                View loading = mRootView.findViewById(R.id.loading);
                if (loading != null){
                    loading.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    protected void displayFailPage(){
        // Hide content, show message
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
            emptyViewText.setTextAppearance(getActivity(), android.R.style.TextAppearance_Large);
            emptyViewText.setText(R.string.network_timeout);
            // Check if a button is needed in the empty view
            Button emptyViewButton = (Button)emptyView.findViewById(R.id.empty_view_button);
            // Show the button and update its label
            emptyViewButton.setVisibility(View.VISIBLE);
            emptyViewButton.setText(getEmptyViewButtonLabel());
            emptyViewButton.setOnClickListener(mEmptyViewButtonClickListener);
        }
    }

    /**
     * This method is called after setting an adapter to the view. It will
     * display the empty message when there is no item or try to restore the
     * previous position.
     */
    protected void postBindAdapter() {
        // NOTE: This hide/show show/hide stuff should be handled by the
        // framework.
        // One just have to tell the ListView which is the emtyView using
        // setEmptyView()

        if (mBrowserAdapter.isEmpty()) {
            // Hide content, show message
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
                // Update the text of the empty view
                TextView emptyViewText = (TextView)emptyView.findViewById(R.id.empty_view_text);
                emptyViewText.setTextAppearance(getActivity(), android.R.style.TextAppearance_Large);
                emptyViewText.setText(getEmptyMessage());
                // Check if a button is needed in the empty view
                Button emptyViewButton = (Button)emptyView.findViewById(R.id.empty_view_button);
                if (showEmptyViewButton()) {
                    // Show the button and update its label
                    emptyViewButton.setVisibility(View.VISIBLE);
                    emptyViewButton.setText(getEmptyViewButtonLabel());
                    emptyViewButton.setOnClickListener(mEmptyViewButtonClickListener);
                }
                else {
                    // Hide the button
                    emptyViewButton.setVisibility(View.GONE);
                }
            }
        } else {
            // Show content, hide message
            mArchosGridView.setVisibility(View.VISIBLE);
            View emptyView = mRootView.findViewById(R.id.empty_view);
            if (emptyView != null) {
                emptyView.setVisibility(View.GONE);
            }

            /*mSelectedPosition = Utils.restoreBestPositionWithScroll(mArchosGridView,
                    mSelectedPosition,
                    mFirstVisiblePosition,
                    mScroll);*/
            if(mListState!=null)
                mArchosGridView.onRestoreInstanceState(mListState);

        }
        getActivity().invalidateOptionsMenu();
    }

    /*
     * Subclasses can override this method to display a specific message
     */
    public int getEmptyMessage() {
        return ImportState.VIDEO.isInitialImport() ? R.string.you_have_no_video_yet : R.string.you_have_no_video;
    }

    /*
     * Subclasses can override this method to set a specific label to the empty view button
     */
    public int getEmptyViewButtonLabel() {
        return R.string.refresh;
    }

    /*
     * Subclasses can override this method if they want a button to be shown in the empty view
     */
    public boolean showEmptyViewButton() {
        return false;
    }

    protected View.OnClickListener mEmptyViewButtonClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            // The user clicked on the empty view button.
            // This button is only used so far to start searching infos online.
            if(!onEmptyviewButtonClick()) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClass(mContext, AutoScraperActivity.class);
                startActivity(intent);
            }
        }
    };

    protected boolean onEmptyviewButtonClick(){
        return false;
    }

    protected boolean mMultiplePositionEnabled = false;


    /**
     * Check the file type at this position
     */
    abstract public FileType getFileType(int position);

    /**
     * Return the number of files (including the folders!)
     * @return
     */
    abstract public int getFileAndFolderSize();


    public int getFileSize(){
        return getFileAndFolderSize()-getFirstFilePosition();
    }

    /**
     * Return the position of first file (in case there is a header)
     * @return
     */
    abstract public int getFirstFilePosition();

    /**
     * Get the File at position.
     */
    abstract public File getFile(int position);

    /**
     * Get the FilePath at position.
     */
    abstract public String getFilePath(int position);

    /**
     * Return the Uri of the video file at position.
     */
    abstract public Uri getUriFromPosition(int position);
    public Uri getRealPathUriFromPosition(int position){
        return getUriFromPosition(position);
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

    public void showSubsRetrievingDialog(SubtitleManager engine){
        mDialogRetrieveSubtitles = new DialogRetrieveSubtitles();
        mDialogRetrieveSubtitles.show(getFragmentManager(), null);
        mDialogRetrieveSubtitles.setDownloader(engine);

    }
    public void hideSubsRetrievingDialog(){
        if(mDialogRetrieveSubtitles!=null&&mDialogRetrieveSubtitles.isShowing())
            mDialogRetrieveSubtitles.dismiss();
        mDialogRetrieveSubtitles=null;
    }


    public abstract void displayInfo(int position) ;



    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Remember where the user clicked
            mTouchX = (int)event.getX();
            mTouchY = (int)event.getY();
        }
        return false;
    }

    protected boolean isClickValid(View v) {
        boolean validClick = true;

        if (mViewMode == VideoUtils.VIEW_MODE_LIST || mViewMode == VideoUtils.VIEW_MODE_DETAILS) {
            // Check if the (+) symbol is currently visible
            View expandSymbol = v.findViewById(R.id.expanded);
            if (expandSymbol != null && expandSymbol.getVisibility() == View.VISIBLE) {
                // The (+) symbol is visible => check its position
                int expandSymbolX = v.getWidth() - expandSymbol.getWidth();
                if (mViewMode == VideoUtils.VIEW_MODE_LIST) {
                    // List mode => ignore clicks above, below and to the right of the (+) symbol
                    if (mTouchX > expandSymbolX) {
                        return false;
                    }
                }
                else {
                    int expandSymbolY = v.getHeight() - expandSymbol.getHeight();
                    if (mTouchX > expandSymbolX && mTouchY > expandSymbolY) {
                        // Detailed list mode => ignore clicks below and to the right of the (+) symbol
                        return false;
                    }
                }
            }
        }

        return validClick;
    }

    public boolean isItemClickable(int position) {
        return true;
    }
    public void setItemChecked(int position){

        mArchosGridView.setItemChecked(position, !mArchosGridView.isItemChecked(position));

    }
    @Override
    public boolean onItemLongClick(AdapterView parent, View v, int position, long id) {
        return mMultiplePositionEnabled; // disable context menu when multiple selection is enabled

    }
    // The user clicked on an item of the list
    public void onItemClick(AdapterView parent, View v, int position, long id) {


        if(mMultiplePositionEnabled){
            mIsClickValid = false;
            if(mActionModeManager!=null)
                mActionModeManager.invalidateActionBar();
            return;
        }
        mSelectedPosition=position;
        if (!isItemClickable(position)) {
            return;
        }


        mIsClickValid = isClickValid(v);
        if (mIsClickValid) {
            mThumbnailEngine.cancelPendingRequestsForThisListener(this);
        }
    }


    // This will display the menu with enabled actions for the file.
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

    }

    protected enum UpdateDbXmlType {
        HIDE,
        BOOKMARK,
        RESUME,
        TRAKT_RESUME,
        TRAKT_SEEN
    }

    abstract protected boolean updateDbXml(int position, UpdateDbXmlType type, int valuel);

    protected void syncTrakt(final int position) {

    }

    public void markAsRead(final int position, boolean updateDb, boolean updateRemoteXml) {
        final boolean dbUpdated = updateDbXml(position, UpdateDbXmlType.RESUME, PlayerActivity.LAST_POSITION_END);
        if (Trakt.isTraktV2Enabled(mContext, mPreferences)) {
            if (dbUpdated) {
                syncTrakt(position);
            }
            Toast.makeText(mContext, R.string.trakt_toast_syncing, Toast.LENGTH_SHORT).show();
        }
    }

    public void markAsNotRead(final int position, boolean updateDb, boolean updateRemoteXml) {
        final boolean dbUpdated = updateDbXml(position, UpdateDbXmlType.RESUME, -1);
        if (Trakt.isTraktV2Enabled(mContext, mPreferences)) {
            if (dbUpdated) {
                updateDbXml(position, UpdateDbXmlType.TRAKT_SEEN, Trakt.TRAKT_DB_UNMARK);
                syncTrakt(position);
            }
            Toast.makeText(mContext, R.string.trakt_toast_syncing, Toast.LENGTH_SHORT).show();
        }
    }
    public void enableMultiple(int position, boolean toggle){
        if(mActionModeManager==null)
            mActionModeManager = new MultipleSelectionManager(this, mArchosGridView, mBrowserAdapter);
        mMultiplePositionEnabled = true;
        mActionModeManager.setActionBar(((AppCompatActivity)getActivity()).startSupportActionMode(mActionModeManager));
        mArchosGridView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        if(toggle)
            setItemChecked(position);
        getActivity().invalidateOptionsMenu();

    }
    public void disableMultiple(){
        mArchosGridView.clearChoices();
        mMultiplePositionEnabled = false;
        mActionModeManager = null;
        mArchosGridView.requestLayout();
        mArchosGridView.post(new Runnable() {
            @Override
            public void run() {
                //needed because of a bug when setting choice mode before choices are cleared
                mArchosGridView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);
                mArchosGridView.invalidateViews();
            }
        });
        getActivity().invalidateOptionsMenu();
    }
    //return whether we need to download remote subs or not, sometimes we know that we don't have subs
    // this can't be a parameter of startvideo because otherwise we won't be able to use it in context menu
    public boolean shouldDownloadRemoteSubtitle(int position){
        return true;
    }

    /**
     *  ask user to check is the selected file has to be deleted
     *
     * @param isParentFolder if we are deleting a parent folder of a video file previously deleted
     * @param uri needed to delete a parent folder, other wise, can be null
     */
    public void showConfirmDeleteDialog(final boolean isParentFolder, final List<Uri> uri) {

        AlertDialog.Builder b = new AlertDialog.Builder(getActivity()).setTitle("");
        if(!isParentFolder)
            b.setIcon(R.drawable.filetype_new_video);
        else
            b.setIcon(R.drawable.filetype_new_folder);

        if(!isParentFolder)
            b.setMessage(R.string.confirm_delete);
        else
            b.setMessage(R.string.confirm_delete_parent_folder);
        mDialogDelete =b.setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (mActionModeManager != null) {
                            mActionModeManager.destroyActionBar();
                        }
                        if (!isParentFolder)
                            startDeletingDialog(uri);
                        else {
                            Delete delete = new Delete(Browser.this, getActivity());
                            delete.deleteFolder(uri.get(0));
                        }

                    }
                }).create();
        mDialogDelete.show();
    }

    /**
     * Start searching the whole database if folderPath is null or empty Start
     * searching inside the provided folder otherwise
     */
    protected void startOnlineSearchForFolder(String folderPath) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(getActivity(), AutoScraperActivity.class);
        if (folderPath != null && folderPath.length() > 0) {
            intent.setData(Uri.parse(folderPath));
        }
        startActivity(intent);
    }

    protected void startSubtitlesWizard(String videoPath) {
        if (videoPath != null && videoPath.length() > 0) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(getActivity(), SubtitlesWizardActivity.class);
            intent.setData(Uri.parse(videoPath));
            startActivity(intent);
        }
    }

    @SuppressWarnings("unchecked")
    public void update(Observable observable, Object data){


    }


    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean ret = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                // if (isChild() == true) {
                // final VideoBrowserActivity parent = (VideoBrowserActivity)
                // getParent();
                // parent.launchGlobalResume();
                // }
                ret = true;
        }

        // if (!ret)
        // ret = super.onKeyUp(keyCode, event);

        return ret;
    }

    public boolean shouldEnableMultiSelection(){
        return true;
    }

    protected MultipleSelectionManager mActionModeManager ;

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty()) {
            // Add the "view mode" item
            MenuItem viewModeMenuItem = menu.add(MENU_VIEW_MODE_GROUP, MENU_VIEW_MODE, Menu.NONE, R.string.view_mode);
            viewModeMenuItem.setIcon(R.drawable.ic_menu_view_mode);
            viewModeMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            mDisplayModeSubmenu.attachMenuItem(viewModeMenuItem);

            mDisplayModeSubmenu.clear();
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_list_mode2, R.string.view_mode_list, 0);
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_poster_mode, R.string.view_mode_grid, 0);
            // Details view is only proposed on tablets, not on phones
            if (getResources().getConfiguration().isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)||TVUtils.isTV(getActivity())) {
                mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_details_mode2, R.string.view_mode_details, 0);
            }
            mDisplayModeSubmenu.selectSubmenuItem(getSubmenuItemIndex(mViewMode));
        }
        if(shouldEnableMultiSelection())
            menu.add(0, R.string.multiple_selection, 0, R.string.multiple_selection);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.setGroupVisible(MENU_HIDE_WATCHED_GROUP, mHideOption);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // NOTE: ignore the MENU_VIEW_MODE item which is
        // already handled internally in ActionBarSubmenu

        if (item.getItemId() == MENU_VIEW_HIDE_SEEN){
            mHideWatched = !mHideWatched;
            item.setTitle(mHideWatched ? R.string.hide_seen : R.string.show_all);
            mPreferences.edit().putBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, mHideWatched).apply();
        }

        else if (item.getItemId()==R.string.multiple_selection){
            enableMultiple(0, false);
        }
        return super.onOptionsItemSelected(item);
    }

    public void onSubmenuItemSelected(ActionBarSubmenu submenu, int position, long itemId) {
        switch (position) {
            case SUBMENU_ITEM_LIST_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_LIST) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_LIST);
                }
                break;

            case SUBMENU_ITEM_GRID_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_GRID) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_GRID);
                }
                break;

            case SUBMENU_ITEM_DETAILS_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_DETAILS) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_DETAILS);
                }
                break;
        }
    }

    protected int getSubmenuItemIndex(int viewMode) {
        switch (mViewMode) {
            case VideoUtils.VIEW_MODE_GRID:
                return SUBMENU_ITEM_GRID_INDEX;
            case VideoUtils.VIEW_MODE_DETAILS:
                return SUBMENU_ITEM_DETAILS_INDEX;
            case VideoUtils.VIEW_MODE_LIST:
            default:
                return SUBMENU_ITEM_LIST_INDEX;
        }
    }

    /*
        missingSubVideoPaths : all videos with no subs
        allVideoPaths : all videos
     */
    public void getMissingSubtitles(boolean force,ArrayList<String> allVideoPaths, ArrayList<String> missingSubVideoPaths){
        ArrayList<String> videoPaths;
        if(!force)
            videoPaths = missingSubVideoPaths;
        else
            videoPaths = allVideoPaths;
        if (videoPaths.isEmpty()&&!force){
            mDialogForceDlSubtitles = new DialogForceDlSubtitles();
            Bundle args = new Bundle();
            args.putSerializable(SubtitlesDownloaderActivity.FILE_URLS, allVideoPaths);
            mDialogForceDlSubtitles.setArguments(args);
            mDialogForceDlSubtitles.setTargetFragment(this, 0);
            mDialogForceDlSubtitles.show(getFragmentManager(), "dialog_force_dl_subtitles");
        }else {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(mContext, SubtitlesDownloaderActivity.class);
            intent.putExtra(SubtitlesDownloaderActivity.FILE_URLS, videoPaths);
            startActivity(intent);
        }
    }

    protected void setPosition() {
        mSelectedPosition = mArchosGridView.getSelectedItemPosition()>=0?mArchosGridView.getSelectedItemPosition():mArchosGridView.getFirstVisiblePosition();
        mFirstVisiblePosition = mArchosGridView.getFirstVisiblePosition();
        View v = mArchosGridView.getChildAt(mSelectedPosition-mFirstVisiblePosition);
        mScroll = (v == null) ? 0 : v.getTop();

    }

    protected boolean hasSavedPosition() {
        return mSelectedPosition > 0 || mFirstVisiblePosition > 0 ;
    }

    protected void applySelectedViewMode(int newMode) {
        // Save the current position variables before changing the view mode
        setPosition();
        mSelectedPosition = mArchosGridView.getFirstVisiblePosition();

        setViewMode(newMode);
        bindAdapter();
    }

    @Override
    public void onVideoFileRemoved(Uri videoFile, boolean askForFolderRemoval, Uri folder) {
        if(askForFolderRemoval) {
            List<Uri> toDelete = new ArrayList<>();
            toDelete.add(folder);
            showConfirmDeleteDialog(true, toDelete);

        }
    }

    @Override
    public void onDeleteSuccess() {
        mDialogDeleting.dismiss();
        refresh();
    }
    @Override
    public void onDeleteVideoFailed(Uri videoFile) {
        Toast.makeText(getActivity(), R.string.delete_error,Toast.LENGTH_LONG).show();
        mDialogDeleting.dismiss();
    }


    @Override
    public void onFolderRemoved(final Uri folder) {
        if(isAdded()) {
            Toast.makeText(getActivity(), R.string.directory_deleted, Toast.LENGTH_SHORT).show();
        }
    }
    public void startDeletingDialog(List<Uri> uriToDelete){
        mArchosGridView.getCheckedItemPosition();
        mDialogDeleting = new DeleteDialog();
        mDialogDeleting.show(getFragmentManager(), null);
        final Delete delete = new Delete(this,getActivity());
        if(uriToDelete.size()>1) {
            delete.startMultipleDeleteProcess(uriToDelete);
        }
        else
            delete.startDeleteProcess(uriToDelete.get(0));


    }

    /**
     * refresh list
     */
    protected abstract void refresh();
    @SuppressLint("ValidFragment") // XXX
    public static class DialogForceDlSubtitles extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String title = getString(R.string.sub_force_all_title);
            int icon = R.drawable.ic_menu_subtitles;
            return new AlertDialog.Builder(getActivity()).setTitle(title).setIcon(icon)
                    .setMessage(R.string.sub_force_all)
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ((Browser)getTargetFragment()).getMissingSubtitles(true, getArguments().getStringArrayList(SubtitlesDownloaderActivity.FILE_URLS), getArguments().getStringArrayList(SubtitlesDownloaderActivity.FILE_URLS));
                        }
                    }).create();
        }
    }


    //download with metafile2
    public void startDownloadingVideo(List<Uri> uris) {
        showPasteDialog();

        FileManagerService.fileManagerService.copyUri(uris, Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)));
    }


    protected void showPasteDialog(){
        mPasteDialog = new Paste(getActivity());
        mPasteDialog.show();
    }

    protected String getExtension(String filename) {
        if (filename == null)
            return null;
        int dotPos = filename.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < filename.length()) {
            return filename.substring(dotPos + 1).toLowerCase();
        }
        return null;
    }
}
