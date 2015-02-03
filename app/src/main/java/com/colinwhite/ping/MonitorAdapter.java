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

import java.text.SimpleDateFormat;
import java.util.Date;

public class MonitorAdapter extends CursorAdapter {
    private static SimpleDateFormat mTimeFormat;

    public MonitorAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
        mTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
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
                String url = cursor.getString(cursor.getColumnIndex(MonitorEntry.URL));
                PingSyncAdapter.syncImmediately(context, url);
            }
        });

        // Set the Title and URL.
        viewHolder.titleView.setText(cursor.getString(cursor.getColumnIndex(MonitorEntry.TITLE)));
        viewHolder.urlView.setText(cursor.getString(cursor.getColumnIndex(MonitorEntry.URL)));

        // Set the time last checked.
        long timeLastCheckedMillis = cursor.getLong(cursor.getColumnIndex(MonitorEntry.TIME_LAST_CHECKED));
        Date timeLastChecked = new Date(timeLastCheckedMillis);
        viewHolder.lastCheckedView.setText("Last checked on " + mTimeFormat.format(timeLastChecked));

        switch (cursor.getInt(cursor.getColumnIndex(MonitorEntry.STATUS))) {
            case PingSyncAdapter.NO_INFO:
                viewHolder.statusView.setImageDrawable(context.getResources().getDrawable(R.drawable.down_button));
                break;
            case PingSyncAdapter.IS_UP:
                viewHolder.statusView.setImageDrawable(context.getResources().getDrawable(R.drawable.up_button));
                break;
            case PingSyncAdapter.IS_DOWN:
                viewHolder.statusView.setImageDrawable(context.getResources().getDrawable(R.drawable.down_button));
                break;
            case PingSyncAdapter.DOES_NOT_EXIST:
                viewHolder.statusView.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_share_variant_white_48dp));
                break;
            default:
                // Something went wrong. Just set it as down since we don't have an icon for this yet.
                viewHolder.statusView.setImageDrawable(context.getResources().getDrawable(R.drawable.down_button));
                break;
        }
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