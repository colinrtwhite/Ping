package com.colinwhite.ping;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.colinwhite.ping.data.PingContract;
import com.colinwhite.ping.data.PingContract.MonitorEntry;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MonitorDetailActivity extends ActionBarActivity {
    public static final String URL_FIELD_VALUE = "URL_FIELD_VALUE";
    public static final String PAGE_TYPE_ID = "PAGE_TYPE_ID";
    public static final String CREATE = "CREATE";
    public static final String DETAIL = "DETAIL";
    private static final String DATE_FORMAT = "E, d MMMM, y";

    // Fields that are used in the database.
    private static EditText mTitleField;
    private static EditText mUrlField;
    private static SeekBar mPingFrequency;
    private static Calendar mDatePickerDate;

    // Other UI elements
    private static Toolbar mToolbar;
    private static ImageView mMonitorIcon;
    private static TextView mDatePickerOutput;
    private static DatePickerDialog mDatePickerDialog;
    private static Button mConfirmButton;
    private static Button mDeleteButton;

    private static SimpleDateFormat mDateFormat;
    private Intent mStartIntent;
    private boolean mHasEndDate = false;

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
        mConfirmButton = (Button) findViewById(R.id.save_button);

        // Initialise the date format of the DatePicker's output and the DatePicker's initial date.
        mDateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.UK);
        mDatePickerDate = Calendar.getInstance();

        // Change UI elements and data whether we are creating or updating/looking at a Monitor.
        // Default to a creation activity.
        mStartIntent = getIntent();
        if (mStartIntent.getStringExtra(PAGE_TYPE_ID).equals(DETAIL)) {
            buildDetailPageElements();
        } else {
            buildCreatePageElements();
        }

        // Make the DatePicker set the output TextField's date when it is changed.
        final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                mDatePickerDate.set(Calendar.YEAR, year);
                mDatePickerDate.set(Calendar.MONTH, month);
                mDatePickerDate.set(Calendar.DAY_OF_MONTH, day);

                // Set the output TextView's text.
                mDatePickerOutput.setText(mDateFormat.format(mDatePickerDate.getTime()));

                mHasEndDate = true;
            }
        };

        // Set the DatePicker to popup when the TextField is clicked.
        mDatePickerDialog = new DatePickerDialog(this,
                date,
                mDatePickerDate.get(Calendar.YEAR),
                mDatePickerDate.get(Calendar.MONTH),
                mDatePickerDate.get(Calendar.DAY_OF_MONTH));
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
        long monitorId = mStartIntent.getLongExtra(MonitorEntry._ID, -1);
        if (monitorId == -1) {
            Log.e("MonitorDetailActivity", "Intent does not contain a Monitor ID.");
            finish(); // Close the activity.
        }

        // Get the specific Monitor's data.
        final String[] projection = {
                MonitorEntry._ID,
                MonitorEntry.TITLE,
                MonitorEntry.URL,
                MonitorEntry.PING_FREQUENCY,
                MonitorEntry.END_DATE};
        final String selection = MonitorEntry._ID + " = ?";
        final String[] selectionArgs = { String.valueOf(monitorId) };
        Cursor query = getContentResolver().query(
                MonitorEntry.CONTENT_URI.buildUpon().appendPath(String.valueOf(monitorId)).build(),
                projection, selection, selectionArgs, null);

        // Show the delete button and make it work.
        mDeleteButton = (Button) findViewById(R.id.delete_button);
        mDeleteButton.setVisibility(View.VISIBLE);
        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContentResolver().delete(MonitorEntry.CONTENT_URI, selection, selectionArgs);
                getContentResolver().notifyChange(MonitorEntry.CONTENT_URI, null);
                finish();
            }
        });

        // Populate all the fields.
        query.moveToFirst();
        final String title = query.getString(query.getColumnIndex(MonitorEntry.TITLE));
        mTitleField.setText(title);
        final String url = query.getString(query.getColumnIndex(MonitorEntry.URL));
        mUrlField.setText(url);
        final int pingFrequency = query.getInt(query.getColumnIndex(MonitorEntry.PING_FREQUENCY));
        mPingFrequency.setProgress(pingFrequency);

        // Populate the endDate. If we get 0, that means the monitor is set to run indefinitely.
        long endDateInMillis = query.getLong(query.getColumnIndex(MonitorEntry.END_DATE));
        if (endDateInMillis > 0) {
            mDatePickerDate.setTimeInMillis(endDateInMillis);
            mDatePickerOutput.setText(mDateFormat.format(mDatePickerDate.getTime()));
            mHasEndDate = true;
        }

        // Set the confirm button to update the current Monitor and close the activity.
        mConfirmButton.setText(R.string.update_button_text);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Update all fields. Don't bother to check, as it takes more time than to just
                // update all the possible columns.
                saveAllFields(DETAIL, selection, selectionArgs);
            }
        });

        query.close();
    }

    // Build the elements for a Monitor creation version of this Activity.
    private void buildCreatePageElements() {
        // Set the URL EditText to the value passed in the Intent, if it exists.
        if (mStartIntent.hasExtra(URL_FIELD_VALUE)) {
            mUrlField.setText(mStartIntent.getStringExtra(URL_FIELD_VALUE));
        }

        // Set the confirm button to create a new entry in the database and close the activity.
        mConfirmButton.setText(R.string.create_button_text);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAllFields(CREATE, null, null);
            }
        });
    }

    // Save all user-accessible fields in the activity, and create a new Monitor/update a current
    // one.
    private void saveAllFields(String pageType, String selection, String[] selectionArgs) {
        ContentValues values = new ContentValues();
        values.put(MonitorEntry.TITLE, mTitleField.getText().toString());
        values.put(MonitorEntry.URL, mUrlField.getText().toString());
        values.put(MonitorEntry.PING_FREQUENCY, mPingFrequency.getProgress());
        if (mHasEndDate) {
            values.put(MonitorEntry.END_DATE, mDatePickerDate.getTimeInMillis());
        }

        if (DETAIL.equals(pageType)) {
            getContentResolver().update(MonitorEntry.CONTENT_URI, values, selection, selectionArgs);
        } else {
            getContentResolver().insert(PingContract.MonitorEntry.CONTENT_URI, values);
        }
        getContentResolver().notifyChange(MonitorEntry.CONTENT_URI, null);
        finish();
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
