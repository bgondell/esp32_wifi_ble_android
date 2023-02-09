package tk.giesecke.esp32wifible;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Activity for scanning and displaying available ESP32 devices.
 */
public class DeviceScanActivity extends ListActivity {

	private final static String TAG = "ESP32WIFI_SCAN";
	public static final String EXTRAS_DEVICE = "DEVICE";
	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

	private Intent scanBroadcastReceiverIntent = null;

	private BtDeviceListAdapter btDeviceListAdapter;
	private BluetoothAdapter mBluetoothAdapter;
	private boolean mScanning;
	private boolean hasBLE;

	private static final int REQUEST_ENABLE_BT = 1;

	// Create a BroadcastReceiver for Bluetooth scan ACTION_FOUND.
	private final BroadcastReceiver btScanResultReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Discovery has found a device. Get the BluetoothDevice
				// object and its info from the Intent.
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				String deviceName = device.getName();
				String deviceHardwareAddress = device.getAddress(); // MAC address

				if ((deviceName != null) && (deviceName.startsWith("ESP32") || deviceName.startsWith("Galaxy"))) {
					btDeviceListAdapter.addDevice(device);
					btDeviceListAdapter.notifyDataSetChanged();
					if (device.getBondState() == BluetoothDevice.BOND_NONE) {
						Log.i("BT_SCAN" , "Device not bonded. Try to bond.");
						if (android.os.Build.VERSION.SDK_INT >= 19) {
							device.createBond();
						} else {
							Toast.makeText(getApplicationContext(),R.string.manual_pair_req,Toast.LENGTH_LONG).show();
						}
					} else {
						Log.i("BT_SCAN" , "Device already bonded.");
					}
				}
				Log.i("BT_SCAN" , "Device Name: " + deviceName);
				Log.i("BT_SCAN" , "MAC: "  + deviceHardwareAddress);
				switch (device.getType()) {
					case BluetoothDevice.DEVICE_TYPE_CLASSIC:
						Log.d("BT_SCAN", "Device is Bluetooth");
						break;
					case BluetoothDevice.DEVICE_TYPE_LE:
						Log.d("BT_SCAN", "Device is BLE");
						break;
					case BluetoothDevice.DEVICE_TYPE_DUAL:
						Log.d("BT_SCAN", "Device is Bluetooth and BLE");
						break;
					default:
						Log.d("BT_SCAN", "Device type is unknown");
						break;
				}
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		android.app.ActionBar thisActionBar = getActionBar();
		if (thisActionBar != null) {
			thisActionBar.setTitle(R.string.title_devices);
		}

