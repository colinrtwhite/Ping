package com.colinwhite.ping.data;

import android.provider.BaseColumns;

public final class PingContract {

    // To prevent accidental instantiation
    public PingContract() {
    }

    public static abstract class MonitorEntry implements BaseColumns {
        public static final String TABLE_NAME = "monitor";
        public static final String TITLE = "title";
        public static final String URL = "url";
        public static final String PING_FREQUENCY = "ping_frequency";
        public static final String END_DATE = "end_date";
    }
}
