/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.ionic.starter;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.Logger;

/**
 * HTSManager class performs BluetoothGatt operations for connection, service discovery, enabling
 * indication and reading characteristics. All operations required to connect to device with BLE HT
 * Service and reading health thermometer values are performed here.
 * HTSActivity implements HTSManagerCallbacks in order to receive callbacks of BluetoothGatt operations.
 */

public class HTSManager extends BleManager<HTSManagerCallbacks> {

	private final String TAG = "APP_HTSManager";

	/** Health Thermometer service UUID */
	public final static UUID HT_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
	/** Health Thermometer Measurement characteristic UUID */
	private static final UUID HT_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb");

	private BluetoothGattCharacteristic mHTCharacteristic;

	// Supports nRF Logger
	private ILogSession mLogSession;
	private UUID mServiceUuid;

	private BluetoothDevice mBleDevice;
	private String mDeviceName;


	public HTSManager(final Context context, BluetoothDevice bleDevice, String deviceName, String uuid) {
		super(context);
		mBleDevice = bleDevice;
		mDeviceName = deviceName;
		mServiceUuid = UUID.fromString(uuid);
		Log.d(TAG, "HTSManger created for " + mDeviceName);
	}

	@NonNull
	@Override
	protected BleManagerGattCallback getGattCallback() {
		return mGattCallback;
	}

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery,
	 * receiving indication, etc..
	 */
	private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {
		@Override
		protected void initialize() {
			Log.d(TAG, "Initialising BLE device.");
			super.initialize();
			setIndicationCallback(mHTCharacteristic);
//					.with(new TemperatureMeasurementDataCallback() {
//						@Override
//						public void onDataReceived(@NonNull final BluetoothDevice device, @NonNull final Data data) {
//							String msg = "Received this: \"" + TemperatureMeasurementParser.parse(data) + "\"";
//							log(LogContract.Log.Level.APPLICATION, msg);
//							Log.d(TAG, msg);
//							super.onDataReceived(device, data);
//						}
//
//						@Override
//						public void onTemperatureMeasurementReceived(@NonNull final BluetoothDevice device,
//																	 final float temperature, final int unit,
//																	 @Nullable final Calendar calendar,
//																	 @Nullable final Integer type) {
//							Log.d(TAG, "Temperature reading sent to service.");
//							mCallbacks.onTemperatureMeasurementReceived(device, temperature, unit, calendar, type);
//						}
//					});
			enableIndications(mHTCharacteristic).enqueue();
		}

		@Override
		protected boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
			final BluetoothGattService service = gatt.getService(HT_SERVICE_UUID);
			if (service != null) {
				mHTCharacteristic = service.getCharacteristic(HT_MEASUREMENT_CHARACTERISTIC_UUID);
			}
			return mHTCharacteristic != null;
		}

		@Override
		protected void onDeviceDisconnected() {
			//super.onDeviceDisconnected();
			mHTCharacteristic = null;
			Log.v(TAG, "in onDeviceDisconnected()");
		}
	};


	/********************************************************************************************
	 *
	 * nRF Logger
	 *
	 ********************************************************************************************/


	/**
	 * Called by the service that creates the SavedHTSManager object.
	 * @param session - an ILogSession previously created by the service
	 */

	public void setLogger(ILogSession session) {
		mLogSession = session;
	}

	/**
	 * Called by this class and also by BleManager
	 * @param priority - one of these:
	 * DEBUG = 0;
	 * VERBOSE = 1;
	 * INFO = 5;
	 * APPLICATION = 10;
	 * WARNING = 15;
	 * ERROR = 20;
	 *
	 * @param msg - the message to write
	 */

	@Override
	public void log(int priority, String msg) {
		Logger.log(mLogSession, LogContract.Log.Level.fromPriority(priority), msg);
	}
}
