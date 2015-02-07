package com.colinwhite.ping;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;

import com.colinwhite.ping.data.PingContract;
import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.sync.PingSyncAdapter;

public class SyncRemovalService extends IntentService {
    final String[] mProjection = {PingContract.MonitorEntry.URL};
    final String mSelection = PingContract.MonitorEntry._ID + " = ?";

    public SyncRemovalService() { super(SyncRemovalService.class.getName()); }
    public SyncRemovalService(String name) { super(name); }

    @Override
    protected void onHandleIntent(Intent intent) {
        int monitorId = intent.getIntExtra(PingContract.MonitorEntry._ID, -1);
        if (monitorId < 0) {
            return;
        }

        // Get the specific Monitor's data.
        final String[] selectionArgs = {String.valueOf(monitorId)};
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(
                MonitorEntry.CONTENT_URI.buildUpon().appendPath(String.valueOf(monitorId)).build(),
                mProjection,
                mSelection,
                selectionArgs,
                null);
        cursor.moveToFirst();

        // Turn off the expiry date and set the ping frequency to not update automatically.
        ContentValues values = new ContentValues();
        values.put(MonitorEntry.PING_FREQUENCY, MonitorEntry.PING_FREQUENCY_MAX);
        values.put(MonitorEntry.END_TIME, MonitorEntry.END_TIME_NONE);

        // Sync one last time.
        String url = cursor.getString(cursor.getColumnIndex(MonitorEntry.URL));
        PingSyncAdapter.syncImmediately(this, PingSyncAdapter.getSyncAccount(this), url, monitorId);

        // Remove the periodic sync.
        PingSyncAdapter.removePeriodicSync(
                this,
                url,
                monitorId);
        cursor.close();

        contentResolver.update(MonitorEntry.CONTENT_URI, values, mSelection, selectionArgs);
    }
}
