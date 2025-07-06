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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
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
import com.archos.mediacenter.video.utils.CustomTypefaceSpan;
import com.archos.mediacenter.video.utils.ExternalPlayerResultListener;
import com.archos.mediacenter.video.utils.ExternalPlayerWithResultStarter;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity2;
import com.archos.mediacenter.video.utils.SubtitlesWizardActivity;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.MenuBaseAdapter;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import httpimage.HttpImageManager;

public abstract class BrowserByVideoObjects extends Browser implements CommonPresenter.ExtendedClickListener, ExternalPlayerWithResultStarter {

    private static final Logger log = LoggerFactory.getLogger(BrowserByVideoObjects.class);

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
        AdapterContextMenuInfo info;
        try {
            info = (AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            log.error("onCreateContextMenu: bad menuInfo", e);
            return;
        }

        if (info == null) {
            log.error("onCreateContextMenu: null info");
            return;
        }

        int position = info.position;
        Video video = mAdapterByVideoObjects.getVideoItem(position);
        if (!isItemClickable(position)) return;

        // ✅ Trigger haptic feedback manually on long click anchor
        if (v != null) {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }

        showPowerMenu(v, video, position);
    }

    private void showPowerMenu(View anchor, Video video, int position) {
        List<PowerMenuItem> menuItems = new ArrayList<>();

        // Optional title (non-clickable if needed)
        String title = video instanceof Episode
                ? ((Episode) video).getShowName() + ": " + video.getName()
                : video.getName();
        //menuItems.add(new PowerMenuItem(title, false)); // `false` disables click highlight

        menuItems.add(new PowerMenuItem(mContext.getString(R.string.play_selection)));

        final int resumePosition = video.getResumeMs();
        final boolean resume = resumePosition > 0;
        final boolean delete = !FileUtils.isSlowRemote(Uri.parse(video.getFilePath())) && video.locationSupportsDelete();
        final boolean markAsTrakt = Trakt.isTraktV2Enabled(mContext, mPreferences);
        final boolean isNetwork = !FileUtils.isLocal(video.getFileUri());

        if (resume && resumePosition != PlayerActivity.LAST_POSITION_END) {
            String resumeText = mContext.getString(R.string.resume) + " (" + MediaUtils.formatTime(resumePosition) + ")";
            menuItems.add(new PowerMenuItem(resumeText));
        }
        if (delete)
            menuItems.add(new PowerMenuItem(mContext.getString(R.string.delete)));
        if (resume)
            menuItems.add(new PowerMenuItem(mContext.getString(R.string.delete_resume)));

        menuItems.add(new PowerMenuItem(mContext.getString(R.string.info)));

        if (!(video instanceof NonIndexedVideo)) {
            if (markAsTrakt) {
                menuItems.add(new PowerMenuItem(mContext.getString(
                        video.isWatched() ? R.string.mark_as_not_watched : R.string.mark_as_watched)));
            } else {
                menuItems.add(new PowerMenuItem(mContext.getString(
                        resumePosition != PlayerActivity.LAST_POSITION_END
                                ? R.string.mark_as_watched : R.string.mark_as_not_watched)));
            }
        }

        if (!isNetwork)
            menuItems.add(new PowerMenuItem(mContext.getString(R.string.get_subtitles_on_drive)));

        menuItems.add(new PowerMenuItem(mContext.getString(R.string.get_subtitles_online)));

        if (video.hasScraperData())
            menuItems.add(new PowerMenuItem(mContext.getString(R.string.add_to_list)));

        menuItems.add(new PowerMenuItem(mContext.getString(
                video.getId() > 0 ? R.string.video_browser_unindex_file : R.string.video_browser_index_file)));

        if (isNetwork)
            menuItems.add(new PowerMenuItem(mContext.getString(R.string.copy_on_device)));

        Context themedContext = new ContextThemeWrapper(mContext, R.style.PowerMenuTheme);

        View decorView = ((Activity) anchor.getContext()).getWindow().getDecorView();

        int[] decorLocation = new int[2];
        decorView.getLocationOnScreen(decorLocation);

        int xOffset = mTouchX - decorLocation[0];
        int yOffset = mTouchY - decorLocation[1];

        Log.d("PowerMenu", "DecorView location: " + decorLocation[0] + "," + decorLocation[1]);
        Log.d("PowerMenu", "Calculated offsets: " + xOffset + "," + yOffset);

        Typeface typeface = ResourcesCompat.getFont(mContext, R.font.nhaasgroteskdspro_75bd);
        if (typeface == null) typeface = Typeface.DEFAULT;
        float textSizeSp = 16f;
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        Paint paint = new Paint();
        paint.setTypeface(typeface);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, metrics));
        // Find the widest item
        float maxTextWidth = 0;
        for (PowerMenuItem item : menuItems) {
            float width = paint.measureText(item.title.toString());
            if (width > maxTextWidth) maxTextWidth = width;
        }
        // Calculate total width: max text + padding + margin
        int internalPaddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, metrics); // start + end padding
        int externalMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics); // optional
        int menuWidth = (int) (maxTextWidth + internalPaddingPx + externalMarginPx);

        PowerMenu powerMenu = new PowerMenu.Builder(themedContext)
                .addItemList(menuItems)
                .setMenuRadius(16f)
                .setMenuShadow(8f)
                .setAnimation(MenuAnimation.DROP_DOWN)
                //.setAnimationStyle(android.R.style.Animation_Dialog)
                .setAutoDismiss(true)
                .setBackgroundColor(ContextCompat.getColor(mContext, R.color.transparent))
                .setHeaderView(R.layout.power_menu_header)
                .setWidth(menuWidth) // ⬅ set width here
                .build();
        ListView listView = powerMenu.getMenuListView();
        CustomPowerMenuAdapter adapter = new CustomPowerMenuAdapter(listView);
        adapter.addItemList(menuItems);
        listView.setAdapter(adapter);

        View header = powerMenu.getHeaderView();
        if (header != null) {
            TextView headerText = header.findViewById(R.id.header_title);
            headerText.setText(title);
        }

        View menuListView = powerMenu.getMenuListView();
        if (menuListView != null) {
            View parent = (View) menuListView.getParent();

            // Create a drawable with corner radius and stroke
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ContextCompat.getColor(mContext, R.color.tranparent_deep_blue)); // Fill color

            // Convert dp to pixels
            float radiusPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 12f, mContext.getResources().getDisplayMetrics());
            int strokeWidthPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1f, mContext.getResources().getDisplayMetrics());

            // Set radius and stroke
            bg.setCornerRadius(radiusPx);
            bg.setStroke(strokeWidthPx, ContextCompat.getColor(mContext, R.color.black)); // Replace with your color

            parent.setBackground(bg);
        }

        powerMenu.setOnMenuItemClickListener((positionClicked, item) -> {
            // Dismiss menu on the next message loop to avoid race conditions
            //anchor.post(() -> powerMenu.dismiss());
            String titleClicked = ((PowerMenuItem) item).title.toString();
            handlePowerMenuClick(titleClicked, video, position);
        });
        Log.d("PowerMenu", "Anchor attached: " + anchor.isAttachedToWindow());
        powerMenu.showAtLocation(decorView, Gravity.NO_GRAVITY, xOffset, yOffset);
    }

    public class CustomPowerMenuAdapter extends MenuBaseAdapter<PowerMenuItem> {
        public CustomPowerMenuAdapter(ListView listView) {
            super(listView);
        }
        @Override
        public View getView(int index, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                view = inflater.inflate(R.layout.item_power_menu, parent, false);
            }
            PowerMenuItem item = (PowerMenuItem) getItem(index);
            TextView title = view.findViewById(R.id.menu_item_title);
            title.setText(((PowerMenuItem) item).title.toString());
            return view;
        }
    }

    private void handlePowerMenuClick(String title, Video video, int position) {
        int resumePosition = video.getResumeMs();
        boolean resumeAvailable = resumePosition > 0 && resumePosition != PlayerActivity.LAST_POSITION_END;

        if (title.equals(mContext.getString(R.string.play_selection)) ||
                title.equals(mContext.getString(R.string.play_from_beginning))) {
            startVideo(video, PlayerActivity.RESUME_NO);
        } else if (title.startsWith(mContext.getString(R.string.resume))) {
            startVideo(video, PlayerActivity.RESUME_FROM_LAST_POS);
        } else if (title.equals(mContext.getString(R.string.info))) {
            displayInfo(position);
        } else if (title.equals(mContext.getString(R.string.video_browser_index_file))) {
            VideoStore.requestIndexing(video.getFileUri(), getActivity());
        } else if (title.equals(mContext.getString(R.string.video_browser_unindex_file))) {
            updateDbXml(position, UpdateDbXmlType.HIDE, 1);
        } else if (title.equals(mContext.getString(R.string.delete_resume))) {
            updateDbXml(position, UpdateDbXmlType.RESUME, -1);
            if (Trakt.isTraktV2Enabled(getActivity(), mPreferences)) {
                new TraktService.Client(mContext, null, false).watchingStop(video.getId(), 0);
            }
        } else if (title.equals(mContext.getString(R.string.delete))) {
            if (ArchosSettings.isDemoModeActive(getActivity())) {
                getActivity().startService(
                        new Intent(ArchosIntents.ACTION_DEMO_MODE_FEATURE_DISABLED));
            } else {
                List<Uri> toDelete = new ArrayList<>();
                toDelete.add(video.getFileUri());
                mDeletedPosition = position;
                showConfirmDeleteDialog(false, toDelete);
            }
        } else if (title.equals(mContext.getString(R.string.mark_as_watched))) {
            markAsRead(position, true,
                    mPreferences.getBoolean(BrowserByNetwork.KEY_NETWORK_BOOKMARKS, true));
        } else if (title.equals(mContext.getString(R.string.mark_as_not_watched))) {
            markAsNotRead(position, true,
                    mPreferences.getBoolean(BrowserByNetwork.KEY_NETWORK_BOOKMARKS, true));
        } else if (title.equals(mContext.getString(R.string.get_subtitles_on_drive))) {
            Activity activity = getActivity();
            // Subtitles wizard
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(mContext, SubtitlesWizardActivity.class);
            intent.setData(video.getFileUri());
            activity.startActivity(intent);
        } else if (title.equals(mContext.getString(R.string.get_subtitles_online))) {
            Intent subIntent = new Intent(Intent.ACTION_MAIN);
            subIntent.setClass(mContext, SubtitlesDownloaderActivity2.class);
            subIntent.putExtra(SubtitlesDownloaderActivity2.FILE_URL, video.getFileUri().toString());
            getActivity().startActivity(subIntent);
        } else if (title.equals(mContext.getString(R.string.copy_on_device))) {
            List<Uri> toCopy = new ArrayList<>();
            toCopy.add(video.getFileUri());
            startDownloadingVideo(toCopy);
        } else if (title.equals(mContext.getString(R.string.add_to_list))) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(ListDialog.EXTRA_VIDEO, video);
            ListDialog dialog = new ListDialog();
            dialog.setArguments(bundle);
            dialog.show(getActivity().getSupportFragmentManager(), "list_dialog");
        } else {
            log.warn("Unhandled PowerMenu click: " + title);
        }
    }

    public void startVideo(int video, int resume) {

    }
    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        super.onItemClick(parent, v, position, id);
        if (mIgnoreNextClick) {
            mIgnoreNextClick = false; // reset
            return; // ignore this click — it was actually a long-click
        }
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
            adapterInterface.setPresenter(NonIndexedVideo.class, new ScrapedVideoDetailedPresenter(activity, listener,imageManager));
            adapterInterface.setPresenter(Video.class, new ScrapedVideoDetailedPresenter(activity, listener,imageManager));
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
                    // TODO: not sure it is the right call
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
                log.debug("onContextItemSelected: get_subtitles_online for " + getRealPathUriFromPosition(info.position));
                subIntent.setClass(mContext, SubtitlesDownloaderActivity2.class);
                subIntent.putExtra(SubtitlesDownloaderActivity2.FILE_URL, getRealPathUriFromPosition(info.position).toString());
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
                dialog.show(getActivity().getSupportFragmentManager(), "list_dialog");
                break;
            default:
                ret = super.onContextItemSelected(item);
                log.error("onContextItemSelected: unexpected default case! " + index);
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
