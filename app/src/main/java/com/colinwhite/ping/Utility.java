package com.colinwhite.ping;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.colinwhite.ping.data.PingContract;
import com.colinwhite.ping.data.PingContract.MonitorEntry;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Utility {
    private static final int MAX_DURATION_PARTS = 2;
    private static final String[] DURATION_SUFFIXES = {
            "day", " days",
            "hour", " hours",
            "minute", "minutes",
            "second", "seconds"};
    private static final String HOST = "http://www.downforeveryoneorjustme.com/";

    public static final String TIME_FORMAT_12_HOURS = "h:mm a";
    public static final String TIME_FORMAT_24_HOURS = "H:mm";

    /**
     * Converts an amount of time into a formatted string.
     * @param duration The duration of time in milliseconds.
     * @return A formatted string of the duration (ex. "1 day, 5 hours").
     */
    public static String formatTimeDuration(long duration) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        if (seconds < 1) {
            return "0 seconds";
        }

        StringBuilder formattedString = new StringBuilder();
        // Number of individual time unit parts.
        int numParts = 0;

        // Separate the time into days, hours, minutes, and seconds.
        long[] timeComponents = new long[4];
        long remainingSeconds = seconds;
        timeComponents[0] = TimeUnit.SECONDS.toDays(remainingSeconds);
        remainingSeconds -= TimeUnit.DAYS.toSeconds(timeComponents[0]);
        timeComponents[1] = TimeUnit.SECONDS.toHours(remainingSeconds);
        remainingSeconds -= TimeUnit.HOURS.toSeconds(timeComponents[1]);
        timeComponents[2] = TimeUnit.SECONDS.toMinutes(remainingSeconds);
        remainingSeconds -= TimeUnit.MINUTES.toSeconds(timeComponents[2]);
        timeComponents[3] = remainingSeconds;

        for (int i = 0; i < timeComponents.length; i++) {
            if (timeComponents[i] > 0 && numParts < MAX_DURATION_PARTS) {
                // Ensure that we handle time unit pluralization correctly.
                int suffixIndex = (timeComponents[i] == 1) ? i * 2 : i * 2 + 1;

                // Append a comma at the front if this is not the first duration part.
                String durationPart = (numParts > 0) ? ", " : "";
                durationPart += timeComponents[i] + " " + DURATION_SUFFIXES[suffixIndex];

                formattedString.append(durationPart);
                numParts++;
            }
        }

        return formattedString.toString();
    }

    /**
     * Convert a date into a formatted string (used for the Monitor ListView).
     * @param date The date in milliseconds from the epoch.
     * @return A formatted string of the date (ex. "10:24, yesterday").
     */
    public static String formatDate(long date) {
        if (date < 1) {
            // This shouldn't happen, but in case it does...
            return "some time";
        }

        // Get the last checked time.
        SimpleDateFormat dateFormat = new SimpleDateFormat(TIME_FORMAT_12_HOURS);
        StringBuilder formattedString = new StringBuilder(dateFormat.format(new Date(date)));

        Calendar lastCheckedDate = Calendar.getInstance();
        lastCheckedDate.setTimeInMillis(date);
        Calendar currentDate = Calendar.getInstance();

        // Find out how many days ago the URL was checked.
        int numYearsDifference = currentDate.get(Calendar.YEAR) - lastCheckedDate.get(Calendar.YEAR);
        int numDaysDifference = currentDate.get(Calendar.DAY_OF_YEAR) -
                lastCheckedDate.get(Calendar.DAY_OF_YEAR) + 365 * numYearsDifference;

        // Append any necessary date information.
        if (numDaysDifference == 0) {
            return formattedString.toString();
        } else if (numDaysDifference == 1) {
            return formattedString.append(", yesterday").toString();
        } else if (numDaysDifference < 7) {
            // Append the day name of the last checked date.
            return formattedString.append(", " + (new SimpleDateFormat("EEEE")).format(
                    lastCheckedDate.getTime())).toString();
        } else {
            return formattedString.append(String.format(", %d days ago", numDaysDifference)).toString();
        }
    }

    /**
     * Send an HTTP request to the HOST and return its HTML.
     *
     * @param url The URL the check through the HOST.
     * @return A long string of HTML all on one line. On error, returns an empty string.
     */
    public static String getHtml(String url) {
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
            return "";
        }
    }

    /**
     * Determines whether the device is connected to a network (and thus, if we can have a connection
     * to the Internet).
     * @param context The Context used to access the ConnectivityManager.
     */
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        // If info is null, there are no active networks.
        return (info != null);
    }

    /**
     * Sets the SyncRemovalService to run at the specified end time.
     * @param context Context used to access the AlarmManager.
     * @param monitorId ID of the relevant Monitor in the database.
     * @param endTime Date/time when the SyncRemovalService will be triggered in milliseconds.
     */
    public static void addRemovalAlarm(Context context, int monitorId, long endTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, endTime, createPendingRemovalIntent(context, monitorId, endTime));
    }

    /**
     * Removes the alarm previously set for the Monitor with monitorId.
     * Note: All the parameters must match the ones originally passed to addRemovalAlarm.
     * @param context Context used to access the AlarmManager.
     * @param monitorId ID of the relevant Monitor in the database.
     * @param endTime Date/time when the SyncRemovalService will be triggered in milliseconds.
     */
    public static void deleteRemovalAlarm(Context context, int monitorId, long endTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(context.ALARM_SERVICE);
        alarmManager.cancel(createPendingRemovalIntent(context, monitorId, endTime));
    }

    /**
     * Helper method to create the pending removal intent.
     * @param context Context used to access get the SyncRemovalService.
     * @param monitorId ID of the relevant Monitor in the database.
     * @param endTime Date/time when the SyncRemovalService will be triggered in milliseconds.
     */
    private static PendingIntent createPendingRemovalIntent(Context context, int monitorId, long endTime) {
        Intent intent = new Intent(context, SyncRemovalService.class);
        intent.putExtra(PingContract.MonitorEntry._ID, monitorId);
        return PendingIntent.getService(context, monitorId, intent, PendingIntent.FLAG_ONE_SHOT);
    }

    public static int getStatusIcon(int status) {
        switch (status) {
            case PingContract.MonitorEntry.STATUS_NO_INFO:
                return R.drawable.down_button;
            case PingContract.MonitorEntry.STATUS_IS_UP:
                return R.drawable.up_button;
            case PingContract.MonitorEntry.STATUS_IS_DOWN:
                return R.drawable.down_button;
            case PingContract.MonitorEntry.STATUS_NO_INTERNET:
                return R.drawable.ic_share_variant_white_48dp;
            default:
                // Something went wrong.
                return R.drawable.down_button;
        }
    }

    public static boolean isErrorStatus(int status) {
        switch (status) {
            case MonitorEntry.STATUS_IS_UP:
            case MonitorEntry.STATUS_IS_DOWN:
                return false;
            case MonitorEntry.STATUS_NO_INFO:
            case MonitorEntry.STATUS_IS_NOT_WEBSITE:
            case MonitorEntry.STATUS_NO_INTERNET:
            default:
                return true;
        }
    }
}
