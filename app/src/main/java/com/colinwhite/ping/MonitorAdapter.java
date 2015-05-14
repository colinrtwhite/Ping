package com.colinwhite.ping;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.sync.PingSyncAdapter;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * The MonitorAdapter class is a slightly modified CursorAdapter, which is used in the main ListView
 * of the MainActivity.
 */
class MonitorAdapter extends CursorAdapter {
    private static Vibrator vibratorService;

    public MonitorAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);

        // Get the service for haptic feedback.
        vibratorService = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.monitor_list_item, parent, false);
        ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);

        return view;
    }

    @Override
    public void bindView(View view, final Context context, Cursor cursor) {
        // Our ViewHolder already contains references to the relevant views, so set the appropriate
        // values through the viewHolder references instead of costly findViewById calls.
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        // Store the cursor's elements, as the cursor's information can become unavailable at any time.
        final ContentValues values = new ContentValues();
        values.put(MonitorEntry.URL, cursor.getString(cursor.getColumnIndex(MonitorEntry.URL)));
        values.put(MonitorEntry._ID, cursor.getInt(cursor.getColumnIndex(MonitorEntry._ID)));
        values.put(MonitorEntry.TITLE, cursor.getString(cursor.getColumnIndex(MonitorEntry.TITLE)));
        values.put(MonitorEntry.TIME_LAST_CHECKED, cursor.getLong(cursor.getColumnIndex(MonitorEntry.TIME_LAST_CHECKED)));
        values.put(MonitorEntry.STATUS, cursor.getInt(cursor.getColumnIndex(MonitorEntry.STATUS)));
        values.put(MonitorEntry.IS_LOADING, cursor.getInt(cursor.getColumnIndex(MonitorEntry.IS_LOADING)) == 1);

        if (!(boolean) values.get(MonitorEntry.IS_LOADING)) {
            // We are not refreshing right now so hide the loading spinner and show the manual
            // refresh button.
            viewHolder.refreshButtonView.setVisibility(View.VISIBLE);
            viewHolder.progressSpinner.setVisibility(View.GONE);
            viewHolder.refreshButtonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Pulse haptic feedback.
                    vibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

                    if (Utility.hasNetworkConnection(context)) {
                        // Refresh the Monitor right now.
                        PingSyncAdapter.syncImmediately(
                                context,
                                PingSyncAdapter.getSyncAccount(context),
                                (String) values.get(MonitorEntry.URL),
                                (int) values.get(MonitorEntry._ID));
                    } else {
                        Toast.makeText(context,
                                context.getResources().getString(R.string.error_poor_connection),
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            // We are refreshing right now so hide the button and show the loading spinner.
            viewHolder.refreshButtonView.setVisibility(View.GONE);
            viewHolder.progressSpinner.setVisibility(View.VISIBLE);
        }

        // Set the Title and URL.
        viewHolder.titleView.setText((String) values.get(MonitorEntry.TITLE));
        viewHolder.urlView.setText((String) values.get(MonitorEntry.URL));

        // Set the time last checked.
        long timeLastCheckedMillis = (long) values.get(MonitorEntry.TIME_LAST_CHECKED);
        if (timeLastCheckedMillis != MonitorEntry.TIME_LAST_CHECKED_NONE) {
            // Format the time last checked and place it in the resource string.
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            String formattedTime = Utility.formatDate(timeLastCheckedMillis,
                    sharedPref.getBoolean(context.getString(R.string.pref_key_24_hour_clock), false));
            viewHolder.lastCheckedView.setText(Html.fromHtml(
                    String.format(context.getString(R.string.last_checked_text), formattedTime)));
        } else {
            // If timeLastCheckedMillis is 0, it hasn't been checked yet.
            viewHolder.lastCheckedView.setText(context.getResources().getString(R.string.last_checked_text_no_info));
        }

        // Set the icon based on the Monitor's status.
        int statusIcon = Utility.getStatusIcon((int) values.get(MonitorEntry.STATUS));
        viewHolder.statusView.setImageDrawable(ContextCompat.getDrawable(context, statusIcon));
    }

    /**
     * Cache of the children views for a Monitor list item.
     */
    public static class ViewHolder {
        @InjectView(R.id.list_item_refresh_button) ImageButton refreshButtonView;
        @InjectView(R.id.progress_bar) View progressSpinner;
        @InjectView(R.id.list_item_title) TextView titleView;
        @InjectView(R.id.list_item_url) TextView urlView;
        @InjectView(R.id.list_item_time_last_checked) TextView lastCheckedView;
        @InjectView(R.id.list_item_status) ImageView statusView;

        public ViewHolder(View view) {
            ButterKnife.inject(this, view);
        }
    }
}