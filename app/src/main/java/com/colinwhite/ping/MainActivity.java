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
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.colinwhite.ping.data.PingContract.MonitorEntry;


public class MainActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    public final static String URL_ID = "URL_ID";

    // UI elements
    private static Toolbar mToolbar;
    private static ImageButton mButton;
    private static ImageButton mFloatingButton;
    private static EditText mEditText;
    private static LinearLayout mActivityContainer;
    private static ListView mMonitorList;

    private SimpleCursorAdapter mAdapter;
    private CursorLoader mCursorLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mActivityContainer = (LinearLayout) findViewById(R.id.activity_container);

        // Attach the main_toolbar to the activity.
        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
        }

        // Set the onClickListener for the ping button to attempt to start the PingService.
        mButton = (ImageButton) findViewById(R.id.ping_button);
        mButton.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v) {
                startPingService();
            }
        });

        // Give the same logic to the "enter" key on the soft keyboard while in the EditText.
        mEditText = (EditText) findViewById(R.id.url_text_field_quick);
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean isActionDone = actionId == EditorInfo.IME_ACTION_DONE;
                if (isActionDone) {
                    startPingService();
                }
                return isActionDone;
            }
        });

        // Set the floating button to open the MonitorDetailActivity.
        mFloatingButton = (ImageButton) findViewById(R.id.add_button);
        mFloatingButton.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v) {
                // Pass the value of the EditText to MonitorDetailActivity.
                Intent createMonitorActivityIntent = new Intent(getApplicationContext(),
                        MonitorDetailActivity.class);
                String text = mEditText.getText().toString();
                if (!text.isEmpty()) {
                    createMonitorActivityIntent.putExtra(MonitorDetailActivity.URL_FIELD_VALUE,
                            text);
                }
                startActivity(createMonitorActivityIntent);
            }
        });

        // Set the text that is shown when the list of monitors is empty.
        mMonitorList = (ListView) findViewById(R.id.monitor_list);
        mMonitorList.setEmptyView(findViewById(R.id.empty_monitor_list_text));

        // Initialise the Loader for the ListView.
        String[] uiBindFrom = { MonitorEntry.TITLE, MonitorEntry.URL};
        int[] uiBindTo = { R.id.list_item_title, R.id.list_item_url };
        mAdapter = new SimpleCursorAdapter(this, R.layout.monitor_list_item, null, uiBindFrom, uiBindTo, 0);
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

    private void startPingService() {
        // Retrieve the text from the URL input field.
        String inputText = mEditText.getText().toString();

        // Validate input
        if (!inputText.isEmpty()) {
            Intent pingServiceIntent = new Intent(getApplicationContext(),
                    PingService.class);

            // Add the URL from the text field to the Intent.
            pingServiceIntent.putExtra(URL_ID, inputText);

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
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection = {
                MonitorEntry._ID,
                MonitorEntry.TITLE,
                MonitorEntry.URL,
                MonitorEntry.PING_FREQUENCY,
                MonitorEntry.END_DATE};

        mCursorLoader = new CursorLoader(this, MonitorEntry.CONTENT_URI, projection, null, null, null);
        return mCursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (mAdapter != null && data != null) {
            mAdapter.swapCursor(data);
        } else {
            Log.v("MainActivity", "OnLoadFinished: mAdapter is null.");
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (mAdapter != null) {
            mAdapter.swapCursor(null);
        } else {
            Log.v("MainActivity", "OnLoadFinished: mAdapter is null.");
        }
    }

    /**
     * Simple BroadcastReceiver that is notified when the background PingService
     * finishes and updates the main TextView with the returned information.
     */
    public class PingServiceReceiver extends BroadcastReceiver {
        public static final String ACTION_RESPONSE =
                "com.colinwhite.ping.intent.action.CHECK_UP_SERVICE_COMPLETE";

        @Override
        public void onReceive(Context context, Intent intent) {
            TextView textView = (TextView) findViewById(R.id.output_text_view);

            switch (intent.getIntExtra(PingService.STATUS_ID, PingService.OTHER)) {
                case PingService.IS_UP:
                    textView.setText(R.string.is_up);
                    break;
                case PingService.IS_DOWN:
                    textView.setText(R.string.is_down);
                    break;
                case PingService.DOES_NOT_EXIST:
                    textView.setText(R.string.does_not_exist);
                    break;
                case PingService.NO_INTERNET_CONNECTION:
                    textView.setText(R.string.no_internet_connection);
                    break;
                case PingService.OTHER:
                    textView.setText(R.string.other);
                    break;
            }

            // Don't leak the BroadcastReceiver.
            context.unregisterReceiver(this);
        }
    }
}
