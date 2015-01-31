package com.colinwhite.ping.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.colinwhite.ping.data.PingContract.MonitorEntry;

public class MonitorProvider extends ContentProvider {
    // The URI Matcher used by this content provider.
    private static final UriMatcher mUriMatcher = buildUriMatcher();
    private static final int MONITOR = 0;
    private static final int MONITOR_BY_ID = 1;
    // SQL commands
    private static final String[] mProjection = {
            PingContract.MonitorEntry._ID,
            PingContract.MonitorEntry.TITLE,
            PingContract.MonitorEntry.URL,
            PingContract.MonitorEntry.PING_FREQUENCY,
            PingContract.MonitorEntry.END_DATE};
    private static final String mSortOrder = PingContract.MonitorEntry._ID + " ASC";
    private static PingDbHelper mDbHelper;

    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = PingContract.CONTENT_AUTHORITY;

        matcher.addURI(authority, PingContract.PATH_MONITOR, MONITOR);
        matcher.addURI(authority, PingContract.PATH_MONITOR + "/#", MONITOR_BY_ID);

        return matcher;
    }

    @Override
    public boolean onCreate() {
        mDbHelper = new PingDbHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Here's the switch statement that, given a URI, will determine what kind of request it is,
        // and query the database accordingly.
        final Cursor retCursor;

        switch (mUriMatcher.match(uri)) {
            case MONITOR:
                retCursor = mDbHelper.getReadableDatabase().query(
                        PingContract.MonitorEntry.TABLE_NAME,
                        mProjection,
                        null, null, null, null,
                        mSortOrder);
                break;
            case MONITOR_BY_ID:
                retCursor = mDbHelper.getReadableDatabase().query(
                        PingContract.MonitorEntry.TABLE_NAME,
                        mProjection,
                        selection,
                        selectionArgs,
                        null, null,
                        mSortOrder);
                break;
            default:
                throw new UnsupportedOperationException("Unknown URI: " + uri);
        }

        // Register a content observer so the Cursor can re-query when the database changes.
        retCursor.registerContentObserver(new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                retCursor.requery();
            }

            @Override
            public boolean deliverSelfNotifications() {
                return true;
            }
        });
        retCursor.setNotificationUri(getContext().getContentResolver(), uri);

        return retCursor;
    }

    @Override
    public String getType(Uri uri) {
        // Use the Uri Matcher to determine what kind of URI this is.
        final int match = mUriMatcher.match(uri);

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
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        final int match = mUriMatcher.match(uri);
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
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        final int match = mUriMatcher.match(uri);
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
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        final int match = mUriMatcher.match(uri);
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
