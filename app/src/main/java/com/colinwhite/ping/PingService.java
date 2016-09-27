package com.colinwhite.ping;

import android.app.IntentService;
import android.content.Intent;
import android.os.ResultReceiver;
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
	private static final String LOG_TAG = MainActivity.class.getSimpleName();
	public static final String RESULT_RECEIVER_KEY = "result_receiver";

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
		// Check that we have all the necessary data.
		if (!intent.hasExtra(MonitorEntry.URL) || !intent.hasExtra(RESULT_RECEIVER_KEY)) {
			Log.e(LOG_TAG, "Missing a ResultsReceiver or URL.");
			return;
		}

		String url = intent.getStringExtra(MonitorEntry.URL);
		ResultReceiver resultReceiver = intent.getParcelableExtra(RESULT_RECEIVER_KEY);
		int status;

		try {
			String html = Utility.getHtml(url);

			// Compile the regex patterns.
			Pattern up = Pattern.compile("It's just you.");
			Pattern down = Pattern.compile("It's not just you!");
			Pattern doesNotExist = Pattern.compile("doesn't look like a site on the interwho.");

			// Parse the HTML to find the appropriate return stats.
			if (!Utility.hasNetworkConnection(this)) {
				status = MonitorEntry.STATUS_NO_INTERNET;
			} else if (up.matcher(html).find()) {
				status = MonitorEntry.STATUS_IS_UP;
			} else if (down.matcher(html).find()) {
				status = MonitorEntry.STATUS_IS_DOWN;
			} else if (doesNotExist.matcher(html).find()) {
				status = MonitorEntry.STATUS_IS_NOT_WEBSITE;
			} else {
				Log.v(LOG_TAG, "Website has an unknown status.");
				status = -1;
			}
		} catch (SocketTimeoutException e) {
			Log.v(LOG_TAG, "Was unable to connect to the host.");
			status = MonitorEntry.STATUS_NO_INTERNET;
		} catch (IOException e) {
			Log.e(LOG_TAG, "An error occurred while fetching a parsing the host's HTML.");
			status = -1;
		}

		resultReceiver.send(status, null);
	}
}
