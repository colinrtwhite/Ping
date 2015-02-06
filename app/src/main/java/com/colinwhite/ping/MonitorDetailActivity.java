package com.colinwhite.ping;

import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

import com.colinwhite.ping.data.PingContract;
import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.sync.PingSyncAdapter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MonitorDetailActivity extends ActionBarActivity {
    public static final String LOG_TAG = MonitorDetailActivity.class.getSimpleName();
    public static final String PAGE_TYPE_ID = "PAGE_TYPE_ID";
    public static final String PAGE_CREATE = "PAGE_CREATE";
    public static final String PAGE_DETAIL = "PAGE_DETAIL";
    private static final int PING_FREQUENCY_ON_CREATE = 4;
    private static final String DATE_FORMAT = "EEEE, d MMMM, y";
    public static final int[] PING_FREQUENCY_MINUTES = {1, 5, 15, 30, 60, 120, 240, 720, 1440};

    // Fields that are used in the database.
    private static EditText mTitleField;
    private static EditText mUrlField;
    private static SeekBar mPingFrequency;
    private static Calendar mSelectedDateTime;

    // Other UI elements
    private static Toolbar mToolbar;
    private static ImageView mStatusIcon;
    private static TextView mDatePickerOutput;
    private static TextView mTimePickerOutput;
    private static DatePickerDialog mDatePickerDialog;
    private static TimePickerDialog mTimePickerDialog;
    private static Button mConfirmButton;
    private static Button mDeleteButton;
    private static TextView mPingFrequencyExplanation;
    private static Switch mDatePickerSwitch;
    private static TextView mExpirationDateExplanation;

    // Only used on DETAIL pages
    private static Cursor mCursor; // Contains the full database row on the current monitor.
    private boolean mHasEndDate = false;

    private Intent mStartIntent;
    private SimpleDateFormat mDateFormat;
    private SimpleDateFormat mTimeFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor_detail);

        // Set up the Toolbar.
        mToolbar = (Toolbar) findViewById(R.id.create_monitor_toolbar);
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialise some UI elements.
        mUrlField = (EditText) findViewById(R.id.url_text_field_create);
        mTitleField = (EditText) findViewById(R.id.create_monitor_title);
        mPingFrequency = (SeekBar) findViewById(R.id.ping_frequency_seek_bar);
        mDatePickerOutput = (TextView) findViewById(R.id.date_picker_output);
        mTimePickerOutput = (TextView) findViewById(R.id.time_picker_output);
        mExpirationDateExplanation = (TextView) findViewById(R.id.expiration_date_explanation);
        mConfirmButton = (Button) findViewById(R.id.save_button);
        mPingFrequencyExplanation = (TextView) findViewById(R.id.ping_frequency_explanation);
        mDatePickerSwitch = (Switch) findViewById(R.id.date_picker_switch);
        mStatusIcon = (ImageView) findViewById(R.id.status_icon);

        // Initialise the formats of the date and time pickers and get the date picker's initial date.
        mDateFormat = new SimpleDateFormat(DATE_FORMAT);
        mTimeFormat = new SimpleDateFormat(Utility.TIME_FORMAT_12_HOURS);
        mSelectedDateTime = Calendar.getInstance();

        // Set the ping frequency SeekBar to update its explanation TextField when its progress changes.
        mPingFrequency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setPingFrequencyExplanation(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { /* Do nothing. */ }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { /* Do nothing. */ }
        });

        // Change UI elements and data whether we are creating or updating/looking at a Monitor.
        // Default to a creation activity.
        mStartIntent = getIntent();
        if (mStartIntent.getStringExtra(PAGE_TYPE_ID).equals(PAGE_DETAIL)) {
            buildDetailPageElements();
        } else {
            buildCreatePageElements();
        }

        // Hide any notifications for this Monitor.
        NotificationManager notificationManager = ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        notificationManager.cancel((int) getIntent().getLongExtra(MonitorEntry._ID, -1));

        // Set the expiry date + time section of the page.
        setExpiryDateElements();
    }

    private void setExpiryDateElements() {
        // Set the Switch to change the visibility of the pickers and explanation.
        mDatePickerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int visibility = (isChecked) ? View.VISIBLE : View.GONE;
                mDatePickerOutput.setVisibility(visibility);
                mTimePickerOutput.setVisibility(visibility);
                mExpirationDateExplanation.setVisibility(visibility);
                mHasEndDate = isChecked;
            }
        });

        // Set the Monitor's preference if this is a DETAIL page.
        if (mStartIntent.getStringExtra(PAGE_TYPE_ID).equals(PAGE_DETAIL)) {
            mDatePickerSwitch.setChecked(mCursor.getLong(mCursor.getColumnIndex(MonitorEntry.END_TIME))
                    != MonitorEntry.END_DATE_NONE);
        }

        // -- TIME PICKER --
        // Make the TimePicker set the output TextField's time when it is changed.
        final TimePickerDialog.OnTimeSetListener time = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                mSelectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mSelectedDateTime.set(Calendar.MINUTE, minute);

                // Set the output TextView's text.
                mTimePickerOutput.setText(mTimeFormat.format(mSelectedDateTime.getTime()));
            }
        };

        // Set the TimePicker to popup when the TextField is clicked.
        mTimePickerDialog = new TimePickerDialog(
                this,
                time,
                mSelectedDateTime.get(Calendar.HOUR_OF_DAY),
                mSelectedDateTime.get(Calendar.MINUTE),
                false);
        mTimePickerOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTimePickerDialog.show();
            }
        });

        // -- DATE PICKER --
        // Make the DatePicker set the output TextField's date when it is changed.
        final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                mSelectedDateTime.set(Calendar.YEAR, year);
                mSelectedDateTime.set(Calendar.MONTH, month);
                mSelectedDateTime.set(Calendar.DAY_OF_MONTH, day);

                // Set the output TextView's text.
                mDatePickerOutput.setText(mDateFormat.format(mSelectedDateTime.getTime()));
            }
        };

        // Set the DatePicker to popup when the TextField is clicked.
        mDatePickerDialog = new DatePickerDialog(
                this,
                date,
                mSelectedDateTime.get(Calendar.YEAR),
                mSelectedDateTime.get(Calendar.MONTH),
                mSelectedDateTime.get(Calendar.DAY_OF_MONTH));
        mDatePickerOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDatePickerDialog.show();
            }
        });
    }

    // Build the elements necessary to update a Monitor's details/delete it.
    private void buildDetailPageElements() {
        // Change the title
        setTitle(R.string.monitor_detail_activity_title);

        // Get the ID of the Monitor.
        final long monitorId = mStartIntent.getLongExtra(MonitorEntry._ID, -1);
        if (monitorId == -1) {
            Log.e(LOG_TAG, "Intent does not contain a Monitor ID.");
            finish(); // Close the activity.
        }

        // Get the specific Monitor's data.
        final String[] projection = {
                MonitorEntry._ID,
                MonitorEntry.TITLE,
                MonitorEntry.URL,
                MonitorEntry.PING_FREQUENCY,
                MonitorEntry.END_TIME};
        final String selection = MonitorEntry._ID + " = ?";
        final String[] selectionArgs = {String.valueOf(monitorId)};
        mCursor = getContentResolver().query(
                MonitorEntry.CONTENT_URI.buildUpon().appendPath(String.valueOf(monitorId)).build(),
                projection, selection, selectionArgs, null);

        // Show the delete button and make it work.
        mDeleteButton = (Button) findViewById(R.id.delete_button);
        mDeleteButton.setVisibility(View.VISIBLE);
        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Delete the Monitor's database entry and its sync account then close the activity.
                getContentResolver().delete(MonitorEntry.CONTENT_URI, selection, selectionArgs);
                PingSyncAdapter.removePeriodicSync(
                        v.getContext(),
                        mStartIntent.getStringExtra(MonitorEntry.URL),
                        (int) monitorId);
                finish();
            }
        });

        // Populate all the fields.
        mCursor.moveToFirst();
        final String title = mCursor.getString(mCursor.getColumnIndex(MonitorEntry.TITLE));
        mTitleField.setText(title);
        final String url = mCursor.getString(mCursor.getColumnIndex(MonitorEntry.URL));
        mUrlField.setText(url);
        final int pingFrequency = mCursor.getInt(mCursor.getColumnIndex(MonitorEntry.PING_FREQUENCY));
        mPingFrequency.setProgress(pingFrequency);
        setPingFrequencyExplanation(pingFrequency);

        // Populate the endDate, only if one already exists.
        long endDateInMillis = mCursor.getLong(mCursor.getColumnIndex(MonitorEntry.END_TIME));
        if (endDateInMillis != MonitorEntry.END_DATE_NONE) {
            mSelectedDateTime.setTimeInMillis(endDateInMillis);
            mDatePickerOutput.setText(mDateFormat.format(mSelectedDateTime.getTime()));
            mTimePickerOutput.setText(mTimeFormat.format(mSelectedDateTime.getTime()));
        }

        // Set the confirm button to update the current Monitor and close the activity.
        mConfirmButton.setText(R.string.update_button_text);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Update all fields. Don't bother to check, as it takes more time than to just
                // update all the possible columns.
                saveAllFields(PAGE_DETAIL, selection, selectionArgs);
            }
        });
    }

    // Build the elements for a Monitor creation version of this Activity.
    private void buildCreatePageElements() {
        // Set the URL EditText to the value passed in the Intent, if it exists.
        if (mStartIntent.hasExtra(MonitorEntry.URL)) {
            mUrlField.setText(mStartIntent.getStringExtra(MonitorEntry.URL));
        }

        // Set the confirm button to create a new entry in the database and close the activity.
        mConfirmButton.setText(R.string.create_button_text);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAllFields(PAGE_CREATE, null, null);
            }
        });

        // Set the initial ping frequency values.
        mPingFrequency.setProgress(PING_FREQUENCY_ON_CREATE);
        setPingFrequencyExplanation(PING_FREQUENCY_ON_CREATE);
    }

    /**
     * Save all user-accessible fields in the activity, and create a new Monitor/update a current one.
     * Used for the CREATE/UPDATE buttons.
     */
    private void saveAllFields(String pageType, String selection, String[] selectionArgs) {
        ContentValues values = new ContentValues();
        values.put(MonitorEntry.TITLE, mTitleField.getText().toString());
        values.put(MonitorEntry.URL, mUrlField.getText().toString());
        values.put(MonitorEntry.PING_FREQUENCY, mPingFrequency.getProgress());
        long endDate = (mHasEndDate) ? mSelectedDateTime.getTimeInMillis() : MonitorEntry.END_DATE_NONE;
        values.put(MonitorEntry.END_TIME, endDate);

        int monitorId = -1;
        if (PAGE_DETAIL.equals(pageType)) {
            // This is a detail page.
            getContentResolver().update(MonitorEntry.CONTENT_URI, values, selection, selectionArgs);

            // The only selection arg should be the Monitor ID.
            monitorId = Integer.parseInt(selectionArgs[0]);
            // Remove the current periodic sync timer for this Monitor, later we create a new one.
            PingSyncAdapter.removePeriodicSync(
                    this,
                    mStartIntent.getStringExtra(MonitorEntry.URL),
                    monitorId);
        } else {
            // This is a create page.
            Uri returnUri = getContentResolver().insert(PingContract.MonitorEntry.CONTENT_URI, values);

            // Get the ID from the URI and initialise the sync parameters.
            String path = returnUri.getPath();
            monitorId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
        }

        // Don't create a sync timer for the last ping frequency option.
        if (mPingFrequency.getProgress() != mPingFrequency.getMax()) {
            // Create the new sync timer for the Monitor.
            PingSyncAdapter.createPeriodicSync(
                    this,
                    mUrlField.getText().toString(),
                    monitorId,
                    (int) TimeUnit.MINUTES.toSeconds(PING_FREQUENCY_MINUTES[mPingFrequency.getProgress()]));
        } else {
            // Instead, we just sync once.
            PingSyncAdapter.syncImmediately(
                    this,
                    PingSyncAdapter.getSyncAccount(this),
                    mUrlField.getText().toString(),
                    monitorId);
        }

        finish();
    }

    private void setPingFrequencyExplanation(int progress) {
        if (progress == mPingFrequency.getMax()) {
            mPingFrequencyExplanation.setText(R.string.ping_explanation_never);
            return;
        }
        long duration = TimeUnit.MINUTES.toMillis(PING_FREQUENCY_MINUTES[progress]);

        // Place the formatted duration in the resource string.
        String formattedStr = String.format(
                getString(R.string.ping_frequency_explanation),
                Utility.formatTimeDuration(duration));

        // Set the result as the explanation TextField for the frequency SeekBar.
        if (progress == 0) {
            formattedStr += "<br><font color=\"#ff0000\">WARNING:</font> This will keep turn your cell radio on very often and will reduce battery life.";
            mPingFrequencyExplanation.setText(Html.fromHtml(formattedStr));
        } else {
            mPingFrequencyExplanation.setText(formattedStr);
        }

        mPingFrequencyExplanation.setText(Html.fromHtml(formattedStr));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_create_monitor, menu);

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
}
