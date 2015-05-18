package com.colinwhite.ping;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
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
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.colinwhite.ping.data.PingContract;
import com.colinwhite.ping.data.PingContract.MonitorEntry;
import com.colinwhite.ping.sync.PingSyncAdapter;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * The MonitorDetailActivity handles the layouts and logic for both when the user wants to
 * create a new Monitor and when the select an additional Monitor to see its info/edit it. Thus,
 * this activity requires that it receive a valid PAGE_TYPE_ID from its starting Intent.
 *
 * If passed a URL, when PAGE_TYPE_ID == PAGE_CREATE it will set this as the URL EditText's text.
 * When PAGE_TYPE_ID == PAGE_DETAIL, the Activity must be passed a valid Monitor ID.
 */
public class MonitorDetailActivity extends AppCompatActivity {
    private static final String LOG_TAG = MonitorDetailActivity.class.getSimpleName();
    public static final String PAGE_TYPE_ID = "PAGE_TYPE_ID";
    private static final int PING_FREQUENCY_ON_CREATE = 4;
    private static final String DATE_FORMAT = "EEEE, d MMMM, y";

    // Valid values for PAGE_TYPE_ID.
    public static final String PAGE_CREATE = "PAGE_CREATE";
    public static final String PAGE_DETAIL = "PAGE_DETAIL";

    // Fields that are used in the database.
    @InjectView(R.id.create_monitor_title) EditText titleField;
    @InjectView(R.id.url_text_field_create) EditText urlField;
    @InjectView(R.id.ping_frequency_seek_bar) SeekBar pingFrequency;
    private Calendar selectedDateTime;

    // Other UI elements
    @InjectView(R.id.toolbar) Toolbar toolbar;
    @InjectView(R.id.status_icon) ImageView statusIcon;
    @InjectView(R.id.date_picker_output) TextView datePickerOutput;
    @InjectView(R.id.time_picker_output) TextView timePickerOutput;
    @InjectView(R.id.ping_frequency_explanation) TextView pingFrequencyExplanation;
    @InjectView(R.id.date_picker_switch) SwitchCompat datePickerSwitch;
    @InjectView(R.id.expiration_date_explanation) TextView expirationDateExplanation;
    @InjectView(R.id.detail_last_checked_text) TextView lastCheckedField;
    private Dialog whyApproximateDialog = null;

    // Only used on DETAIL pages
    private Dialog confirmDeleteDialog = null;
    private ContentValues values;
    private boolean hasEndDate = false;
    private boolean isTimePickerSet = false;
    private boolean isDatePickerSet = false;

