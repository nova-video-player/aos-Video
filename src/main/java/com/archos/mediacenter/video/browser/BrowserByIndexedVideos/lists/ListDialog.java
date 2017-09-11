package com.archos.mediacenter.video.browser.BrowserByIndexedVideos.lists;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.ListsLoader;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;

/**
 * Created by alexandre on 16/05/17.
 */

public class ListDialog extends DialogFragment {
    public static final String EXTRA_VIDEO = "video";
    private Video mVideo;
    private Adapter mAdapter;

    private class Adapter extends CursorAdapter{

        private final int mTitleIndex;

        public Adapter(Context context, Cursor c, boolean autoRequery) {
            super(context, c, autoRequery);
            mTitleIndex = getCursor().getColumnIndex(VideoStore.List.Columns.TITLE);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
            TextView tv = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.browser_item_list_text, null);
            return tv;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(position<getCount()-1)
                return super.getView(position, convertView, parent);
            else {
                if(convertView == null)
                    convertView = newView(getContext(), getCursor(), parent);
                bindView(convertView,getContext(),null);
                return convertView;
            }
        }

        @Override
        public int getCount(){
            return super.getCount()+1;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            if(cursor==null)
                ((TextView)view).setText(R.string.create_new_list);
            else
                ((TextView)view).setText(cursor.getString(mTitleIndex));
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mVideo = (Video) getArguments().get(EXTRA_VIDEO);
        return getAlertDialog();
    }

    private AlertDialog getAlertDialog(){
        return new AlertDialog.Builder(getActivity()).setAdapter(getAdapter(), getOnClickListener()).create();
    }

    private DialogInterface.OnClickListener getOnClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if(i==mAdapter.getCount()-1){
                    //add new list
                    NewListDialog listDialog = new NewListDialog();
                    listDialog.setArguments(getArguments());
                    listDialog.show(getFragmentManager(), "");
                }
                else{
                    int id = mAdapter.getCursor().getInt(mAdapter.getCursor().getColumnIndex(VideoStore.List.Columns.ID));
                    BaseTags metadata = mVideo.getFullScraperTags(getActivity());
                    boolean isEpisode = metadata instanceof EpisodeTags;
                    VideoStore.VideoList.VideoItem videoItem  =
                            new VideoStore.VideoList.VideoItem(-1,!isEpisode?(int)metadata.getOnlineId():-1, isEpisode?(int)metadata.getOnlineId():-1, VideoStore.List.SyncStatus.STATUS_NOT_SYNC);
                    getActivity().getContentResolver().insert(VideoStore.List.getListUri(id), videoItem.toContentValues());
                    TraktService.sync(ArchosUtils.getGlobalContext(), TraktService.FLAG_SYNC_AUTO);

                }
            }
        };
    }

    private Adapter getAdapter() {

        mAdapter = new Adapter(getContext(), new ListsLoader(getContext()).loadInBackground(), true);
        return mAdapter;
    }
}
