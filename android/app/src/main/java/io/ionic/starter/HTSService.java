package io.ionic.starter;

/**
 * See here on services: https://developer.android.com/guide/components/services
 *
 * Note that for Android 8 (26) you cannot start a service while the app is in background. See here:
 * https://stackoverflow.com/questions/51587863/bad-notification-for-start-foreground-invalid-channel-for-service-notification
 * https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
 */

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.ionic.starter.htsSupport.TemperatureMeasurementCallback;
import io.ionic.starter.util.APIClient;
import io.ionic.starter.util.APIInterface;
import io.ionic.starter.util.BleData;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.Logger;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HTSService extends Service implements io.ionic.starter.HTSManagerCallbacks {

    private static final String TAG = "APP_HTSService";
    public static final String STARTUP_SOURCE = "io.ionic.starter.STARTUP_SOURCE";
    private static final String NOTIFICATION_CHANNEL_ID = "io.ionic.starter.startup_CHANNEL";
    private static final String NOTIFICATIONS_CHANNEL_NAME = "io.ionic.starter.startup_NOTIFICATIONS_CHANNEL_NAME";


    // These are topics of broadcast messages to be sent to BleProfileServiceReadyActivity

    public static final String BROADCAST_CONNECTION_STATE = "io.ionic.starter.BROADCAST_CONNECTION_STATE";
    public static final String BROADCAST_BOND_STATE = "io.ionic.starter.BROADCAST_BOND_STATE";
    public static final String BROADCAST_ERROR = "io.ionic.starter.BROADCAST_ERROR";

    public static final String EXTRA_ERROR_MESSAGE = "io.ionic.starter.EXTRA_ERROR_MESSAGE";
    public static final String EXTRA_ERROR_CODE = "io.ionic.starter.EXTRA_ERROR_CODE";

    public static final String EXTRA_DEVICE = "io.ionic.starter.EXTRA_DEVICE";
    public static final String EXTRA_CONNECTION_STATE = "io.ionic.starter.EXTRA_CONNECTION_STATE";
    public static final String EXTRA_BOND_STATE = "io.ionic.starter.EXTRA_BOND_STATE";
    public static final String EXTRA_SERVICE_PRIMARY = "io.ionic.starter.EXTRA_SERVICE_PRIMARY";
    public static final String EXTRA_SERVICE_SECONDARY = "io.ionic.starter.EXTRA_SERVICE_SECONDARY";

    @Deprecated
    public static final String BROADCAST_BATTERY_LEVEL = "io.ionic.starter.BROADCAST_BATTERY_LEVEL";
    @Deprecated
    public static final String EXTRA_BATTERY_LEVEL = "io.ionic.starter.EXTRA_BATTERY_LEVEL";

    public static final String BROADCAST_HTS_TEMPERATURE = "io.ionic.starter.BROADCAST_HTS_TEMPERATURE";
    public static final String EXTRA_HTS_TEMPERATURE = "io.ionic.starter.EXTRA_HTS_TEMPERATURE";
    public static final String EXTRA_HTS_UNITS = "io.ionic.starter.EXTRA_HTS_UNITS";
    public static final String EXTRA_HTS_TIMESTAMP = "io.ionic.starter.EXTRA_HTS_TIMESTAMP";
    public static final String EXTRA_HTS_TYPE = "io.ionic.starter.EXTRA_HTS_TYPE";

    public static final int STATE_LINK_LOSS = -1;
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_SERVICES_DISCOVERED = 3;
    public static final int STATE_DEVICE_READY = 4;
    public static final int STATE_DISCONNECTING = 5;


    public static final int MSG_REGISTER_CLIENT = 1;
    //public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_SEND_TO_SERVICE = 3;
    public static final int MSG_SEND_TO_ACTIVITY = 4;


    private final IBinder mBinder = new StartServiceBinder();

    private static String startupReason;

    private String mThing;

    private static BluetoothDevice sBleDevice;

    private static String sDeviceName;

    private static String sUuid;

    public static final int FE_SCAN_MODE_1 = 0;
    public static final int FE_SCAN_MODE_2 = 1;
    private static int feScanMode= FE_SCAN_MODE_2; //0: Mode1, 1: Mode2
//    private static String customerName;
//    private static String contactList;
    private int isScanFirstTime = 1;

    private io.ionic.starter.HTSManager mManager;
    private static SharedPreferences pref = null;

    // This is the BroadcastReceiver in the BleReceiver class
    BroadcastReceiver mBleBroadcastReceiver;

    /** The last received temperature value in Celsius degrees. */
    private Float mTemp;

    private ILogSession mLogSession;

    private static int seqNum = 0;

    APIInterface apiInterface;

    public HTSService() {
        Log.d(TAG, "in Constructor");
    }

    /**
     * "If the startService(intent) method is called and the service is not yet running,
     * the service object is created and the onCreate() method of the service is called."
     * <p>
     * see this for foreground services:
     * https://stackoverflow.com/questions/51587863/bad-notification-for-start-foreground-invalid-channel-for-service-notification
     */
    @Override
    public void onCreate() {
        super.onCreate();

        // O is 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "onCreate() - registering BleReceiver (Version O or greater)");
            startNotificationChannel();
        }
        else {
            Log.d(TAG, "onCreate() - registering BleReceiver (less than Version O)");
        }
        startForegroundWithNotification();

        Notifier.playSound(this, "shipsbell", 100);

//        pref = this.getApplicationContext().getSharedPreferences(MainActivity.PREF_FILE, Context.MODE_PRIVATE);

        apiInterface = APIClient.getClient().create(APIInterface.class);


        //This sets up the receiver that listens for BLE devices advertising
        // NOTE: actions must be registered in AndroidManifest.xml
        mBleBroadcastReceiver = new io.ionic.starter.BleReceiver();    // My Receiver class, extends BroadcastReceiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBleBroadcastReceiver, intentFilter);      // Now .ACTION_STATE_CHANGED events arrive on onReceive()

        // This receives messages for purposes of connecting to the device and getting its data
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalBroadcastReceiver, makeIntentFilter());
    }

    /**
     *
     * Starting in Android 8.0 (API level 26), all notifications must be assigned to a channel.
     *  See: https://developer.android.com/training/notify-user/channels
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void startNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATIONS_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        //notificationChannel.setLightColor(R.color.colorBlue);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(notificationChannel);
    }

    /**
     * set up an activity to be run if the user clicks on the notification
     * See https://developer.android.com/training/notify-user/navigation
     */
    private void startForegroundWithNotification() {

//        int a = 1;
//        if ( a == 1) {
//            Log.d(TAG, "startForegroundWithNotification/___________________");
//            return;
//        }

        Log.d(TAG, "startForegroundWithNotification()");
        // Create an Intent for the activity you want to start
        Intent resultIntent = new Intent(this, io.ionic.starter.MainActivity.class);

//        Intent resultIntent = null;
//        try {
//            resultIntent = new Intent(this, Class.forName("io.ionic.starter.MainActivity"));
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//        if ( resultIntent == null ) {
//            Log.d(TAG, "startForegroundWithNotification/ resultIntent NULL");
//            return;
//        }

        // Create the TaskStackBuilder and add the intent, which inflates the back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        // Get the PendingIntent containing the entire back stack
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                //.setSmallIcon(R.drawable.cross_notification)
                .setContentTitle("bgproc app is running in background")
                .setContentText("(Detects events even when the device is asleep)")
                .setContentInfo("Info about bgproc app is provided here.")
                .setContentIntent(pendingIntent)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        startForeground(3, notification);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy()");

        unregisterReceiver(mBleBroadcastReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);
    }

    @Override
    /**
     * Called by the OS: - can be called several times
     * "Called by the system every time a client explicitly starts the service by calling startService()"
     * "Once the service is started, the onStartCommand(intent) method in the service is called.
     *  It passes in the Intent object from the startService(intent) call."
     */
    public int onStartCommand(final Intent intent, final int flags, final int startId) {

        if (intent == null) {
            // I seems to have had this, for some reason... doc says this:
            // "This may be null if the service is being restarted after
            //   its process has gone away, and it had previously returned anything
            //    except {@link #START_STICKY_COMPATIBILITY}.
            Log.e(TAG, "onStartCommand() WITH NULL INTENT! id = " + startId + ", received " + startupReason);
        }
        else {

            startupReason = intent.getStringExtra(STARTUP_SOURCE);
            Log.d(TAG, "onStartCommand() id = " + startId + ", startup reason: '" + startupReason + "'");
        }

        // Ask the BleReceiver to restore its state
        // TODO - should this be called only after a reboot?
        io.ionic.starter.BleReceiver.initialiseAfterReboot();

        // Ask the database for a list of devices we should be listening for.
        // This will result in a Broadcast of DatabaseShim.FOUND_DEVICE_LIST
        // at which point we will ask the BleReceiver to start scanning
        //DatabaseShim.requestDeviceList();

        //return START_REDELIVER_INTENT;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


/*
    private Messenger mClient = null;	// Activity 에서 가져온 Messenger

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }



    // activity로부터 binding 된 Messenger
    private final Messenger mMessenger = new Messenger(new Handler(new Handler.Callback() {

        //receive message from activity
        @Override
        public boolean handleMessage(Message msg) {
            Log.d(TAG, "[MSG] HTSService <--- MainActivity");
            Log.d(TAG,"msg what: "+ msg.what +" , msg.obj: "+ msg.obj);
            if ( msg.obj == null ) {
                Log.d(TAG, "handleMessage/msg.obj NULL");


                Log.d(TAG, pref.getString(MainActivity.PREF_KEY_NAME, ""));
                Log.d(TAG, pref.getString(MainActivity.PREF_KEY_PHONE, ""));
                Log.d(TAG, pref.getString(MainActivity.PREF_KEY_YEAR, ""));
                Log.d(TAG, "value:" + pref.getBoolean(MainActivity.PREF_KEY_GENDER, true));
                Log.d(TAG, pref.getString(MainActivity.PREF_KEY_NOTE, ""));

                Log.d(TAG, pref.getString(MainActivity.PREF_KEY_FE_MAC, ""));
                Log.d(TAG, pref.getString(MainActivity.PREF_KEY_FE_LOC1, ""));
                Log.d(TAG, pref.getString(MainActivity.PREF_KEY_FE_LOC2, ""));

                return false;
            }

            try {
                JSONArray arr = new JSONArray(String.valueOf(msg.obj));
                feScanMode = arr.getInt(0);
                customerName = arr.getString(1);
                contactList = arr.getString(2);


                Log.d(TAG, "______FE_SCAN_MODE: " + arr.getInt(0));
                Log.d(TAG, "______etName: " + arr.getString(1));

                JSONArray cList = new JSONArray(arr.getString(2));
                for (int i=0; i<cList.length(); i++) {
                    Log.d(TAG, "______CONTACT_LIST: " + cList.getString(i));
                }

            } catch (JSONException e){
                e.printStackTrace();
                Log.e (TAG, "mMessenger/handleMessage _e.printStackTrace()");
                return false;
            }

            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClient = msg.replyTo;	// activity로부터 가져온
                    Log.d(TAG, "handleMessage/MSG_REGISTER_CLIENT...");
                    break;
            }

            //sendMsgToActivity(5555);
            return false;
        }
    }));

    //send message to activity
    private void sendMsgToActivity(int sendValue) {
        try {
            Log.d (TAG, "[MSG] HTSService ---> MainActivity");
            Log.d(TAG,"msg: " + sendValue);

            Log.d (TAG, "sendMsgToActivity(), value:"+ sendValue);
            Bundle bundle = new Bundle();
            bundle.putInt("fromService", sendValue);
            bundle.putString("test","abcdefg");
            Message msg = Message.obtain(null, MSG_SEND_TO_ACTIVITY);
            msg.setData(bundle);
            mClient.send(msg);		// msg 보내기
        } catch (RemoteException e) {
        }
    }
*/


    public class StartServiceBinder extends Binder {

        public io.ionic.starter.HTSService getService() {
            return io.ionic.starter.HTSService.this;
        }

        // unused API placeholders:
        public void setSomething(String thing) {
            mThing = thing;
            Log.d(TAG, "Set: " + thing);
        }

        public String getSomething() {
            return mThing;
        }
    }

    public String getStartupReason() {
        return startupReason;
    }


    /*********************************************************************************************
     *
     * Broadcast receiver
     *
     *********************************************************************************************/

    // Used by the activity to identify the broadcast items it wants to subscribe to
    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(io.ionic.starter.BleReceiver.DEVICE_FOUND);    // BleReceiver sends this when teh scan has found a device

        return intentFilter;
    }


    private void sendEmergencyMsg () {

    }
    /**
     * This processes incoming broadcast messages
     */
    private final BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            switch (action) {

                // Message from BleReceiver when a matching device is found.
                // We connect to the device.
                case io.ionic.starter.BleReceiver.DEVICE_FOUND: {
                    // here when the BleReceiver detects that a device has been found
                    sBleDevice = intent.getParcelableExtra(io.ionic.starter.BleReceiver.BLE_DEVICE);
                    sDeviceName = intent.getStringExtra(io.ionic.starter.BleReceiver.BLE_DEVICE_NAME);
                    sUuid = intent.getStringExtra(io.ionic.starter.BleReceiver.BLE_SERVICE_UUID);
                    int rssi = intent.getIntExtra(io.ionic.starter.BleReceiver.BLE_DEVICE_RSSI, 0);
                    Log.d(TAG, "Device found: " + sDeviceName + ", " + sBleDevice.getAddress() + " " + rssi + "dBm");
//                    Notifier.playSound(context, "ovending", 100);

                    io.ionic.starter.BleReceiver.setProcessingDevice(true);  // inhibit further device found events

                    Notifier.playSound(getApplicationContext(), "boxing3", 100);

                    if(rssi < -99) {
                        rssi = -99;
                    }

                    // Send API

                    HashMap params = new HashMap<String, String>();
                    params.put("userid", "1000024");
                    params.put("beaconmac", sBleDevice.getAddress());
                    params.put("rssi", rssi);
                    params.put("beaconuuid", sUuid);

                    SendData sd = new SendData(params.toString().getBytes());
                    sd.start();



                    BleData bdata = new BleData("morpheus", "leader");


//                    console.log('bluetoothConnect() 1 : ', JSON.stringify(bluetooth));
//                    console.log('bluetoothConnect() 2 : ', this.updateDisable);
//                    let test:any = await this.connect.run('Get_UserBicon', { // Insert_EnterBicon
//                            bicon_uq_id: this.strongest_bluetooth.address,
//                            rssi: this.strongest_bluetooth.rssi
//                      });
//                    console.log(JSON.stringify(test));







                    // start a logger session
                    mLogSession = Logger.newSession(getApplicationContext(), getString(R.string.app_name), sBleDevice.getAddress(), sDeviceName);
                    Logger.d(mLogSession, "Started log session");

                    if ( feScanMode == FE_SCAN_MODE_2)
                        isScanFirstTime = 0;
                    else if ( feScanMode == FE_SCAN_MODE_1 )
                        connectToDevice(sBleDevice, sDeviceName, sUuid);

                    break;
                }


            }

        }
    };

    /*********************************************************************************************
     *
     * BLUETOOTH MANAGER STUFF
     *
     *********************************************************************************************/

    private void connectToDevice(BluetoothDevice bleDevice, String deviceName, String uuid) {

        Log.v(TAG, "Creating HTSManager now");

        mManager = new io.ionic.starter.HTSManager(this, bleDevice, deviceName, uuid);
        mManager.setGattCallbacks(this);
        mManager.setLogger(mLogSession);

        Log.d(TAG, "Attempting to connect to " + bleDevice.getAddress());

        if (mManager != null) {
            // This is where we request a connection to the BLE device.
            mManager.connect(bleDevice).enqueue();
        } else {
            Log.e(TAG, "ERROR: mManager is null!");
        }
    }


    /********************************************************************************************
     *
     * BleManagerCallbacks - implementation of this Interface
     *
     ********************************************************************************************/

    /**
     * These are a series of Intents that are broadcast from BleProfileService
     * to BleProfileServiceReadyActivity. They then propagate through to MainActivity
     *
     * @param device
     */
    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
        Log.d(TAG, "  ... BleManager reports Device Connecting");
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTING);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }


    private void sendSmsMessage(String username, String phoneNo) {

        String sms = username + " 감지되었습니다.";

        //https://itmir.tistory.com/458 참고해서 테스트 해볼 것
//        PendingIntent sentIntent = PendingIntent.getBroadcast(this, 0, new Intent("SMS_SENT_ACTION"), 0);
        try {
            //전송
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNo, null, sms, null, null);
            Toast.makeText(getApplicationContext(), phoneNo + " 번호로 긴급 문자 전송 완료!", Toast.LENGTH_LONG).show();
            Log.d (TAG, phoneNo + " 번호로 긴급 문자 전송 완료!");
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "SMS faild, please try again later!", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        Log.d(TAG, "  ... BleManager reports Device Connected");
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTED);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        if ( feScanMode == FE_SCAN_MODE_1 && isScanFirstTime == 0) {
//            Log.d(TAG, "MODE1. Send Emergency SMS...");
            Notifier.playSound(this, "ovending", 100);

//            try {
////                String username = pref.getString(MainActivity.PREF_KEY_NAME, "");
////                String contactList = pref.getString(MainActivity.PREF_KEY_CONTACT, "");
////                JSONArray arr = new JSONArray(contactList);
////                for (int i = 0; i < arr.length(); i++) {
////                    sendSmsMessage(username, arr.getString(i));
////                }
//                String username = "tmp_user";
//                String contactList = "contact1";
//                sendSmsMessage(username, contactList);
//
//            } catch (Exception e) {
//                Log.e(TAG, "MODE1. Send Emergency SMS/ JSONException error");
//                e.printStackTrace();
//            }
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {
        // Notify user about changing the state to DISCONNECTING
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTING);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onDeviceDisconnected(final BluetoothDevice device) {
        // Note 1: Do not use the device argument here unless you change calling onDeviceDisconnected from the binder above

        // Note 2: if BleManager#shouldAutoConnect() for this device returned true, this callback will be
        // invoked ONLY when user requested disconnection (using Disconnect button). If the device
        // disconnects due to a link loss, the onLinkLossOccurred(BluetoothDevice) method will be called instead.
        Log.d(TAG, "  ... BleManager reports Device Disconnected.");

        if ( feScanMode == FE_SCAN_MODE_1 && isScanFirstTime == 0) {
            Log.d(TAG, "MODE1. CALL to 119!!!");
/*
            Intent tt = new Intent(Intent.ACTION_DIAL);
            tt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //tt.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            tt.setData(Uri.parse("tel:01058501462"));
            tt.addFlags(Intent.FLAG_FROM_BACKGROUND);

            try {
                this.getApplicationContext().startActivity(tt);
            } catch (Exception e) {
                Log.e(TAG, "onDeviceDisconnected/ startActivity failed. Exception e occured!");
                e.printStackTrace();
            }
*/
        }
        isScanFirstTime = 0;

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTED);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        // now tell the BleReceiver it can resume scanning
        io.ionic.starter.BleReceiver.setProcessingDevice(false);
    }

    @Override
    public void onLinkLossOccurred(final BluetoothDevice device) {
        Log.d(TAG, "  ... BleManager reports Link Loss Occurred");

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_LINK_LOSS);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
        Log.d(TAG, "  ... BleManager reports Services Discovered");

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
         broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_SERVICES_DISCOVERED);

        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);
        broadcast.putExtra(EXTRA_SERVICE_PRIMARY, true);
        broadcast.putExtra(EXTRA_SERVICE_SECONDARY, optionalServicesFound);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onDeviceReady(final BluetoothDevice device) {
        Log.d(TAG, "  ... BleManager reports Device Ready");
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DEVICE_READY);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBondingRequired(final BluetoothDevice device) {
        Log.d(TAG, "  ... BleManager reports Bonding Required");

        final Intent broadcast = new Intent(BROADCAST_BOND_STATE);
        broadcast.putExtra(EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDING);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBonded(final BluetoothDevice device) {

        Log.d(TAG, "  ... BleManager reports Bonded");
        final Intent broadcast = new Intent(BROADCAST_BOND_STATE);
         broadcast.putExtra(EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBondingFailed(final BluetoothDevice device) {

        Log.d(TAG, "  ... BleManager reports Bonding Failed");
        final Intent broadcast = new Intent(BROADCAST_BOND_STATE);
        broadcast.putExtra(EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onError(final BluetoothDevice device, final String message, final int errorCode) {

        final Intent broadcast = new Intent(BROADCAST_ERROR);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);
        broadcast.putExtra(EXTRA_ERROR_MESSAGE, message);
        broadcast.putExtra(EXTRA_ERROR_CODE, errorCode);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        Notifier.playSound(this, "plunk", 100);

        Log.e(TAG, "  ... BleManager reports Error: " +  message + " (" + errorCode + ")");
    }

    @Override
    public void onDeviceNotSupported(final BluetoothDevice device) {

        Log.d(TAG, "  ... BleManager reports Device Not Supported");

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_SERVICES_DISCOVERED);

        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);
        broadcast.putExtra(EXTRA_SERVICE_PRIMARY, false);
        broadcast.putExtra(EXTRA_SERVICE_SECONDARY, false);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        // no need for disconnecting, it will be disconnected by the manager automatically
    }

    @Override
    public void onBatteryValueReceived(final BluetoothDevice device, final int value) {
        final Intent broadcast = new Intent(BROADCAST_BATTERY_LEVEL);
        broadcast.putExtra(EXTRA_DEVICE, sBleDevice);
        broadcast.putExtra(EXTRA_BATTERY_LEVEL, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }


   // @ Override
    /********************************************************************************************
     *
     * SavedHTSCharacteristicCallbacks - These are all defined in this Interface.
     * They are all called by SavedHTSManager to notify this Service of various device events.
     *
     ********************************************************************************************/

    @Override
    public void onTemperatureMeasurementReceived(BluetoothDevice device,
                                                 float temperature,
                                                 //@TemperatureUnit final int unit,
                                                 int unit,
                                                 Calendar calendar,
                                                 Integer type) {
        mTemp = TemperatureMeasurementCallback.toCelsius(temperature, unit);
        Log.d(TAG, "  ... BleManager reports temperature is " + mTemp);

        // Broadcast the reading to MainActivity.
        // Typically, store in a database or process otherwise
        final Intent broadcast = new Intent(BROADCAST_HTS_TEMPERATURE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_HTS_TEMPERATURE, temperature);
        broadcast.putExtra(EXTRA_HTS_UNITS, unit);
        broadcast.putExtra(EXTRA_HTS_TYPE, type);
        broadcast.putExtra(EXTRA_HTS_TIMESTAMP, calendar);

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        // That's all we need from the BLE device, so shut down...
        String msg = "Sequence finished. Disconnecting in 100ms.";
        Log.v(TAG, msg);
        Logger.d(mLogSession, msg);

        Notifier.playSound(this, "boxing3", 100);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // hack - delay a little so the MainActivity collects the records from the service
                // before the service is destroyed
                // TODO - figure out something more elegant!
                Logger.d(mLogSession, " Disconnecting now");
                disconnectAndStopService();
            }
        }, 100);

    }

    /**
     * This allows the user etc to disconnect the device and so delete the service
     */
    public void disconnectAndStopService() {

        final int state = mManager.getConnectionState();
        if (state == BluetoothGatt.STATE_DISCONNECTED || state == BluetoothGatt.STATE_DISCONNECTING) {
            Log.d(TAG, "Attempting to disconnect from " + sBleDevice.getName());
            mManager.close();
            onDeviceDisconnected(sBleDevice);
            return;
        }
        else {

            Log.d(TAG, "  ... asking BleManager to disconnect ");
            // This ends up in BleManager.internalDisconnect() which calls the OS mBluetoothGatt.disconnect();
            // NOTE: if the BLE device appears not to disconnect it might be because nRF Connect is also listening: see
            // https://devzone.nordicsemi.com/f/nordic-q-a/38422/android-disconnect-and-close-do-not-disconnect
            mManager.disconnect().enqueue();
        }
    }

    public void sendMessage(String msg) throws SocketException, UnknownHostException {
        if ( msg != null ) {


            UDPSendPacket sendPacket = new UDPSendPacket(mUDPSocket, msg.getBytes());
            sendPacket.run();
        }
    }



    DatagramSocket mUDPSocket = null;


    private class UDPSendPacket extends Thread {
        private final DatagramSocket mSocket;

        private final byte[] mSendByte;

        public UDPSendPacket(DatagramSocket mSocket, byte[] bytes) throws SocketException, UnknownHostException {
            this.mSocket = mSocket;

            DatagramSocket socket = new DatagramSocket();
            InetAddress serverAddr = InetAddress.getByName("15.165.40.44");

//            mSocket = socket;
            mSendByte = bytes;
        }

        @Override
        public void run() {

            if ( mSocket != null ) {

                try {
                    // 패킷 전송
                    DatagramPacket sendPacket = new DatagramPacket(mSendByte, mSendByte.length);
                    mUDPSocket.send(sendPacket);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }


    class SendData extends Thread {

        private final byte[] mSendByte;

        SendData(byte[] mSendByte) {
            this.mSendByte = mSendByte;
        }

        @Override
        public void run(){
            try{
                DatagramSocket socket = new DatagramSocket();
                InetAddress serverAddr = InetAddress.getByName("15.165.40.44");

                //byte[] buf = ("Hello World").getBytes();

                DatagramPacket packet = new DatagramPacket(mSendByte, mSendByte.length, serverAddr, 8091);

                socket.send(packet);

                Log.e("EEEEEEEEEEE","Send Byte!! UDP - "+mSendByte.toString());

//                socket.receive(packet);
//                String msg = new String(packet.getData());

            }catch (Exception e){
                Log.e("EEEEEEEEEEE",e.toString());
            }
        }
    }

}
