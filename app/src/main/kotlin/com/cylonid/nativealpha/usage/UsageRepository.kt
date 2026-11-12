package com.cylonid.nativealpha.usage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Stores foreground intervals per web app in SQLite. All web apps run in the main process,
 * so a single helper instance is enough.
 */
class UsageRepository private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "usage.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, webapp_id INTEGER NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_sessions_app_time ON sessions(webapp_id, start_ms)")
        db.execSQL("CREATE INDEX idx_sessions_end ON sessions(end_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Opens a session; returns its row id. */
    fun startSession(webAppId: Int, nowMs: Long): Long =
        writableDatabase.insert("sessions", null, ContentValues().apply {
            put("webapp_id", webAppId); put("start_ms", nowMs); put("end_ms", nowMs)
        })

    /** Extends a session's end time (heartbeat or close). */
    fun updateSessionEnd(sessionId: Long, endMs: Long) {
        writableDatabase.update("sessions", ContentValues().apply { put("end_ms", endMs) }, "id = ?", arrayOf(sessionId.toString()))
    }

    /** Intervals overlapping [from, to), optionally restricted to one web app. */
    fun intervals(from: Long, to: Long, webAppId: Int? = null): List<UsageInterval> {
        val where = StringBuilder("end_ms > ? AND start_ms < ?")
        val args = mutableListOf(from.toString(), to.toString())
        if (webAppId != null) { where.append(" AND webapp_id = ?"); args += webAppId.toString() }
        readableDatabase.query("sessions", arrayOf("webapp_id", "start_ms", "end_ms"), where.toString(), args.toTypedArray(), null, null, "start_ms").use { c ->
            val out = ArrayList<UsageInterval>(c.count)
            while (c.moveToNext()) out += UsageInterval(c.getInt(0), c.getLong(1), c.getLong(2))
            return out
        }
    }

    fun totalForDay(webAppId: Int, nowMs: Long): Long {
        val from = UsageMath.startOfDay(nowMs)
        val to = UsageMath.startOfNextDay(nowMs)
        return UsageMath.clippedTotal(intervals(from, to, webAppId), from, to)
    }

    /** Total per web app over [from, to). */
    fun totalsByApp(from: Long, to: Long): Map<Int, Long> =
        intervals(from, to).groupBy { it.webAppId }.mapValues { (_, v) -> UsageMath.clippedTotal(v, from, to) }

    fun deleteForWebApp(webAppId: Int) {
        writableDatabase.delete("sessions", "webapp_id = ?", arrayOf(webAppId.toString()))
    }

    /** Drops data older than [keepDays] days. */
    fun prune(nowMs: Long, keepDays: Int = 90) {
        writableDatabase.delete("sessions", "end_ms < ?", arrayOf((nowMs - keepDays * 86_400_000L).toString()))
    }

    companion object {
        @Volatile private var instance: UsageRepository? = null

        @JvmStatic
        fun get(context: Context): UsageRepository =
            instance ?: synchronized(this) { instance ?: UsageRepository(context).also { instance = it } }
    }
}
