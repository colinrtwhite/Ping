package com.colinwhite.ping;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }

        Intent checkIfUpServiceIntent = new Intent(this, CheckIfUpService.class);
        startService(checkIfUpServiceIntent);

        // Instantiate the intent filter and the receiver
        IntentFilter filter = new IntentFilter(CheckUpServiceReceiver.ACTION_RESPONSE);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        CheckUpServiceReceiver receiver = new CheckUpServiceReceiver();
        registerReceiver(receiver, filter);
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }


    /**
     * Simple BroadcastReceiver that is notified when the background CheckIfUpService
     * finishes and updates the main TextView with the returned information.
     */
    public class CheckUpServiceReceiver extends BroadcastReceiver {
        public static final String ACTION_RESPONSE =
                "com.colinwhite.ping.intent.action.CHECK_UP_SERVICE_COMPLETE";

        @Override
        public void onReceive(Context context, Intent intent) {
            TextView textView = (TextView) findViewById(R.id.output_text_view_id);

            switch (intent.getIntExtra(CheckIfUpService.STATUS, CheckIfUpService.OTHER)) {
                case CheckIfUpService.IS_UP:
                    textView.setText(R.string.is_up);
                    break;
                case CheckIfUpService.IS_DOWN:
                    textView.setText(R.string.is_down);
                    break;
                case CheckIfUpService.DOES_NOT_EXIST:
                    textView.setText(R.string.does_not_exist);
                    break;
                case CheckIfUpService.OTHER:
                    textView.setText(R.string.other);
                    break;
            }
        }
    }
}
