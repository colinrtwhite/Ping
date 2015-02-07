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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.sync.PingSyncAdapter;


public class MainActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    public static final String LOG_TAG = MainActivity.class.getSimpleName();

    // UI elements
    private static Toolbar mToolbar;
    private static ImageButton mPingButton;
    private static ImageButton mFloatingButton;
    private static EditText mEditText;
    private static LinearLayout mActivityContainer;
    private static ListView mMonitorList;

    private Vibrator mVibratorService;
    private MonitorAdapter mAdapter;
    private CursorLoader mCursorLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Attempt to initialise the SyncAdapter Account for Ping.
        PingSyncAdapter.getSyncAccount(this);

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

                String inputText = mEditText.getText().toString();
                if (isValidInput(inputText)) {
                    startPingService(inputText);
                }
            }
        });

        // Give the same logic to the "enter" key on the soft keyboard while in the EditText.
        mEditText = (EditText) findViewById(R.id.url_text_field_quick);
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean isActionDone = actionId == EditorInfo.IME_ACTION_DONE;
                String inputText = mEditText.getText().toString();
                if (isActionDone && isValidInput(inputText)) {
                    startPingService(inputText);
                }
                return isActionDone;
            }
        });

        // Set the floating button to open the MonitorDetailActivity.
        mFloatingButton = (ImageButton) findViewById(R.id.add_button);
        mFloatingButton.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v) {
                // Pulse haptic feedback.
                mVibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

                // Pass the value of the EditText to MonitorDetailActivity.
                Intent monitorDetailActivityIntent = new Intent(getApplicationContext(),
                        MonitorDetailActivity.class);
                String text = mEditText.getText().toString();
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

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

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

    private void startPingService(String inputText) {
        Intent pingServiceIntent = new Intent(getApplicationContext(), PingService.class);

        // Add the URL from the text field to the Intent.
        pingServiceIntent.putExtra(MonitorEntry.URL, inputText);

        // Close the virtual keyboard.
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);

        // Remove focus from mEditText once URL has been entered.
        mActivityContainer.requestFocus();

        // Instantiate the intent filter and the receiver to receive the output.
        IntentFilter filter = new IntentFilter(PingServiceReceiver.ACTION_RESPONSE);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        PingServiceReceiver receiver = new PingServiceReceiver();
        registerReceiver(receiver, filter);

        startService(pingServiceIntent);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
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
        if (mAdapter != null && data != null) {
            mAdapter.swapCursor(data);
        } else {
            Log.v(LOG_TAG, "OnLoadFinished: mAdapter is null.");
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
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
        public static final String ACTION_RESPONSE =
                "com.colinwhite.ping.intent.action.CHECK_UP_SERVICE_COMPLETE";

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(PingService.STATUS_ID, -1)) {
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

            // Don't leak the BroadcastReceiver.
            context.unregisterReceiver(this);
        }
    }
}