    private Vibrator vibratorService;
    private Intent startIntent;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor_detail);
        ButterKnife.inject(this);

        try {
            // Set up the Toolbar.
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException e) {
            Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
            finish();
        }

        // Get the service for haptic feedback.
        vibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Initialise the formats of the date and time pickers and get the date picker's initial date.
        dateFormat = new SimpleDateFormat(DATE_FORMAT);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPref.getBoolean(getString(R.string.pref_key_24_hour_clock), false)) {
            timeFormat = new SimpleDateFormat(Utility.TIME_FORMAT_24_HOURS);
        } else {
            timeFormat = new SimpleDateFormat(Utility.TIME_FORMAT_12_HOURS);
        }
        selectedDateTime = Calendar.getInstance();
        selectedDateTime.set(Calendar.SECOND, 0);
        selectedDateTime.set(Calendar.MILLISECOND, 0);

        // Set the ping frequency SeekBar to update its explanation TextField when its progress changes.
        pingFrequency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
        startIntent = getIntent();
        if (startIntent.getStringExtra(PAGE_TYPE_ID).equals(PAGE_DETAIL)) {
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

    /**
     * Build the elements necessary to update a Monitor's details/delete it.
     */
    private void buildDetailPageElements() {
        // Change the title.
        setTitle(R.string.monitor_detail_activity_title);

        // Get the ID of the Monitor.
        final long monitorId = startIntent.getLongExtra(MonitorEntry._ID, -1);
        if (monitorId == -1) {
            Log.e(LOG_TAG, "Intent does not contain a Monitor ID.");
            onBackPressed(); // Close the activity.
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
                MonitorEntry.buildUri(monitorId),
                projection, selection, selectionArgs, null);

        // Store all the initial values.
        cursor.moveToFirst();
        values = new ContentValues();
        values.put(MonitorEntry._ID, cursor.getInt(cursor.getColumnIndex(MonitorEntry._ID)));
        values.put(MonitorEntry.TITLE, cursor.getString(cursor.getColumnIndex(MonitorEntry.TITLE)));
        values.put(MonitorEntry.URL, cursor.getString(cursor.getColumnIndex(MonitorEntry.URL)));
        values.put(MonitorEntry.PING_FREQUENCY, cursor.getInt(cursor.getColumnIndex(MonitorEntry.PING_FREQUENCY)));
        values.put(MonitorEntry.END_TIME, cursor.getLong(cursor.getColumnIndex(MonitorEntry.END_TIME)));
        values.put(MonitorEntry.TIME_LAST_CHECKED, cursor.getLong(cursor.getColumnIndex(MonitorEntry.TIME_LAST_CHECKED)));
        values.put(MonitorEntry.STATUS, cursor.getInt(cursor.getColumnIndex(MonitorEntry.STATUS)));
        cursor.close();

        // Populate all the user-accessible fields.
        final String title = (String) values.get(MonitorEntry.TITLE);
        titleField.setText(title);
        final String url = (String) values.get(MonitorEntry.URL);
        urlField.setText(url);
        final int progress = (int) values.get(MonitorEntry.PING_FREQUENCY);
        pingFrequency.setProgress(progress);
        setPingFrequencyExplanation(progress);

        // Populate the endDate, only if one already exists.
        long endDateInMillis = (long) values.get(MonitorEntry.END_TIME);
        if (endDateInMillis != MonitorEntry.END_TIME_NONE) {
            selectedDateTime.setTimeInMillis(endDateInMillis);
            datePickerOutput.setText(dateFormat.format(selectedDateTime.getTime()));
            timePickerOutput.setText(timeFormat.format(selectedDateTime.getTime()) + getString(R.string.approximately_tag));
            isDatePickerSet = true;
            isTimePickerSet = true;
        }

        // Set the status icon.
        statusIcon.setImageDrawable(ContextCompat.getDrawable(this,
                Utility.getStatusIcon((int) values.get(MonitorEntry.STATUS))));
        // NOTE: lastCheckedField and statusIcon do not update if the database changes.
        // Format the time last checked and place it in the resource string.
        long timeLastChecked = (long) values.get(MonitorEntry.TIME_LAST_CHECKED);
        lastCheckedField.setVisibility(View.VISIBLE);
        if (timeLastChecked != MonitorEntry.TIME_LAST_CHECKED_NONE) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String formattedTime = Utility.formatDate(timeLastChecked,
                    sharedPref.getBoolean(getString(R.string.pref_key_24_hour_clock), false));
            lastCheckedField.setText(Html.fromHtml(String.format(getString(R.string.last_checked_text),
                    formattedTime)));
        } else {
            // If timeLastChecked is 0, it hasn't been checked yet.
            lastCheckedField.setText(getString(R.string.last_checked_text_no_info));
        }
    }

    /**
     * Build the elements for a Monitor creation version of this Activity.
     */
    private void buildCreatePageElements() {
        // Set the URL EditText to the value passed in the Intent, if it exists.
        if (startIntent.hasExtra(MonitorEntry.URL)) {
            urlField.setText(startIntent.getStringExtra(MonitorEntry.URL));
        }

        // Set the initial ping frequency values.
        pingFrequency.setProgress(PING_FREQUENCY_ON_CREATE);
        setPingFrequencyExplanation(PING_FREQUENCY_ON_CREATE);
    }

    /**
     * Build the UI elements that handle setting/editing an expiration date for a Monitor.
     */
    private void setExpirationDateElements() {
        // Set the Switch to change the visibility of the pickers and explanation.
        datePickerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int visibility = (isChecked) ? View.VISIBLE : View.GONE;
                datePickerOutput.setVisibility(visibility);
                timePickerOutput.setVisibility(visibility);
                expirationDateExplanation.setVisibility(visibility);
                hasEndDate = isChecked;
            }
        });

        // Set the Monitor's preference if this is a DETAIL page.
        if (startIntent.getStringExtra(PAGE_TYPE_ID).equals(PAGE_DETAIL)) {
            datePickerSwitch.setChecked(((long) values.get(MonitorEntry.END_TIME))
                    != MonitorEntry.END_TIME_NONE);
        }

        // Build the correct Time and DateDialogPickers depending on the user's version of Android.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BuildLollipopPickers();
        } else {
            BuildAppCompatPickers();
        }
    }

    /**
     * Build and set up a TimePickerDialog and a DatePickerDialog using the Lollipop API.
     */
    private void BuildLollipopPickers() {
        // -- TIME PICKER --
        // Make the TimePicker set the output TextField's time when it is changed.
        final android.app.TimePickerDialog.OnTimeSetListener time = new android.app.TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                setSelectedTime(hourOfDay, minute);
            }
        };

        // Set the TimePicker to popup when the TextField is clicked.
        final android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(
                this,
                time,
                selectedDateTime.get(Calendar.HOUR_OF_DAY),
                selectedDateTime.get(Calendar.MINUTE),
                false);
        timePickerOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timePickerDialog.show();
            }
        });

        // -- DATE PICKER --
        // Make the DatePicker set the output TextField's date when it is changed.
        final android.app.DatePickerDialog.OnDateSetListener date = new android.app.DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                setSelectedDate(year, month, day);
            }
        };

        // Set the DatePicker to popup when the TextField is clicked.
        final android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                this,
                date,
                selectedDateTime.get(Calendar.YEAR),
                selectedDateTime.get(Calendar.MONTH),
                selectedDateTime.get(Calendar.DAY_OF_MONTH));
        datePickerOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                datePickerDialog.show();
            }
        });
    }

    /**
     * Build and set up a TimePickerDialog and a DatePickerDialog using an open source API.
     */
    private void BuildAppCompatPickers() {
        final FragmentManager fragmentManager = getFragmentManager();

        // -- TIME PICKER --
        // Make the TimePicker set the output TextField's time when it is changed.
        final com.wdullaer.materialdatetimepicker.time.TimePickerDialog.OnTimeSetListener time = new com.wdullaer.materialdatetimepicker.time.TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(RadialPickerLayout radialPickerLayout, int hourOfDay, int minute) {
                setSelectedTime(hourOfDay, minute);
            }
        };

        // Set the TimePicker to popup when the TextField is clicked.
        final com.wdullaer.materialdatetimepicker.time.TimePickerDialog timePickerDialog =
                com.wdullaer.materialdatetimepicker.time.TimePickerDialog.newInstance(
                        time,
                        selectedDateTime.get(Calendar.HOUR_OF_DAY),
                        selectedDateTime.get(Calendar.MINUTE),
                        false);
        timePickerOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timePickerDialog.show(fragmentManager, "");
            }
        });

        // -- DATE PICKER --
        // Make the DatePicker set the output TextField's date when it is changed.
        com.wdullaer.materialdatetimepicker.date.DatePickerDialog.OnDateSetListener date =
                new com.wdullaer.materialdatetimepicker.date.DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePickerDialog datePickerDialog, int year, int month, int day) {
                        setSelectedDate(year, month, day);
                    }
                };

        // Set the DatePicker to popup when the TextField is clicked.
        final com.wdullaer.materialdatetimepicker.date.DatePickerDialog datePickerDialog =
                com.wdullaer.materialdatetimepicker.date.DatePickerDialog.newInstance(
                        date,
                        selectedDateTime.get(Calendar.YEAR),
                        selectedDateTime.get(Calendar.MONTH),
                        selectedDateTime.get(Calendar.DAY_OF_MONTH));
        datePickerOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                datePickerDialog.show(fragmentManager, "");
            }
        });
    }

    /**
     * Set the time TextField.
     * @param hourOfDay Hour of day (0 - 23)
     * @param minute Minute in hour
     */
    private void setSelectedTime(int hourOfDay, int minute) {
        selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
        selectedDateTime.set(Calendar.MINUTE, minute);

        // Set the output TextView's text.
        timePickerOutput.setText(timeFormat.format(selectedDateTime.getTime()) + getString(R.string.approximately_tag));
        isTimePickerSet = true;
    }

    /**
     * Set the date TextField.
     * @param year Year
     * @param month Month in year
     * @param day Day in month
     */
    private void setSelectedDate(int year, int month, int day) {
        selectedDateTime.set(Calendar.YEAR, year);
        selectedDateTime.set(Calendar.MONTH, month);
        selectedDateTime.set(Calendar.DAY_OF_MONTH, day);

        // Set the output TextView's text.
        datePickerOutput.setText(dateFormat.format(selectedDateTime.getTime()));
        isDatePickerSet = true;
    }

    /**
     * Save all user-accessible fields in the activity, and create a new Monitor/update a current one.
     * Used for the CREATE/UPDATE buttons.
     * @param pageType The current page type. Either PAGE_DETAIL or PAGE_CREATE.
     * @param monitorId Only needed for PAGE_DETAIL. The ID of the Monitor this page is displaying.
     */
    private void saveAllFields(String pageType, int monitorId) {
        ContentValues values = new ContentValues();
        values.put(MonitorEntry.TITLE, titleField.getText().toString());
        String url = urlField.getText().toString();
        values.put(MonitorEntry.URL, url);
        values.put(MonitorEntry.PING_FREQUENCY, pingFrequency.getProgress());
        long endDate = (hasEndDate) ? selectedDateTime.getTimeInMillis() : MonitorEntry.END_TIME_NONE;
        values.put(MonitorEntry.END_TIME, endDate);
        // If this is a detail page and the URL has changed, invalidate the last checked time and status.
        if (PAGE_DETAIL.equals(startIntent.getStringExtra(PAGE_TYPE_ID)) &&
                !this.values.get(MonitorEntry.URL).equals(url)) {
            values.put(MonitorEntry.TIME_LAST_CHECKED, MonitorEntry.TIME_LAST_CHECKED_NONE);
            values.put(MonitorEntry.STATUS, MonitorEntry.STATUS_NO_INFO);
        }

        if (PAGE_DETAIL.equals(pageType)) {
            // This is a detail page.
            final String selection = MonitorEntry._ID + " = ?";
            final String[] selectionArgs = {String.valueOf(monitorId)};
            getContentResolver().update(MonitorEntry.CONTENT_URI, values, selection, selectionArgs);

            // Remove the current periodic sync timer for this Monitor. Later we create a new one.
            PingSyncAdapter.removePeriodicSync(
                    this,
                    startIntent.getStringExtra(MonitorEntry.URL),
                    monitorId);
            // If the Monitor previously had a removal alarm set, delete it.
            if (((long) this.values.get(MonitorEntry.END_TIME)) != MonitorEntry.END_TIME_NONE) {
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
        if (pingFrequency.getProgress() != pingFrequency.getMax()) {
            // Create the new sync timer for the Monitor.
            PingSyncAdapter.createPeriodicSync(
                    this,
                    urlField.getText().toString(),
                    monitorId,
                    (int) TimeUnit.MINUTES.toSeconds(Utility.PING_FREQUENCY_MINUTES[pingFrequency.getProgress()]));
            // If the Monitor has been set to never ping automatically, don't add a removal alarm.
            if (endDate != MonitorEntry.END_TIME_NONE) {
                Utility.addRemovalAlarm(this, monitorId, endDate);
            }
        } else {
            // Instead, we just sync once.
            PingSyncAdapter.syncImmediately(
                    this,
                    PingSyncAdapter.getSyncAccount(this),
                    urlField.getText().toString(),
                    monitorId);
        }

        onBackPressed();
    }

    /**
     * Given the current progress of the ping frequency SeekBar, set the text of
     * pingFrequencyExplanation to match.
     * @param progress A valid progress value from pingFrequencyExplanation.
     */
    private void setPingFrequencyExplanation(int progress) {
        if (progress == pingFrequency.getMax()) {
            pingFrequencyExplanation.setText(R.string.ping_explanation_never);
            return;
        }
        long duration = TimeUnit.MINUTES.toMillis(Utility.PING_FREQUENCY_MINUTES[progress]);

        // Place the formatted duration in the resource string.
        String formattedStr = String.format(
                getString(R.string.ping_frequency_explanation),
                Utility.formatTimeDuration(duration),
                getString(R.string.approximately_tag));

        // Set the result as the explanation TextField for the frequency SeekBar.
        if (progress == 0) {
            formattedStr += getString(R.string.ping_frequency_warning);
            pingFrequencyExplanation.setText(Html.fromHtml(formattedStr));
        } else {
            pingFrequencyExplanation.setText(formattedStr);
        }

        pingFrequencyExplanation.setText(Html.fromHtml(formattedStr));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu to create its buttons.
        getMenuInflater().inflate(R.menu.menu_detail_monitor, menu);

        // Get the Toolbar button references.
        Menu toolbarMenu = toolbar.getMenu();
        MenuItem saveButton = toolbarMenu.findItem(R.id.action_content_save);
        MenuItem deleteButton = toolbarMenu.findItem(R.id.action_delete);

        // Set the visibility and titles of the Toolbar buttons depending on the page type.
        if (PAGE_DETAIL.equals(startIntent.getStringExtra(PAGE_TYPE_ID))) {
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
                // Pulse haptic feedback.
                vibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

                if (isValidInput()) {
                    if (PAGE_DETAIL.equals(startIntent.getStringExtra(PAGE_TYPE_ID))) {
                        // Update all fields. Don't bother to check, as it takes more time than to
                        // just update all the possible columns.
                        saveAllFields(PAGE_DETAIL, (int) values.get(MonitorEntry._ID));
                    } else {
                        // Create a new entry in the database and close the activity.
                        saveAllFields(PAGE_CREATE, -1);
                    }
                }
                return true;
            case R.id.action_delete:
                // Pulse haptic feedback.
                vibratorService.vibrate(Utility.HAPTIC_FEEDBACK_DURATION);

                showConfirmDeleteDialog();
                return true;
            case R.id.action_explain_approximate:
                showApproximateDialog();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showConfirmDeleteDialog() {
        if (confirmDeleteDialog == null) {
            // Initiate the confirmation dialog for when the delete button is pressed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                confirmDeleteDialog = builder.setTitle(getString(R.string.dialog_confirm_delete_title))
                        .setMessage(R.string.dialog_confirm_delete)
                        .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                deleteDialogOnPositiveClick();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }
                        }).show();
            } else {
                confirmDeleteDialog = new MaterialDialog.Builder(this)
                        .title(R.string.dialog_confirm_delete_title)
                        .content(R.string.dialog_confirm_delete)
                        .positiveText(R.string.confirm)
                        .negativeText(R.string.cancel)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                deleteDialogOnPositiveClick();
                            }
                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                dialog.dismiss();
                            }
                        }).show();
            }
        } else {
            confirmDeleteDialog.show();
        }
    }

    private void deleteDialogOnPositiveClick() {
        // Delete the Monitor's database entry and its sync account then close the activity.
        int monitorId = (int) values.get(MonitorEntry._ID);
        final String selection = MonitorEntry._ID + " = ?";
        final String[] selectionArgs = {String.valueOf(monitorId)};
        getContentResolver().delete(MonitorEntry.CONTENT_URI, selection, selectionArgs);
        PingSyncAdapter.removePeriodicSync(
                MonitorDetailActivity.this,
                startIntent.getStringExtra(MonitorEntry.URL),
                monitorId);
        // If the Monitor had a removal alarm set, delete it.
        long endDate = (long) values.get(MonitorEntry.END_TIME);
        if (endDate != MonitorEntry.END_TIME_NONE) {
            Utility.deleteRemovalAlarm(MonitorDetailActivity.this, monitorId);
        }
        onBackPressed();
    }

    private void showApproximateDialog() {
        if (whyApproximateDialog == null) {
            // Build and show the dialog, which explains why ping frequencies and expiration dates
            // can't be exact.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                whyApproximateDialog = builder.setTitle(getString(R.string.action_explain_approximate_dialog_title))
                        .setMessage(getString(R.string.dialog_explain_approximate_message))
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.okay),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                }).show();
            } else {
                whyApproximateDialog = new MaterialDialog.Builder(this)
                        .title(R.string.action_explain_approximate_dialog_title)
                        .content(R.string.dialog_explain_approximate_message)
                        .positiveText(R.string.okay)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                dialog.dismiss();
                            }
                        }).show();
            }
        } else {
            whyApproximateDialog.show();
        }
    }

    /**
     * Validate the input of every user-accessible field on the page.
     * @return Whether all input on the page is valid.
     */
    private boolean isValidInput() {
        String url = urlField.getText().toString();

        if ("".equals(titleField.getText().toString())) {
            Toast.makeText(this, getString(R.string.invalid_input_title), Toast.LENGTH_LONG).show();
            return false;
        } else if ("".equals(url)) {
            Toast.makeText(this, getString(R.string.invalid_input_url_empty), Toast.LENGTH_LONG).show();
            return false;
        } else if (!Patterns.WEB_URL.matcher(urlField.getText().toString()).matches()) {
            Toast.makeText(this, getString(R.string.invalid_input_url), Toast.LENGTH_LONG).show();
            return false;
        } else if (datePickerSwitch.isChecked()) {
            // Check both the time field and the date field.
            if (pingFrequency.getProgress() == pingFrequency.getMax()) {
                Toast.makeText(this, getString(R.string.invalid_input_ping_frequency), Toast.LENGTH_LONG).show();
                return false;
            } else if (!isTimePickerSet) {
                Toast.makeText(this, getString(R.string.invalid_input_time), Toast.LENGTH_LONG).show();
                return false;
            } else if (!isDatePickerSet) {
                Toast.makeText(this, getString(R.string.invalid_input_date), Toast.LENGTH_LONG).show();
                return false;
            } else if (Calendar.getInstance().after(selectedDateTime)) {
                Toast.makeText(this, getString(R.string.invalid_input_date_before_now), Toast.LENGTH_LONG).show();
                return false;
            }
        }

        // If all the previous validations pass perform the more computationally expensive URL
        // uniqueness check.
        final String[] projection = { MonitorEntry._ID, MonitorEntry.URL };
        Cursor cursor = getContentResolver().query(MonitorEntry.CONTENT_URI, projection, null, null, null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                // Check if a Monitor has the same URL as the current one. If so, make sure it has
                // a different ID from the current one (i.e. they are not the same Monitor).
                if (url.equals(cursor.getString(cursor.getColumnIndex(MonitorEntry.URL))) &&
                        cursor.getInt(cursor.getColumnIndex(MonitorEntry._ID)) !=
                                (int) startIntent.getLongExtra(MonitorEntry._ID, -1)) {
                    Toast.makeText(this, getString(R.string.invalid_input_url_uniqueness), Toast.LENGTH_LONG).show();
                    return false;
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        return true;
    }
}
