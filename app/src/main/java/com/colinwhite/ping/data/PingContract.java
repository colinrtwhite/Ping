package com.colinwhite.ping.data;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.BaseColumns;

public final class PingContract {
    public static final String PATH_MONITOR = "monitor";
    public static final String CONTENT_AUTHORITY = "com.colinwhite.ping";

    // To prevent accidental instantiation
    public PingContract() { }

    public static abstract class MonitorEntry implements BaseColumns {
        public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
        public static final Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_MONITOR).build();
        public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/" + CONTENT_AUTHORITY + "/" + PATH_MONITOR;
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/" + CONTENT_AUTHORITY + "/" + PATH_MONITOR;
        public static final String TABLE_NAME = "monitor";
        public static final String TITLE = "title";
        public static final String URL = "url";
        public static final String PING_FREQUENCY = "ping_frequency";
        public static final String END_DATE = "end_date";
        public static final String TIME_LAST_CHECKED = "time_last_checked";
        public static final String STATUS = "status";

        // Status codes for MonitorEntry.STATUS
        public static final int STATUS_NO_INFO = 0;
        public static final int STATUS_IS_UP = 1;
        public static final int STATUS_IS_DOWN = 2;
        public static final int STATUS_IS_NOT_WEBSITE = 3;
        public static final int STATUS_NO_INTERNET = 4;

        public static Uri buildUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
    }
}
