package io.ionic.starter;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import java.util.ArrayList;

@NativePlugin()
public class CustomNativePlugin extends Plugin {

  private static final String TAG = "CustomNativePlugin";

  private String mSelectedUUID = HTSManager.HT_SERVICE_UUID.toString();
  private ArrayList<String> scanNameList = new ArrayList<>();  //mCallList

  @PluginMethod()
  public void startBle(PluginCall call) {
    scanNameList.clear();
    scanNameList.add("GSIL");
    String scanNameStr = scanNameList.toString();
    startScanning(1000, scanNameStr);
  }

  @PluginMethod()
  public void stopBle(PluginCall call) {
    boolean b = stopScanning();
    Log.d(TAG, "stop scan result:" + b);
  }

  private boolean startScanning(int alertIntervalMs, String scanNameStr){
    if (BleReceiver.isScanning())
    return false;
    BleReceiver.startScanning(mSelectedUUID, alertIntervalMs, scanNameStr );
    return true;
  }
  public boolean stopScanning() {
    BleReceiver.stopScanning();
    return true;
  }
}
