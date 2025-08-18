package com.rds.mews

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        companion object {
            private const val DB_VERSION = 1
            private const val DB_NAME = "MainDB"

            private const val MESS_NAME = "messages"
            private const val MESS_ID = "id"
            private const val MESS_TIME = "time"
            private const val MESS_LINK = "link"
            private const val MESS_SOURCE = "source"
            private const val MESS = "message"

            private const val TITLES_NAME = "titles"
            private const val TITLES_ID = "id"
            private const val TITLES_TIME = "time"
            private const val TITLE = "title"
            private const val TITLES_TEXT = "text"
            private const val TITLES_SOURCES = "sources"
            private const val TITLES_LINKS = "messages"

            private const val RSS_NAME = "rss"
            private const val RSS_ID = "id"
            private const val RSS_SOURCE = "source"
            private const val RSS_LINK = "link"
        }

    override fun onCreate(db: SQLiteDatabase?) {
        db.let {
            db!!.execSQL("CREATE TABLE IF NOT EXISTS $MESS_NAME ($MESS_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    " $MESS_TIME INTEGER, $MESS_SOURCE TEXT, $MESS_LINK TEXT, $MESS TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TITLES_NAME ($TITLES_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$TITLES_TIME INTEGER, $TITLE TEXT, $TITLES_TEXT TEXT, $TITLES_SOURCES TEXT, $TITLES_LINKS TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $RSS_NAME ($RSS_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    " $RSS_SOURCE TEXT, $RSS_LINK TEXT)")
        }
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        p1: Int,
        p2: Int
    ) {
        db!!.execSQL("DROP TABLE IF EXISTS $MESS_NAME")
        db.execSQL("DROP TABLE IF EXISTS $TITLES_NAME")
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
        if (str.split(", ").lastIndex != 0) {
            for (i in str.split(", ")) res.add(i.trim())
        }

        return res
    }

    @Synchronized
    fun findMessage(sourceName: String, mess: String): Message? {
        val db = this.readableDatabase
        val query = "SELECT * FROM $MESS_NAME WHERE $MESS_SOURCE = ? AND $MESS = ?"
        val args = arrayOf(sourceName, mess)

        return db.rawQuery(query, args).use {
            cursor ->
            if (cursor.moveToFirst()) {
                val messageId = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_ID))
                val messageTime = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_TIME))
                val messageLink = cursor.getString(cursor.getColumnIndexOrThrow(MESS_LINK))
                val messageSource = cursor.getString(cursor.getColumnIndexOrThrow(MESS_SOURCE))
                val messageText = cursor.getString(cursor.getColumnIndexOrThrow(MESS))

                Message(messageId, messageTime, messageLink, messageSource, messageText)
            }
            else { null }
        }
    }

    @Synchronized
    fun findRSS(sourceName: String, sourceLink: String = ""): RSS? {
        val db = this.readableDatabase
        val query = "SELECT * FROM $RSS_NAME WHERE $RSS_SOURCE = ? OR $RSS_LINK = ?"
        val args = arrayOf(sourceName, sourceLink)

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
    fun getMessage(id: Long): Message? {
        val db = this.readableDatabase
        val query = "SELECT * FROM $MESS_NAME WHERE $MESS_ID = ?"
        val args = arrayOf(id.toString())

        return db.rawQuery(query, args).use {
            cursor ->
            if (cursor.moveToFirst()) {
                val messageId = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_ID))
                val messageTime = cursor.getLong(cursor.getColumnIndexOrThrow(MESS_TIME))
                val messageLink = cursor.getString(cursor.getColumnIndexOrThrow(MESS_LINK))
                val messageSource = cursor.getString(cursor.getColumnIndexOrThrow(MESS_SOURCE))
                val messageText = cursor.getString(cursor.getColumnIndexOrThrow(MESS))

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
        var query = "SELECT * FROM $MESS_NAME"
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
                    val mess = cursor.getString(cursor.getColumnIndexOrThrow(MESS))

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
        var query = "SELECT * FROM $TITLES_NAME"
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
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(TITLE))
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
        var query = "SELECT * FROM $RSS_NAME"
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
    fun findMessageByID(id: Long): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $MESS_ID FROM $MESS_NAME WHERE $MESS_ID = ?", arrayOf(id.toString()))

        return cursor.use {
            it.moveToFirst()
        }
    }

    @Synchronized
    fun findTitleByID(id: Long): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $TITLES_ID FROM $TITLES_NAME WHERE $TITLES_ID = ?", arrayOf(id.toString()))

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
            put(MESS, messageText)
        }

        val mess = findMessage(source, messageText)
        val result = when (mess) {
            null -> db.insert(MESS_NAME, null, values)
            else -> mess.id
        }

        return result
    }

    @Synchronized
    fun addTitle(titleTime: Long, title: String, text: String, sources: String, links: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(TITLES_TIME , titleTime)
            put(TITLE, title)
            put(TITLES_TEXT, text)
            put(TITLES_SOURCES, sources)
            put(TITLES_LINKS, links)
        }

        println("$title\n$text")
        return db.insert(TITLES_NAME, null, values)
    }

    @Synchronized
    fun addRSS(source: String, link: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(RSS_SOURCE, source)
            put(RSS_LINK, link)
        }

        val rssItem = findRSS(source, link)
        val result = when(rssItem) {
            null -> db.insert(RSS_NAME, null, values)
            else -> rssItem.id
        }

        return result
    }


    @Synchronized
    fun delMessage(id: Long): Boolean {
        val db = this.writableDatabase
        val flag = db.delete(MESS_NAME, "$MESS_ID = ?", arrayOf(id.toString())) > 0

        return flag
    }

    @Synchronized
    fun delTitle(id: Long): Boolean {
        val db = this.writableDatabase
        val flag = db.delete(TITLES_NAME, "$TITLES_ID = ?", arrayOf(id.toString())) > 0

        return flag
    }

    @Synchronized
    fun delRSS(source: String? = null, id: Long? = null): Boolean {
        val db = this.writableDatabase
        val flag = if (source == null && id == null) false
        else {
            if (source != null) db.delete(RSS_NAME, "$RSS_SOURCE = ?", arrayOf(source)) > 0
            else db.delete(RSS_NAME, "$RSS_ID = ?", arrayOf(id.toString())) > 0
        }

        return flag
    }

    @Synchronized
    fun changeRssSource(oldSource: String, newSource: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(RSS_SOURCE, newSource)
        }

        return when (findRSS(oldSource)) {
            null -> false
            else -> {
                db.update(RSS_NAME, values, "$RSS_SOURCE = ?", arrayOf(oldSource)) > 0
            }
        }
    }

    @Synchronized
    fun messageTimeKill(timeSeconds: Long): Int {
        val db = this.writableDatabase
        val killTime = System.currentTimeMillis() - timeSeconds * 1000

        return db.delete(MESS_NAME,
            "$MESS_TIME < ?",
            arrayOf(killTime.toString()))
    }

    @Synchronized
    fun titlesTimeKill(timeSeconds: Long): Int {
        val db = this.writableDatabase
        val killTime = System.currentTimeMillis() - timeSeconds * 1000

        return db.delete(TITLES_NAME,
            "$TITLES_TIME < ?",
            arrayOf(killTime.toString()))
    }

    @Synchronized
    fun getTitleLinks(id: Long): Pair<String, String>? {
        val db = this.readableDatabase
        when (findTitleByID(id)) {
            true -> {
                val cursor = db.rawQuery("SELECT $TITLES_LINKS, $TITLES_SOURCES FROM $TITLES_NAME WHERE $TITLES_ID = ?", arrayOf(id.toString()))
                return cursor.use {
                    cursor.moveToFirst()
                    val links = cursor.getString(cursor.getColumnIndexOrThrow(TITLES_LINKS))
                    val sources = cursor.getString(cursor.getColumnIndexOrThrow(TITLES_SOURCES))

                    var linksStr = ""
                    dbUnpack(links).forEach {
                        linksStr = "$linksStr\n${getMessage(it.toLong())?.link ?: "Ссылка не найдена"}"
                    }
                    var sourcesStr = ""
                    dbUnpack(sources).forEach {
                        val rss = getRSS(it.toLong())
                        if (rss.isNotEmpty()) {sourcesStr = "$sourcesStr, ${rss[0].source}"}
                    }
                    if (sourcesStr == "") sourcesStr = "Источники не найдены"

                    Pair(sources, links)
                }

            }
            false -> return null
        }
    }
}