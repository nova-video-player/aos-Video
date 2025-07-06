package com.archos.mediacenter.video.browser.BrowserByIndexedVideos;

import com.archos.environment.ArchosIntents;
import com.archos.environment.ArchosSettings;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.ThumbnailEngineVideo;
import com.archos.mediacenter.video.browser.adapters.GroupOfMovieAdapter;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.filebrowsing.BrowserByFolder;
import com.archos.mediacenter.video.browser.loader.VideosByListLoader;
import com.archos.mediacenter.video.utils.CustomTypefaceSpan;
import com.archos.mediacenter.video.utils.TraktSigninDialogPreference;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediaprovider.video.VideoStore;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.MenuBaseAdapter;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;
import androidx.loader.content.Loader;
import androidx.appcompat.app.ActionBar;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class BrowserPlaylists extends BrowserMoviesBy {

    private static final int ACTIVITY_REQUEST_CODE_PREFERENCES = 1012;
    private boolean mHasLaunchedTrakt = false;

    @Override
    public int getThumbnailsType() {
        return ThumbnailEngineVideo.TYPE_MOVIE_YEAR;
    }

    @Override
    protected Uri getCursorUri() {
        return VideoStore.RAW_QUERY;
    }

    @Override
    public int getEmptyMessage() {
        return R.string.no_list_detected;
    }

    @Override
    public int getEmptyViewButtonLabel() {
        return R.string.trakt_list_signin;
    }

    public boolean showEmptyViewButton() {
        return !Trakt.isTraktV2Enabled(null, PreferenceManager.getDefaultSharedPreferences(getActivity())) ;
    }

    protected boolean onEmptyviewButtonClick(){
        if(mHasLaunchedTrakt)
            return true;
            //connect to trakt
        TraktSigninDialogPreference dialogPreference = new TraktSigninDialogPreference(getContext(),null);
        dialogPreference.showDialog(true);
        dialogPreference.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                postBindAdapter();
                mHasLaunchedTrakt = false;
            }
        });
    mHasLaunchedTrakt = true;
        return true;
    }
    public void onResume(){
        super.onResume();
        ((MainActivity)getActivity()).setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }
    @Override
    public void  onActivityResult(int requestCode, int resultCode, Intent data) {
        if(ACTIVITY_REQUEST_CODE_PREFERENCES == requestCode)
            mHasLaunchedTrakt = false;
        else super.onActivityResult(requestCode, resultCode, data);
    }
    public void addSortOptionsSubmenus(ActionBarSubmenu submenu) {
	    // MENU_ITEM_NAME is not a typo here, because the year will be copied to the name column
        submenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_date_desc), MENU_ITEM_SORT + MENU_ITEM_NAME + MENU_ITEM_DESC);
        submenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_date_asc), MENU_ITEM_SORT + MENU_ITEM_NAME + MENU_ITEM_ASC);
    }

    private SpannableString applyCustomFont(@StringRes int resId) {
        String family ="";
        Typeface typeface = ResourcesCompat.getFont(mContext, R.font.nhaasgroteskdspro_75bd);
        int color = ContextCompat.getColor(mContext, android.R.color.white);
        float textSize = 18f; // in SP
        String text = mContext.getString(resId);
        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new CustomTypefaceSpan(family, typeface, textSize, color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @Override
    protected String getDefaultSortOrder() {
        return COLUMN_NAME+" COLLATE LOCALIZED DESC";
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new VideosByListLoader(getContext(), mSortOrder).getV4CursorLoader(false, mPreferences.getBoolean(VideoPreferencesCommon.KEY_HIDE_WATCHED, false));
    }

    protected void completeNewFragmentBundle(Bundle args, int pos){
        Cursor cursor = ((GroupOfMovieAdapter)mBrowserAdapter).getCursor();
        if (cursor.getCount() > 0 && pos < cursor.getCount()) {
            cursor.moveToPosition(pos);
            args.putString(BrowserVideosInPlaylist.EXTRA_MAP_MOVIES, cursor.getString(cursor.getColumnIndex(VideosByListLoader.COLUMN_MAP_MOVIE_ID)));
            args.putString(BrowserVideosInPlaylist.EXTRA_MAP_EPISODES, cursor.getString(cursor.getColumnIndex(VideosByListLoader.COLUMN_MAP_EPISODE_ID)));
        }
        args.putLong(BrowserVideosInPlaylist.EXTRA_PLAYLIST_ID, mBrowserAdapter.getItemId(pos));
    }

    protected String getBrowserNameToInstantiate(){
        return BrowserVideosInPlaylist.class.getName();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        //rename will come later
        //menu.add(0, R.string.delete, 0, R.string.delete);
        //return;
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            return;
        }
        long listId = mBrowserAdapter.getItemId(info.position);

        // ✅ Trigger haptic feedback manually on long click anchor
        if (v != null) {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }

        showPowerMenu(v, listId);
    }
    private void showPowerMenu(View anchor, long listId) {
        List<PowerMenuItem> menuItems = new ArrayList<>();
        menuItems.add(new PowerMenuItem(mContext.getString(R.string.delete)));

        Context themedContext = new ContextThemeWrapper(mContext, R.style.PowerMenuTheme);
        View decorView = ((Activity) anchor.getContext()).getWindow().getDecorView();
        int[] decorLocation = new int[2];
        decorView.getLocationOnScreen(decorLocation);
        int xOffset = mTouchX - decorLocation[0];
        int yOffset = mTouchY - decorLocation[1];

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
                .setAutoDismiss(true)
                .setBackgroundColor(ContextCompat.getColor(mContext, R.color.transparent))
                .setWidth(menuWidth) // ⬅ set width here
                .build();
        ListView listView = powerMenu.getMenuListView();
        CustomPowerMenuAdapter adapter = new CustomPowerMenuAdapter(listView);
        adapter.addItemList(menuItems);
        listView.setAdapter(adapter);

        View menuListView = powerMenu.getMenuListView();
        if (menuListView != null) {
            View parent = (View) menuListView.getParent();
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ContextCompat.getColor(mContext, R.color.tranparent_deep_blue));
            float radiusPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 12f, mContext.getResources().getDisplayMetrics());
            int strokeWidthPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1f, mContext.getResources().getDisplayMetrics());
            bg.setCornerRadius(radiusPx);
            bg.setStroke(strokeWidthPx, ContextCompat.getColor(mContext, R.color.black));
            parent.setBackground(bg);
        }

        powerMenu.setOnMenuItemClickListener((positionClicked, item) -> {
            String titleClicked = ((PowerMenuItem) item).title.toString();
            handlePowerMenuClick(titleClicked, listId);
        });
        powerMenu.showAtLocation(decorView, Gravity.NO_GRAVITY, xOffset, yOffset);
    }

    private void handlePowerMenuClick(String title, long listId) {
        if (title.equals(mContext.getString(R.string.delete))) {
            getActivity().getContentResolver().delete(VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.ID +" = ?", new String[]{listId+""});
        }
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
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int index = item.getItemId();
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        long listId = mBrowserAdapter.getItemId(info.position);
        switch (index) {
            case R.string.delete:
                getActivity().getContentResolver().delete(VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.ID +" = ?", new String[]{listId+""});
                break;
        }
        return true;
    }

    @Override
    public String getActionBarTitle() {
        return getString(R.string.video_lists);
    }
}
