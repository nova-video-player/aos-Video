package com.archos.mediacenter.video.browser

import android.annotation.SuppressLint
import android.content.Context
import android.database.AbstractCursor
import android.database.Cursor
import android.database.MergeCursor
import android.os.HandlerThread
import android.util.Log
import androidx.loader.content.Loader
import io.requery.android.database.sqlite.SQLiteDatabase
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Handler
import com.archos.mediacenter.video.browser.loader.VideoLoader

object FullTextSearch {
    val initStarted = AtomicBoolean(false)
    val inited = AtomicBoolean(false)
    var db: SQLiteDatabase? = null
    var ctxt: WeakReference<Context>? = null

    val fields = listOf(
        "_id", // f._id
        // show fields
        "s_id", "actors", "s_plot", "s_writers", "s_directors", "s_name", "s_actors",
        // episode fields
        "e_season", "e_episode", "e_plot", "e_actors", "e_writers", "e_directors", "e_name",
        // movie fields
        "title", "m_name", "m_year", "m_actors", "m_directors", "m_writers", "m_studios", "m_plot",
    )

    val handler = Handler(HandlerThread("FullTextSearch").apply { start() }.looper)

    fun init(ctxt: Context) {
        FullTextSearch.ctxt = WeakReference(ctxt)

        handler.post {
            try {
                initStarted.set(true)
Log.e("PHH", "Full text search init")
                // Construct an in-memory database for the full-text search
                db = SQLiteDatabase.create(null)
                db!!.execSQL("CREATE VIRTUAL TABLE video_fts USING fts5(" + fields.joinToString(", ") + ")")

                val originalDb = SQLiteDatabase.openDatabase(
                    ctxt.getDatabasePath("media.db").canonicalPath,
                    null,
                    SQLiteDatabase.OPEN_READONLY)

                val request = "SELECT " + fields.joinToString(", ") + " FROM video"
                val cursor = originalDb.rawQuery(request, null)

                val nRows = cursor.count
                var progress = 0

                val insert = "INSERT INTO video_fts (" + fields.joinToString(", ") + ") VALUES (" + fields.map { "?" }.joinToString(", ") + ")"
                while (cursor.moveToNext()) {
                    // Getting as string is fine since a FTS table only stores strings anyway
                    val values = fields.map { val idx = cursor.getColumnIndex(it); cursor.getString(idx) }
                    db!!.execSQL(insert, values.toTypedArray())
                    progress++

                    if ( (progress * nRows / 100) > ((progress-1) * nRows / 100) ) {
                        Log.d("PHH", "Full-text search initialization: $progress%")
                    }
                }

                cursor.close()

                Log.e("PHH", "Full text search init done")
                inited.set(true)
            } catch (e: Exception) {
                Log.d("PHH", "Error initializing full-text search", e)
            }
        }
    }
    @SuppressLint("StaticFieldLeak")
    fun query(query: String): Loader<Cursor> {
        return object: Loader<Cursor>(ctxt!!.get()!!) {
            override fun onStartLoading() {
                handler.post {
                    val cursor = db!!.rawQuery("SELECT _id FROM video_fts WHERE video_fts MATCH ?", arrayOf(query))

                    val originalDb = SQLiteDatabase.openDatabase(
                        ctxt!!.get()!!.getDatabasePath("media.db").canonicalPath,
                        null,
                        SQLiteDatabase.OPEN_READONLY)
                    val res = mutableListOf<Cursor>()
                    while (cursor.moveToNext()) {
                        res.add(originalDb.rawQuery("SELECT " + VideoLoader.mProjection.joinToString(", ") + " FROM video WHERE _id = ?", arrayOf(cursor.getString(0))))
                    }
                    deliverResult(MergeCursor(res.toTypedArray()))
                }
            }

            override fun onForceLoad() {
                deliverResult(null)
            }
        }
    }
}