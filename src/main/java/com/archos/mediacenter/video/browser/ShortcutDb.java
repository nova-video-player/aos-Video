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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public enum ShortcutDb {

    STATIC();

    private static final String TAG = "ShortcutDb";
    protected final static boolean DBG = false;

    private static final String DATABASE_NAME = "shortcuts2_db";
    private static final String TABLE_NAME = "shortcuts";

    // To be incremented each time the architecture of the database is changed
    private static final int DATABASE_VERSION = 1;

    public static final String KEY_URI = "uri";
    public static final String KEY_SHORTCUT_NAME = "shortcut_name";
    private static final String[] SHORTCUT_COLS = { BaseColumns._ID, KEY_URI, KEY_SHORTCUT_NAME };

    private DatabaseHelper mDbHelper;

    private SQLiteDatabase mDb;

    /**
     * get a CursorLoader to get all columns of all shortcuts
     * @param context
     * @return
     */
    public CursorLoader getAllShortcutsCursorLoader(Context context) {
        return new CursorLoader(context) {
            @Override
            public Cursor loadInBackground() {
                return getCursorAllShortcuts(getContext());
            }
        };
    }

    public long isShortcut(Context context, String path) {
        SQLiteDatabase db = new DatabaseHelper(context).getReadableDatabase();
        Cursor c = db.query(TABLE_NAME,
                SHORTCUT_COLS,
                KEY_URI+" = ?",
                new String[] {path},
                null,
                null,
                null);
        c.moveToFirst();
        long id=-1;
        if (c.getCount()>0) {
            id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
        }
        c.close();
        return id;
    }




    public class Shortcut implements Serializable{
        public String name;
        public String uri;
    }
    /**
     * get Cursor with all columns of all shortcuts
     * @param context
     * @return
     */
    public Cursor getCursorAllShortcuts(Context context) {
        //mContext = context;
        mDbHelper = new DatabaseHelper(context);
        mDb = mDbHelper.getWritableDatabase();

        try {
            return mDb.query(TABLE_NAME,
                    SHORTCUT_COLS,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        catch (SQLiteException e) {
            // The table corresponding to this type does not exist yet
            Log.w(TAG, e);
            return null;
        }
    }

    /**
     * get a CursorLoader to get all columns of all shortcuts
     * @param context
     * @return
     */
    public List<Shortcut> getAllShortcuts(Context context) {
        Cursor shortcutsCursor = getCursorAllShortcuts(context);
        List<Shortcut> shortcuts = new ArrayList<Shortcut>();
        if (shortcutsCursor == null)
            return shortcuts;
        int nameColumn = shortcutsCursor.getColumnIndex(KEY_SHORTCUT_NAME);
        int uriColumn = shortcutsCursor.getColumnIndex(KEY_URI);
        if (shortcutsCursor.getCount()>0){
            shortcutsCursor.moveToFirst();
            do {
                Shortcut shortcut = new Shortcut();
                shortcut.uri = shortcutsCursor.getString(uriColumn);
                shortcut.name = shortcutsCursor.getString(nameColumn);
                shortcuts.add(shortcut);
            } while(shortcutsCursor.moveToNext());
        }
        shortcutsCursor.close();
        return shortcuts;
    }

    /**
     * Insert a new shortcut into the database
     * @param uri: string obtained with android.net.Uri.toString()
     * @param name
     * @return true if the insert succeeded
     */
    public boolean insertShortcut(Uri uri, String name) {
        Log.d(TAG, "insertShortcut "+uri+" "+name);
        ContentValues initialValues = new ContentValues(2);
        initialValues.put(KEY_URI, uri.toString());
        initialValues.put(KEY_SHORTCUT_NAME, name);

        return (mDb.insert(TABLE_NAME, null, initialValues)!=-1);
    }

    /**
     * Remove the shortcut(s) corresponding to the provided Uri
     * 
     * @param uri 
     * @return the number of shortcuts removed
     */
    public int removeShortcut(Uri uri) {
        final String selection = KEY_URI+"=?";
        final String[] selectionArgs = new String[] {uri.toString()};

        int result = mDb.delete(TABLE_NAME, selection, selectionArgs);
        Log.d(TAG, "removeShortcut "+uri.toString()+" mDb.delete returns "+result);

        return result;
    }

    /**
     * Remove the shortcut(s) corresponding to the provided DB id
     * @return true if it erased something
     */
    public boolean removeShortcut(long id) {
        final String selection = BaseColumns._ID+"=?";
        final String[] selectionArgs = new String[] {Long.toString(id)};

        int result = mDb.delete(TABLE_NAME, selection, selectionArgs);
        Log.d(TAG, "removeShortcut "+id+" mDb.delete returns "+result);

        return result>0;
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // This method is only called once when the database is created for the first time
            db.execSQL("create table " + TABLE_NAME + "( "
                    + BaseColumns._ID + " integer primary key autoincrement, "
                    + KEY_URI + " text not null, "
                    + KEY_SHORTCUT_NAME + " text not null );");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {            
        }
    }
}
