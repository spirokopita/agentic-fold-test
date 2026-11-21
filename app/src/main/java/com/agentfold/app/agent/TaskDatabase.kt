package com.agentfold.app.agent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class TaskDatabase(context: Context) : SQLiteOpenHelper(context, "tasks.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, created INTEGER NOT NULL, objective TEXT NOT NULL, state TEXT NOT NULL, summary TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun recoverInterrupted() {
        writableDatabase.execSQL("UPDATE tasks SET state='INTERRUPTED', summary='Process ended; restart explicitly. Control is OFF.' WHERE state='RUNNING'")
    }
    fun create(objective: String): String {
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("tasks", null, ContentValues().apply {
            put("id", id); put("created", System.currentTimeMillis()); put("objective", objective)
            put("state", "RUNNING"); put("summary", "")
        })
        writableDatabase.execSQL("DELETE FROM tasks WHERE id NOT IN (SELECT id FROM tasks ORDER BY created DESC LIMIT 100)")
        return id
    }
    fun finish(id: String, state: String, summary: String) {
        writableDatabase.update("tasks", ContentValues().apply { put("state", state); put("summary", summary.take(12000)) }, "id=?", arrayOf(id))
    }
    fun history(): String = readableDatabase.rawQuery("SELECT created, objective, state, summary FROM tasks ORDER BY created DESC LIMIT 20", null).use { c ->
        buildString { while (c.moveToNext()) append("${java.util.Date(c.getLong(0))}\n${c.getString(2)}: ${c.getString(1)}\n${c.getString(3)}\n\n") }
    }
    fun clearHistory() { writableDatabase.delete("tasks", null, null) }
}
