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
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;

import com.colinwhite.ping.MonitorDetailActivity;
import com.colinwhite.ping.R;
import com.colinwhite.ping.Utility;
import com.colinwhite.ping.data.PingContract.MonitorEntry;

import java.util.Calendar;
import java.util.regex.Pattern;

/**
 * PingSyncAdapter is Ping!'s implementation of the SyncAdapter framework. This lets Ping!
 * intelligently schedule network activity together.
 */
public class PingSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String LOG_TAG = PingSyncAdapter.class.getSimpleName();

    // The SQL selection and projection strings are always the same.
    private static final String selection = MonitorEntry._ID + " = ?";
    private static final String[] projection = { MonitorEntry.TITLE, MonitorEntry.STATUS,
            MonitorEntry.LAST_NON_ERROR_STATUS};

    private static Context context;
    private static Pattern upPattern, downPattern, doesNotExistPattern;
    private static ContentResolver contentResolver;
    private static SharedPreferences sharedPref;
    private static String clockTypeKey;
    private static String disableNotificationsKey;

    public PingSyncAdapter(Context context) {
        super(context, true);
        PingSyncAdapter.context = context;
        contentResolver = context.getContentResolver();

        // Pre-compile the parsing patterns.
        upPattern = Pattern.compile("It's just you.");
        downPattern = Pattern.compile("It's not just you!");
        doesNotExistPattern = Pattern.compile("doesn't look like a site on the interwho.");

        sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        clockTypeKey = context.getString(R.string.pref_key_24_hour_clock);
        disableNotificationsKey = context.getString(R.string.pref_key_disable_notifications);
    }

    @Override
    public void onPerformSync(Account account,
                              Bundle extras,
                              String authority,
                              ContentProviderClient provider,
                              SyncResult syncResult) {
        // Cancel the sync if the user has set the preference to avoid metered connections, however
        // allow the sync if it is manual.
        if (!(extras.getBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false) &&
                extras.getBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false)) &&
                sharedPref.getBoolean(context.getString(R.string.pref_key_avoid_metered_connection), false) &&
                ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).isActiveNetworkMetered()) {
            Log.v(LOG_TAG, "Cancelled sync because of user preference to avoid metered connections.");
            return;
        }

        try {
            // Tell the database that we are now loading.
            int monitorId = extras.getInt(MonitorEntry._ID);
            ContentValues isNowLoadingValue = new ContentValues();
            isNowLoadingValue.put(MonitorEntry.IS_LOADING, true);
            String selection = MonitorEntry._ID + " = ?";
            String[] selectionArgs = {String.valueOf(monitorId)};
            contentResolver.update(MonitorEntry.CONTENT_URI, isNowLoadingValue, selection, selectionArgs);

            // Recover the URL from the intent and get the returned HTML from our request.
            String url = extras.getString(MonitorEntry.URL);
            String html = Utility.getHtml(url);

            // Match the HTML returned from the host.
            int status;
            if (upPattern.matcher(html).find()) {
                status = MonitorEntry.STATUS_IS_UP;
            } else if (downPattern.matcher(html).find()) {
                status = MonitorEntry.STATUS_IS_DOWN;
            } else if (doesNotExistPattern.matcher(html).find()) {
                status = MonitorEntry.STATUS_IS_NOT_WEBSITE;
            } else {
                // We couldn't complete the network request.
                status = MonitorEntry.STATUS_NO_INTERNET;
            }

            long timeLastChecked = Calendar.getInstance().getTimeInMillis();

            // Get the Monitor's previous status to compare.
            Cursor cursor = contentResolver.query(
                    MonitorEntry.buildUri(monitorId),
                    projection,
                    PingSyncAdapter.selection,
                    selectionArgs,
                    null);

            if (cursor.getCount() == 0) {
                cursor.close();
                throw new IllegalStateException("No Monitors found in cursor.");
            }

            cursor.moveToFirst();
            int previousStatus = cursor.getInt(cursor.getColumnIndex(MonitorEntry.STATUS));
            int lastNonErrorStatus = cursor.getInt(cursor.getColumnIndex(MonitorEntry.LAST_NON_ERROR_STATUS));

            // Only trigger a notification if:
            // The user has not disabled notifications in the preferences.
            if (!sharedPref.getBoolean(disableNotificationsKey, false) &&
                    // The previous status was not "no information."
                    previousStatus != 0 &&
                    // The previous status is not the same as the current one.
                    previousStatus != status &&
                    // The current status is not an error.
                    Utility.isNonErrorStatus(status) &&
                    // The most recent non-error status is not the same as the current one (this is to
                    // prevent cases, for instance, where we lost internet for a second, but then got it
                    // back and the website status did not change in the meantime).
                    lastNonErrorStatus != status) {
                // If the status has changed, trigger a notification.
                triggerNotification(
                        monitorId,
                        cursor.getString(cursor.getColumnIndex(MonitorEntry.TITLE)),
                        url,
                        status,
                        timeLastChecked);
            }
            cursor.close();

            // Update the time last checked, the server's status, and the server's last non-error
            // status, if applicable.
            ContentValues values = new ContentValues();
            values.put(MonitorEntry.IS_LOADING, false);
            values.put(MonitorEntry.TIME_LAST_CHECKED, timeLastChecked);
            values.put(MonitorEntry.STATUS, status);
            if (Utility.isNonErrorStatus(status)) {
                values.put(MonitorEntry.LAST_NON_ERROR_STATUS, status);
            }

            contentResolver.update(MonitorEntry.CONTENT_URI, values, PingSyncAdapter.selection, selectionArgs);
        } catch (Exception e) {
            // If any problems occur while syncing simply record an error in the Log, cancel the sync,
            // and try again next time.
            Log.e(LOG_TAG, "Error occurred while syncing: " + e.toString());

            // Set the Monitor's status to errored/uncertain.
            ContentValues values = new ContentValues();
            values.put(MonitorEntry.IS_LOADING, false);
            values.put(MonitorEntry.TIME_LAST_CHECKED, Calendar.getInstance().getTimeInMillis());
            values.put(MonitorEntry.STATUS, MonitorEntry.STATUS_NO_INTERNET);
            String[] selectionArgs = {String.valueOf(extras.getInt(MonitorEntry._ID))};
            contentResolver.update(MonitorEntry.CONTENT_URI, values, selection, selectionArgs);
        }
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
        // If it's not up then it's down.
        String statusStr = (status == MonitorEntry.STATUS_IS_UP) ? "up" : "down";

        String notificationText = String.format(
                context.getString(R.string.notification_text),
                title,
                statusStr,
                Utility.formatDate(timeLastChecked, sharedPref.getBoolean(clockTypeKey, false)));

        // Get the Monitor's status icon.
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(),
                Utility.getStatusIcon(status));
        // Scale the large icon to prevent cropping if we are running any version below Lollipop.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            float multiplier = (float)(metrics.density / 3f * 0.5);
            largeIcon = Bitmap.createScaledBitmap(largeIcon, (int)(largeIcon.getWidth() * multiplier), (int)(largeIcon.getHeight() * multiplier), false);
        }

        // Set the large notification icon to the Monitor's current status and small icon to the
        // bitmap of the app logo.
        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentTitle(url)
                .setContentText(notificationText);

        // Set the small icon colour if we are running Lollipop.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setColor(ContextCompat.getColor(context, R.color.primary));
        }

        // Construct artificial back stack.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        Intent monitorDetailActivityIntent = new Intent(context, MonitorDetailActivity.class);
        monitorDetailActivityIntent.putExtra(MonitorDetailActivity.PAGE_TYPE_ID,
                MonitorDetailActivity.PAGE_DETAIL);
        monitorDetailActivityIntent.putExtra(MonitorEntry._ID, (long) monitorId);
        monitorDetailActivityIntent.putExtra(MonitorEntry.URL, url);
        stackBuilder.addParentStack(MonitorDetailActivity.class);
        stackBuilder.addNextIntent(monitorDetailActivityIntent);
        PendingIntent detailPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(detailPendingIntent);

        // Send the notification.
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
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
        ContentResolver.setSyncAutomatically(account, context.getString(R.string.content_authority), true);

        // Run an immediate sync if we are on Lollipop.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            syncImmediately(context, account, url, monitorId);
        }
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
            SyncRequest request = new SyncRequest.Builder()
                    .syncPeriodic(interval, interval / 2)
                    .setSyncAdapter(account, authority)
                    .setExtras(bundle).build();

            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account, authority, bundle, interval);
        }
    }

    /**
     * Helper method to have the SyncAdapter sync immediately.
     * @param context The Context used to access the account service.
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
        ContentResolver.requestSync(account, context.getString(R.string.content_authority), bundle);
    }

    /**
     * Attempt to delete the selected periodic sync timer.
     * @param context The Context used to access the account service.
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