package com.colinwhite.ping.data;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.colinwhite.ping.data.PingContract.MonitorEntry;

// Contains all the operations required to read/add/delete Monitors from the database. Runs on a
// background thread to prevent UI lag.
public class DataService extends IntentService {
    // Required. Denotes which operation to perform.
    public static final String OPERATION_ID = "OPERATION";
    // List of supported operations.
    public static final String OPERATION_ADD_MONITOR = "OPERATION_ADD_MONITOR";
    public static final String OPERATION_DELETE_MONITOR = "OPERATION_DELETE_MONITOR";
    public static final String OPERATION_GET_MONITOR_BY_ID = "OPERATION_GET_MONITOR_BY_ID";
    public static final String OPERATION_GET_ALL_MONITORS = "OPERATION_GET_ALL_MONITORS";

    // SQL commands
    private static final String[] mProjection = {
            MonitorEntry._ID,
            MonitorEntry.TITLE,
            MonitorEntry.URL,
            MonitorEntry.PING_FREQUENCY,
            MonitorEntry.END_DATE};
    private static final String mSortOrder = MonitorEntry._ID + " DESC";

    // Necessary to access the database.
    private static PingDbHelper mDbHelper;

    public Cursor mCursor;

    public DataService() {
        super("DataService");
    }

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public DataService(String name) {
        super(name);
    }

    // Determine which operation needs to be called.
    @Override
    protected void onHandleIntent(Intent intent) {
        // OPERATION_ID is required.
        if (!intent.hasExtra(OPERATION_ID)) {
            Log.e("DataService", "Intent was passed to DataService without OPERATION_ID extra.");
            return;
        }

        // Initialise the database helper.
        mDbHelper = new PingDbHelper(this);

        // Call the required operation. Necessary read/write data should be stored in the intent.
        switch (intent.getStringExtra(OPERATION_ID)) {
            case OPERATION_ADD_MONITOR:
                addMonitor(intent);
                break;
            case OPERATION_DELETE_MONITOR:
                deleteMonitor(intent);
                break;
            case OPERATION_GET_MONITOR_BY_ID:
                getMonitorById(intent);
                break;
            case OPERATION_GET_ALL_MONITORS:
                getAllMonitors();
                break;
            default:
                Log.e("DataService", "Intent was passed to DataService with unrecognised operation.");
                return;
        }
    }

    private void addMonitor(Intent intent) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(MonitorEntry.TITLE, intent.getStringExtra(MonitorEntry.TITLE));
        values.put(MonitorEntry.URL, intent.getStringExtra(MonitorEntry.URL));
        values.put(MonitorEntry.PING_FREQUENCY, intent.getIntExtra(MonitorEntry.PING_FREQUENCY, 0));
        // End date does not have to exist. The Monitor can run indefinitely.
        long endDate = intent.getLongExtra(MonitorEntry.END_DATE, 0);
        if (endDate != 0) {
            values.put(MonitorEntry.END_DATE, intent.getLongExtra(MonitorEntry.END_DATE, 0));
        }

        db.insert(MonitorEntry.TABLE_NAME, null, values);
    }

    private void deleteMonitor(Intent intent) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        String selection = MonitorEntry._ID + "= ?";
        String[] selectionArgs = {String.valueOf(intent.getIntExtra(MonitorEntry._ID, 0))};

        db.delete(MonitorEntry.TABLE_NAME, selection, selectionArgs);
    }

    private void getMonitorById(Intent intent) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        String selection = MonitorEntry._ID + " = ?";
        String[] selectionArgs = {String.valueOf(intent.getIntExtra(MonitorEntry._ID, 0))};

        mCursor = db.query(MonitorEntry.TABLE_NAME, mProjection, selection, selectionArgs, null, null, mSortOrder);
    }

    private void getAllMonitors() {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        mCursor = db.query(MonitorEntry.TABLE_NAME, mProjection, null, null, null, null, mSortOrder);
    }
}
