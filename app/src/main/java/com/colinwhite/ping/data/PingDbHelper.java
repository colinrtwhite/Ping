package com.colinwhite.ping.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.colinwhite.ping.data.PingContract.MonitorEntry;

public class PingDbHelper extends SQLiteOpenHelper {
    // If we change the database schema, need to increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Ping.db";

    public PingDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        final String COMMA_SEP = ",";
        final String TEXT_TYPE = " TEXT";
        final String INTEGER_TYPE = " INTEGER";
        final String LONG_TYPE = " LONG";
        final String BOOLEAN_TYPE = " BOOLEAN";
        final String NOT_NULL = " NOT NULL";
        final String SQL_CREATE_ENTRIES = "CREATE TABLE " + MonitorEntry.TABLE_NAME + " (" +
                MonitorEntry._ID + INTEGER_TYPE + " PRIMARY KEY" + COMMA_SEP +
                MonitorEntry.TITLE + TEXT_TYPE + NOT_NULL + COMMA_SEP +
                MonitorEntry.URL + TEXT_TYPE + NOT_NULL + COMMA_SEP +
                MonitorEntry.PING_FREQUENCY + INTEGER_TYPE + NOT_NULL + COMMA_SEP +
                MonitorEntry.END_TIME + LONG_TYPE + COMMA_SEP +
                MonitorEntry.TIME_LAST_CHECKED + LONG_TYPE + COMMA_SEP +
                MonitorEntry.STATUS + INTEGER_TYPE + COMMA_SEP +
                MonitorEntry.LAST_NON_ERROR_STATUS + INTEGER_TYPE + COMMA_SEP +
                MonitorEntry.IS_LOADING + BOOLEAN_TYPE + ")";

        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Currently we just delete all entries and recreate the table on upgrade.
        final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + MonitorEntry.TABLE_NAME;
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Using upgrade, we delete all entries and recreate the table on downgrade.
        onUpgrade(db, oldVersion, newVersion);
    }
}
