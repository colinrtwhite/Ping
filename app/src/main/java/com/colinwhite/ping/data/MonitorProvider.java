package com.colinwhite.ping.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.colinwhite.ping.data.PingContract.MonitorEntry;

/**
 * Monitor Provider is the ContentProvider for Monitors. It should be accessed through the
 * ContentResolver using MonitorEntry.CONTENT_URI as a base URI.
 */
public class MonitorProvider extends ContentProvider {
    // The URI Matcher used by this content provider.
    private static final UriMatcher uriMatcher = buildUriMatcher();
    private static final int MONITOR = 0;
    private static final int MONITOR_BY_ID = 1;

    // SQL-related objects (needs to follow what is laid out in MonitorEntry),
    private static final String[] projection = {
            MonitorEntry._ID,
            MonitorEntry.TITLE,
            MonitorEntry.URL,
            MonitorEntry.PING_FREQUENCY,
            MonitorEntry.END_TIME,
            MonitorEntry.TIME_LAST_CHECKED,
            MonitorEntry.STATUS,
            MonitorEntry.LAST_NON_ERROR_STATUS,
            MonitorEntry.IS_LOADING};
    private static PingDbHelper dbHelper;

    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = PingContract.CONTENT_AUTHORITY;

        matcher.addURI(authority, PingContract.PATH_MONITOR, MONITOR);
        matcher.addURI(authority, PingContract.PATH_MONITOR + "/#", MONITOR_BY_ID);

        return matcher;
    }

    @Override
    public boolean onCreate() {
        dbHelper = new PingDbHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Here's the switch statement that, given a URI, will determine what kind of request it is,
        // and query the database accordingly.
        final Cursor retCursor;

        switch (uriMatcher.match(uri)) {
            case MONITOR:
                retCursor = dbHelper.getReadableDatabase().query(
                        PingContract.MonitorEntry.TABLE_NAME,
                        MonitorProvider.projection,
                        null, null, null, null,
                        sortOrder);
                break;
            case MONITOR_BY_ID:
                retCursor = dbHelper.getReadableDatabase().query(
                        PingContract.MonitorEntry.TABLE_NAME,
                        MonitorProvider.projection,
                        selection,
                        selectionArgs,
                        null, null,
                        sortOrder);
                break;
            default:
                throw new UnsupportedOperationException("Unknown URI: " + uri);
        }

        // Set the notification URI so the Cursor can re-query when the database changes.
        retCursor.setNotificationUri(getContext().getContentResolver(), uri);

        return retCursor;
    }

    @Override
    public String getType(Uri uri) {
        // Use the Uri Matcher to determine what kind of URI this is.
        final int match = uriMatcher.match(uri);

        switch (match) {
            case MONITOR:
                return MonitorEntry.CONTENT_TYPE;
            case MONITOR_BY_ID:
                return MonitorEntry.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = uriMatcher.match(uri);
        Uri returnUri;

        switch (match) {
            case MONITOR:
                long _id = db.insert(MonitorEntry.TABLE_NAME, null, values);
                returnUri = MonitorEntry.buildUri(_id);
                break;
            default:
                throw new UnsupportedOperationException("Unknown URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return returnUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = uriMatcher.match(uri);
        int rowsDeleted;

        switch (match) {
            case MONITOR:
                rowsDeleted = db.delete(MonitorEntry.TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown URI: " + uri);
        }

        // Because a NULL deletes all rows.
        if (selection == null || rowsDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsDeleted;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = uriMatcher.match(uri);
        int rowsUpdated;

        switch (match) {
            case MONITOR:
                rowsUpdated = db.update(MonitorEntry.TABLE_NAME, values, selection, selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown URI: " + uri);
        }

        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsUpdated;
    }
}
