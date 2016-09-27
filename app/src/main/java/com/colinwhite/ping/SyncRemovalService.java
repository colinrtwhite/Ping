package com.colinwhite.ping;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;

import com.colinwhite.ping.data.PingContract;
import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.sync.PingSyncAdapter;

/**
 * SyncRemovalService is scheduled in MonitorDetailActivity to run at a user-set date and time. When
 * run, it sets the Monitor with the ID given in its starting intent to the maximum ping frequency
 * SeekBar value (i.e. don't automatically ping), resets any expiration date data to "no expiration
 * date", removes the periodic sync set in PingSyncAdapter, and finally triggers a PingSyncAdapter
 * manual refresh.
 */
public class SyncRemovalService extends IntentService {
	private final String[] projection = {PingContract.MonitorEntry.URL};

	public SyncRemovalService() {
		super(SyncRemovalService.class.getName());
	}

	public SyncRemovalService(String name) {
		super(name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// If we're not given a Monitor ID then there is nothing we can do.
		int monitorId = intent.getIntExtra(PingContract.MonitorEntry._ID, -1);
		if (monitorId < 0) {
			return;
		}

		// Get the specific Monitor's data.
		final String[] selectionArgs = {String.valueOf(monitorId)};
		ContentResolver contentResolver = getContentResolver();
		String selection = MonitorEntry._ID + " = ?";
		Cursor cursor = contentResolver.query(
				MonitorEntry.buildUri(monitorId),
				projection,
				selection,
				selectionArgs,
				null);
		if (cursor == null) {
			return;
		}
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

		contentResolver.update(MonitorEntry.CONTENT_URI, values, selection, selectionArgs);
	}
}
