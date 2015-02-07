package com.colinwhite.ping;

import android.content.Context;
import android.database.Cursor;
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

    public MonitorAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.monitor_list_item, parent, false);
        ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);
        return view;
    }

    @Override
    public void bindView(View view, final Context context, final Cursor cursor) {
        // Our ViewHolder already contains references to the relevant views, so set the appropriate
        // values through the viewHolder references instead of costly findViewById calls.
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        viewHolder.refreshButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Refresh the Monitor right now.
                PingSyncAdapter.syncImmediately(
                        context,
                        PingSyncAdapter.getSyncAccount(context),
                        cursor.getString(cursor.getColumnIndex(MonitorEntry.URL)),
                        cursor.getInt(cursor.getColumnIndex(MonitorEntry._ID)));
            }
        });

        // Set the Title and URL.
        viewHolder.titleView.setText(cursor.getString(cursor.getColumnIndex(MonitorEntry.TITLE)));
        viewHolder.urlView.setText(cursor.getString(cursor.getColumnIndex(MonitorEntry.URL)));

        // Set the time last checked.
        long timeLastCheckedMillis = cursor.getLong(cursor.getColumnIndex(MonitorEntry.TIME_LAST_CHECKED));
        if (timeLastCheckedMillis != MonitorEntry.TIME_LAST_CHECKED_NONE) {
            // Format the time last checked and place it in the resource string.
            viewHolder.lastCheckedView.setText(String.format(
                    context.getString(R.string.last_checked_text),
                    Utility.formatDate(timeLastCheckedMillis)));
        } else {
            // If timeLastCheckedMillis is 0, it hasn't been checked yet.
            viewHolder.lastCheckedView.setText(context.getResources().getString(R.string.last_checked_text_no_info));
        }

        // Set the icon based on the Monitor's status.
        int statusIcon = Utility.getStatusIcon(cursor.getInt(cursor.getColumnIndex(MonitorEntry.STATUS)));
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