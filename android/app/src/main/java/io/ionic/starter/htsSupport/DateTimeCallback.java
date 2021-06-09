package io.ionic.starter.htsSupport;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import java.util.Calendar;

public interface DateTimeCallback {

    /**
     * Callback called when datetime packet has been received.
     *
     * @param device   the target device.
     * @param calendar the date and time received, as {@link Calendar} object.
     */
    void onDateTimeReceived(@NonNull final BluetoothDevice device, @NonNull final Calendar calendar);
}
