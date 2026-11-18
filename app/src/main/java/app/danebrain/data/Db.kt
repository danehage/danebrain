package app.danebrain.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One database for the capture queue and the search index. */
class Db(context: Context) : SQLiteOpenHelper(context, "danebrain.db", null, 1) {

    /** True when the full-text table is FTS5 (ranked results); false when it fell back to FTS4. */
    var fts5: Boolean = true
        private set

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE captures (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                user_text TEXT NOT NULL DEFAULT '',
                pages TEXT NOT NULL DEFAULT '[]',
                status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                analysis TEXT,
                error TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE notes (
                path TEXT PRIMARY KEY,
                stem TEXT NOT NULL,
                title TEXT NOT NULL,
                mtime INTEGER NOT NULL,
                size INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX notes_stem ON notes(stem)")
        try {
            db.execSQL("CREATE VIRTUAL TABLE fts USING fts5(path UNINDEXED, title, body, tokenize='porter unicode61')")
        } catch (e: Exception) {
            db.execSQL("CREATE VIRTUAL TABLE fts USING fts4(path, title, body, notindexed=path, tokenize=porter)")
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        db.rawQuery("SELECT sql FROM sqlite_master WHERE name = 'fts'", null).use { c ->
            if (c.moveToFirst()) fts5 = c.getString(0).contains("fts5", ignoreCase = true)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
