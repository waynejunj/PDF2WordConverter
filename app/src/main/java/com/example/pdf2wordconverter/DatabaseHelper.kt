package com.example.pdf2wordconverter

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "pdf_converter.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_HISTORY = "conversion_history"
        private const val COLUMN_ID = "id"
        private const val COLUMN_FILE_NAME = "file_name"
        private const val COLUMN_INPUT_PATH = "input_path"
        private const val COLUMN_OUTPUT_PATH = "output_path"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_STATUS = "status"

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FILE_NAME TEXT NOT NULL,
                $COLUMN_INPUT_PATH TEXT NOT NULL,
                $COLUMN_OUTPUT_PATH TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    fun addConversion(history: ConversionHistory): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FILE_NAME, history.fileName)
            put(COLUMN_INPUT_PATH, history.inputPath)
            put(COLUMN_OUTPUT_PATH, history.outputPath)
            put(COLUMN_TIMESTAMP, history.timestamp)
            put(COLUMN_STATUS, history.status)
        }
        return db.insert(TABLE_HISTORY, null, values)
    }

    fun getAllConversions(): List<ConversionHistory> {
        val conversions = mutableListOf<ConversionHistory>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                conversions.add(
                    ConversionHistory(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        fileName = it.getString(it.getColumnIndexOrThrow(COLUMN_FILE_NAME)),
                        inputPath = it.getString(it.getColumnIndexOrThrow(COLUMN_INPUT_PATH)),
                        outputPath = it.getString(it.getColumnIndexOrThrow(COLUMN_OUTPUT_PATH)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        status = it.getString(it.getColumnIndexOrThrow(COLUMN_STATUS))
                    )
                )
            }
        }
        return conversions
    }

    fun deleteConversion(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun clearAllHistory(): Int {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, null, null)
    }
}
