package com.colinwhite.ping.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * The service which allows the SyncAdapter framework to access the Authenticator.
 */
public class PingAuthenticatorService extends Service {
	// Instance field that stores the authenticator object.
	private PingAuthenticator authenticator;

	@Override
	public void onCreate() {
		// Create a new authenticator object.
		authenticator = new PingAuthenticator(this);
	}

	/*
	 * When the system binds to this Service to make the RPC call return the authenticator's IBinder.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return authenticator.getIBinder();
	}
}
