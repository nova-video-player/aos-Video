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

/***************************************************************************************************
**  This activity is a ListView which contains the following items:
**    - a SEPARATOR ("subtitles files already associated to the video")
**    - [mWizardCommon.getCurrentFilesCount()] FILES, or a MESSAGE ("list is empty") if mWizardCommon.getCurrentFilesCount() = 0
**    - a SEPARATOR : "other subtitles files available"
**    - [mWizardCommon.getAvailableFilesCount()] FILES, or a MESSAGE ("list is empty") if mWizardCommon.getAvailableFilesCount() = 0
***************************************************************************************************/

package com.archos.mediacenter.video.utils;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.archos.mediacenter.video.R;

public class SubtitlesWizardActivity extends AppCompatActivity implements OnItemClickListener, View.OnCreateContextMenuListener {
    private final static String TAG = "SubtitlesWizardActivity";
    private final static boolean DBG = false;

    private static final int ITEM_DATA_TYPE_SEPARATOR = 0;
    private static final int ITEM_DATA_TYPE_CURRENT = 1;
    private static final int ITEM_DATA_TYPE_AVAILABLE = 2;
    private static final int ITEM_DATA_TYPE_MESSAGE = 3;

    private ListView mListView;
    private TextView mEmptyView;

    private int mPosition;

    private SubtitlesWizardCommon mWizardCommon = new SubtitlesWizardCommon(this);
    
    //private int mDefaultIconsColor;

    private class ItemData {
        int type;       // The item type
        int index;      // The index of the file in the current/available list, not used for other items
        String path;    // The path if the item is a real file, not used otherwise
    }


    //*****************************************************************************
    // Activity lifecycle functions
    //*****************************************************************************

    @Override
    public void onCreate(Bundle icicle) {
        if (DBG) Log.d(TAG, "onCreate");
        super.onCreate(icicle);

        setContentView(R.layout.subtitles_wizard_main);

        mWizardCommon.onCreate();

        // Use the name of the video to build the help message displayed at the top of the screen
        TextView helpMessageHeader = (TextView) findViewById(R.id.help_message_header);

        String helpMessage = mWizardCommon.getHelpMessage();
        helpMessageHeader.setText(helpMessage);

        // Inflate the view to show if no subtitles files are found
        mEmptyView = (TextView) LayoutInflater.from(this).inflate(R.layout.browser_empty_item, null);

        mListView = (ListView) findViewById(R.id.list_items);
        mListView.setEmptyView(mEmptyView);

        SubtitlesWizardAdapter adapter = new SubtitlesWizardAdapter(getApplication(), this);
        mListView.setAdapter(adapter);
        mListView.setCacheColorHint(0);
        mListView.setOnItemClickListener(this);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setSelector(R.drawable.list_selector_no_background);

        //mDefaultIconsColor = getResources().getColor(R.color.default_icons_color_filter);
        
        // Handle the message to display when there are no files
        enableEmptyView(mWizardCommon.getAvailableFilesCount() == 0 && mWizardCommon.getCurrentFilesCount() == 0);
    }

