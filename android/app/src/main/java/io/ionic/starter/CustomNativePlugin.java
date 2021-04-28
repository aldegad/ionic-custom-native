package io.ionic.starter;

import android.widget.Toast;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

@NativePlugin()
public class CustomNativePlugin extends Plugin {
  @PluginMethod()
  public void customCall(PluginCall call) {
    // 웹뷰에서 데이터를 입력합다.
    String message = call.getString("message");
    Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
    toast.show();
    call.success();
  }

  @PluginMethod()
  public void customFunction(PluginCall call) {
    // 웹뷰로 데이터를 반환합니다.
    JSObject ret = new JSObject();
    ret.put("value", "hello");
    call.resolve(ret);
  }
}
