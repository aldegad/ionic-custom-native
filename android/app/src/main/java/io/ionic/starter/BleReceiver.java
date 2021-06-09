package io.ionic.starter;

/*
The code here manages the scanner library. In particular, it enables or disables the scanning.
It also listens for incoming PendingIntents when a matching BLE device is found.

It also listens for events associated with enabling and disabling the device'ss Bluetooth.
 */


import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanRecord;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class BleReceiver extends BroadcastReceiver {

    public static Context context;

    private static final String TAG = "APP_BleReceiver";

    public static final String ACTION_SCANNER_FOUND_DEVICE = "io.ionic.starter.ACTION_SCANNER_FOUND_DEVICE";

    public static final String DEVICE_FOUND = "io.ionic.starter.DEVICE_FOUND";
    public static final String BLE_DEVICE = "io.ionic.starter.BLE_DEVICE";
    public static final String BLE_DEVICE_NAME = "io.ionic.starter.BLE_DEVICE_NAME";
    public static final String BLE_DEVICE_RSSI = "io.ionic.starter.BLE_DEVICE_RSSI";
    public static final String BLE_SERVICE_UUID = "io.ionic.starter.BLE_SERVICE_UUID";

    public static final String SCANNING_STATE = "io.ionic.starter.SCANNING_STATE";
    public static final String EXTRA_SCANNING_STATE = "io.ionic.starter.EXTRA_SCANNING_STATE";
    public static final String EXTRA_SCANNING_UUID = "io.ionic.starter.EXTRA_SCANNING_UUID";

    private static PendingIntent mPendingIntent;

    private static Boolean mScanning = false;
    private static Boolean mShouldScan = false;
    private static int delayMs = 10000; //default 10 sec
    private static String scanList;

    private static Context mContext;

    private static SharedPreferences sSharedPreferences;

    private static String mUuid = "";

    // for re-scheduling scans
    private static Handler mScheduleHandler;

    // A list of "our" devices, so we can ignore other people's HTS devices
    private static HashMap<String, String> sDeviceList;

    // Set true when we are processing a device and false when this is finished.
    private static Boolean mProcessingDevice = false;

    private static int feScanMode = 0; //0: Mode1, 1: Mode2
    public static final int FE_SCAN_MODE_1 = 0;
    public static final int FE_SCAN_MODE_2 = 1;


    /**
     * Constructor
     */
    public BleReceiver() {
        Log.v(TAG, "in Constructor");
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.v(TAG, "  ");
        Log.v(TAG, "onReceive() ");

        if (intent.getAction() == null) {
            Log.e(TAG, "ERROR: action is null");
            return;
        }
        else {
            Log.v(TAG, "DEBUG: action is " + intent.getAction());
        }

        //NOTE: actions must be registered in AndroidManifest.xml
        switch (intent.getAction()) {

            // Look whether we find our device
            case ACTION_SCANNER_FOUND_DEVICE: {
                Bundle extras = intent.getExtras();

                if (extras != null) {
                    Object o = extras.get(BluetoothLeScannerCompat.EXTRA_LIST_SCAN_RESULT);
                    if (o instanceof ArrayList) {
                        ArrayList<ScanResult> scanResults = (ArrayList<ScanResult>) o;
                        Log.v(TAG, "There are " + scanResults.size() + " results");

                        if (!mShouldScan) {
                            Log.d(TAG, "*** Unexpected device found: not scanning");
                        }

                        for (ScanResult result : scanResults) {
                            if (result.getScanRecord() == null) {
                                Log.d(TAG, "getScanRecord is null");
                                continue;
                            }

                            BluetoothDevice device = result.getDevice();
                            ScanRecord scanRecord = result.getScanRecord();
                            String scanName = scanRecord.getDeviceName();
                            String deviceName = device.getName();
                            int rssi = result.getRssi();
                            //mHeader.setText("Single device found: " + device.getName() + " RSSI: " + result.getRssi() + "dBm");
                            Log.i(TAG, "SCAN RESULT\n Found: " + device.getAddress()
                                    + " scan name: " + scanName
                                    + " device name: " + deviceName
                                    + " RSSI: " + result.getRssi() + "dBm");

                            // Sometimes the same device is found again, even though we have stopped scanning as soon as it was found.
                            // Discard these events.
                            if (mProcessingDevice) {
                                Log.d(TAG, "Ignoring " + scanName + " (already processing).");
                                return;
                            }

                            // There could be HTS devices we are not interested in. For now, any are accepted.
                            // Later a list of devices of interest could be constructed.
                            if (isInOurHTSList(device)) {
                                Log.d(TAG, "Hey! ours!");
                                stopScan();
                                notifyDeviceFound(device, scanRecord, rssi);  // broadcast this back to the activity

                                int delay = 2000;  //FE_SCAN_MODE_1
//                                if ( feScanMode == FE_SCAN_MODE_2 ) delay = 5000;

                                scheduleScan(delay);     // restart if no one else allows the scan to resume
                            }
                            else {
                                Log.d(TAG, "Not our device");
                            }
                        }

                    } else {
                        // Received something, but not a list of scan results...
                        Log.d(TAG, "   no ArrayList but " + o);
                    }
                } else {
                    Log.d(TAG, "no extras");
                }

                break;
            }

            // Look at BLE adapter state
            case BluetoothAdapter.ACTION_STATE_CHANGED: {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "BLE off");
                        // Need to take some action or app will fail...
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "BLE turning off");
                        stopScan();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "BLE on");
                        startScan();    // restart scanning (provided the activity wants this to happen)
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "BLE turning on");
                        break;
                }
                break;
            }

            default:
                // should not happen
                Log.d(TAG, "Received unexpected action " + intent.getAction());
        }

    }

    /**
     * After reboot we need to get saved state from SharedPreferences
     * This is called when HTSService starts after reboot.
     */
    public static void initialiseAfterReboot() {

        Log.d(TAG, "Initialising state saved from SharedPreferences");
        HTSScannerApplication.setupSharedPreferences();

        mContext = HTSScannerApplication.getAppContext();

        mUuid = HTSScannerApplication.getSavedUuid();
        mShouldScan = HTSScannerApplication.getScanning();
        delayMs = HTSScannerApplication.getDelay();
        setFeScanMode(FE_SCAN_MODE_2);

        String snl = HTSScannerApplication.getScanNameList();
        Log.d(TAG, "SNL:"+snl);

        String aa = snl;
        if ( aa.length()> 0)
            saveScanNameList(aa);


        if (mShouldScan) {
            Log.d(TAG, "Looks like we were scanning before reboot, so we will start again");
            startScan();
        }
        else {
            Log.d(TAG, "Looks like we were not scanning before reboot.");
        }
    }


    public static void setFeScanMode(int value) {
        feScanMode = value;
    }

    /**
     * MainActivity asks to know what we are doing.
     * We reply with a Broadcast
     */
    public static void requestScanningState() {
        notifyScanState();
    }




    public static void saveScanNameList (String val){

        scanList = val.substring( 1, val.length() - 1 );
//        List<String> list = Arrays.asList(arr.split(","));
//        for (int i=0; i<list.size(); i++){
//            Log.d(TAG, "index:"+i +" str: "+ list.get(i));
//        }

        String[] arr = scanList.split(",");
//        for (int i=0; i<arr.length; i++){
//            Log.d(TAG, "index:"+i +" str: "+ arr[i]);
//        }

    }

    public static void startScanning(String uuid, int delay, String scanNameStr) {
        // Save these in SharedPreferences, so they are available after reboot
        HTSScannerApplication.saveUuid(uuid);
        HTSScannerApplication.saveScanning(true);
        HTSScannerApplication.saveDelay(delay);
        HTSScannerApplication.saveScanNameStr(scanNameStr);

        mContext = context;
        mUuid = uuid;
        mShouldScan = true;
        delayMs = delay;
        saveScanNameList(scanNameStr);

        startScan();
    }

    /**
     * Used internally only
     */
    private static void startScan() {

        mProcessingDevice = false;

        if (!mShouldScan) {
            Log.d(TAG, "User has not requested scanning, so won't scan");
            return;
        }
        else {
            Log.d(TAG, "Trying to start scan.");
        }

        // cancel the scheduled restart, if any
        if (mScheduleHandler != null) {
            mScheduleHandler.removeCallbacksAndMessages(null);
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                //.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                //.setReportDelay(0)
                .build();


        List<ScanFilter> filters = new ArrayList<>();
        filters.clear();
        Log.d(TAG, "Starting to scan for: " + mUuid);

//            ScanFilter sf = new ScanFilter.Builder().setDeviceName("FIRE_ALARM").setServiceUuid(ParcelUuid.fromString(mUuid)).build();

//            ScanFilter sf = new ScanFilter.Builder().setDeviceName("GSIL").build();
//            filters.add(sf);
//             filters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(mUuid)).build());

        String[] arr = scanList.split(",");
        for (int i=0; i<arr.length; i++){
            ScanFilter sf = new ScanFilter.Builder().setDeviceName(arr[i].trim()).build();
            filters.add(sf);
            Log.d(TAG, "index:"+i +" str:"+ arr[i].trim() );
        }


        Intent intent = new Intent(mContext, io.ionic.starter.BleReceiver.class); // explicit intent
        intent.setAction(io.ionic.starter.BleReceiver.ACTION_SCANNER_FOUND_DEVICE);
        int id = 0;     // "Private request code for the sender"

        mPendingIntent = PendingIntent.getBroadcast(mContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Now start the scanner

        try {
            Log.d(TAG, "Asking library to start scanning.");
            BluetoothLeScannerCompat.getScanner().startScan(filters, settings, mContext, mPendingIntent);
            mScanning = true;
            notifyScanState();
        }
        catch (Exception e) {
            Log.e(TAG, "ERROR in startScan() " + e.getMessage());
        }
    }

    /**
     * Called externally only
     */
    public static void stopScanning() {
        mProcessingDevice = false;
        mShouldScan = false;
        stopScan();

        // Save this in SharedPreferences, so it is available after reboot
        HTSScannerApplication.saveScanning(false);
    }

    /**
     * Used internally only
     */
    private static void stopScan() {
        //if (mScanning) {
        // do this unconditionally? maybe we could be still scanning for some reason?

        //if (mPendingIntent != null && mContext != null) {
        Log.d(TAG, "Stop scanning");

        if (mContext== null || mPendingIntent == null) {
            Log.d(TAG, "Can't stop: parameters are null");
            return;
        }

        // cancel the scheduled restart, if any
        if (mScheduleHandler != null) {
            mScheduleHandler.removeCallbacksAndMessages(null);
        }

        try {
            Log.d(TAG, "Asking library to stop scanning.");
            BluetoothLeScannerCompat.getScanner().stopScan(mContext, mPendingIntent);
            mScanning = false;

            notifyScanState();
        } catch (Exception e) {
            Log.e(TAG, "ERROR in stopScan() " + e.getMessage());
        }
    }

    /** Allows the calling activity to know what is happening
     *
     * @return true if scanning is underway.
     */
    public static Boolean isScanning() {
        return mScanning;
    }

    /**
     * Restart scanning after a delay
     */

    private void scheduleScan(int delayMillis) {

        Log.v(TAG, "Scheduling a restart for " + delayMillis + "ms time");
        mScheduleHandler = new Handler();
        mScheduleHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "Time up - scan again");
                setProcessingDevice(false); // This will also re-start the scanning
            }
        }, delayMillis); // milliseconds

    }


    /**
     * The main application sends us this message when it fetches a deviceList after the user logs on.
     * If there are entries in it then we can start scanning
     * @param deviceList
     */
    public static void onDeviceListChanged(HashMap<String, String> deviceList) {

        sDeviceList = deviceList;
        if (deviceList == null) {
            Log.i(TAG, "Device list is null, so stopping scanning");
            stopScan();
        }
        else if(sDeviceList.size() == 0) {
            Log.i(TAG, "Device list is empty, so stopping scanning");
            stopScan();
        }
        else {
            Log.i(TAG, "Device list has entries, so starting scanning");
            // Not sure if we should stop before starting?
            //stopScan();
            startScan();
        }
    }


    /**
     * Check whether the device is actually one we are interested in.
     *
     * TODO - think about how to maintain the right values in sDeviceList
     *  e.g. when a user logs out. Could two different users be using the same app,
     *  so we need a different list of each user? Or in that case would we have to
     *  combine the devices from each user?
     *
     * @param device
     * @return true if it is one of our devices
     */
    private Boolean isInOurHTSList(BluetoothDevice device) {
        Boolean foundMatchingDevice = false;

        if (sDeviceList != null) {
            for (Map.Entry<String, String> entry : sDeviceList.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                //Log.d(TAG, "key: '" + key + "', value: '" + value + "'");
                if (key.equals(device.getAddress())) {
                    foundMatchingDevice = true;
                    break;
                }
            }
        }
        // TODO - this is a hack - return true until such time as the database code is set up to
        // return a list of "our" devices.
        //return foundMatchingDevice;
        Log.d(TAG, "FIXME: matching all devices");
        return true;
    }

    /**
     * Called when the scanner finds one of our HTS devices. Broadcasts an Intent to HTSService
     * which will then connect to it. Also can broadcast to MainActivity
     * @param device
     * @param scanRecord
     * @param rssi
     */
    private void notifyDeviceFound(BluetoothDevice device, ScanRecord scanRecord, int rssi) {

        Intent intent = new Intent(DEVICE_FOUND);
        intent.putExtra(BLE_DEVICE, device);
        intent.putExtra(BLE_DEVICE_NAME, scanRecord.getDeviceName());
        intent.putExtra(BLE_DEVICE_RSSI, rssi);
        intent.putExtra(BLE_SERVICE_UUID, mUuid);
        // NOTE: often device.getName() is null!
      LocalBroadcastManager.getInstance(HTSScannerApplication.getAppContext()).sendBroadcast(intent);
    }

    /**
     * Inform the MainActivity what we are doing in terms of scanning
     */
    private static void notifyScanState() {
        Intent intent = new Intent(SCANNING_STATE);
        intent.putExtra(EXTRA_SCANNING_STATE, mScanning);
        intent.putExtra(EXTRA_SCANNING_UUID, mUuid);
        LocalBroadcastManager.getInstance(HTSScannerApplication.getAppContext()).sendBroadcast(intent);
    }

    // getter and setter for mProcessingDevice
    public static Boolean getProcessingDevice() {
        return mProcessingDevice;
    }

    // called by HTSService

    /**
     * Inhibits processing of spurious ACTION_SCANNER_FOUND_DEVICE messages once we have decided to connect to a HTS device.
     * Called by HTSService twice:
     *  - when it gets a DEVICE_FOUND message from BleReceiver
     *  - when disconnected from the HTS device.
     * @param state
     */
    public static void setProcessingDevice(Boolean state) {
        mProcessingDevice = state;

        // if scanning has been temporarily suspended while we process one device, restart scanning
        if (state == false) {
            startScan();
        }
    }

}
