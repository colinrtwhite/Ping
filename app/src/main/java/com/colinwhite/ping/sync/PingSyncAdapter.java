package com.colinwhite.ping.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;

import com.colinwhite.ping.MonitorDetailActivity;
import com.colinwhite.ping.R;
import com.colinwhite.ping.Utility;
import com.colinwhite.ping.data.PingContract.MonitorEntry;

import java.util.Calendar;
import java.util.regex.Pattern;

public class PingSyncAdapter extends AbstractThreadedSyncAdapter {
    // The SQL selection string is always the same.
    private static final String mSelection = MonitorEntry._ID + " = ?";

    Context mContext;
    Pattern mUpPattern, mDownPattern, mDoesNotExistPattern;
    ContentResolver mContentResolver;

    public PingSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
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
        // Simply return if we're not given ID nor URL.
        // TODO: Fix bug where onPerformSync is called with an empty Bundle after the first Monitor is created.
        if (extras.isEmpty()) {
            return;
        }

        // Recover the URL from the intent and get the returned HTML from our request.
        String url = extras.getString(MonitorEntry.URL);
        String html = Utility.getHtml(url);

        int monitorId = extras.getInt(MonitorEntry._ID);
        final String[] selectionArgs = {String.valueOf(monitorId)};

        int status = -1;
        if (mUpPattern.matcher(html).find()) {
            status = MonitorEntry.STATUS_IS_UP;
        } else if (mDownPattern.matcher(html).find()) {
            status = MonitorEntry.STATUS_IS_DOWN;
        } else if (mDoesNotExistPattern.matcher(html).find()) {
            status = MonitorEntry.STATUS_IS_NOT_WEBSITE;
        }

        long timeLastChecked = Calendar.getInstance().getTimeInMillis();

        // Get the Monitor's previous status to compare.
        String[] projection = {MonitorEntry.TITLE, MonitorEntry.STATUS};
        Cursor cursor = mContentResolver.query(MonitorEntry.CONTENT_URI, projection, mSelection, selectionArgs, null);
        cursor.moveToFirst();
        int previousStatus = cursor.getInt(cursor.getColumnIndex(MonitorEntry.STATUS));
        if (previousStatus != 0 && previousStatus != status) {
            // If the status has changed, trigger a notification.
            triggerNotification(
                    monitorId,
                    cursor.getString(cursor.getColumnIndex(MonitorEntry.TITLE)),
                    url,
                    status,
                    timeLastChecked);
        }

        // Update the server's status, and the time last checked.
        ContentValues values = new ContentValues();
        values.put(MonitorEntry.STATUS, status);
        values.put(MonitorEntry.TIME_LAST_CHECKED, timeLastChecked);

