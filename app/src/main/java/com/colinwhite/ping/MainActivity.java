package com.colinwhite.ping;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.pref.SettingsActivity;
import com.colinwhite.ping.widget.ClearableEditText;

/**
 * The MainActivity handles the logic for all the UI elements in activity_mail.xml and is the main
 * landing page for the app.
 */
public class MainActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    public static final String LOG_TAG = MainActivity.class.getSimpleName();

    // UI elements
    private static Toolbar mToolbar;
    private static ImageButton mPingButton;
    private static ImageButton mFloatingButton;
    private static ClearableEditText mClearableTextField;
    private static LinearLayout mActivityContainer;
    private static ListView mMonitorList;

    private Vibrator mVibratorService;
    private MonitorAdapter mAdapter;
    private CursorLoader mCursorLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the top level View of this Activity.
        mActivityContainer = (LinearLayout) findViewById(R.id.activity_container);

        // Attach the main_toolbar to the activity.
        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
        }

        // Get the service for haptic feedback.
        mVibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Set the onClickListener for the ping button to attempt to start the PingService.
        mPingButton = (ImageButton) findViewById(R.id.ping_button);
        mPingButton.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v) {
                // Pulse haptic feedback.
                mVibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

                String inputText = mClearableTextField.getText().toString();
                if (isValidInput(inputText)) {
                    startPingService(inputText);
                }
            }
        });

        // Give the same logic to the "enter" key on the soft keyboard while in the EditText.
        mClearableTextField = (ClearableEditText) findViewById(R.id.url_text_field_quick);
        mClearableTextField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean isConfirm =
                        (actionId == EditorInfo.IME_ACTION_DONE) ||
                                (actionId == EditorInfo.IME_ACTION_NEXT);
                String inputText = mClearableTextField.getText().toString();
                if (isConfirm && isValidInput(inputText)) {
                    startPingService(inputText);
                }
                return isConfirm;
            }
        });

        // If passed a URL (normally through a share action), check it.
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String url = (String) extras.get(Intent.EXTRA_TEXT); // Key used by Chrome.
            if (url != null) {
                // Get rid of everything before www.
                int index = url.indexOf("//");
                if (index != -1) {
                    url = url.substring(index + 2);
                }

                startPingService(url);
                mClearableTextField.setText(url);
            }
        }

        // Set the floating button to open the MonitorDetailActivity.
        mFloatingButton = (ImageButton) findViewById(R.id.add_button);
        mFloatingButton.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v) {
                // Pulse haptic feedback.
                mVibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

                // Pass the value of the EditText to MonitorDetailActivity.
                Intent monitorDetailActivityIntent = new Intent(getApplicationContext(),
                        MonitorDetailActivity.class);
                String text = mClearableTextField.getText().toString();
                if (!text.isEmpty()) {
                    monitorDetailActivityIntent.putExtra(MonitorEntry.URL, text);
                }

                // Show that we are creating a new Monitor.
                monitorDetailActivityIntent.putExtra(MonitorDetailActivity.PAGE_TYPE_ID,
                        MonitorDetailActivity.PAGE_CREATE);

                startActivity(monitorDetailActivityIntent);
            }
        });

        // Set the text that is shown when the list of monitors is empty.
        mMonitorList = (ListView) findViewById(R.id.monitor_list);
        mMonitorList.setEmptyView(findViewById(R.id.empty_monitor_list));

        // Set the any items in the Monitor ListView to open up their detail activity.
        mMonitorList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent monitorDetailActivityIntent = new Intent(getApplicationContext(), MonitorDetailActivity.class);
                // Show that we are looking at an existing Monitor.
                monitorDetailActivityIntent.putExtra(MonitorDetailActivity.PAGE_TYPE_ID,
                        MonitorDetailActivity.PAGE_DETAIL);
                monitorDetailActivityIntent.putExtra(MonitorEntry._ID, id);
                monitorDetailActivityIntent.putExtra(MonitorEntry.URL, ((TextView) view.findViewById(R.id.list_item_url)).getText());

                startActivity(monitorDetailActivityIntent);
            }
        });

        // Initialise the Loader for the ListView.
        mAdapter = new MonitorAdapter(this, null, 0);
        mMonitorList.setAdapter(mAdapter);
        getLoaderManager().initLoader(1, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_icon_reference) {
            startActivity(new Intent(this, IconReferenceActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Verify that the input received from the Toolbar looks like a valid URL that we can ping.
     *
     * @param inputText The raw input text from the EditText.
     * @return Whether the input text looks like a valid URL.
     */
    private boolean isValidInput(String inputText) {
        if (inputText.isEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_input_url_empty), Toast.LENGTH_LONG).show();
            return false;
        } else if (!Patterns.WEB_URL.matcher(inputText).matches()) {
            Toast.makeText(this, getString(R.string.invalid_input_url), Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    /**
     * Start PingService and send it the passed inputText.
     *
     * @param inputText A valid URL that we can ping.
     */
    private void startPingService(String inputText) {
        Intent pingServiceIntent = new Intent(getApplicationContext(), PingService.class);

        // Add the URL from the text field to the Intent.
        pingServiceIntent.putExtra(MonitorEntry.URL, inputText);

        // Close the virtual keyboard.
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mClearableTextField.getWindowToken(), 0);

        // Remove focus from mClearableTextField once URL has been entered.
        mActivityContainer.requestFocus();

        // Instantiate the intent filter and the receiver to receive the output.
        IntentFilter filter = new IntentFilter(PingServiceReceiver.ACTION_RESPONSE);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        PingServiceReceiver receiver = new PingServiceReceiver();
        registerReceiver(receiver, filter);

        startService(pingServiceIntent);
    }

    // Required to implement a CursorAdapter on the main ListView.
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // We need these columns from the database to run the CursorAdapter.
        String[] projection = {
                MonitorEntry._ID,
                MonitorEntry.TITLE,
                MonitorEntry.URL,
                MonitorEntry.PING_FREQUENCY,
                MonitorEntry.END_TIME,
                MonitorEntry.TIME_LAST_CHECKED,
                MonitorEntry.STATUS};

        mCursorLoader = new CursorLoader(this, MonitorEntry.CONTENT_URI, projection, null, null, null);
        return mCursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Try and show the data.
        if (mAdapter != null && data != null) {
            mAdapter.swapCursor(data);
        } else {
            Log.v(LOG_TAG, "OnLoadFinished: mAdapter is null.");
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Try and show the data.
        if (mAdapter != null) {
            mAdapter.swapCursor(null);
        } else {
            Log.v(LOG_TAG, "OnLoadFinished: mAdapter is null.");
        }
    }

    /**
     * Simple BroadcastReceiver that is notified when the background PingService finishes and updates
     * the main TextView with the returned information.
     */
    public class PingServiceReceiver extends BroadcastReceiver {
        // Filter value for the receiver.
        public static final String ACTION_RESPONSE =
                "com.colinwhite.ping.intent.action.CHECK_UP_SERVICE_COMPLETE";

        /**
         * Handle the status information the PingService broadcasts and display the relevant Toast
         * to the user.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(MonitorEntry.STATUS, -1)) {
                case MonitorEntry.STATUS_IS_UP:
                    Toast.makeText(context, R.string.is_up, Toast.LENGTH_LONG).show();
                    break;
                case MonitorEntry.STATUS_IS_DOWN:
                    Toast.makeText(context, R.string.is_down, Toast.LENGTH_LONG).show();
                    break;
                case MonitorEntry.STATUS_IS_NOT_WEBSITE:
                    Toast.makeText(context, R.string.does_not_exist, Toast.LENGTH_LONG).show();
                    break;
                case MonitorEntry.STATUS_NO_INTERNET:
                    Toast.makeText(context, R.string.no_internet_connection, Toast.LENGTH_LONG).show();
                    break;
                default:
                    Toast.makeText(context, R.string.other, Toast.LENGTH_LONG).show();
                    break;
            }

            // Don't leak the BroadcastReceiver. The receiver will be re-registered if/when we launch
            // PingService again.
            context.unregisterReceiver(this);
        }
    }
}
