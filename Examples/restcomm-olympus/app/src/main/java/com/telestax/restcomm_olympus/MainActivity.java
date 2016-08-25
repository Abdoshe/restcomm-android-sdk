/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package com.telestax.restcomm_olympus;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import com.testfairy.TestFairy;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.telestax.restcomm_olympus.ContactsController.CONTACT_KEY;
import static com.telestax.restcomm_olympus.ContactsController.CONTACT_VALUE;

public class MainActivity extends AppCompatActivity
      implements MainFragment.Callbacks, RCDeviceListener,
      View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener,
      AddUserDialogFragment.ContactDialogListener, ActionFragment.ActionListener {

   private static final String TAG = "MainActivity";
   SharedPreferences prefs;
   private RCDevice device;
   private HashMap<String, Object> params;
   private MainFragment listFragment;
   private AlertDialog alertDialog;
   private RCConnectivityStatus previousConnectivityStatus = RCConnectivityStatus.RCConnectivityStatusNone;
   private static final String APP_VERSION = "Restcomm Android Olympus Client " + BuildConfig.VERSION_NAME + "#" + BuildConfig.VERSION_CODE; //"Restcomm Android Olympus Client 1.0.0-BETA4#20";
   ImageButton btnAdd;

   private static final int CONNECTION_REQUEST = 1;

   /*
   // Retrieve all contact entries from DB and return them in an ArrayList suitable for use by the ContactAdapter
   ArrayList<Map<String, String>> retrieveContactsArray()
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened yet, please call open()");
      }

      //SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
      //qb.setTables(DatabaseContract.ContactEntry.TABLE_NAME);

      // Define a projection that specifies which columns from the database
      // you will actually use after this query.
      String[] columns = {
            DatabaseContract.ContactEntry.COLUMN_NAME_NAME,
            DatabaseContract.ContactEntry.COLUMN_NAME_URI,
      };

      // How you want the results sorted in the resulting Cursor
      String sortOrder = DatabaseContract.ContactEntry.COLUMN_NAME_NAME + " ASC";

      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      Cursor cursor = db.query(
            DatabaseContract.ContactEntry.TABLE_NAME,  // The table to query
            columns,                               // The columns to return
            null,                                // The columns for the WHERE clause
            null,                            // The values for the WHERE clause
            null,                                     // don't group the rows
            null,                                     // don't filter by row groups
            sortOrder                                 // The sort order
      );

      ArrayList<Map<String, String>> contactList = new ArrayList<Map<String, String>>();
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         // iterate the rows, read from db and populate contactList
         for (int i = 0; i < cursor.getCount(); i++) {
            Log.w(TAG, "+++ Entry: " + cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_NAME)));
         }
      }
      cursor.close();

      return null;
   }
   */
   @Override
   protected void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      Log.i(TAG, "%% onCreate");
      Log.i(TAG, "Olympus Version: " + APP_VERSION);

      // For TestFairy troubleshooting. IMPORTANT: remove for production apps, as TestFairy sends logs, screenshots, etc to their servers
      TestFairy.begin(this, "52d3ee1de1b7681d1fcbbc3295b2b86a0aa43fcc");

      // Initialize database
      //DatabaseManager.getInstance().open(getApplicationContext());

      //databaseHelper = new DatabaseHelper(getApplicationContext());

      Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
      setSupportActionBar(toolbar);
      toolbar.setTitle(getTitle());

      listFragment = (MainFragment) getSupportFragmentManager().findFragmentById(R.id.item_list);

      btnAdd = (ImageButton) findViewById(R.id.imageButton_add);
      btnAdd.setOnClickListener(this);

      alertDialog = new AlertDialog.Builder(MainActivity.this).create();

      //PreferenceManager.setDefaultValues(this, "preferences.xml", MODE_PRIVATE, R.xml.preferences, false);
      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
      prefs = PreferenceManager.getDefaultSharedPreferences(this);

      RCClient.setLogLevel(Log.VERBOSE);
      RCClient.initialize(getApplicationContext(), new RCClient.RCInitListener() {
         public void onInitialized()
         {
            Log.i(TAG, "RCClient initialized");
         }

         public void onError(Exception exception)
         {
            Log.e(TAG, "RCClient initialization error");
         }
      });

      params = new HashMap<String, Object>();
      params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, prefs.getString(RCDevice.ParameterKeys.SIGNALING_DOMAIN, ""));
      params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, prefs.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, "android-sdk"));
      params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, prefs.getString(RCDevice.ParameterKeys.SIGNALING_PASSWORD, "1234"));
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_URL, ""));
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ""));
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ""));
      params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true));
      params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false));
      // The SDK provides the user with default sounds for calling, ringing, busy (declined) and message, but the user can override them
      // by providing their own resource files (i.e. .wav, .mp3, etc) at res/raw passing them with Resource IDs like R.raw.calling_sound
      //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING, R.raw.old_calling);
      //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING, R.raw.old_calling);
      //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED, R.raw.old_calling);
      //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE, R.raw.old_calling);

      // This is for debugging purposes, not for release builds
      //params.put(RCDevice.ParameterKeys.SIGNALING_JAIN_SIP_LOGGING_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.SIGNALING_JAIN_SIP_LOGGING_ENABLED, true));
      device = RCClient.createDevice(params, this);
      device.setPendingIntents(new Intent(getApplicationContext(), CallActivity.class),
            new Intent(getApplicationContext(), MessageActivity.class));

      // preferences
      prefs.registerOnSharedPreferenceChangeListener(this);

      // No longer needed, we'll change with toast
      // set it to wifi by default to avoid the status message when starting with wifi
      //previousConnectivityStatus = RCConnectivityStatus.RCConnectivityStatusWiFi;
   }

   @Override
   protected void onStart()
   {
      super.onStart();
      // The activity is about to become visible.
      Log.i(TAG, "%% onStart");
   }

   @Override
   protected void onResume()
   {
      super.onResume();

      if (device.getState() == RCDevice.DeviceState.OFFLINE) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
      }

      if (device != null) {
         // needed if we are returning from Message screen that becomes the Device listener
         device.setDeviceListener(this);
      }

      // The activity has become visible (it is now "resumed").
      Log.i(TAG, "%% onResume");
   }

   @Override
   protected void onPause()
   {
      super.onPause();
      // Another activity is taking focus (this activity is about to be "paused").
      Log.i(TAG, "%% onPause");
   }

   @Override
   protected void onStop()
   {
      super.onStop();
      // The activity is no longer visible (it is now "stopped")
      Log.i(TAG, "%% onStop");
   }

   @Override
   protected void onDestroy()
   {
      super.onDestroy();
      // The activity is about to be destroyed.
      Log.i(TAG, "%% onDestroy");
      RCClient.shutdown();
      device = null;
      prefs.unregisterOnSharedPreferenceChangeListener(this);
   }

   @Override
   public void onNewIntent(Intent intent)
   {
      super.onNewIntent(intent);
      setIntent(intent);
   }

   /**
    * MainFragment Callbacks
    */
   @Override
   public void onItemSelected(HashMap<String, String> contact, MainFragment.ContactSelectionType type)
   {
      // forward to onActionClicked
      onActionClicked(ActionFragment.ActionType.ACTION_TYPE_VIDEO_CALL, contact.get(CONTACT_KEY), contact.get(CONTACT_VALUE));
   }

   public void onContactUpdate(HashMap<String, String> contact, int type)
   {
      DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_UPDATE_CONTACT, contact.get(CONTACT_KEY), contact.get(CONTACT_VALUE));
      newFragment.show(getFragmentManager(), "dialog");
   }

   public void onAccessoryClicked(HashMap<String, String> contact)
   {
      DialogFragment actionFragment = ActionFragment.newInstance(contact.get(CONTACT_KEY), contact.get(CONTACT_VALUE));
      actionFragment.show(getFragmentManager(), "dialog-accessory");
   }


   /**
    * Callbacks for AddUserDialogFragment
    */
   public void onDialogPositiveClick(int type, String username, String sipuri)
   {
      listFragment.updateContact(type, username, sipuri);
   }

   public void onDialogNegativeClick()
   {

   }

   /**
    * Callbacks for ActionFragment
    */
   public void onActionClicked(ActionFragment.ActionType action, String username, String sipuri)
   {
      //DatabaseManager.getInstance().retrieveContactsArray();
      //retrieveContactsArray();

      if (action == ActionFragment.ActionType.ACTION_TYPE_VIDEO_CALL) {
         Intent intent = new Intent(this, CallActivity.class);
         intent.setAction(RCDevice.OUTGOING_CALL);
         intent.putExtra(RCDevice.EXTRA_DID, sipuri);
         intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
         startActivityForResult(intent, CONNECTION_REQUEST);
      }
      if (action == ActionFragment.ActionType.ACTION_TYPE_AUDIO_CALL) {
         Intent intent = new Intent(this, CallActivity.class);
         intent.setAction(RCDevice.OUTGOING_CALL);
         intent.putExtra(RCDevice.EXTRA_DID, sipuri);
         intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
         startActivityForResult(intent, CONNECTION_REQUEST);
      }
      if (action == ActionFragment.ActionType.ACTION_TYPE_TEXT_MESSAGE) {
         Intent intent = new Intent(this, MessageActivity.class);
         intent.setAction(RCDevice.OPEN_MESSAGE_SCREEN);
         intent.putExtra(RCDevice.EXTRA_DID, sipuri);
         startActivity(intent);
      }
   }

   /**
    * Main Activity onClick
    */
   public void onClick(View view)
   {
      if (view.getId() == R.id.imageButton_add) {
         DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_ADD_CONTACT, "", "");
         newFragment.show(getFragmentManager(), "dialog");
      }
   }

   /**
    * RCDeviceListener callbacks
    */
   public void onStartListening(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {
      handleConnectivityUpdate(connectivityStatus, null);
   }

   public void onStopListening(RCDevice device)
   {

   }

   public void onStopListening(RCDevice device, int errorCode, String errorText)
   {
      if (errorCode == RCClient.ErrorCodes.SUCCESS.ordinal()) {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice: " + errorText);
      }
      else {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice Error: " + errorText);
      }
   }

   public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
   {
      if (statusCode == RCClient.ErrorCodes.SUCCESS.ordinal()) {
         handleConnectivityUpdate(connectivityStatus, "RCDevice successfully initialized, using: " + connectivityStatus);
      }
      else if (statusCode == RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY.ordinal()) {
         // This is not really an error, since if connectivity comes back the RCDevice will resume automatically
         handleConnectivityUpdate(connectivityStatus, null);
      }
      else {
         //Toast.makeText(getApplicationContext(), "RCDevice Initialization Error: " + statusText, Toast.LENGTH_LONG).show();
         //showOkAlert("RCDevice Initialization Error", statusText);
         //handleConnectivityUpdate(connectivityStatus, "RCDevice Initialization Error: " + statusText);
         Toast.makeText(getApplicationContext(), "RCDevice Initialization Error: " + statusText, Toast.LENGTH_LONG).show();
      }

   }

   public void onInitializationError(int errorCode, String errorText)
   {
      Toast.makeText(getApplicationContext(), "RCDevice Initialization Error: " + errorText, Toast.LENGTH_LONG).show();
   }

   public void onReleased(RCDevice device, int statusCode, String statusText)
   {
      if (statusCode != RCClient.ErrorCodes.SUCCESS.ordinal()) {
         //showOkAlert("RCDevice Release Error", statusText);
         Toast.makeText(getApplicationContext(), "RCDevice Release Error: " + statusText, Toast.LENGTH_LONG).show();
      }
      else {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice Released: " + statusText);
      }
   }

   public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
   {
      handleConnectivityUpdate(connectivityStatus, null);
   }

   public void handleConnectivityUpdate(RCConnectivityStatus connectivityStatus, String text)
   {
      if (text == null) {
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
            text = "RCDevice connectivity change: Lost connectivity";
         }
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusWiFi) {
            text = "RCDevice connectivity change: Reestablished connectivity (Wifi)";
         }
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusCellular) {
            text = "RCDevice connectivity change: Reestablished connectivity (Cellular)";
         }
      }

      if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
      }

      //if (connectivityStatus != this.previousConnectivityStatus) {
         //showOkAlert("RCDevice connectivity change", text);
         Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
         this.previousConnectivityStatus = connectivityStatus;
      //}
   }

   public void onMessageSent(RCDevice device, int statusCode, String statusText)
   {

   }

   public boolean receivePresenceEvents(RCDevice device)
   {
      return false;
   }

   public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
   {

   }

   /**
    * Settings Menu callbacks
    */
   @Override
   public void onConfigurationChanged(Configuration newConfig)
   {
      super.onConfigurationChanged(newConfig);
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu)
   {
      // Inflate the menu; this adds items to the action bar if it is present.
      getMenuInflater().inflate(R.menu.menu_main, menu);
      return true;
   }


   @Override
   public boolean onOptionsItemSelected(MenuItem item)
   {
      // Handle action bar item clicks here. The action bar will
      // automatically handle clicks on the Home/Up button, so long
      // as you specify a parent activity in AndroidManifest.xml.
      int id = item.getItemId();
      if (id == R.id.action_settings) {
         Intent i = new Intent(this, SettingsActivity.class);
         startActivity(i);
      }
      if (id == R.id.action_about) {
         DialogFragment newFragment = AboutFragment.newInstance();
         newFragment.show(getFragmentManager(), "dialog-about");
      }
      return super.onOptionsItemSelected(item);
   }

   @Override
   public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                         String key)
   {

   }

   /**
    * Helpers
    */
   /*
   private void showOkAlert(final String title, final String detail)
   {
      if (alertDialog.isShowing()) {
         Log.w(TAG, "Alert already showing, hiding to show new alert");
         alertDialog.hide();
      }

      alertDialog.setTitle(title);
      alertDialog.setMessage(detail);
      alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int which)
         {
            dialog.dismiss();
         }
      });
      alertDialog.show();
   }
   */

}