    private void enableEmptyView(boolean empty) {
        if (empty) {
            mEmptyView.setText(R.string.subtitles_wizard_no_files);
            mEmptyView.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        }
        else {
            mEmptyView.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        super.onDestroy();
    }


    //*****************************************************************************
    // Activity events management
    //*****************************************************************************

    public void onItemClick(AdapterView parent, View view, int position, long id) {
        if (DBG) Log.d(TAG, "onItemClick : position=" + position);
        ItemData itemData = getItemData(position);
        if (itemData.type == ITEM_DATA_TYPE_AVAILABLE) {
            renameFile(position);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo)menuInfo;
        mPosition = adapterMenuInfo.position;

        ItemData itemData = getItemData(mPosition);
        if (itemData.type == ITEM_DATA_TYPE_SEPARATOR || itemData.type == ITEM_DATA_TYPE_MESSAGE) {
            // No contextual menu for separators or messages
            return;
        }

        // Show the name of the file in the header
        menu.setHeaderTitle(mWizardCommon.getFileName(itemData.path));

        if (itemData.type == ITEM_DATA_TYPE_CURRENT) {
            // Contextual menu for current subtitles files
            menu.add(0, R.string.subtitles_wizard_delete, 0, R.string.subtitles_wizard_delete);
        }
        else {
            // Contextual menu for available subtitles files
            menu.add(0, R.string.subtitles_wizard_associate, 0, R.string.subtitles_wizard_associate);
            menu.add(0, R.string.subtitles_wizard_delete, 0, R.string.subtitles_wizard_delete);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int menuId = item.getItemId();
        if (menuId == R.string.subtitles_wizard_associate) {
            renameFile(mPosition);
            return true;
        }
        else if (menuId == R.string.subtitles_wizard_delete) {
            deleteFile(mPosition);
            return true;
        }

        return false;
    }


    //*****************************************************************************
    // Activity local functions
    //*****************************************************************************

    /*
     * Returns the number of items in the ListView
     */
    private int getItemsCount() {
        // Take into account the "list is empty" message when a list is empty
        int currentListItemsCount = Math.max(mWizardCommon.getCurrentFilesCount(), 1);
        int availableListItemsCount = Math.max(mWizardCommon.getAvailableFilesCount(), 1);

        // Add the separators
        return currentListItemsCount + availableListItemsCount + 2;
    }

    /*
     * Returns data for the item at the provided position
     * NOTE : keep all knowledge of the ListView contents here!
     */
    private ItemData getItemData(int position) {
        ItemData data = new ItemData();

        int currentListItemsCount = Math.max(mWizardCommon.getCurrentFilesCount(), 1);

        if (position == 0 || position == currentListItemsCount + 1) {
            data.type = ITEM_DATA_TYPE_SEPARATOR;
            data.index = 0;
            data.path = null;
        }
        else if (position <= currentListItemsCount) {
            if (mWizardCommon.getCurrentFilesCount() > 0) {
                data.type = ITEM_DATA_TYPE_CURRENT;
                data.index = position - 1;
                data.path =  mWizardCommon.getCurrentFile(data.index);
            }
            else {
                data.type = ITEM_DATA_TYPE_MESSAGE;
                data.index = 0;
                data.path = null;
            }
        }
        else {
            if (mWizardCommon.getAvailableFilesCount() > 0) {
                data.type = ITEM_DATA_TYPE_AVAILABLE;
                data.index = position - currentListItemsCount - 2;
                data.path =  mWizardCommon.getAvailableFile(data.index);
            }
            else {
                // "List is empty" message
                data.type = ITEM_DATA_TYPE_MESSAGE;
                data.index = 0;
                data.path = null;
            }
        }

        return data;
    }

    private void renameFile(int position) {
        ItemData itemData = getItemData(position);

        // Only files from the available list can be renamed
        if (itemData.type == ITEM_DATA_TYPE_AVAILABLE) {
            boolean fileRenamed = mWizardCommon.renameFile(itemData.path, itemData.index);

            if (fileRenamed) {
                // Update the activity screen
                mListView.invalidateViews();
                setResult(AppCompatActivity.RESULT_OK);
            }
        }
    }

    private void deleteFile(int position) {
        ItemData itemData = getItemData(position);

        if (itemData.type == ITEM_DATA_TYPE_CURRENT || itemData.type == ITEM_DATA_TYPE_AVAILABLE) {
            boolean fileDeleted = mWizardCommon.deleteFile(itemData.path, itemData.index, itemData.type == ITEM_DATA_TYPE_CURRENT);

            if (fileDeleted) {
                if (mWizardCommon.getCurrentFilesCount() == 0 && mWizardCommon.getAvailableFilesCount() == 0) {
                    // The user deleted the last subtitles file of the folder
                    enableEmptyView(true);
                }

                // Update the activity screen
                mListView.invalidateViews();
                setResult(AppCompatActivity.RESULT_OK);
            }
        }
    }


    //************************************************************************************
    // Adapter
    //************************************************************************************

    class SubtitlesWizardAdapter extends BaseAdapter {
        // Set one constant for each possible type of layout
        private static final int ITEM_VIEW_TYPE_SEPARATOR = 0;
        private static final int ITEM_VIEW_TYPE_FILE = 1;
        private static final int ITEM_VIEW_TYPE_MESSAGE = 2;

        private final SubtitlesWizardActivity mActivity;
        private final LayoutInflater mInflater;

        class ViewHolder {
            LinearLayout container;
            ImageView icon;
            TextView text;
            TextView size;
        };

        SubtitlesWizardAdapter(Context context, SubtitlesWizardActivity activity) {
            super();

            mActivity = activity;
            mInflater = LayoutInflater.from(context);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            // Needed to select the layout and background
            int itemType = getItemViewType(position);

            // Needed to select the info to display
            ItemData itemData = mActivity.getItemData(position);
            boolean isFile = (itemData.type == ITEM_DATA_TYPE_CURRENT || itemData.type == ITEM_DATA_TYPE_AVAILABLE);

            //-------------------------------------------------------
            // Re-use/create a valid convertView
            //-------------------------------------------------------
            if (convertView == null) {
                // Inflate a new layout for this item
                holder = new ViewHolder();

                switch(itemType) {
                    case ITEM_VIEW_TYPE_SEPARATOR:
                        convertView = mInflater.inflate(R.layout.subtitles_wizard_item_separator, parent, false);
                        holder.container = (LinearLayout)convertView.findViewById(R.id.separator_container);
                        holder.text = (TextView)convertView.findViewById(R.id.separator_name);
                        holder.icon = null;
                        holder.size = null;
                        break;

                    case ITEM_VIEW_TYPE_MESSAGE:
                        convertView = mInflater.inflate(R.layout.subtitles_wizard_item_message, parent, false);
                        holder.container = (LinearLayout)convertView.findViewById(R.id.message_container);
                        holder.text = (TextView)convertView.findViewById(R.id.message_text);
                        holder.icon = null;
                        holder.size = null;
                        break;

                    case ITEM_VIEW_TYPE_FILE:
                    default:
                        convertView = mInflater.inflate(R.layout.subtitles_wizard_item_file, parent, false);
                        holder.container = (LinearLayout)convertView.findViewById(R.id.file_container);
                        holder.text = (TextView)convertView.findViewById(R.id.file_name);
                        holder.icon = (ImageView)convertView.findViewById(R.id.file_icon);
                        holder.size = (TextView)convertView.findViewById(R.id.file_size);
                        break;
                }

                convertView.setTag(holder);
            }
            else {
                // Use the provided ViewHolder
                holder = (ViewHolder)convertView.getTag();
            }

            //-------------------------------------------------------
            // Update the item
            //-------------------------------------------------------
            // Background bitmap
            if (holder.container != null) {
                int resId = (itemType == ITEM_VIEW_TYPE_FILE) ? R.drawable.list_item_background : 0;
                holder.container.setBackgroundResource(resId);
            }
            
            // Icon
            if (holder.icon != null && isFile) {
                holder.icon.setImageResource(R.drawable.filetype_video_subtitles);
                //holder.icon.setColorFilter(mDefaultIconsColor);
            }

            // Text
            if (holder.text != null) {
                holder.text.setText((String)getItem(position));
            }

            // Size
            if (holder.size != null) {
                String size = "";

                if (isFile && itemData.path != null) {
                    size = mWizardCommon.getFileSize(itemData.path);
                }

                holder.size.setText(size);
            }

            return convertView;
        }

        public boolean areAllItemsEnabled() {
            return false;
        }

        public boolean isEnabled(int position) {
            ItemData itemData = getItemData(position);
            return (itemData.type == ITEM_DATA_TYPE_CURRENT || itemData.type == ITEM_DATA_TYPE_AVAILABLE);
        }

        public boolean isEmpty() {
            // We have at least the two separators
            return false;
        }

        public int getViewTypeCount() {
            // Return how many different layouts are used
            return 3;
        }

        public int getItemViewType(int position) {
            ItemData itemData = mActivity.getItemData(position);
            if (itemData.type == ITEM_DATA_TYPE_SEPARATOR) {
                return ITEM_VIEW_TYPE_SEPARATOR;
            }
            else if (itemData.type == ITEM_DATA_TYPE_MESSAGE) {
                return ITEM_VIEW_TYPE_MESSAGE;
            }
            return ITEM_VIEW_TYPE_FILE;
        }

        public boolean hasStableIds() {
            return true;
        }

        public int getCount() {
            return mActivity.getItemsCount();
        }

        public Object getItem(int position) {
            String text = null;

            // Return the string to display at this position
            ItemData itemData = mActivity.getItemData(position);
            switch (itemData.type) {
                case ITEM_DATA_TYPE_SEPARATOR:
                    if (position == 0) {
                        // First separator
                        text = mActivity.getString(R.string.subtitles_wizard_current_files);
                    }
                    else {
                        // Second separator
                        text = mActivity.getString(R.string.subtitles_wizard_available_files);
                    }
                    break;

                case ITEM_DATA_TYPE_MESSAGE:
                    // Message
                    text = mActivity.getString(R.string.subtitles_wizard_empty_list);
                    if (position == getCount() - 1) {
                        // Additional text for the bottom message
                        text += ". " + mActivity.getString(R.string.subtitles_wizard_add_files);
                    }
                    break;

                default:
                    // File
                    text = mWizardCommon.getFileName(itemData.path);
            }

            return text;
        }

        public long getItemId(int position) {
            return position;
        }

        public void registerDataSetObserver(DataSetObserver observer) {
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
        }
    }
}
