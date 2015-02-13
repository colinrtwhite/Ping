package com.colinwhite.ping;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.colinwhite.ping.data.PingContract.MonitorEntry;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.regex.Pattern;

/**
 * PingService resolves if the host in Utility.HOST can see the user's website and sends its result
 * to PingServiceReceiver.
 *
 * @see com.colinwhite.ping.MainActivity.PingServiceReceiver
 */
public class PingService extends IntentService {
    public static final String LOG_TAG = MainActivity.class.getSimpleName();

    public PingService() {
        super(PingService.class.getName());
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
        String url = intent.getStringExtra(MonitorEntry.URL);

        // Build the response intent for PingServiceReceiver.
        Intent response = new Intent()
                .setAction(MainActivity.PingServiceReceiver.ACTION_RESPONSE)
                .addCategory(Intent.CATEGORY_DEFAULT);
        try {
            String html = Utility.getHtml(url);

            // Compile the regex patterns.
            Pattern up = Pattern.compile("It's just you.");
            Pattern down = Pattern.compile("It's not just you!");
            Pattern doesNotExist = Pattern.compile("doesn't look like a site on the interwho.");

            // Parse the HTML to find the appropriate return stats.
            if (!Utility.hasNetworkConnection(this)) {
                response.putExtra(MonitorEntry.STATUS, MonitorEntry.STATUS_NO_INTERNET);
            } else if (up.matcher(html).find()) {
                response.putExtra(MonitorEntry.STATUS, MonitorEntry.STATUS_IS_UP);
            } else if (down.matcher(html).find()) {
                response.putExtra(MonitorEntry.STATUS, MonitorEntry.STATUS_IS_DOWN);
            } else if (doesNotExist.matcher(html).find()) {
                response.putExtra(MonitorEntry.STATUS, MonitorEntry.STATUS_IS_NOT_WEBSITE);
            } else {
                Log.v(LOG_TAG, "Website has an unknown status.");
                response.putExtra(MonitorEntry.STATUS, -1);
            }
        } catch (SocketTimeoutException e) {
            Log.v(LOG_TAG, "Was unable to connect to the host.");
            response.putExtra(MonitorEntry.STATUS, MonitorEntry.STATUS_NO_INTERNET);
        } catch (IOException e) {
            Log.e(LOG_TAG, "An error occurred while fetching a parsing the host's HTML.");
            response.putExtra(MonitorEntry.STATUS, -1);
        }

        sendBroadcast(response);
    }
}
