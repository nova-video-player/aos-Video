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
**    - [mCurrentFilesCount] FILES, or a MESSAGE ("list is empty") if mCurrentFilesCount = 0
**    - a SEPARATOR : "other subtitles files available"
**    - [mAvailableFilesCount] FILES, or a MESSAGE ("list is empty") if mAvailableFilesCount = 0
***************************************************************************************************/

package com.archos.mediacenter.video.utils;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.R;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaFactory;
import com.archos.environment.ArchosUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class SubtitlesWizardActivity extends Activity implements OnItemClickListener, View.OnCreateContextMenuListener {
    private final static String TAG = "SubtitlesWizardActivity";
    private final static boolean DBG = false;

    // Subtitles files will be renamed as : video_name + SUBTITLES_FILES_SUFFIX + file_counter + extension
    // (for instance "." => myvideo.srt, myvideo.1.srt, myvideo.2.srt, ...)
    private final static String SUBTITLES_FILES_SUFFIX = ".";

    private static final int ITEM_DATA_TYPE_SEPARATOR = 0;
    private static final int ITEM_DATA_TYPE_CURRENT = 1;
    private static final int ITEM_DATA_TYPE_AVAILABLE = 2;
    private static final int ITEM_DATA_TYPE_MESSAGE = 3;

    private ListView mListView;
    private TextView mEmptyView;

    private String mVideoPath;
    private Uri mVideoUri;

    private List<String> mCurrentFiles;
    private int mCurrentFilesCount;

    private List<String> mAvailableFiles;
    private int mAvailableFilesCount;

    private int mPosition;
    
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

        // Extract the path of the video to handle from the intent
        Uri videoUri = getIntent().getData();
        // FIXME: this is broken for smb:// files
        if (videoUri != null) {
            mVideoUri = videoUri;
            mVideoPath = videoUri.getPath();
            if (DBG) Log.d(TAG, "onCreate : video to process = " + mVideoPath);

            if (mVideoPath != null) {
                File videoFile = new File(mVideoPath);

                // Retrieve the list of subtitles files already associated with the video
                mCurrentFilesCount = buildCurrentSubtitlesFilesList(mVideoPath);
                if (DBG) Log.d(TAG, "onCreate : mCurrentFilesCount = " + mCurrentFilesCount);

                // Get the list of subtitles files available in the current folder
                mAvailableFilesCount = buildAvailableSubtitlesFilesList(videoFile.getParent());
                if (DBG) Log.d(TAG, "onCreate : mAvailableFilesCount = " + mAvailableFilesCount);
            }
        }
        else {
            // Bad intent
            Log.e(TAG, "onCreate error : no folder provided");
            mVideoUri = null;
        }

        // Use the name of the video to build the help message displayed at the top of the screen
        TextView helpMessageHeader = (TextView) findViewById(R.id.help_message_header);
        String name = FileUtils.getFileNameWithoutExtension(mVideoUri);

        String helpMessage;
        if (mAvailableFilesCount == 0 && mCurrentFilesCount == 0) {
            helpMessage = getString(R.string.subtitles_wizard_empty_list_help).replace("%s", name);
        }
        else {
            helpMessage = getString(R.string.subtitles_wizard_help).replace("%s", name);
        }
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
        enableEmptyView(mAvailableFilesCount == 0 && mCurrentFilesCount == 0);
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
        File file = new File(itemData.path);
        menu.setHeaderTitle(file.getName());

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
        int currentListItemsCount = Math.max(mCurrentFilesCount, 1);
        int availableListItemsCount = Math.max(mAvailableFilesCount, 1);

        // Add the separators
        return currentListItemsCount + availableListItemsCount + 2;
    }

    /*
     * Returns data for the item at the provided position
     * NOTE : keep all knowledge of the ListView contents here!
     */
    private ItemData getItemData(int position) {
        ItemData data = new ItemData();

        int currentListItemsCount = Math.max(mCurrentFilesCount, 1);

        if (position == 0 || position == currentListItemsCount + 1) {
            data.type = ITEM_DATA_TYPE_SEPARATOR;
            data.index = 0;
            data.path = null;
        }
        else if (position <= currentListItemsCount) {
            if (mCurrentFilesCount > 0) {
                data.type = ITEM_DATA_TYPE_CURRENT;
                data.index = position - 1;
                data.path =  mCurrentFiles.get(data.index);
            }
            else {
                data.type = ITEM_DATA_TYPE_MESSAGE;
                data.index = 0;
                data.path = null;
            }
        }
        else {
            if (mAvailableFilesCount > 0) {
                data.type = ITEM_DATA_TYPE_AVAILABLE;
                data.index = position - currentListItemsCount - 2;
                data.path =  mAvailableFiles.get(data.index);
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

    private int buildCurrentSubtitlesFilesList(String videoPath) {
        if (DBG) Log.d(TAG, "buildCurrentSubtitlesFilesList : get current subtitles files");

        mCurrentFiles = new ArrayList<String>();

        // Get subtitles files from MetaDataRetriever
        IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(getBaseContext());

        try {
            retriever.setDataSource(videoPath);

            // Get first the number of subtitles files
            String data = retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_NB_SUBTITLE_TRACK);
            int subtitlesCount = (data != null) ? Integer.parseInt(data) : 0;

            // Retrieve the path of each file found and add it to the available subtitles list
            if (subtitlesCount > 0) {
                int i;
                int key;
                String path;

                for (i = 0; i < subtitlesCount; i++) {
                    // Only take into account the file if its path is valid (an empty path means that
                    // the subtitles are embedded in the video and do not correspond to an external file)
                    key = IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_MAX * i;
                    path = retriever.extractMetadata(key + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_PATH);
                    if (!path.isEmpty()) {
                        mCurrentFiles.add(path);
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "buildCurrentSubtitlesFilesList error : failed to get data from MetadataRetriever");
        }
        retriever.release();

        return mCurrentFiles.size();
    }

    private int buildAvailableSubtitlesFilesList(String folderPath) {
        if (DBG) Log.d(TAG, "buildAvailableSubtitlesFilesList : search subtitles files in folder " + folderPath);

        mAvailableFiles = new ArrayList<String>();

        // Make sure the provided path corresponds to a folder
        File folder = new File(folderPath);
        if (!folder.isDirectory()) {
            return 0;
        }

        // Get the list of entries contained in the provided directory
        File[] files = null;
        try {
            files = folder.listFiles();
        }
        catch (SecurityException e) {
            Log.e(TAG, "buildAvailableSubtitlesFilesList error : can not get list of files");
            return 0;
        }

        // Check the entries and keep only those which correspond to subtitles files
        for (File f : files) {
            // Make sure this is a file
            if (f.isFile()) {
                // Ignore files starting with a dot
                if (!f.getName().startsWith(".")) {
                    // Check the file extension
                    String path = f.getPath();
                    String extension = getExtension(path);
                    if ((extension != null) && VideoUtils.getSubtitleExtensions().contains(extension)) {
                        // We found a subtitles file => add it to the available list
                        // if it is not already associated to the selected video
                        int i;
                        boolean fileAlreadyAssociated = false;

                        for (i = 0; i < mCurrentFilesCount; i++) {
                            String pathToCheck = mCurrentFiles.get(i);
                            if (pathToCheck.equals(path)) {
                                // This file already belongs to the current list of subtitles files => skip it
                                fileAlreadyAssociated = true;
                                break;
                            }
                        }

                        if (!fileAlreadyAssociated) {
                            mAvailableFiles.add(f.getPath());
                        }
                    }
                }
            }
        }

        if (DBG) Log.d(TAG, "Found " + mAvailableFiles.size() + " subtitles files out of " + files.length);
        return mAvailableFiles.size();
    }

    private String getExtension(String filename) {
        int dotPos = filename.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < filename.length()) {
            return filename.substring(dotPos + 1).toLowerCase();
        }
        return null;
    }

    /*
     * Builds a new name for a subtitles file when it is associated to the selected video
     */
    private String buildSubtitlesFilename(String subtitlesPath) {
        int i;
        int count;
        int maxCount = 0;
        boolean sameNameFound = false;

        // Split the path of the provided subtitles file
        String subtitlesExtension = getExtension(subtitlesPath);
        // String subtitlesName;
        // if (subtitlesExtension != null) {
        //     subtitlesName = subtitlesPath.substring(0, subtitlesPath.length() - subtitlesExtension.length() - 1);
        // }
        // else {
        //     subtitlesName = subtitlesPath;
        // }

        // Split the path of the selected video
        String videoExtension = getExtension(mVideoPath);
        String videoName;
        if (videoExtension != null) {
            videoName = mVideoPath.substring(0, mVideoPath.length() - videoExtension.length() - 1);
        }
        else {
            videoName = mVideoPath;
        }
        int videoNameLength = videoName.length();

        // Compare the name of the selected video to the name of all current subtitle files
        // in order to check if there is a suffix after the name
        for (i = 0; i < mCurrentFilesCount; i++) {
            // Remove the extension
            String path = mCurrentFiles.get(i);
            String extension = getExtension(path);
            if (extension != null) {
                String name = path.substring(0, path.length() - extension.length() - 1);
                int nameLength = name.length();
                if (nameLength >= videoNameLength && name.startsWith(videoName)) {
                    sameNameFound = true;
                    String suffix = name.substring(videoNameLength);
                    if (suffix.startsWith(SUBTITLES_FILES_SUFFIX)) {
                        // cs should correspond to a number (for instance path="Big Buck Bunny.17.srt" => cs="17"
                        String cs = suffix.substring(SUBTITLES_FILES_SUFFIX.length());
                        try   {
                            // valid index, remember the highest value
                            count = Integer.parseInt(cs);
                            if (count > maxCount) {
                                maxCount = count;
                            }
                        }
                        catch (NumberFormatException e) {
                            // Invalid format  => ignore this file
                        }
                    }
                }
            }
        }

        // Build a name for the provided subtitles file starting with the name of the video
        if (sameNameFound) {
            return (videoName + SUBTITLES_FILES_SUFFIX + String.valueOf(maxCount + 1) + "." + subtitlesExtension);
        }
        else {
            return (videoName + "." + subtitlesExtension);
        }
    }

    private void renameFile(int position) {
        ItemData itemData = getItemData(position);

        // Only files from the available list can be renamed
        if (itemData.type == ITEM_DATA_TYPE_AVAILABLE) {
            boolean fileRenamed = false;

            // Find a name for this file
            String oldFilePath = itemData.path;
            String newFilePath = buildSubtitlesFilename(oldFilePath);

            File oldFile = new File(oldFilePath);
            File newFile = new File(newFilePath);
            // Rename the file
            try {
                fileRenamed = oldFile.renameTo(newFile);
                if (DBG) Log.d(TAG, "onItemClick : selected file renamed as " + newFilePath);
            }
            catch (SecurityException e) {
                Log.d(TAG, "renameFile : can not rename file as " + newFilePath);
            }

            if (fileRenamed) {
                // Update the medialib
                Uri oldUri = Uri.fromFile(oldFile);
                Intent intent1 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, oldUri);
                intent1.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent1);
                Uri newUri = Uri.fromFile(newFile);
                Intent intent2 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, newUri);
                intent2.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent2);

                if (DBG) Log.d(TAG, "rescanning Video: " + mVideoUri.toString());
                Intent intent3 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mVideoUri);
                intent3.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent3);

                // Update the local data : add the file to current subtitles list
                // and remove it from the available subtitles list
                mCurrentFiles.add(newFilePath);
                mCurrentFilesCount++;
                mAvailableFiles.remove(itemData.index);
                mAvailableFilesCount--;

                // Update the activity screen
                mListView.invalidateViews();
            }
        }
    }

    private void deleteFile(int position) {
        ItemData itemData = getItemData(position);

        if (itemData.type == ITEM_DATA_TYPE_CURRENT || itemData.type == ITEM_DATA_TYPE_AVAILABLE) {
            boolean fileDeleted = false;

            File file = new File(itemData.path);
            try {
                fileDeleted = file.delete();
                if (DBG) Log.d(TAG, "deleteFile : file " + itemData.path + " deleted");
            }
            catch (SecurityException e) {
                Log.d(TAG, "deleteFile : can not delete file " + itemData.path);
            }

            if (fileDeleted) {
                // Update the medialib
                Uri uri = Uri.fromFile(file);
                Intent intent1 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
                intent1.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent1);

                if (DBG) Log.d(TAG, "rescanning Video: " + mVideoUri.toString());
                Intent intent2 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mVideoUri);
                intent2.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent2);
                // Update the local data : remove the file from the list it belongs
                if (itemData.type == ITEM_DATA_TYPE_CURRENT) {
                    mCurrentFiles.remove(itemData.index);
                    mCurrentFilesCount--;
                }
                else {
                    mAvailableFiles.remove(itemData.index);
                    mAvailableFilesCount--;
                }

                if (mCurrentFilesCount == 0 && mAvailableFilesCount == 0) {
                    // The user deleted the last subtitles file of the folder
                    enableEmptyView(true);
                }

                // Update the activity screen
                mListView.invalidateViews();
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
                    File file = new File(itemData.path);
                    if (file != null) {
                        size = Formatter.formatFileSize(mActivity, file.length());
                    }
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
                    File file = new File(itemData.path);
                    text = file.getName();
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
