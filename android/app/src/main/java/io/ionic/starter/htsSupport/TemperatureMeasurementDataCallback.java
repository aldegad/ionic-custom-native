package io.ionic.starter.htsSupport;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.Calendar;

import no.nordicsemi.android.ble.callback.profile.ProfileReadResponse;
import no.nordicsemi.android.ble.data.Data;

public abstract class TemperatureMeasurementDataCallback extends ProfileReadResponse implements TemperatureMeasurementCallback {

    public TemperatureMeasurementDataCallback() {
        // empty
    }

    protected TemperatureMeasurementDataCallback(final Parcel in) {
        super(in);
    }

    @Override
    public void onDataReceived(@NonNull final BluetoothDevice device, @NonNull final Data data) {
        super.onDataReceived(device, data);

        if (data.size() < 5) {
            onInvalidDataReceived(device, data);
            return;
        }

        int offset = 0;
        final int flags = data.getIntValue(Data.FORMAT_UINT8, offset);
        final int unit = (flags & 0x01) == UNIT_C ? UNIT_C : UNIT_F;
        final boolean timestampPresent = (flags & 0x02) != 0;
        final boolean temperatureTypePresent = (flags & 0x04) != 0;
        offset += 1;

        if (data.size() < 5 + (timestampPresent ? 7 : 0) + (temperatureTypePresent ? 1 : 0)) {
            onInvalidDataReceived(device, data);
            return;
        }

        final float temperature = data.getFloatValue(Data.FORMAT_FLOAT, 1);
        offset += 4;

        Calendar calendar = null;
        if (timestampPresent) {
            calendar = DateTimeDataCallback.readDateTime(data, offset);
            offset += 7;
        }

        Integer type = null;
        if (temperatureTypePresent) {
            type = data.getIntValue(Data.FORMAT_UINT8, offset);
            // offset += 1;
        }

        onTemperatureMeasurementReceived(device, temperature, unit, calendar, type);
    }
}

