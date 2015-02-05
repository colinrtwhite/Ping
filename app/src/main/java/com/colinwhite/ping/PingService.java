package com.colinwhite.ping;

import android.app.IntentService;
import android.content.Intent;

import com.colinwhite.ping.data.PingContract.MonitorEntry;

import java.util.regex.Pattern;

/**
 * PingService resolves if the HOST can see the user's website and sends its result to
 * PingServiceReceiver.
 *
 * @see com.colinwhite.ping.MainActivity.PingServiceReceiver
 */
public class PingService extends IntentService {

    public static final String STATUS_ID = "WEBSITE_STATUS";

    public PingService() {
        super("PingService");
    }
    public PingService(String name) {
        super(name);
    }

    /**
     * Calls Utility.getHtml and parses the returned HTML from the HOST to see if the user's website
     * is accessible by the host or not. Sends a broadcast response for PingServiceReceiver.
     *
     * @param intent Intent that was sent to start this service.
     * @see com.colinwhite.ping.MainActivity.PingServiceReceiver
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        // Recover the URL from the intent and get the returned HTML from our request.
        String url = intent.getStringExtra(MainActivity.URL_ID);
        String html = Utility.getHtml(url);

        Pattern up = Pattern.compile("It's just you.");
        Pattern down = Pattern.compile("It's not just you!");
        Pattern doesNotExist = Pattern.compile("doesn't look like a site on the interwho.");

        Intent response = new Intent()
                .setAction(MainActivity.PingServiceReceiver.ACTION_RESPONSE)
                .addCategory(Intent.CATEGORY_DEFAULT);

        if (!Utility.isNetworkConnected(this)) {
            response.putExtra(STATUS_ID, MonitorEntry.STATUS_NO_INTERNET);
        } else if (up.matcher(html).find()) {
            response.putExtra(STATUS_ID, MonitorEntry.STATUS_IS_UP);
        } else if (down.matcher(html).find()) {
            response.putExtra(STATUS_ID, MonitorEntry.STATUS_IS_DOWN);
        } else if (doesNotExist.matcher(html).find()) {
            response.putExtra(STATUS_ID, MonitorEntry.STATUS_IS_NOT_WEBSITE);
        } else {
            response.putExtra(STATUS_ID, -1);
        }

        sendBroadcast(response);
    }
}
