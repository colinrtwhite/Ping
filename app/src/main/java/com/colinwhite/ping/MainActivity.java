package com.colinwhite.ping;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SyncInfo;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.pref.PreferencesActivity;
import com.colinwhite.ping.sync.PingSyncAdapter;
import com.colinwhite.ping.widget.ClearableEditText;
import com.melnykov.fab.FloatingActionButton;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

/**
 * The MainActivity handles the logic for all the UI elements in activity_mail.xml and is the main
 * landing page for the app.
 */
public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String PREF_SORT_ORDER_ID = "sort_order_pref";
    private static final String PREF_SORT_ORDER_DEFAULT_VALUE = MonitorEntry._ID + " DESC";

    private final PingServiceReceiver resultReceiver = new PingServiceReceiver(new Handler());
    private SharedPreferences sharedPref;
    private Vibrator vibratorService;
    private MonitorAdapter monitorAdapter;
    private InputMethodManager inputMethodManager;
    private Object syncHandle;

    // UI elements
    @InjectView(R.id.toolbar) Toolbar toolbar;
    @InjectView(R.id.url_text_field_quick) ClearableEditText clearableEditText;
    @InjectView(R.id.activity_container) LinearLayout activityContainer;
    @InjectView(R.id.swiper_container) SwipeRefreshLayout swipeContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
        setSupportActionBar(toolbar);

        // Get classes for vibration, preferences, and the keyboard.
        vibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

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
        final ListView monitorList = (ListView) findViewById(R.id.monitor_list);
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

        // Setup refresh listener, which refreshes all Monitors.
        swipeContainer.setEnabled(false);
        swipeContainer.setColorSchemeResources(
                R.color.accent,
                R.color.primary);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Pulse haptic feedback.
                vibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

                // Using the Cursor from the list adapter, get and sync all the Monitors.
                if (Utility.hasNetworkConnection(MainActivity.this)) {
                    final Cursor cursor = monitorAdapter.getCursor();
                    cursor.moveToFirst();

                    SyncStatusObserver observer = new SyncStatusObserver() {
                        int numMonitors = cursor.getCount();

                        @Override
                        public void onStatusChanged(int which) {
                            // Once all Monitors have finished syncing, remove the listener and stop
                            // the refreshing animation.
                            if (numMonitors <= 0) {
                                ContentResolver.removeStatusChangeListener(syncHandle);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeContainer.setRefreshing(false);
                                    }
                                });
                            }

                            // Check if any of the Monitors became active.
                            List<SyncInfo> test = ContentResolver.getCurrentSyncs();
                            for (SyncInfo syncInfo : test) {
                                if (syncInfo.authority.equals(getString(R.string.content_authority))) {
                                    // Decrement the number of expected syncing Monitors.
                                    numMonitors--;
                                }
                            }
                        }
                    };
                    syncHandle = ContentResolver.addStatusChangeListener(
                            ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE,
                            observer);
                    do {
                        // Refresh the Monitor right now.
                        PingSyncAdapter.recreateRefreshPeriodicSync(
                                MainActivity.this,
                                cursor.getString(cursor.getColumnIndex(MonitorEntry.URL)),
                                cursor.getInt(cursor.getColumnIndex(MonitorEntry._ID)),
                                cursor.getInt(cursor.getColumnIndex(MonitorEntry.PING_FREQUENCY)));
                    } while (cursor.moveToNext());
                } else {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.error_poor_connection),
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        // Initialise the Loader for the ListView.
        monitorAdapter = new MonitorAdapter(this, null, 0);
        monitorList.setAdapter(monitorAdapter);
        getLoaderManager().initLoader(0, null, this);

        // Attach the FAB to the ListView.
        final FloatingActionButton addButton = (FloatingActionButton) findViewById(R.id.add_button);
        addButton.attachToListView(monitorList);

        // If we are running Lollipop or higher reveal the FAB with an animation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addButton.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onGlobalLayout() {
                    addButton.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    // Get the centre and final radius for the clipping circle. Set up the animation.
                    int cx = addButton.getWidth() / 2;
                    int cy = addButton.getHeight() / 2;
                    int finalRadius = Math.max(addButton.getWidth(), addButton.getHeight());
                    Animator animation = ViewAnimationUtils.createCircularReveal(addButton, cx, cy, 0, finalRadius);

                    // Make the view visible and start the animation.
                    animation.setStartDelay(1); // Start delay 0 causes the animation to start before the activity is open.
                    animation.setDuration(150);
                    animation.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            addButton.setVisibility(View.VISIBLE);
                        }
                        @Override
                        public void onAnimationEnd(Animator animation) { /* Do nothing. */ }
                        @Override
                        public void onAnimationCancel(Animator animation) { /* Do nothing. */ }
                        @Override
                        public void onAnimationRepeat(Animator animation) { /* Do nothing. */ }
                    });
                    animation.start();
                }
            });
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PREF_SORT_ORDER_ID)) {
            getLoaderManager().restartLoader(0, null, MainActivity.this);
        } else if (key.equals(getString(R.string.pref_key_24_hour_clock))) {
            monitorAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sort_by_date_created:
                sharedPref.edit().putString(PREF_SORT_ORDER_ID, MonitorEntry._ID + " DESC").apply();
                return true;
            case R.id.sort_by_name:
                sharedPref.edit().putString(PREF_SORT_ORDER_ID, MonitorEntry.TITLE + " ASC").apply();
                return true;
            case R.id.sort_by_last_checked:
                sharedPref.edit().putString(PREF_SORT_ORDER_ID, MonitorEntry.TIME_LAST_CHECKED + " DESC").apply();
                return true;
            case R.id.sort_by_state:
                sharedPref.edit().putString(PREF_SORT_ORDER_ID, MonitorEntry.STATUS + " DESC").apply();
                return true;
            case R.id.action_icon_reference:
                startActivity(new Intent(this, IconReferenceActivity.class));
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, PreferencesActivity.class));
                return true;
            case R.id.rate_app:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                } catch (ActivityNotFoundException e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + getPackageName())));
                }
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

        // Add the URL from the text field and the ResultReceiver to the Intent.
        pingServiceIntent.putExtra(MonitorEntry.URL, inputText);
        pingServiceIntent.putExtra(PingService.RESULT_RECEIVER_KEY, resultReceiver);

        // Close the virtual keyboard and remove focus from clearableEditText.
        inputMethodManager.hideSoftInputFromWindow(clearableEditText.getWindowToken(), 0);
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

        return new CursorLoader(this, MonitorEntry.CONTENT_URI, projection, null, null,
                sharedPref.getString(PREF_SORT_ORDER_ID, PREF_SORT_ORDER_DEFAULT_VALUE));
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Try and show the data.
        if (monitorAdapter != null && data != null) {
            // Enable the SwipeRefreshLayout if there is at least one Monitor.
            swipeContainer.setEnabled(data.getCount() > 0);

            monitorAdapter.swapCursor(data);
        } else {
            Log.v(LOG_TAG, "OnLoadFinished: monitorAdapter is null.");
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Try and show the data.
        if (monitorAdapter != null) {
            swipeContainer.setEnabled(false);

            monitorAdapter.swapCursor(null);
        } else {
            Log.v(LOG_TAG, "OnLoadFinished: monitorAdapter is null.");
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
        vibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

        String inputText = clearableEditText.getText().toString();
        if (isValidInput(inputText)) {
            startPingService(inputText);
        }
    }

    @OnClick(R.id.add_button)
    public void onFabClick() {
        // Pulse haptic feedback.
        vibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

        // Pass the value of the EditText to MonitorDetailActivity.
        Intent monitorDetailActivityIntent = new Intent(getApplicationContext(),
                MonitorDetailActivity.class);
        String text = clearableEditText.getText().toString();
        if (!text.isEmpty()) {
            monitorDetailActivityIntent.putExtra(MonitorEntry.URL, text);
        }

        // Add that we are creating a new Monitor.
        monitorDetailActivityIntent.putExtra(MonitorDetailActivity.PAGE_TYPE_ID,
                MonitorDetailActivity.PAGE_CREATE);

        startActivity(monitorDetailActivityIntent);
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent, null);

        // Remove focus from the quick check URL EditText, if it is focused.
        if (inputMethodManager.isAcceptingText()) {
            activityContainer.requestFocus();
        }
    }
}
