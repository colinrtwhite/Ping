package com.colinwhite.ping.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PingSyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();
    private static PingSyncAdapter sSunshineSyncAdapter = null;

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock) {
            if (sSunshineSyncAdapter == null) {
                sSunshineSyncAdapter = new PingSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSunshineSyncAdapter.getSyncAdapterBinder();
    }
}