		if (android.os.Build.VERSION.SDK_INT >= 21) {
			getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimaryDark));
		}
		if (android.os.Build.VERSION.SDK_INT >= 18) {
			Drawable actionBarDrawable  = new ColorDrawable(getResources().getColor(R.color.colorPrimary));
			if (thisActionBar != null) {
				thisActionBar.setBackgroundDrawable(actionBarDrawable);
			}
		}

		// Use this check to determine whether Bluetooth is supported on the device.  Then you can
		// selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
			Toast.makeText(this, R.string.error_no_bluetooth, Toast.LENGTH_LONG).show();
			finish();
		}
		// Use this check to determine whether BLE is supported on the device.  Then you can
		// selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.error_no_ble, Toast.LENGTH_LONG).show();
			Log.e(TAG, getResources().getString(R.string.error_no_ble));
			hasBLE = false;
		} else {
			hasBLE = true;
		}

		// On newer Android versions it is required to get the permission of the user to
		// get the location of the device. I am not sure at all what that has to be with
		// the permission to use Bluetooth or BLE, but you need to get it anyway
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
						!= PackageManager.PERMISSION_GRANTED) {
			// Request the permission (Shortcut, I just request it without big explanation)
			ActivityCompat.requestPermissions(DeviceScanActivity.this,
							new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
		}

		// Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager =
						(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		if (bluetoothManager != null) {
			mBluetoothAdapter = bluetoothManager.getAdapter();
		}

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.error_no_bluetooth, Toast.LENGTH_LONG).show();
			Log.e(TAG, getResources().getString(R.string.error_no_bluetooth));
			finish();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Initializes list view adapter.
		btDeviceListAdapter = new BtDeviceListAdapter();
		setListAdapter(btDeviceListAdapter);

		// Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
		// fire an intent to display a dialog asking the user to grant permission to enable it.
		if(!mBluetoothAdapter.isEnabled())
		{
			// Bluetooth is off, wait with scan until user switched it on
			Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBluetooth, REQUEST_ENABLE_BT);
		} else {
			// Bluetooth is on, start scan now
			scanBT(true);
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
			Log.e(TAG, getResources().getString(R.string.error_user_reject));
			finish();
			return;
		}
		scanBT(true);
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (scanBroadcastReceiverIntent != null) {
			unregisterReceiver(btScanResultReceiver);
			scanBroadcastReceiverIntent = null;
		}
		scanBT(false);
		btDeviceListAdapter.clear();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.scan, menu);
		if (!mScanning) {
			menu.findItem(R.id.menu_stop).setVisible(false);
			menu.findItem(R.id.menu_busy).setVisible(false);
			menu.findItem(R.id.menu_scan).setVisible(true);
		} else {
			menu.findItem(R.id.menu_stop).setVisible(true);
			menu.findItem(R.id.menu_busy).setVisible(true);
			menu.findItem(R.id.menu_scan).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_scan:
				btDeviceListAdapter.clear();
				scanBT(true);
				break;
			case R.id.menu_stop:
				scanBT(false);
				break;
		}
		return true;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		final BluetoothDevice device = btDeviceListAdapter.getDevice(position);
		if (device == null) return;
		if ((device.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC)
		|| (device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL)){
			final Intent intent = new Intent(this, DeviceControlBTSerialActivity.class);
			intent.putExtra(EXTRAS_DEVICE_NAME, device.getName());
			intent.putExtra(EXTRAS_DEVICE, device);
	
			if (mBluetoothAdapter.isDiscovering()) {
				mBluetoothAdapter.cancelDiscovery();
			}
			mScanning = false;
			startActivity(intent);
		} else if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
			final Intent intent = new Intent(this, DeviceControlBLEActivity.class);
			intent.putExtra(EXTRAS_DEVICE, device);
			intent.putExtra(EXTRAS_DEVICE_NAME, device.getName());
			intent.putExtra(EXTRAS_DEVICE_ADDRESS, device.getAddress());
			if (mScanning) {
				mBluetoothAdapter.stopLeScan(mLeScanCallback);
				mScanning = false;
			}
			startActivity(intent);
		}
	}

	private void scanBT(boolean enable) {
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(btScanResultReceiver, filter);

		if (enable) {
			// Stops scanning after a pre-defined scan period.
//			mHandler.postDelayed(new Runnable() {
//				@Override
//				public void run() {
//					mScanning = false;
//					Log.i("BT_SCAN" , "BT scan stopped");
//					if (hasBLE) mBluetoothAdapter.stopLeScan(mLeScanCallback);
//					mBluetoothAdapter.cancelDiscovery();
//					if (scanBroadcastReceiverIntent != null) {
//						unregisterReceiver(btScanResultReceiver);
//						scanBroadcastReceiverIntent = null;
//					}
//					invalidateOptionsMenu();
//				}
//			}, SCAN_PERIOD);

			mScanning = true;
			Log.i("BT_SCAN" , "BT scan started");
			if (hasBLE) mBluetoothAdapter.startLeScan(mLeScanCallback);
			if (mBluetoothAdapter.isDiscovering()) {
				mBluetoothAdapter.cancelDiscovery();
			}
			mBluetoothAdapter.startDiscovery();
		} else {
			mScanning = false;
			if (hasBLE) mBluetoothAdapter.stopLeScan(mLeScanCallback);
			mBluetoothAdapter.cancelDiscovery();
			if (scanBroadcastReceiverIntent != null) {
				unregisterReceiver(btScanResultReceiver);
				scanBroadcastReceiverIntent = null;
			}
		}
		invalidateOptionsMenu();
	}

	// Adapter for holding devices found through scanning.
	class BtDeviceListAdapter extends BaseAdapter {
		private final ArrayList<BluetoothDevice> btDevices;
		private final LayoutInflater mInflator;

		BtDeviceListAdapter() {
			super();
			btDevices = new ArrayList<>();
			mInflator = DeviceScanActivity.this.getLayoutInflater();
		}

		void addDevice(BluetoothDevice device) {
			if(!btDevices.contains(device)) {
				btDevices.add(device);
			}
		}

		BluetoothDevice getDevice(int position) {
			return btDevices.get(position);
		}

		void clear() {
			btDevices.clear();
		}

		@Override
		public int getCount() {
			return btDevices.size();
		}

		@Override
		public Object getItem(int i) {
			return btDevices.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@SuppressLint("InflateParams")
		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder;
			// General ListView optimization code.
			if (view == null) {
				view = mInflator.inflate(R.layout.listitem_device, null);
				viewHolder = new ViewHolder();
				viewHolder.deviceAddress = view.findViewById(R.id.device_address);
				viewHolder.deviceName = view.findViewById(R.id.device_name);
				viewHolder.deviceType = view.findViewById(R.id.device_type);
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			BluetoothDevice device = btDevices.get(i);
			final String deviceName = device.getName();
			if (deviceName != null && deviceName.length() > 0)
				viewHolder.deviceName.setText(deviceName);
			else
				viewHolder.deviceName.setText(R.string.unknown_device);
			viewHolder.deviceAddress.setText(device.getAddress());
			switch (device.getType()) {
				case BluetoothDevice.DEVICE_TYPE_CLASSIC:
					viewHolder.deviceType.setText(getResources().getString(R.string.type_bt));
					break;
				case BluetoothDevice.DEVICE_TYPE_LE:
					viewHolder.deviceType.setText(getResources().getString(R.string.type_ble));
					break;
				case BluetoothDevice.DEVICE_TYPE_DUAL:
					viewHolder.deviceType.setText(getResources().getString(R.string.type_mix));
					break;
				default:
					viewHolder.deviceType.setText(getResources().getString(R.string.type_unknown));
					break;
			}

			return view;
		}
	}

	// Device scan callback.
	private final BluetoothAdapter.LeScanCallback mLeScanCallback =
					new BluetoothAdapter.LeScanCallback() {

						@Override
						public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									btDeviceListAdapter.addDevice(device);
									btDeviceListAdapter.notifyDataSetChanged();
								}
							});
						}
					};

	static class ViewHolder {
		TextView deviceName;
		TextView deviceAddress;
		TextView deviceType;
	}
}
