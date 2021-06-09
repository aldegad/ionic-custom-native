package io.ionic.starter;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;

import java.util.ArrayList;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;

public class MainActivity extends BridgeActivity {

  private static final String TAG = "APP_MainActivity";

  private static final int PERMISSIONS_REQUEST = 1;
  protected static final int REQUEST_ENABLE_BT = 2;

  public static final int FE_SCAN_MODE_1 = 0;
  public static final int FE_SCAN_MODE_2 = 1;
  private static int feScanMode = FE_SCAN_MODE_2; //0: Mode1, 1: Mode2

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Initializes the Bridge
    this.init(savedInstanceState, new ArrayList<Class<? extends Plugin>>() {{
      // Additional plugins you've installed go here
      // Ex: add(TotallyAwesomePlugin.class);
      add(CustomNativePlugin.class);
    }});

    BleReceiver.context = getApplicationContext();

    // If not already started, start .
    // This will read preferences from SharedPreferences and initialise BleReceiver so it scans properly
    startHTSService("Activity created");

    // This receives messages for purposes of painting the screen etc
    LocalBroadcastManager.getInstance(this).registerReceiver(mLocalBroadcastReceiver, makeIntentFilter());
  }

  /**
   * Called when screen is rotated!
   */
  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");
    LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);

    super.onDestroy();
  }

  private boolean mServiceIsBound;

  @Override
  public void onStart() {
    super.onStart();
    Log.d(TAG, "onStart()");

    /* Check access to BLE is granted */
    verifyBluetooth();
    requestBluetoothPermission();



    // If the service has not been started before, the following lines will not start it.
    // However, if it's running, the Activity will bind to it and notified via mServiceConnection.

    final Intent service = new Intent(this, HTSService.class);
    // We pass 0 as a flag so the service will not be created if not exists.
    Log.d(TAG, "Binding service");
    bindService(service, mServiceConnection, 0);
    mServiceIsBound = true;

    BleReceiver.setFeScanMode(feScanMode);

  }

  @Override
  public void onStop() {
    super.onStop();
    Log.d(TAG, "onStop()");

    if ( mServiceIsBound ) {
      unbindService(mServiceConnection);
      mServiceIsBound = false;
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume()");
    BleReceiver.requestScanningState(); // see what we are doing and paint the screen
  }

  /*********************************************************************************************
   *
   * Service Connection: allows bi-directional communication with the service.
   *
   *********************************************************************************************/

  private ServiceConnection mServiceConnection = new ServiceConnection() {
    // Interface for monitoring the state of an application service

    @Override
    // We get here when the StartService service has connected to this activity.
    public void onServiceConnected(final ComponentName name, final IBinder binder) {
      HTSService.StartServiceBinder b = (HTSService.StartServiceBinder) binder;
      HTSService mService = b.getService();

      // Now
      String reason = mService.getStartupReason();
      String msg = "Activity connected to the service. Reason: '" + reason +"'";
      Log.d(TAG, msg);
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
      // Note: this method is called only when the service is killed by the system,
      // not when it stops itself or is stopped by the activity.
      // It will be called only when there is critically low memory, in practice never
      // when the activity is in foreground.
      String msg = "Activity disconnected from the service";
      Log.d(TAG, msg);

    }
  };


  /**
   * Starts the HTSService
   * Called from onCreate() in case it has not been auto-started already.
   * Can also be called from StartupReceiover following a reboot.
   */
  private void startHTSService(String reason) {
    Intent serviceIntent = new Intent(getApplicationContext(), HTSService.class);
    serviceIntent.putExtra(HTSService.STARTUP_SOURCE, reason);

    getApplicationContext().startService(serviceIntent);
    bindService(serviceIntent, mServiceConnection, 0);

//        try {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                getApplicationContext().startForegroundService(serviceIntent);
//                bindService(serviceIntent, mServiceConnection, 0);
//                Log.d(TAG, "Starting foreground service");
//            } else {
//                getApplicationContext().startService(serviceIntent);
//                bindService(serviceIntent, mServiceConnection, 0);
//                Log.d(TAG, "Starting background service");
//            }
//        }
//        catch (Exception e) {
//            // I got this when using startService() with Android 26:
//            // Not allowed to start service Intent { cmp=net.dksoft.rebootstartup/.startup.HTSService (has extras) }: app is in background
//            // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
//            Log.e(TAG, "ERROR " + e.getMessage());
//        }

  }

  /*********************************************************************************************
   *
   * BLUETOOTH PERMISSIONS
   *
   *********************************************************************************************/
  public void requestBluetoothPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Log.d(TAG, "Checking Bluetooth permissions");
      if ((this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
//                    || (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        || (this.checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)
        || (this.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED)
//                    || (this.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
//                    || (this.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
//                    || (this.checkSelfPermission(Manifest.permission.PROCESS_OUTGOING_CALLS) != PackageManager.PERMISSION_GRANTED)
      ) {
        requestPermissions(new String[] {ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION, BLUETOOTH, BLUETOOTH_ADMIN},
          PERMISSIONS_REQUEST);
      }
      else {
        Log.d(TAG, "Bluetooth Permission is granted");
      }
    }
  }

  /* This is called when the user responds to the request permission dialog */
  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

    switch (requestCode) {
      case PERMISSIONS_REQUEST: {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.d(TAG, "Coarse location permission granted");
        }
        else {
          Log.d(TAG, "Coarse location permission refused.");
          final AlertDialog.Builder builder = new AlertDialog.Builder(this);
          builder.setTitle("This App will not Work as Intended");
          builder.setMessage("Android requires you to grant access to device\'s location in order to scan for Bluetooth devices.");
          //builder.setIcon(R.drawable.cross);
          builder.setPositiveButton(android.R.string.ok, null);
          builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface dialog) { }
          });
          builder.show();
        }
        return;
      }
    }
  }


  /**
   * Check BLE is enabled, and pop up a dialog if not
   */
  public void verifyBluetooth() {

    try {
      if (!checkAvailability()) {
        Log.d(TAG, "BLE not available.");
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bluetooth is Not Enabled");
        builder.setMessage("Bluetooth must be on for this app to work.\nPlease allow the app to turn on Bluetooth when asked, or the app will be terminated.");
        //builder.setIcon(R.drawable.cross);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            // Ask for permission to turn on BLE
            askToTurnOnBLE();
          }
        });
        builder.show();
      }
      else {
        Log.d(TAG, "BLE is available.");
      }
    }
    catch (RuntimeException e) {
      Log.d(TAG, "BLE not supported.");
      final AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setTitle("Bluetooth Low Energy not available");
      builder.setMessage("Sorry, this device does not support Bluetooth Low Energy.");
      builder.setPositiveButton(android.R.string.ok, null);
      builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

        @Override
        public void onDismiss(DialogInterface dialog) {
          // kill the app
          finish();
          System.exit(0);
        }

      });
      builder.show();
    }
  }

  /**
   * Check if Bluetooth LE is supported by this Android device, and if so, make sure it is enabled.
   *
   * @return false if it is supported and not enabled
   * @throws RuntimeException if Bluetooth LE is not supported.  (Note: The Android emulator will do this)
   */
  @TargetApi(18)
  public boolean checkAvailability() throws RuntimeException {
    if (!isBleAvailable()) {
      throw new RuntimeException("Bluetooth LE not supported by this device");
    }
    return ((BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().isEnabled();
  }

  /**
   * Checks if the device supports BLE
   * @return true if it does
   */
  private boolean isBleAvailable() {
    boolean available = false;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
      Log.w(TAG, "Bluetooth LE not supported prior to API 18.");
    } else if (!this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      Log.w(TAG, "This device does not support bluetooth LE.");
    } else {
      available = true;
    }
    return available;
  }
  /**
   * Asks user's permission to turn on the Bluetooth
   */
  protected void askToTurnOnBLE() {
    final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
  }

  @Override
  /**
   * Processes the user's response when asked to turn on Bluetooth
   */
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    try {
      super.onActivityResult(requestCode, resultCode, data);

      if (requestCode == REQUEST_ENABLE_BT  && resultCode  == RESULT_OK) {

        Log.d(TAG, "DEBUG: permission granted");
      }
      else {
        Log.d(TAG, "DEBUG: permission denied");
        // kill the app
        finish();
        System.exit(0);
      }
    } catch (Exception ex) {
      Toast.makeText(this, ex.toString(),
        Toast.LENGTH_SHORT).show();
    }

  }

  /*********************************************************************************************
   *
   * Broadcast receiver
   *
   *********************************************************************************************/

  // Used by the activity to identify the broadcast items it wants to subscribe to
  private static IntentFilter makeIntentFilter() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(BleReceiver.DEVICE_FOUND);	// BleReceiver sends this when teh scan has found a device
    intentFilter.addAction(BleReceiver.SCANNING_STATE);
    intentFilter.addAction(HTSService.BROADCAST_CONNECTION_STATE);
    intentFilter.addAction(HTSService.BROADCAST_BOND_STATE);
    intentFilter.addAction(HTSService.BROADCAST_ERROR);
    intentFilter.addAction(HTSService.BROADCAST_HTS_TEMPERATURE);
    return intentFilter;
  }


  /**
   * This processes incoming broadcast messages
   */
  private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(final Context context, final Intent intent) {
      final String action = intent.getAction();

      switch (action) {
        // Listen for new HTS temperatures and paint themto the screen.
        case HTSService.BROADCAST_HTS_TEMPERATURE: {
          BluetoothDevice device = intent.getParcelableExtra(HTSService.EXTRA_DEVICE);
          float temperature = intent.getFloatExtra(HTSService.EXTRA_HTS_TEMPERATURE, 0);
          int units = intent.getIntExtra(HTSService.EXTRA_HTS_UNITS, 0);
          int type = intent.getIntExtra(HTSService.EXTRA_HTS_TYPE, 0);
          // Calendar calendar = intent.getParcelableExtra(HTSService.EXTRA_HTS_TIMESTAMP);
          //int seqNum =  intent.getIntExtra(HTSService.EXTRA_HTS_SEQNUM, 0);

          String msg = "";
          if (device != null) {
            msg = device.getName() + " reports " + temperature + " degrees";
            //mStatus.setText(msg);
            //mMessage.setText(String.format("%.02f degrees", temperature));

          }
          else {
            // error...
            Log.e(TAG, "BROADCAST_HTS_RECORD with no device");
          }
          break;
        }

        // can listen to other broadcasts here also... Currently used to report status of connection etc.

        // Message from BleReceiver to inform us of the scanning state
        case BleReceiver.SCANNING_STATE: {
          Boolean isScanning = intent.getBooleanExtra(BleReceiver.EXTRA_SCANNING_STATE, false);
          String uuid = intent.getStringExtra(BleReceiver.EXTRA_SCANNING_UUID);
          String msg = "Not Scanning_";
          if (isScanning) {
            msg = "Scanning for " + uuid;
          }
          Log.d(TAG, msg);
          break;
        }

        // Message from BleReceiver when a matching device is found.
        // Here we just paint the screen, but HTSService should takes steps to connect
        case BleReceiver.DEVICE_FOUND: {
          // here when the BleReceiver detects that a device has been found
          BluetoothDevice sBleDevice = intent.getParcelableExtra(BleReceiver.BLE_DEVICE);
          String sDeviceName = intent.getStringExtra(BleReceiver.BLE_DEVICE_NAME);
          int rssi = intent.getIntExtra(BleReceiver.BLE_DEVICE_RSSI, 0);
          String msg = "MainActivity_Alerted/ Found,  advName:" + sDeviceName + ", macAddr:" + sBleDevice.getAddress() + ", rssi: " + rssi + "dBm";
          Log.d(TAG, msg);
          break;
        }

        case HTSService.BROADCAST_CONNECTION_STATE: {
          int state = intent.getIntExtra(HTSService.EXTRA_CONNECTION_STATE, HTSService.STATE_DISCONNECTED);
          BluetoothDevice device = intent.getParcelableExtra(HTSService.EXTRA_DEVICE);

          String msg;
          if (device != null) {
            msg = device.getName() + " state: ";
          }
          else {
            msg = "state: ";
          }

          switch (state) {
            case HTSService.STATE_LINK_LOSS:
              msg += "link loss";
              break;

            case HTSService.STATE_DISCONNECTED:
              msg += "disconnected";
              break;

            case HTSService.STATE_CONNECTED:
              msg += "connected";
              break;

            case HTSService.STATE_CONNECTING:
              msg += "connecting";
              break;

            case HTSService.STATE_SERVICES_DISCOVERED:
              if (intent.getBooleanExtra(HTSService.EXTRA_SERVICE_PRIMARY, false)) {
                msg += "services discovered";
              }
              else {
                msg += "services not supported";
              }
              break;

            case HTSService.STATE_DEVICE_READY:
              msg += "ready";
              break;

            case HTSService.STATE_DISCONNECTING:
              msg += "disconnecting";
              break;
          }
          //mStatus.setText(msg);
          Log.d(TAG, msg);
          break;
        }

        case HTSService.BROADCAST_BOND_STATE: {
          int state = intent.getIntExtra(HTSService.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
          BluetoothDevice device = intent.getParcelableExtra(HTSService.EXTRA_DEVICE);
          String msg;
          if (device != null) {
            msg = device.getName() + " bond state: ";
          }
          else {
            msg = "Bond state: ";
          }

          switch (state) {
            case BluetoothDevice.BOND_NONE:
              msg += "not bonded";
              break;
            case BluetoothDevice.BOND_BONDING:
              msg += "bonding";
              break;
            case BluetoothDevice.BOND_BONDED:
              msg += "bonded";
              break;

          }
          Log.d(TAG, msg);
          break;
        }

        case HTSService.BROADCAST_ERROR: {
          BluetoothDevice device = intent.getParcelableExtra(HTSService.EXTRA_DEVICE);
          int errorCode = intent.getIntExtra(HTSService.EXTRA_ERROR_CODE, 0);
          String errorMessage = intent.getStringExtra(HTSService.EXTRA_ERROR_MESSAGE);

          if (device != null) {
            String msg = device.getName() + " reports error " + errorMessage + " (" + errorCode +")";
            Log.d(TAG, msg);
          }
          else {
            // error...
            Log.e(TAG, "BROADCAST_ERROR with no device, " + errorMessage);
          }
          break;
        }
      }
    }
  };
}
