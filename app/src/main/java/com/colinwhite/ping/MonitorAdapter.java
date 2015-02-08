package com.colinwhite.ping;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.sync.PingSyncAdapter;

public class MonitorAdapter extends CursorAdapter {

    private Vibrator mVibratorService;

    public MonitorAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.monitor_list_item, parent, false);
        ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);

        // Get the service for haptic feedback.
        mVibratorService = (Vibrator) context.getSystemService(context.VIBRATOR_SERVICE);

        return view;
    }

    @Override
    public void bindView(View view, final Context context, final Cursor cursor) {
        // Store the cursor's elements, as the cursor's information can become unavailable at any time.
        final ContentValues values = new ContentValues();
        values.put(MonitorEntry.URL, cursor.getString(cursor.getColumnIndex(MonitorEntry.URL)));
        values.put(MonitorEntry._ID, cursor.getInt(cursor.getColumnIndex(MonitorEntry._ID)));
        values.put(MonitorEntry.TITLE, cursor.getString(cursor.getColumnIndex(MonitorEntry.TITLE)));
        values.put(MonitorEntry.TIME_LAST_CHECKED, cursor.getLong(cursor.getColumnIndex(MonitorEntry.TIME_LAST_CHECKED)));
        values.put(MonitorEntry.STATUS, cursor.getInt(cursor.getColumnIndex(MonitorEntry.STATUS)));

        // Our ViewHolder already contains references to the relevant views, so set the appropriate
        // values through the viewHolder references instead of costly findViewById calls.
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        viewHolder.refreshButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Pulse haptic feedback.
                mVibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

                // Refresh the Monitor right now.
                PingSyncAdapter.syncImmediately(
                        context,
                        PingSyncAdapter.getSyncAccount(context),
                        (String)values.get(MonitorEntry.URL),
                        (int)values.get(MonitorEntry._ID));
            }
        });

        // Set the Title and URL.
        viewHolder.titleView.setText((String)values.get(MonitorEntry.TITLE));
        viewHolder.urlView.setText((String)values.get(MonitorEntry.URL));

        // Set the time last checked.
        long timeLastCheckedMillis = (long)values.get(MonitorEntry.TIME_LAST_CHECKED);
        if (timeLastCheckedMillis != MonitorEntry.TIME_LAST_CHECKED_NONE) {
            // Format the time last checked and place it in the resource string.
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            String formattedTime = Utility.formatDate(timeLastCheckedMillis,
                    sharedPref.getBoolean(context.getString(R.string.pref_key_24_hour_clock), false));
            viewHolder.lastCheckedView.setText(String.format(
                    context.getString(R.string.last_checked_text), formattedTime));
        } else {
            // If timeLastCheckedMillis is 0, it hasn't been checked yet.
            viewHolder.lastCheckedView.setText(context.getResources().getString(R.string.last_checked_text_no_info));
        }

        // Set the icon based on the Monitor's status.
        int statusIcon = Utility.getStatusIcon((int)values.get(MonitorEntry.STATUS));
        viewHolder.statusView.setImageDrawable(context.getResources().getDrawable(statusIcon));
    }

    /**
     * Cache of the children views for a forecast list item.
     */
    public static class ViewHolder {
        public final ImageButton refreshButtonView;
        public final TextView titleView;
        public final TextView urlView;
        public final TextView lastCheckedView;
        public final ImageView statusView;

        public ViewHolder(View view) {
            refreshButtonView = (ImageButton) view.findViewById(R.id.list_item_refresh_button);
            titleView = (TextView) view.findViewById(R.id.list_item_title);
            urlView = (TextView) view.findViewById(R.id.list_item_url);
            lastCheckedView = (TextView) view.findViewById(R.id.list_item_time_last_checked);
            statusView = (ImageView) view.findViewById(R.id.list_item_status);
        }
    }
}