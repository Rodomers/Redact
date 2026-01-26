package com.rds.mews.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.RSS
import com.rds.mews.localcore.Title

class DbHelper(val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        companion object {
            private const val DB_VERSION = 1
            private const val DB_NAME = "MainDB"

            private const val MESSAGES = "messages"
            private const val MESS_ID = "id"
            private const val MESS_TIME = "time"
            private const val MESS_LINK = "link"
            private const val MESS_SOURCE = "source"
            private const val MESS_TEXT = "message"

            private const val TITLES = "titles"
            private const val TITLES_ID = "id"
            private const val TITLES_TIME = "time"
            private const val TITLE_NAME = "title"
            private const val TITLES_TEXT = "text"
            private const val TITLES_SOURCES = "sources"
            private const val TITLES_LINKS = "messages"

            private const val RSS = "rss"
            private const val RSS_ID = "id"
            private const val RSS_SOURCE = "source"
            private const val RSS_LINK = "link"
        }

    override fun onCreate(db: SQLiteDatabase?) {
        db.let {
            db!!.execSQL("CREATE TABLE IF NOT EXISTS $MESSAGES ($MESS_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    " $MESS_TIME INTEGER, $MESS_SOURCE TEXT, $MESS_LINK TEXT, $MESS_TEXT TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TITLES ($TITLES_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$TITLES_TIME INTEGER, $TITLE_NAME TEXT, $TITLES_TEXT TEXT, $TITLES_SOURCES TEXT, $TITLES_LINKS TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $RSS ($RSS_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    " $RSS_SOURCE TEXT, $RSS_LINK TEXT)")

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_mess_source ON $MESSAGES($MESS_SOURCE)")
        }
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        p1: Int,
        p2: Int
    ) {
        db!!.execSQL("DROP TABLE IF EXISTS $MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TITLES")
        onCreate(db)
    }

    fun dbPack(vararg ids: String): String {
        return when (ids.isEmpty()) {
            true -> ""
            else -> {
                ids.joinToString(separator = ", ").trim()
            }
        }
    }

    fun dbUnpack(str: String): List<String> {
        val res = mutableListOf<String>()
        if (str.split(", ").isNotEmpty()) {
            for (i in str.split(", ")) res.add(i.trim())
        }

        return res
    }

    @Synchronized
    fun findMessage(sourceName: String, mess: String): Message? {
        val db = this.readableDatabase
        val query = "SELECT * FROM $MESSAGES WHERE $MESS_SOURCE = ? AND $MESS_TEXT = ?"
        val args = arrayOf(sourceName, mess)

        return db.rawQuery(query, args).use {
            cursor ->
            if (cursor.moveToFirst()) {
                val messageId = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_ID))
                val messageTime = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_TIME))
                val messageLink = cursor.getString(cursor.getColumnIndexOrThrow(MESS_LINK))
                val messageSource = cursor.getString(cursor.getColumnIndexOrThrow(MESS_SOURCE))
                val messageText = cursor.getString(cursor.getColumnIndexOrThrow(MESS_TEXT))

                Message(messageId, messageTime, messageLink, messageSource, messageText)
            }
            else { null }
        }
    }

    @Synchronized
    fun findRSS(sourceName: String = "", sourceLink: String = "", id: Long? = null): RSS? {
        val db = this.readableDatabase
        val query = "SELECT * FROM $RSS WHERE $RSS_SOURCE = ? OR $RSS_LINK = ? OR $RSS_ID = ?"
        val args = arrayOf(sourceName, sourceLink, id.toString())

        return db.rawQuery(query, args).use { cursor ->
            if (cursor.moveToFirst()) {
                val rssId = cursor.getLong(cursor.getColumnIndexOrThrow(RSS_ID))
                val rssSource = cursor.getString(cursor.getColumnIndexOrThrow(RSS_SOURCE))
                val rssLink = cursor.getString(cursor.getColumnIndexOrThrow(RSS_LINK))

                RSS(rssId, rssSource, rssLink)
            } else { null }
        }
    }

    @Synchronized
    fun getMessage(id: Long?): Message? {
        val db = this.readableDatabase
        val query = "SELECT * FROM $MESSAGES WHERE $MESS_ID = ?"
        val args = arrayOf(id.toString())

        return db.rawQuery(query, args).use {
            cursor ->
            if (cursor.moveToFirst()) {
                val messageId = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_ID))
                val messageTime = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_TIME))
                val messageLink = cursor.getString(cursor.getColumnIndexOrThrow(MESS_LINK))
                val messageSource = cursor.getString(cursor.getColumnIndexOrThrow(MESS_SOURCE))
                val messageText = cursor.getString(cursor.getColumnIndexOrThrow(MESS_TEXT))

                Message(messageId, messageTime, messageLink, messageSource, messageText)
            } else {
                null
            }
        }
    }

    @Synchronized
    fun getMessages(timeSeconds: Long? = null): List<Message> {
        val db = this.readableDatabase
        val list = mutableListOf<Message>()
        var query = "SELECT * FROM $MESSAGES"
        var args: Array<String>?
        if (timeSeconds != null) {
            query = "$query WHERE $MESS_TIME > ?"
            args = arrayOf((System.currentTimeMillis() - timeSeconds * 1000).toString())
        }
        else {args = null}

        db.rawQuery(query, args).use {
            cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_ID))
                    val time = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_TIME))
                    val link = cursor.getString(cursor.getColumnIndexOrThrow(MESS_LINK))
                    val source = cursor.getString(cursor.getColumnIndexOrThrow(MESS_SOURCE))
                    val mess = cursor.getString(cursor.getColumnIndexOrThrow(MESS_TEXT))

                    list.add(Message(id, time, link, source, mess))
                } while (cursor.moveToNext())
            }
        }

        return list
    }

    @Synchronized
    fun getTitles(timeSeconds: Long? = null, earlier: Boolean = false): List<Title> {
        val db = this.readableDatabase
        val list = mutableListOf<Title>()
        var query = "SELECT * FROM $TITLES"
        var args: Array<String>?
        if (timeSeconds != null) {
            query = when (earlier) {
                true -> "$query WHERE $TITLES_TIME < ?"
                else -> "$query WHERE $TITLES_TIME > ?"
            }
            args = arrayOf((System.currentTimeMillis() - timeSeconds * 1000L).toString())
        }
        else { args = null }
        query = "$query ORDER BY $TITLES_TIME ASC"

        db.rawQuery(query, args).use {
                cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(TITLES_ID))
                    val time = cursor.getLong(cursor.getColumnIndexOrThrow(TITLES_TIME))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(TITLE_NAME))
                    val text = cursor.getString(cursor.getColumnIndexOrThrow(TITLES_TEXT))
                    val sources = cursor.getString(cursor.getColumnIndexOrThrow(TITLES_SOURCES))
                    val links = cursor.getString(cursor.getColumnIndexOrThrow(TITLES_LINKS))

                    list.add(Title(id, time, title, text, sources, links))
                } while (cursor.moveToNext())
            }
        }

        return list
    }

    @Synchronized
    fun getRSS(id: Long? = null): List<RSS> {
        val db = this.readableDatabase
        val list = mutableListOf<RSS>()
        var query = "SELECT * FROM $RSS"
        if (id != null) query = "$query WHERE id = $id"

        db.rawQuery(query, null).use {
            cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(RSS_ID))
                    val source = cursor.getString(cursor.getColumnIndexOrThrow(RSS_SOURCE))
                    val link = cursor.getString(cursor.getColumnIndexOrThrow(RSS_LINK))

                    list.add(RSS(id, source, link))
                } while (cursor.moveToNext())
            }
        }

        return list
    }

    @Synchronized
    fun findTitleByID(id: Long): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $TITLES_ID FROM $TITLES WHERE $TITLES_ID = ?", arrayOf(id.toString()))

        return cursor.use {
            it.moveToFirst()
        }
    }

    @Synchronized
    fun addMessage(messageTime: Long, link: String, source: String, messageText: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(MESS_TIME, messageTime)
            put(MESS_LINK, link)
            put(MESS_SOURCE, source)
            put(MESS_TEXT, messageText)
        }

        val mess = findMessage(source, messageText)
        val result = when (mess) {
            null -> db.insert(MESSAGES, null, values)
            else -> mess.id
        }

        return result
    }

    @Synchronized
    fun addTitle(titleTime: Long, title: String, text: String, sources: String, links: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(TITLES_TIME , titleTime)
            put(TITLE_NAME, title)
            put(TITLES_TEXT, text)
            put(TITLES_SOURCES, sources)
            put(TITLES_LINKS, links)
        }

        println("$title\n$text")
        return db.insert(TITLES, null, values)
    }

    @Synchronized
    fun addRSS(source: String, link: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(RSS_SOURCE, source)
            put(RSS_LINK, link)
        }

        val result = when(val rssItem = findRSS(source, link)) {
            null -> db.insert(RSS, null, values)
            else -> rssItem.id
        }

        return result
    }


    @Synchronized
    fun delMessage(id: Long): Boolean {
        val db = this.writableDatabase
        val flag = db.delete(MESSAGES, "$MESS_ID = ?", arrayOf(id.toString())) > 0

        return flag
    }

    @Synchronized
    fun delTitle(id: Long? = null, name: String? = null): Boolean {
        val db = this.writableDatabase
        val qPart = if (id != null) "$TITLES_ID = ?" else "$TITLE_NAME = ?"
        return when (id) {
            null -> if (name != null) db.delete(TITLES, qPart, arrayOf(name)) > 0 else false
            else -> db.delete(TITLES, qPart, arrayOf(id.toString())) > 0
        }
    }

    @Synchronized
    fun delRSS(source: String? = null, id: Long? = null): Boolean {
        val db = this.writableDatabase
        val flag = if (source == null && id == null) false
        else {
            if (source != null) db.delete(RSS, "$RSS_SOURCE = ?", arrayOf(source)) > 0
            else db.delete(RSS, "$RSS_ID = ?", arrayOf(id.toString())) > 0
        }

        return flag
    }

    @Synchronized
    fun changeRssSource(id: Long, newSource: String): Boolean {
        val db = this.writableDatabase

        val rss = findRSS(id = id) ?: return false
        val oldSource = rss.source

        val queryTitles = "SELECT $TITLES_ID, $TITLES_SOURCES FROM $TITLES WHERE $TITLES_SOURCES LIKE ?"
        val cursor = db.rawQuery(queryTitles, arrayOf("%$oldSource%"))

        cursor.use { c ->
            if (c.moveToFirst()) {
                val idIndex = c.getColumnIndexOrThrow(TITLES_ID)
                val sourcesIndex = c.getColumnIndexOrThrow(TITLES_SOURCES)

                do {
                    val tId = c.getLong(idIndex)
                    val rawSources = c.getString(sourcesIndex)
                    val sourceList = dbUnpack(rawSources)
                    if (sourceList.contains(oldSource)) {
                        val newSourceList = sourceList.map { if (it == oldSource) newSource else it }
                        val newPackedSources = dbPack(*newSourceList.toTypedArray())
                        val titleValues = ContentValues().apply {
                            put(TITLES_SOURCES, newPackedSources)
                        }
                        db.update(TITLES, titleValues, "$TITLES_ID = ?", arrayOf(tId.toString()))
                    }
                } while (c.moveToNext())
            }
        }

        val values = ContentValues().apply {
            put(RSS_SOURCE, newSource)
            put(MESS_SOURCE, newSource)
        }

        db.update(MESSAGES, values, "$MESS_SOURCE = ?", arrayOf(oldSource))
        return db.update(RSS, values, "$RSS_SOURCE = ?", arrayOf(oldSource)) > 0
    }

    @Synchronized
    fun updateTitle(
        id: Long,
        name: String,
        newText: String,
        newSources: String,
        newLinks: String,
        newTime: Long
    ): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(TITLE_NAME, name)
            put(TITLES_TEXT, newText)
            put(TITLES_SOURCES, newSources)
            put(TITLES_LINKS, newLinks)
            put(TITLES_TIME, newTime)
        }

        return db.update(TITLES, values, "$TITLES_ID = ?", arrayOf(id.toString())) > 0
    }

    @Synchronized
    fun messageTimeKill(timeSeconds: Long): Int {
        val db = this.writableDatabase
        val killTime = System.currentTimeMillis() - timeSeconds * 1000

        return db.delete(MESSAGES,
            "$MESS_TIME < ?",
            arrayOf(killTime.toString()))
    }

    @Synchronized
    fun titlesTimeKill(timeSeconds: Long): Int {
        val db = this.writableDatabase
        val killTime = System.currentTimeMillis() - timeSeconds * 1000

        return db.delete(TITLES,
            "$TITLES_TIME < ?",
            arrayOf(killTime.toString()))
    }

    @Synchronized
    fun getMessagesForSource(sourceName: String): List<String> {
        val db = this.readableDatabase
        val descriptions = mutableListOf<String>()
        val query = "SELECT $MESS_TEXT FROM $MESSAGES WHERE $MESS_SOURCE = ?"
        val args = arrayOf(sourceName)

        db.rawQuery(query, args).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val description = cursor.getString(cursor.getColumnIndexOrThrow(MESS_TEXT))
                    descriptions.add(description)
                } while (cursor.moveToNext())
            }
        }
        return descriptions
    }
}