package com.colinwhite.ping.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * PingSyncService exists as a Service only to be bound to PingSyncAdapter, which lets
 * PingSyncAdapter be run.
 */
public class PingSyncService extends Service {
	private static PingSyncAdapter pingSyncAdapter;

	@Override
	public void onCreate() {
		super.onCreate();
		if (pingSyncAdapter == null) {
			pingSyncAdapter = new PingSyncAdapter(getApplicationContext());
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return pingSyncAdapter.getSyncAdapterBinder();
	}
}
