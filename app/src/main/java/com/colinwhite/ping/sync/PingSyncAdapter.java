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
import android.os.Build;
import android.os.Bundle;

import com.colinwhite.ping.R;
import com.colinwhite.ping.Utility;
import com.colinwhite.ping.data.PingContract.MonitorEntry;

import java.util.Calendar;
import java.util.regex.Pattern;

public class PingSyncAdapter extends AbstractThreadedSyncAdapter {
    public static final int[] PING_FREQUENCY_MINUTES = {1, 5, 15, 30, 60, 120, 240, 720, 1440};

    // The SQL selection string is always the same.
    private static final String mSelection = MonitorEntry._ID + " = ?";

    Pattern mUpPattern, mDownPattern, mDoesNotExistPattern;
    ContentResolver mContentResolver;

    public PingSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContentResolver = context.getContentResolver();

        mUpPattern = Pattern.compile("It's just you.");
        mDownPattern = Pattern.compile("It's not just you!");
        mDoesNotExistPattern = Pattern.compile("doesn't look like a site on the interwho.");
    }

    @Override
    public void onPerformSync(Account account,
                              Bundle extras,
                              String authority,
                              ContentProviderClient provider,
                              SyncResult syncResult) {
        // Recover the URL from the intent and get the returned HTML from our request.
        String url = account.name;
        String html = Utility.getHtml(url);

        final String[] selectionArgs = { String.valueOf(extras.getInt(MonitorEntry._ID)) };

        int status = -1;
        if (mUpPattern.matcher(html).find()) {
            status = MonitorEntry.STATUS_IS_UP;
        } else if (mDownPattern.matcher(html).find()) {
            status = MonitorEntry.STATUS_IS_DOWN;
        } else if (mDoesNotExistPattern.matcher(html).find()) {
            status = MonitorEntry.STATUS_IS_NOT_WEBSITE;
        }

        // Update the server's status, and the time last checked.
        ContentValues values = new ContentValues();
        values.put(MonitorEntry.STATUS, status);
        values.put(MonitorEntry.TIME_LAST_CHECKED, Calendar.getInstance().getTimeInMillis());

        mContentResolver.update(MonitorEntry.CONTENT_URI, values, mSelection, selectionArgs);
    }

    /**
     * Create a new Account for a new URL Monitor.
     * @param context The Context used to access the account service.
     * @param url The URL that the Account is tied to.
     * @param id The id of the relevant Monitor in the database.
     * @param interval The duration between syncs in minutes.
     */
    public static Account initSyncAccount(Context context, String url, int id, int interval) {
        // Get an instance of the Android account manager.
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the Account type and default Account.
        Account newAccount = new Account(url, context.getString(R.string.sync_account_type));

        // If the password exists then the Account already exists.
        if (null != accountManager.getPassword(newAccount)) {
            return null;
        }

        // Add the Account and Account type with no password or user data.
        // If successful, return the Account object, otherwise report an error.
        if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
            return null;
        }

        // Set up the data required for onPerformSync to be called on an interval.
        configurePeriodicSync(context, url, id, interval);

        // Without calling setSyncAutomatically, our periodic sync will not be enabled.
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);

        // Perform an initial sync.
        syncImmediately(context, url, id);

        return newAccount;
    }

    /**
     * Helper method to get the fake Account to be used with SyncAdapter. If the Account doesn't
     * exist, we return null.
     * @param context The Context used to access the account service.
     * @param url The URL that the Account is tied to.
     * @return The account tied to the provided URL.
     */
    public static Account getSyncAccount(Context context, String url) {
        // Get an instance of the Android account manager.
        AccountManager accountManager = AccountManager.get(context);

        // Create the account type and default account.
        Account existingAccount = new Account(url, context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist.
        if (null == accountManager.getPassword(existingAccount)) {
            return null;
        }
        return existingAccount;
    }

    /**
     * Schedule the SyncAdapter periodic execution.
     * @param context The Context used to access the account service.
     * @param url The URL that the Account is tied to.
     * @param id The id of the relevant Monitor in the database.
     * @param interval The duration between syncs in minutes.
     */
    public static void configurePeriodicSync(Context context, String url, int id, int interval) {
        Account account = getSyncAccount(context, url);
        String authority = context.getString(R.string.content_authority);

        // We need to provide the Monitor's ID to correctly update the database during a sync.
        Bundle bundle = new Bundle();
        bundle.putInt(MonitorEntry._ID, id);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // We can enable inexact timers in our periodic sync.
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(PING_FREQUENCY_MINUTES[interval], PING_FREQUENCY_MINUTES[interval] / 2).
                    setSyncAdapter(account, authority)
                    .setExtras(bundle).build();

            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account, authority, bundle, PING_FREQUENCY_MINUTES[interval]);
        }
    }

    /**
     * Helper method to have the sync adapter sync immediately.
     * @param context The context used to access the account service
     * @param url The URL that the account is tied to.
     */
    public static void syncImmediately(Context context, String url, int id) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putInt(MonitorEntry._ID, id);
        ContentResolver.requestSync(getSyncAccount(context, url),
                context.getString(R.string.content_authority), bundle);
    }

    /**
     * Delete the selected sync Account and stop it from syncing any more.
     * @param context The Context used to access the account service.
     * @param url The URL that the Account is tied to.
     */
    public static void removeSyncAccount(Context context, String url) {
        Account toRemove = getSyncAccount(context, url);
        AccountManager accountManager = AccountManager.get(context);
        accountManager.removeAccount(toRemove, null, null);
    }
}