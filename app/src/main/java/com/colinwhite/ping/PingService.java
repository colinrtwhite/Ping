package com.colinwhite.ping;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * PingService resolves if the HOST can see the user's website and sends its result to
 * PingServiceReceiver.
 *
 * @see com.colinwhite.ping.MainActivity.PingServiceReceiver
 */
public class PingService extends IntentService {

    public static final String STATUS_ID = "WEBSITE_STATUS";
    public static final int IS_UP = 0;
    public static final int IS_DOWN = 1;
    public static final int DOES_NOT_EXIST = 2;
    public static final int NO_INTERNET_CONNECTION = 3;
    public static final int OTHER = 4;
    private static final String HOST = "http://www.downforeveryoneorjustme.com/";

    public PingService() {
        super("PingService");
    }

    /**
     * Creates an IntentService.  Invoked by your IntentService's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public PingService(String name) {
        super(name);
    }

    /**
     * Calls getHtml and parses the returned HTML from the HOST to see if the user's website
     * is accessible by the host or not. Sends a broadcast response for PingServiceReceiver.
     *
     * @param intent Intent that was sent to start this service.
     * @see com.colinwhite.ping.MainActivity.PingServiceReceiver
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        // Recover the URL from the intent and get the returned HTML from our request.
        String url = intent.getStringExtra(MainActivity.URL_ID);
        String html = getHtml(url);

        Pattern up = Pattern.compile("It's just you.");
        Pattern down = Pattern.compile("It's not just you!");
        Pattern doesNotExist = Pattern.compile("doesn't look like a site on the interwho.");

        Intent response = new Intent()
                .setAction(MainActivity.PingServiceReceiver.ACTION_RESPONSE)
                .addCategory(Intent.CATEGORY_DEFAULT);

        if (!isNetworkConnected()) {
            response.putExtra(STATUS_ID, NO_INTERNET_CONNECTION);
        } else if (up.matcher(html).find()) {
            response.putExtra(STATUS_ID, IS_UP);
        } else if (down.matcher(html).find()) {
            response.putExtra(STATUS_ID, IS_DOWN);
        } else if (doesNotExist.matcher(html).find()) {
            response.putExtra(STATUS_ID, DOES_NOT_EXIST);
        } else {
            response.putExtra(STATUS_ID, OTHER);
        }

        sendBroadcast(response);
    }

    /**
     * Send an HTTP request to the HOST and return its HTML.
     *
     * @param url The URL the check through the HOST.
     * @return A long string of HTML all on one line. On error, returns an empty string.
     */
    private String getHtml(String url) {
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
        }

        return "";
    }

    private boolean isNetworkConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        // If info is null, there are no active networks.
        return (info != null);
    }
}
