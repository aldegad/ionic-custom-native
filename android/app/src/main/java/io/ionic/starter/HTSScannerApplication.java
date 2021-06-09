package io.ionic.starter;

/**
 * This file exists primarily so we can have a context to use before MainActivity starts.
 *
 * See here for getting the application sContext:
 * https://stackoverflow.com/questions/2002288/static-way-to-get-context-in-android
 */

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Map;
import java.util.Set;

public class HTSScannerApplication extends Application {

    private static final String TAG = "APP_HTSScannerApp";
    public static final String SETTINGS_APP_FIRST_TIME = "io.ionic.starter.SETTINGS_APP_FIRST_TIME";
    public static final String SETTINGS_UUID = "io.ionic.starter.SETTINGS_UUID";
    public static final String SETTINGS_SCANNING = "io.ionic.starter.SETTINGS_SCANNING";
    public static final String SETTINGS_DELAY = "io.ionic.starter.SETTINGS_DELAY";
    public static final String SETTINGS_SCAN_LIST = "io.ionic.starter.SETTINGS_SCAN_LIST";


    private static SharedPreferences sSharedPreferences;

    private static Context sContext;

    /** Called when the application is starting, before any activity, service,
     * or receiver objects (excluding content providers) have been created.
    */
    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();
        Log.d(TAG, " ");
        Log.d(TAG, "*** onCreate()");
    }

    public static Context getAppContext() {
        return sContext;
    }

    /*********************************************************************************************
     *
     * MANAGE SHAREDPREFERENCES
     *
     * This allows information to be saved across reboots, so the state before the reboot can be restored afterwards
     *
     *********************************************************************************************/

    public static void setupSharedPreferences() {
        sSharedPreferences = PreferenceManager.getDefaultSharedPreferences(io.ionic.starter.HTSScannerApplication.getAppContext());

        // Check for for run. I am not doing anything here, but I wanted to test the concept. See refs above.
        if(sSharedPreferences.getBoolean(SETTINGS_APP_FIRST_TIME, true)) {
            // run your one time code
            Log.d(TAG, "This application is being run for the first time");
            SharedPreferences.Editor editor = sSharedPreferences.edit();
            editor.putBoolean(SETTINGS_APP_FIRST_TIME, false);
            editor.commit();
        };

        // debug: print all the values
        Log.d (TAG, printAllPreferences());
    }

    /**
     * Print all of the shared preferences_database, for debugging
     *
     * @return a string that can be printed or displayed
     */
    public static String printAllPreferences() {
        Map<String, ?> allPrefs = sSharedPreferences.getAll(); //your sharedPreference
        Set<String> set = allPrefs.keySet();
        String msg = "Shared Preferences:";
        // iterate through each saved setting
        for(String s : set){
            msg += s + " <" + allPrefs.get(s).getClass().getSimpleName() +"> =  "
                    + allPrefs.get(s).toString() + "\r\n";
        }
        return msg;
    }


    public static void saveUuid(String uuid) {
        SharedPreferences.Editor editor = sSharedPreferences.edit();
        editor.putString(SETTINGS_UUID, uuid);
        editor.commit();
    }

    public static void saveScanning(Boolean scanning) {
        SharedPreferences.Editor editor = sSharedPreferences.edit();
        editor.putBoolean(SETTINGS_SCANNING, scanning);
        editor.commit();
    }

    public static void saveDelay(int delay) {
        SharedPreferences.Editor editor = sSharedPreferences.edit();
        editor.putInt(SETTINGS_DELAY, delay);
        editor.commit();
    }

    public static void saveScanNameStr(String scanNameStr) {
        SharedPreferences.Editor editor = sSharedPreferences.edit();
        editor.putString(SETTINGS_SCAN_LIST, scanNameStr);
        editor.commit();
    }


    public static String getSavedUuid() {
        return sSharedPreferences.getString(SETTINGS_UUID, io.ionic.starter.HTSManager.HT_SERVICE_UUID.toString());
    }

    public static Boolean getScanning() {
        return  sSharedPreferences.getBoolean(SETTINGS_SCANNING, false);
    }

    public static int getDelay() {
        return  sSharedPreferences.getInt(SETTINGS_DELAY, 0 );
    }

    public static String getScanNameList(){
        return sSharedPreferences.getString(SETTINGS_SCAN_LIST, "");
    }

}
