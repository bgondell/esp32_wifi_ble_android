package tk.giesecke.esp32wifible;

import android.app.Service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.polidea.rxandroidble2.internal.util.BleConnectionCompat;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
	private final static String TAG = "ESP32WIFI_BLE";

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;

	private final String serviceUUID = "0000aaaa-ead2-11e7-80c1-9a214cf093ae";
	private final String wifiUUID = "00005555-ead2-11e7-80c1-9a214cf093ae";

	public final static String ACTION_GATT_CONNECTED =
					"ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED =
					"ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED =
					"ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_AVAILABLE =
					"ACTION_DATA_AVAILABLE";
	public final static String EXTRA_DATA =
					"EXTRA_DATA";

	// Implements callback methods for GATT events that the app cares about.For example,
	// connection change and services discovered.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			String intentAction;
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				intentAction = ACTION_GATT_CONNECTED;
				broadcastUpdate(intentAction, status);
				Log.i(TAG, "Connected to GATT server.");
				// Attempts to discover services after successful connection.
				Log.i(TAG, "Attempting to start service discovery:" +
								mBluetoothGatt.discoverServices());

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				Log.i(TAG, "Disconnected from GATT server. with status " + Integer.toString(status));
				mBluetoothGatt.close();
				mBluetoothGatt = null;
				broadcastUpdate(intentAction, status);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, status);

				StringBuilder serviceDiscovery;

				List<BluetoothGattService> gattServices = mBluetoothGatt.getServices();
				Log.e("onServicesDiscovered", "Services count: "+gattServices.size());
				serviceDiscovery = new StringBuilder("Found " + gattServices.size() + " services\n");
				for (BluetoothGattService gattService : gattServices) {
					String serviceUUID = gattService.getUuid().toString();
					Log.e("onServicesDiscovered", "Service uuid "+serviceUUID);
					serviceDiscovery.append("Service uuid ").append(serviceUUID).append("\n");
				}
				broadcastUpdate(serviceDiscovery.toString());

			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
																		 BluetoothGattCharacteristic characteristic,
																		 int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(characteristic, status);
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
																			BluetoothGattCharacteristic characteristic,
																			int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				readCustomCharacteristic();
			}
		}
		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
																				BluetoothGattCharacteristic characteristic) {
			broadcastUpdate(characteristic, 0);
		}
	};

	private void broadcastUpdate(final String action, int status) {
		final Intent intent = new Intent(action);
		intent.putExtra("status", status);
		sendBroadcast(intent);
	}

	private void broadcastUpdate(String data) {
		final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
		intent.putExtra("status", 0);
		intent.putExtra(EXTRA_DATA, data);
		sendBroadcast(intent);
	}

	private void broadcastUpdate(final BluetoothGattCharacteristic characteristic, int status) {
		final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
		intent.putExtra("status", status);

		// Write the data as received for debug purposes
		final byte[] data = characteristic.getValue();
		if (data != null && data.length > 0) {
			intent.putExtra(EXTRA_DATA, new String(data));
		} else {
			intent.putExtra(EXTRA_DATA, getResources().getString(R.string.empty_response));
		}
		sendBroadcast(intent);
	}

	public class LocalBinder extends Binder {
		BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
// After using a given device, you should make sure that BluetoothGatt.close() is called
// such that resources are cleaned up properly.In this particular example, close() is
// invoked when the UI is disconnected from the Service.
		close();
		return super.onUnbind(intent);
	}

	private final IBinder mBinder = new LocalBinder();

	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 *
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
// For API level 18 and above, get a reference to BluetoothAdapter through
// BluetoothManager.
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return true;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return true;
		}

		return false;
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 * @param address The device address of the destination device.
	 *
	 * @return Return true if the connection is initiated successfully. The connection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

// Previously connected device.Try to reconnect.
		if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
						&& mBluetoothGatt != null) {
			Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
			return mBluetoothGatt.connect();
		}

		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.Unable to connect.");
			return false;
		}
// We want to directly connect to the device, so we are setting the autoConnect
// parameter to false.
//		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		mBluetoothGatt = (new BleConnectionCompat(this)).connectGatt(device, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;
		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The disconnection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure resources are
	 * released properly.
	 */
	private void close() {
		if (mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	public void readCustomCharacteristic() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		/*check if the service is available on the device*/
		BluetoothGattService mCustomService = mBluetoothGatt.
						getService(UUID.fromString(serviceUUID));
		if(mCustomService == null){
			Log.w(TAG, "Custom BLE Service not found");
			return;
		}
		/*get the read characteristic from the service*/
		BluetoothGattCharacteristic mReadCharacteristic = mCustomService.
						getCharacteristic(UUID.fromString(wifiUUID));
		if(!mBluetoothGatt.readCharacteristic(mReadCharacteristic)){
			Log.w(TAG, "Failed to read characteristic");
			return;
		}
//		Log.i(TAG, mReadCharacteristic.getStringValue(0));
		Log.i(TAG, "Trying to log received value");
	}

	public void writeCustomCharacteristic(String wifiCredentials) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		/*check if the service is available on the device*/
		BluetoothGattService mCustomService = mBluetoothGatt
						.getService(UUID.fromString(serviceUUID));
		if(mCustomService == null){
			Log.w(TAG, "Custom BLE Service not found");
			return;
		}
		/*get the read characteristic from the service*/
		BluetoothGattCharacteristic mWriteCharacteristic = mCustomService
						.getCharacteristic(UUID.fromString(wifiUUID));
		mWriteCharacteristic.setValue(wifiCredentials);
		if(!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)){
			Log.w(TAG, "Failed to write characteristic");
		}
	}
}
