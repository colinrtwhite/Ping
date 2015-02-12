package com.colinwhite.ping.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * PingSyncService exists as a Service only to be bound to PingSyncAdapter, which lets
 * PingSyncAdapter be run.
 */
public class PingSyncService extends Service {
    private static PingSyncAdapter mPingSyncAdapter = null;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mPingSyncAdapter == null) {
            mPingSyncAdapter = new PingSyncAdapter(getApplicationContext(), true);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mPingSyncAdapter.getSyncAdapterBinder();
    }
}