        mContentResolver.update(MonitorEntry.CONTENT_URI, values, mSelection, selectionArgs);
    }

    /**
     * Creates and displays a notification of a Monitor's status change.
     * @param monitorId Database ID of the Monitor.
     * @param title Title of the Monitor.
     * @param url URL of the Monitor.
     * @param status The current status of the Monitor.
     * @param timeLastChecked The time the status was checked at in milliseconds.
     */
    private void triggerNotification(int monitorId, String title, String url, int status, long timeLastChecked) {
        String statusStr;
        switch (status) {
            case MonitorEntry.STATUS_IS_UP:
                statusStr = "up";
                break;
            case MonitorEntry.STATUS_IS_DOWN:
                statusStr = "down";
                break;
            case MonitorEntry.STATUS_IS_NOT_WEBSITE:
            case MonitorEntry.STATUS_NO_INTERNET:
            case MonitorEntry.STATUS_NO_INFO:
            default:
                // Don't trigger a notification for these.
                return;
        }

        String notificationText = String.format(
                mContext.getString(R.string.notification_text),
                Utility.formatDate(timeLastChecked),
                url,
                statusStr);

        Notification.Builder notificationBuilder = new Notification.Builder(mContext)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(notificationText);

        // Construct artificial back stack.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
        Intent monitorDetailActivityIntent = new Intent(mContext, MonitorDetailActivity.class);
        monitorDetailActivityIntent.putExtra(MonitorDetailActivity.PAGE_TYPE_ID,
                MonitorDetailActivity.PAGE_DETAIL);
        monitorDetailActivityIntent.putExtra(MonitorEntry._ID, (long) monitorId);
        monitorDetailActivityIntent.putExtra(MonitorEntry.URL, url);
        stackBuilder.addParentStack(MonitorDetailActivity.class);
        stackBuilder.addNextIntent(monitorDetailActivityIntent);
        PendingIntent detailPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(detailPendingIntent);

        // Send the notification.
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(monitorId, notificationBuilder.build());
    }

    /**
     * Get or create a new Account for a new URL Monitor if one does not already exist.
     * @param context The Context used to access the account service.
     * @return The account associated with Ping's sync account type.
     */
    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager.
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the Account type and default Account.
        Account pingAccount = new Account(context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist then the Account doesn't exists and we need to create it.
        if (null == accountManager.getPassword(pingAccount)) {
            // Add the Account and Account type with no password or user data.
            // If successful, return the Account object, otherwise report an error.
            if (!accountManager.addAccountExplicitly(pingAccount, "", null)) {
                return null;
            }
        }

        return pingAccount;
    }

    /**
     * Create a new periodic sync timer for a Monitor.
     * @param context The Context used to access the account service.
     * @param url The URL that the periodic sync is tied to.
     * @param monitorId The ID of the relevant Monitor in the database.
     * @param interval The duration between syncs in seconds.
     */
    public static void createPeriodicSync(Context context, String url, int monitorId, int interval) {
        Account account = getSyncAccount(context);

        // Set up the data required for onPerformSync to be called on an interval.
        configurePeriodicSync(context, account, url, monitorId, interval);

        // Without calling setSyncAutomatically, our periodic sync will not be enabled.
        ContentResolver.setSyncAutomatically(account, context.getString(R.string.content_authority),
                true);

        // Perform an initial sync.
        syncImmediately(context, account, url, monitorId);
    }

    /**
     * Schedule the SyncAdapter periodic execution.
     * @param context The Context used to access the account service.
     * @param account Ping's sync account.
     * @param url The URL that the periodic sync is tied to.
     * @param monitorId The ID of the relevant Monitor in the database.
     * @param interval The duration between syncs in seconds.
     */
    private static void configurePeriodicSync(Context context, Account account, String url,
                                              int monitorId, int interval) {
        String authority = context.getString(R.string.content_authority);

        // We need to provide the Monitor's ID and URL to correctly ping the website and update the
        // database during a sync.
        Bundle bundle = new Bundle();
        bundle.putInt(MonitorEntry._ID, monitorId);
        bundle.putString(MonitorEntry.URL, url);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // We can enable inexact timers in our periodic sync.
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(interval, interval / 2).
                    setSyncAdapter(account, authority)
                    .setExtras(bundle).build();

            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account, authority, bundle, interval);
        }
    }

    /**
     * Helper method to have the SyncAdapter sync immediately.
     * @param context The context used to access the account service
     * @param account Ping's sync account.
     * @param url The URL that the periodic sync is tied to.
     * @param monitorId The ID of the relevant Monitor in the database.
     */
    public static void syncImmediately(Context context, Account account, String url, int monitorId) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putInt(MonitorEntry._ID, monitorId);
        bundle.putString(MonitorEntry.URL, url);
        ContentResolver.requestSync(account,
                context.getString(R.string.content_authority), bundle);
    }

    /**
     * Attempt to delete the selected periodic sync timer.
     * @param context The context used to access the account service
     * @param url The URL that the periodic sync is tied to.
     * @param monitorId The ID of the relevant Monitor in the database.
     */
    public static void removePeriodicSync(Context context, String url, int monitorId) {
        Account account = getSyncAccount(context);
        Bundle bundle = new Bundle();
        bundle.putInt(MonitorEntry._ID, monitorId);
        bundle.putString(MonitorEntry.URL, url);
        ContentResolver.removePeriodicSync(account, context.getString(R.string.content_authority), bundle);
    }
}