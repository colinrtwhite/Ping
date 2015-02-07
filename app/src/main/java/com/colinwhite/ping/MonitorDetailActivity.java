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
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

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
    private static TextView mPingFrequencyExplanation;
    private static Switch mDatePickerSwitch;
    private static TextView mExpirationDateExplanation;
    private static TextView mLastCheckedField;

    // Only used on DETAIL pages
    private ContentValues mValues;
    private boolean mHasEndDate = false;
    private boolean mIsTimePickerSet = false;
    private boolean mIsDatePickerSet = false;

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
        mPingFrequencyExplanation = (TextView) findViewById(R.id.ping_frequency_explanation);
        mDatePickerSwitch = (Switch) findViewById(R.id.date_picker_switch);
        mStatusIcon = (ImageView) findViewById(R.id.status_icon);
        mLastCheckedField = (TextView) findViewById(R.id.detail_last_checked_text);

        // Initialise the formats of the date and time pickers and get the date picker's initial date.
        mDateFormat = new SimpleDateFormat(DATE_FORMAT);
        mTimeFormat = new SimpleDateFormat(Utility.TIME_FORMAT_12_HOURS);
        mSelectedDateTime = Calendar.getInstance();
        mSelectedDateTime.set(Calendar.SECOND, 0);
        mSelectedDateTime.set(Calendar.MILLISECOND, 0);

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
        NotificationManager notificationManager = ((NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE));
        notificationManager.cancel((int) getIntent().getLongExtra(MonitorEntry._ID, -1));

        // Set the expiry date + time section of the page.
        setExpirationDateElements();
    }

    // Build the elements necessary to update a Monitor's details/delete it.
    private void buildDetailPageElements() {
        // Change the title.
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
                MonitorEntry.END_TIME,
                MonitorEntry.TIME_LAST_CHECKED,
                MonitorEntry.STATUS};
        final String selection = MonitorEntry._ID + " = ?";
        final String[] selectionArgs = {String.valueOf(monitorId)};
        Cursor cursor = getContentResolver().query(
                MonitorEntry.CONTENT_URI.buildUpon().appendPath(String.valueOf(monitorId)).build(),
                projection, selection, selectionArgs, null);

        // Store all the initial values.
        cursor.moveToFirst();
        mValues = new ContentValues();
        mValues.put(MonitorEntry._ID, cursor.getInt(cursor.getColumnIndex(MonitorEntry._ID)));
        mValues.put(MonitorEntry.TITLE, cursor.getString(cursor.getColumnIndex(MonitorEntry.TITLE)));
        mValues.put(MonitorEntry.URL, cursor.getString(cursor.getColumnIndex(MonitorEntry.URL)));
        mValues.put(MonitorEntry.PING_FREQUENCY, cursor.getInt(cursor.getColumnIndex(MonitorEntry.PING_FREQUENCY)));
        mValues.put(MonitorEntry.END_TIME, cursor.getLong(cursor.getColumnIndex(MonitorEntry.END_TIME)));
        mValues.put(MonitorEntry.TIME_LAST_CHECKED, cursor.getLong(cursor.getColumnIndex(MonitorEntry.TIME_LAST_CHECKED)));
        mValues.put(MonitorEntry.STATUS, cursor.getInt(cursor.getColumnIndex(MonitorEntry.STATUS)));
        cursor.close();

        // Populate all the user-accessible fields.
        final String title = (String) mValues.get(MonitorEntry.TITLE);
        mTitleField.setText(title);
        final String url = (String) mValues.get(MonitorEntry.URL);
        mUrlField.setText(url);
        final int pingFrequency = (int) mValues.get(MonitorEntry.PING_FREQUENCY);
        mPingFrequency.setProgress(pingFrequency);
        setPingFrequencyExplanation(pingFrequency);

        // Populate the endDate, only if one already exists.
        long endDateInMillis = (long) mValues.get(MonitorEntry.END_TIME);
        if (endDateInMillis != MonitorEntry.END_TIME_NONE) {
            mSelectedDateTime.setTimeInMillis(endDateInMillis);
            mDatePickerOutput.setText(mDateFormat.format(mSelectedDateTime.getTime()));
            mTimePickerOutput.setText(mTimeFormat.format(mSelectedDateTime.getTime()) +
                    getString(R.string.approximately_tag));
            mIsDatePickerSet = true;
            mIsTimePickerSet = true;
        }

        // Set the status icon.
        mStatusIcon.setImageDrawable(getResources().getDrawable(
                Utility.getStatusIcon((int) mValues.get(MonitorEntry.STATUS))));
        // NOTE: mLastCheckedField and mStatusIcon do not update if the database changes.
        // Format the time last checked and place it in the resource string.
        long timeLastChecked = (long) mValues.get(MonitorEntry.TIME_LAST_CHECKED);
        mLastCheckedField.setVisibility(View.VISIBLE);
        if (timeLastChecked != MonitorEntry.TIME_LAST_CHECKED_NONE) {
            mLastCheckedField.setText(String.format(
                    getString(R.string.last_checked_text),
                    Utility.formatDate(timeLastChecked)));
        } else {
            // If timeLastChecked is 0, it hasn't been checked yet.
            mLastCheckedField.setText(getString(R.string.last_checked_text_no_info));
        }
    }

    // Build the elements for a Monitor creation version of this Activity.
    private void buildCreatePageElements() {
        // Set the URL EditText to the value passed in the Intent, if it exists.
        if (mStartIntent.hasExtra(MonitorEntry.URL)) {
            mUrlField.setText(mStartIntent.getStringExtra(MonitorEntry.URL));
        }

        // Set the initial ping frequency values.
        mPingFrequency.setProgress(PING_FREQUENCY_ON_CREATE);
        setPingFrequencyExplanation(PING_FREQUENCY_ON_CREATE);
    }

    private void setExpirationDateElements() {
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
            mDatePickerSwitch.setChecked(((long)mValues.get(MonitorEntry.END_TIME))
                    != MonitorEntry.END_TIME_NONE);
        }

        // -- TIME PICKER --
        // Make the TimePicker set the output TextField's time when it is changed.
        final TimePickerDialog.OnTimeSetListener time = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                mSelectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mSelectedDateTime.set(Calendar.MINUTE, minute);

                // Set the output TextView's text.
                mTimePickerOutput.setText(mTimeFormat.format(mSelectedDateTime.getTime()) +
                        getString(R.string.approximately_tag));
                mIsTimePickerSet = true;
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
                mIsDatePickerSet = true;
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

    /**
     * Save all user-accessible fields in the activity, and create a new Monitor/update a current one.
     * Used for the CREATE/UPDATE buttons.
     */
    private void saveAllFields(String pageType, String selection, String[] selectionArgs) {
        ContentValues values = new ContentValues();
        values.put(MonitorEntry.TITLE, mTitleField.getText().toString());
        String url = mUrlField.getText().toString();
        values.put(MonitorEntry.URL, url);
        values.put(MonitorEntry.PING_FREQUENCY, mPingFrequency.getProgress());
        long endDate = (mHasEndDate) ? mSelectedDateTime.getTimeInMillis() : MonitorEntry.END_TIME_NONE;
        values.put(MonitorEntry.END_TIME, endDate);
        // If this is a detail page and the URL has changed, invalidate the last checked time and status.
        if (PAGE_DETAIL.equals(mStartIntent.getStringExtra(PAGE_TYPE_ID)) &&
                !mValues.get(MonitorEntry.URL).equals(url)) {
            values.put(MonitorEntry.TIME_LAST_CHECKED, MonitorEntry.TIME_LAST_CHECKED_NONE);
            values.put(MonitorEntry.STATUS, MonitorEntry.STATUS_NO_INFO);
        }

        int monitorId;
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
            // If the Monitor previously had a removal alarm set, delete it.
            if (((long)mValues.get(MonitorEntry.END_TIME)) != MonitorEntry.END_TIME_NONE) {
                Utility.deleteRemovalAlarm(this, monitorId);
            }
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
            // If the Monitor has been set to never ping automatically, don't add a removal alarm.
            if (endDate != MonitorEntry.END_TIME_NONE) {
                Utility.addRemovalAlarm(this, monitorId, endDate);
            }
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
            formattedStr += getString(R.string.ping_frequency_warning);
            mPingFrequencyExplanation.setText(Html.fromHtml(formattedStr));
        } else {
            mPingFrequencyExplanation.setText(formattedStr);
        }

        mPingFrequencyExplanation.setText(Html.fromHtml(formattedStr));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the empty menu.
        getMenuInflater().inflate(R.menu.menu_detail_monitor, menu);

        // Get the Toolbar button references.
        Menu toolbarMenu = mToolbar.getMenu();
        MenuItem saveButton = toolbarMenu.findItem(R.id.action_content_save);
        MenuItem deleteButton = toolbarMenu.findItem(R.id.action_delete);

        // Set the visibility and titles of the Toolbar buttons depending on the page type.
        if (PAGE_DETAIL.equals(mStartIntent.getStringExtra(PAGE_TYPE_ID))) {
            saveButton.setTitle(R.string.save_button_title);
            deleteButton.setVisible(true);
        } else {
            saveButton.setTitle(R.string.create_button_title);
            deleteButton.setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_content_save:
                if (isValidInput()) {
                    if (PAGE_DETAIL.equals(mStartIntent.getStringExtra(PAGE_TYPE_ID))) {
                        // Update all fields. Don't bother to check, as it takes more time than to
                        // just update all the possible columns.
                        int monitorId = (int) mValues.get(MonitorEntry._ID);
                        final String selection = MonitorEntry._ID + " = ?";
                        final String[] selectionArgs = {String.valueOf(monitorId)};
                        saveAllFields(PAGE_DETAIL, selection, selectionArgs);
                    } else {
                        // Create a new entry in the database and close the activity.
                        saveAllFields(PAGE_CREATE, null, null);
                    }
                }
                return true;
            case R.id.action_delete:
                // Delete the Monitor's database entry and its sync account then close the activity.
                int monitorId = (int) mValues.get(MonitorEntry._ID);
                final String selection = MonitorEntry._ID + " = ?";
                final String[] selectionArgs = {String.valueOf(monitorId)};
                getContentResolver().delete(MonitorEntry.CONTENT_URI, selection, selectionArgs);
                PingSyncAdapter.removePeriodicSync(
                        this,
                        mStartIntent.getStringExtra(MonitorEntry.URL),
                        monitorId);
                // If the Monitor had a removal alarm set, delete it.
                long endDate = (long) mValues.get(MonitorEntry.END_TIME);
                if (endDate != MonitorEntry.END_TIME_NONE) {
                    Utility.deleteRemovalAlarm(this, monitorId);
                }
                finish();
                return true;
            case R.id.action_settings:
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean isValidInput() {
        if ("".equals(mTitleField.getText().toString())) {
            Toast.makeText(this, getString(R.string.invalid_input_title), Toast.LENGTH_LONG).show();
            return false;
        } else if ("".equals(mUrlField.getText().toString())) {
            Toast.makeText(this, getString(R.string.invalid_input_url_empty), Toast.LENGTH_LONG).show();
            return false;
        } else if (!Patterns.WEB_URL.matcher(mUrlField.getText().toString()).matches()) {
            Toast.makeText(this, getString(R.string.invalid_input_url), Toast.LENGTH_LONG).show();
            return false;
        } else if (mDatePickerSwitch.isChecked()) {
            // Check both the time field and the date field.
            if (mPingFrequency.getProgress() == mPingFrequency.getMax()) {
                Toast.makeText(this, getString(R.string.invalid_input_ping_frequency), Toast.LENGTH_LONG).show();
                return false;
            } else if (!mIsTimePickerSet) {
                Toast.makeText(this, getString(R.string.invalid_input_time), Toast.LENGTH_LONG).show();
                return false;
            } else if (!mIsDatePickerSet) {
                Toast.makeText(this, getString(R.string.invalid_input_date), Toast.LENGTH_LONG).show();
                return false;
            }
        }

        return true;
    }
}
