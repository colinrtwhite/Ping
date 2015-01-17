package com.colinwhite.ping;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.colinwhite.ping.MainActivity.CheckUpServiceReceiver;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

public class CheckIfUpService extends IntentService {

    public static final String STATUS = "WEBSITE_STATUS";
    public static final int IS_UP = 0;
    public static final int IS_DOWN = 1;
    public static final int DOES_NOT_EXIST = 2;
    public static final int OTHER = 3;
    private static final String HOST = "http://www.downforeveryoneorjustme.com/";
    private String mUrl = "google.con";

    public CheckIfUpService() {
        super("CheckIfUpService");
    }

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public CheckIfUpService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String html = getHtml();

        Pattern up = Pattern.compile("It's just you.");
        Pattern down = Pattern.compile("It's not just you!");
        Pattern doesNotExist = Pattern.compile("doesn't look like a site on the interwho.");

        Intent response = new Intent()
                .setAction(CheckUpServiceReceiver.ACTION_RESPONSE)
                .addCategory(Intent.CATEGORY_DEFAULT);

        if (up.matcher(html).find()) {
            response.putExtra(STATUS, IS_UP);
        } else if (down.matcher(html).find()) {
            response.putExtra(STATUS, IS_DOWN);
        } else if (doesNotExist.matcher(html).find()) {
            response.putExtra(STATUS, DOES_NOT_EXIST);
        } else {
            response.putExtra(STATUS, OTHER);
        }

        sendBroadcast(response);
    }

    private String getHtml() {
        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(HOST + mUrl);
            HttpResponse response = client.execute(request);

            InputStream in = response.getEntity().getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder html = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                html.append(line);
            }
            in.close();

            return html.toString();
        } catch (IOException e) {
            Log.e("CheckIfUpService", "onHandleIntent failed; error: " + e.toString());
        }

        return "";
    }
}
