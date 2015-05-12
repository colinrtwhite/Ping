package com.colinwhite.ping;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.pref.SettingsActivity;
import com.colinwhite.ping.widget.ClearableEditText;
import com.melnykov.fab.FloatingActionButton;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

/**
 * The MainActivity handles the logic for all the UI elements in activity_mail.xml and is the main
 * landing page for the app.
 */
public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private final PingServiceReceiver resultReceiver = new PingServiceReceiver(new Handler());
    private Vibrator mVibratorService;
    private MonitorAdapter mAdapter;

    // UI elements
    @InjectView(R.id.toolbar) Toolbar toolbar;
    @InjectView(R.id.url_text_field_quick) ClearableEditText clearableEditText;
    @InjectView(R.id.activity_container) LinearLayout activityContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
        setSupportActionBar(toolbar);

        // Get the service for haptic feedback.
        mVibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Give the same logic to the "enter" key on the soft keyboard while in the EditText.
        clearableEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean isConfirm =
                        (actionId == EditorInfo.IME_ACTION_DONE) ||
                                (actionId == EditorInfo.IME_ACTION_NEXT);
                String inputText = clearableEditText.getText().toString();
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
                clearableEditText.setText(url);
            }
        }

        // Set the text that is shown when the list of monitors is empty.
        ListView monitorList = (ListView) findViewById(R.id.monitor_list);
        monitorList.setEmptyView(findViewById(R.id.empty_monitor_list));

        // Set the any items in the Monitor ListView to open up their detail activity.
        monitorList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent monitorDetailIntent = new Intent(getApplicationContext(), MonitorDetailActivity.class);
                // Show that we are looking at an existing Monitor.
                monitorDetailIntent.putExtra(MonitorDetailActivity.PAGE_TYPE_ID,
                        MonitorDetailActivity.PAGE_DETAIL);
                monitorDetailIntent.putExtra(MonitorEntry._ID, id);
                monitorDetailIntent.putExtra(MonitorEntry.URL, ((TextView) view.findViewById(R.id.list_item_url)).getText());

                startActivity(monitorDetailIntent);
            }
        });

        // Initialise the Loader for the ListView.
        mAdapter = new MonitorAdapter(this, null, 0);
        monitorList.setAdapter(mAdapter);
        getLoaderManager().initLoader(1, null, this);

        // Attach the FAB to the ListView.
        FloatingActionButton addButton = (FloatingActionButton) findViewById(R.id.add_button);
        addButton.attachToListView(monitorList);
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

        // Add the URL from the text field and the ResultReciever to the Intent.
        pingServiceIntent.putExtra(MonitorEntry.URL, inputText);
        pingServiceIntent.putExtra(PingService.RESULT_RECEIVER_KEY, resultReceiver);

        // Close the virtual keyboard.
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(clearableEditText.getWindowToken(), 0);

        // Remove focus from clearableEditText once URL has been entered.
        activityContainer.requestFocus();

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

        return new CursorLoader(this, MonitorEntry.CONTENT_URI, projection, null, null, null);
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
    public class PingServiceReceiver extends ResultReceiver {
        public PingServiceReceiver(Handler handler) { super(handler); }

        /**
         * Handle the status information the PingService broadcasts and display the relevant Toast
         * to the user.
         */
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            switch (resultCode) {
                case MonitorEntry.STATUS_IS_UP:
                    Toast.makeText(MainActivity.this, R.string.is_up, Toast.LENGTH_LONG).show();
                    break;
                case MonitorEntry.STATUS_IS_DOWN:
                    Toast.makeText(MainActivity.this, R.string.is_down, Toast.LENGTH_LONG).show();
                    break;
                case MonitorEntry.STATUS_IS_NOT_WEBSITE:
                    Toast.makeText(MainActivity.this, R.string.does_not_exist, Toast.LENGTH_LONG).show();
                    break;
                case MonitorEntry.STATUS_NO_INTERNET:
                    Toast.makeText(MainActivity.this, R.string.no_internet_connection, Toast.LENGTH_LONG).show();
                    break;
                default:
                    Toast.makeText(MainActivity.this, R.string.other, Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    @OnClick(R.id.ping_button)
    public void onPingButtonClick() {
        // Pulse haptic feedback.
        mVibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

        String inputText = clearableEditText.getText().toString();
        if (isValidInput(inputText)) {
            startPingService(inputText);
        }
    }

    @OnClick(R.id.add_button)
    public void onFabClick() {
        // Pulse haptic feedback.
        mVibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

        // Pass the value of the EditText to MonitorDetailActivity.
        Intent monitorDetailActivityIntent = new Intent(getApplicationContext(),
                MonitorDetailActivity.class);
        String text = clearableEditText.getText().toString();
        if (!text.isEmpty()) {
            monitorDetailActivityIntent.putExtra(MonitorEntry.URL, text);
        }

        // Show that we are creating a new Monitor.
        monitorDetailActivityIntent.putExtra(MonitorDetailActivity.PAGE_TYPE_ID,
                MonitorDetailActivity.PAGE_CREATE);

        startActivity(monitorDetailActivityIntent);
    }
}
