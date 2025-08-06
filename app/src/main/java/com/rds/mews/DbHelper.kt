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
            private const val SOURCE = "source"
            private const val SOURCE_NAME = "name"
            private const val MESS = "message"
            private const val FACTS = "facts"

            private const val FACTS_NAME = "facts"
            private const val FACTS_ID = "id"
            private const val MESS_LINK = "mess_id"
            private const val FACTS_TEXT = "text"

            private const val TITLES_NAME = "titles"
            private const val TITLES_ID = "id"
            private const val TITLES_TIME = "time"
            private const val TITLES_TEXT = "text"
            private const val TITLES_MESSAGES = "messages"
            private const val TITLES_FACTS = "facts"
        }

    override fun onCreate(db: SQLiteDatabase?) {
        db!!.execSQL("CREATE TABLE $MESS_NAME ($MESS_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                " $MESS_TIME TEXT, $SOURCE TEXT, $SOURCE_NAME TEXT, $MESS TEXT, $FACTS TEXT)")
        db.execSQL("CREATE TABLE $FACTS_NAME ($FACTS_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$MESS_LINK INTEGER, $FACTS_TEXT TEXT)")
        db.execSQL("CREATE TABLE $TITLES_NAME ($TITLES_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                " $TITLES_TIME TEXT, $TITLES_TEXT TEXT, $TITLES_MESSAGES TEXT, $TITLES_FACTS TEXT)")
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        p1: Int,
        p2: Int
    ) {
        db!!.execSQL("DROP TABLE IF EXISTS $MESS_NAME")
        db.execSQL("DROP TABLE IF EXISTS $FACTS_NAME")
        db.execSQL("DROP TABLE IF EXISTS $TITLES_NAME")
        onCreate(db)
    }

    fun dbPack(vararg ids: Long): String {
        return when (ids.isEmpty()) {
            true -> ""
            else -> {
                ids.joinToString(separator = ", ")
            }
        }
    }

    fun dbUnpack(str: String): List<Long> {
        val res = mutableListOf<Long>()
        if (str.split(", ").lastIndex != 0) {
            for (i in str.split(", ")) res.add(i.toLong())
        }

        return res
    }

    private fun linkFacts(messID: Long, factID: Long): Boolean {
        val db = this.writableDatabase
        val cursor = db.rawQuery("SELECT $FACTS FROM $MESS_NAME WHERE $MESS_ID = ?", arrayOf(messID.toString()))
        val result: List<Long>? = try { dbUnpack(cursor.getString(cursor.getColumnIndexOrThrow(FACTS))) }
        catch (e: Exception) { null }
        finally { cursor.close() }

        val values = ContentValues()
        when (result) {
            null -> values.apply {
                put(FACTS, dbPack(factID))
            }
            else -> {
                values.apply {
                    put(FACTS, dbPack(*result.toLongArray(), factID))
                }
            }
        }
        val rows: Boolean = db.update(MESS_NAME, values, "$MESS_ID = ?", arrayOf(messID.toString())) > 0

//        values.clear()
//        values.apply { put(MESS_LINK, messID) }
//        db.update(FACTS_NAME, values, "$FACTS_ID = ?", arrayOf(factID.toString()))

        cursor.close()
        db.close()
        return rows
    }

    fun addFacts(messID: Long, factText: String): Boolean {
        val db = this.writableDatabase
        val cursor = db.rawQuery("SELECT * FROM $MESS_NAME WHERE $MESS_ID = ?", arrayOf(messID.toString()))
        var flag = true
        if (cursor.moveToFirst()) {
            val values = ContentValues().apply {
                put(MESS_LINK, messID)
                put(FACTS_TEXT, factText)
            }
            val factID = db.insert(FACTS_NAME, null, values)
            flag = when (factID) {
                -1L -> false
                else -> linkFacts(messID, factID)
            }
        }
        else flag = false

        cursor.close()
        db.close()
        return flag
    }

    fun findMessage(source: String, sourceName: String, message: String): Long? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $MESS_ID FROM $MESS_NAME WHERE $SOURCE = ? AND" +
                " $SOURCE_NAME = ? AND $MESS = ?", arrayOf(source, sourceName, message))
        val result: Long? = try {
            cursor.getLong(cursor.getColumnIndexOrThrow(MESS_ID))
        } catch (e: Exception) {
            null
        } finally {
            cursor.close()
            db.close()
        }

        return result
    }

    fun findMessageByID(id: Long): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $MESS FROM $MESS_NAME WHERE $MESS_ID = ?", arrayOf(id.toString()))
        val result: Boolean = try {
            cursor.moveToFirst()
        } catch (e: Exception) {
            false
        } finally {
            cursor.close()
            db.close()
        }

        return result
    }

    fun findFactByID(id: Long): String? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $FACTS_TEXT FROM $FACTS_NAME WHERE $FACTS_ID = ?", arrayOf(id.toString()))
        val result: String? = try {
            cursor.getString(cursor.getColumnIndexOrThrow(FACTS_TEXT))
        } catch (e: Exception) {
            null
        } finally {
            cursor.close()
            db.close()
        }

        return result
    }

    fun addMessage(messageTime: String, source: String, sourceName: String, messageText: String, facts: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(MESS_TIME, messageTime)
            put(SOURCE, source)
            put(SOURCE_NAME, sourceName)
            put(MESS, messageText)
            put(FACTS, facts)
        }

        val messID = findMessage(source, sourceName, messageText)
        val result =  when (messID) {
            null -> db.insert(MESS_NAME, null, values)
            else -> messID
        }
        db.close()

        return result
    }

    fun delMessage(id: Long): Boolean {
        val db = this.writableDatabase
        val flag = db.delete(MESS_NAME, "$MESS_ID = ?", arrayOf(id.toString())) > 0
        db.close()

        return flag
    }

    fun delFact(id: Long): Boolean {
        val db = this.writableDatabase
        val flag = db.delete(FACTS_NAME, "$FACTS_ID = ?", arrayOf(id.toString())) > 0
        db.close()

        return flag
    }
}