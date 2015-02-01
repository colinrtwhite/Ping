package com.colinwhite.ping.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.colinwhite.ping.R;
import com.colinwhite.ping.data.PingContract.MonitorEntry;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.regex.Pattern;

public class PingSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final int SYNC_INTERVAL = 1;
    private static final int SYNC_FLEXTIME = SYNC_INTERVAL / 2;
    private static final String HOST = "http://www.downforeveryoneorjustme.com/";
    public static final int[] PING_FREQUENCY_MINUTES = {1, 5, 15, 30, 60, 120, 240, 720, 1440};

    // Status codes for MonitorEntry.STATUS
    public static final int IS_UP = 0;
    public static final int IS_DOWN = 1;
    public static final int DOES_NOT_EXIST = 2;
    public static final int OTHER = 3;

    // The SQL selection string is always the same.
    private static final String mSelection = MonitorEntry._ID + " = ?";

    Pattern mUp, mDown, mDoesNotExist;
    ContentResolver mContentResolver;

    public PingSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContentResolver = context.getContentResolver();

        mUp = Pattern.compile("It's just you.");
        mDown = Pattern.compile("It's not just you!");
        mDoesNotExist = Pattern.compile("doesn't look like a site on the interwho.");
    }

    @Override
    public void onPerformSync(Account account,
                              Bundle extras,
                              String authority,
                              ContentProviderClient provider,
                              SyncResult syncResult) {
        // Recover the URL from the intent and get the returned HTML from our request.
        String url = extras.getString(MonitorEntry.URL);
        String html = getHtml(url);

        final String[] selectionArgs = { String.valueOf(extras.getInt(MonitorEntry._ID)) };

        // If there is no network connection then we can't sync.
        if (!isNetworkConnected()) {
            return;
        }

        int status = OTHER;
        if (mUp.matcher(html).find()) {
            status = IS_UP;
        } else if (mDown.matcher(html).find()) {
            status = IS_DOWN;
        } else if (mDoesNotExist.matcher(html).find()) {
            status = DOES_NOT_EXIST;
        }

        // Update the server's status, and the time last checked.
        ContentValues values = new ContentValues();
        values.put(MonitorEntry.STATUS, status);
        values.put(MonitorEntry.TIME_LAST_CHECKED, Calendar.getInstance().getTimeInMillis());

        mContentResolver.update(MonitorEntry.CONTENT_URI, values, mSelection, selectionArgs);
    }


    /**
     * Send an HTTP request to the HOST and return its HTML.
     *
     * @param url The URL the check through the HOST.
     * @return A long string of HTML all on one line. On error, returns an empty string.
     */
    private String getHtml(String url) {
        try {
            // Build the client and the request
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(HOST + url);
            HttpResponse response = client.execute(request);

            // Read and store the result line by line then return the entire string.
            InputStream in = response.getEntity().getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder html = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                html.append(line);
            }
            in.close();

            return html.toString();
        } catch (IOException e) {
            Log.e("PingService", "getHtml failed; error: " + e.toString());
        }

        return "";
    }

    /**
     * Check if we the device is connected to any network (and thus, if we can have a connection to
     * the Internet).
     */
    private boolean isNetworkConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        // If info is null, there are no active networks.
        return (info != null);
    }

    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The Context used to access the account service.
     * @param url The URL that the account is tied to.
     * @return The account tied to the provided URL.
     */
    public static Account getSyncAccount(Context context, String url) {
        // Get an instance of the Android account manager.
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account.
        Account newAccount = new Account(url, context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist.
        if (null == accountManager.getPassword(newAccount)) {
            // Add the account and account type, no password or user data
            // If successful, return the Account object, otherwise report an error.
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            onAccountCreated(context, newAccount, url);
        }
        return newAccount;
    }

    /**
     * Helper method to schedule the sync adapter periodic execution.
     */
    public static void configurePeriodicSync(Context context, String url, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context, url);
        String authority = context.getString(R.string.content_authority);
        Bundle bundle = new Bundle();
        bundle.putString(MonitorEntry.URL, url);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // We can enable inexact timers in our periodic sync.
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority)
                    .setExtras(bundle).build();

            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account, authority, bundle, syncInterval);
        }
    }

    private static void onAccountCreated(Context context, Account newAccount, String url) {
        // Since we've created an account.
        PingSyncAdapter.configurePeriodicSync(context, url, SYNC_INTERVAL, SYNC_FLEXTIME);

        // Without calling setSyncAutomatically, our periodic sync will not be enabled.
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);

        // Finally, let's do a sync to get things started.
        syncImmediately(context, url);
    }

    /**
     * Helper method to have the sync adapter sync immediately.
     * @param context The context used to access the account service
     * @param url The URL that the account is tied to.
     */
    public static void syncImmediately(Context context, String url) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putString(MonitorEntry.URL, url);
        ContentResolver.requestSync(getSyncAccount(context, url),
                context.getString(R.string.content_authority), bundle);
    }
}