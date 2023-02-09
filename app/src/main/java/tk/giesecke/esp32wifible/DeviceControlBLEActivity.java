package tk.giesecke.esp32wifible;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import static tk.giesecke.esp32wifible.DeviceScanActivity.EXTRAS_DEVICE;
import static tk.giesecke.esp32wifible.DeviceScanActivity.EXTRAS_DEVICE_ADDRESS;
import static tk.giesecke.esp32wifible.DeviceScanActivity.EXTRAS_DEVICE_NAME;
import static tk.giesecke.esp32wifible.XorCoding.xorCode;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlBLEActivity extends Activity {
	private final static String TAG = "Porta4.0_BLE_CTRL";

	private TextView mDataField;
	private String mDeviceAddress;
	private BluetoothLeService mBluetoothLeService;
	private boolean mConnected = false;
	private BluetoothDevice mmDevice;

	private String ID = "";
	private String ssid = "";
	private String password = "";
	private String apn = "";
	private String gprsUser = "";
	private String gprsPass = "";
	private Double mVperV = 0.0;
	private Double mVperA = 0.0;

	private Boolean USE_WIFI = true;

	private EditText IDET;
	private EditText ssidET;
	private EditText passwordET;
	private EditText apnET;
	private EditText gprsUserET;
	private EditText gprsPassET;
	private EditText mVperVET;
	private EditText mVperAET;

	private Menu thisMenu;

	private Boolean firstView = true;

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
// Automatically connects to the device upon successful start-up initialization.
			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device.This can be a result of read
	//or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action != null) {
				switch (action) {
					case BluetoothLeService.ACTION_GATT_CONNECTED:
						mConnected = true;
						invalidateOptionsMenu();
						break;
					case BluetoothLeService.ACTION_GATT_DISCONNECTED:
						Bundle extras = intent.getExtras();
						int result = 0;
						if (extras != null) {
							result = extras.getInt("status");
						}
						if (result == 133) { // connection failed!!!!
							Log.e(TAG, "Server connection failed");
							Toast.makeText(getApplicationContext()
											, "Server connection failed\nRetry to connect again\nOr try to reset the ESP32"
											, Toast.LENGTH_LONG).show();
						}
						mConnected = false;
						invalidateOptionsMenu();
						clearUI();
						break;
					case BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
						Log.d(TAG, "Discovery finished");
						if(mBluetoothLeService != null) {
							mBluetoothLeService.readCustomCharacteristic();
						}
					case BluetoothLeService.ACTION_DATA_AVAILABLE:
						thisMenu.findItem(R.id.menu_connect).setActionView(null);
						String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
						if (data != null) {
							// Decode the data
							byte[] decodedData = xorCode(mmDevice.getName(),data.getBytes(),data.length());
							String finalData = new String(decodedData);

							displayData("Received:\n--\n" + data + "\n--\n" + finalData);

							// Get stored WiFi credentials from the received data
							JSONObject receivedConfigJSON;
							try {
								receivedConfigJSON = new JSONObject(finalData);
								if (receivedConfigJSON.has("ID")) {
									ID = receivedConfigJSON.getString("ID");
									IDET.setText(ID);
								}
								if (receivedConfigJSON.has("ssid")) {
									ssid = receivedConfigJSON.getString("ssid");
									ssidET.setText(ssid);
								}
								if (receivedConfigJSON.has("password")) {
									password = receivedConfigJSON.getString("password");
									passwordET.setText(password);
								}
								if (receivedConfigJSON.has("apn")) {
									apn = receivedConfigJSON.getString("apn");
									apnET.setText(apn);
								}
								if (receivedConfigJSON.has("gprsUser")) {
									gprsUser = receivedConfigJSON.getString("gprsUser");
									gprsUserET.setText(gprsUser);
								}
								if (receivedConfigJSON.has("gprsPass")) {
									gprsPass = receivedConfigJSON.getString("gprsPass");
									gprsPassET.setText(gprsPass);
								}
								if (receivedConfigJSON.has("mVperV")) {
									mVperV = receivedConfigJSON.getDouble("mVperV");
									mVperVET.setText(String.valueOf(mVperV));
								}
								if (receivedConfigJSON.has("mVperA")) {
									mVperA = receivedConfigJSON.getDouble("mVperA");
									mVperAET.setText(String.valueOf(mVperA));
								}
								if (receivedConfigJSON.has("USE_WIFI")) {
									USE_WIFI = receivedConfigJSON.getBoolean("USE_WIFI");
									TextView chgHdr;
									EditText chgEt;
									Switch enaUSE_WIFI = findViewById(R.id.USE_WIFISelector);
									enaUSE_WIFI.setChecked(USE_WIFI);
								}
							} catch (JSONException e) {
								e.printStackTrace();
							}
						}
						break;
				}
			}
		}
	};

	private void clearUI() {
		mDataField.setText(R.string.no_data);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.device_control);

		final Intent intent = getIntent();
		String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
		mmDevice = intent.getParcelableExtra(EXTRAS_DEVICE);

		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		android.app.ActionBar thisActionBar = getActionBar();
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			getWindow().setStatusBarColor(getResources().getColor(R.color.colorSecondaryDark));
		}
		if (android.os.Build.VERSION.SDK_INT >= 18) {
			Drawable actionBarDrawable  = new ColorDrawable(getResources().getColor(R.color.colorSecondary));
			if (thisActionBar != null) {
				thisActionBar.setBackgroundDrawable(actionBarDrawable);
			}
		}

		// Sets up UI references.
		mDataField = findViewById(R.id.data_value);
		IDET = findViewById(R.id.ID);
		ssidET = findViewById(R.id.ssid);
		passwordET = findViewById(R.id.password);
		apnET = findViewById(R.id.apn);
		gprsUserET = findViewById(R.id.gprsUser);
		gprsPassET = findViewById(R.id.gprsPass);
		mVperVET = findViewById(R.id.mVperV);
		mVperAET = findViewById(R.id.mVperA);

		//noinspection ConstantConditions
		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect request result=" + result);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.control, menu);
		if (mConnected) {
			menu.findItem(R.id.menu_connect).setVisible(false);
			menu.findItem(R.id.menu_disconnect).setVisible(true);
		} else {
			menu.findItem(R.id.menu_connect).setVisible(true);
			if (firstView) {
				menu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
				firstView = false;
			} else {
				menu.findItem(R.id.menu_connect).setActionView(null);
			}
			menu.findItem(R.id.menu_disconnect).setVisible(false);
		}
		thisMenu = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.menu_connect:
				thisMenu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
				mBluetoothLeService.connect(mDeviceAddress);
				return true;
			case R.id.menu_disconnect:
				mBluetoothLeService.disconnect();
				thisMenu.findItem(R.id.menu_connect).setActionView(null);
				return true;
			case android.R.id.home:
				thisMenu.findItem(R.id.menu_connect).setActionView(null);
				firstView = false;
				onBackPressed();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void displayData(String data) {
		if (data != null) {
			mDataField.setText(data);
		}
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}

	@SuppressWarnings("unused")
	public void onClickWrite(View v){
		if(mBluetoothLeService != null) {

			// Update credentials with last edit text values
			ID = IDET.getText().toString();
			ssid = ssidET.getText().toString();
			password = passwordET.getText().toString();
			apn = apnET.getText().toString();
			gprsUser = gprsUserET.getText().toString();
			gprsPass = gprsPassET.getText().toString();
			if (mVperVET.getText().toString().equals("")) mVperV = 0.0;
			else mVperV = Double.valueOf(mVperVET.getText().toString());
			if (mVperAET.getText().toString().equals("")) mVperA = 0.0;
			else mVperA = Double.valueOf(mVperAET.getText().toString());

			// Create JSON object
			JSONObject wifiCreds = new JSONObject();
			try {
				wifiCreds.put("USE_WIFI", USE_WIFI);
				if (ID.equals("")) {
					Toast.makeText(getApplicationContext()
							, "Missing ID entry"
							, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("ID", ID);
				}
				if (ssid.equals("")) {
					Toast.makeText(getApplicationContext()
							, "Missing SSID entry"
							, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("ssid", ssid);
				}
				if (password.equals("")) {
					Toast.makeText(getApplicationContext()
							, "Missing password entry"
							, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("password", password);
				}
				if (apn.equals("")) {
					Toast.makeText(getApplicationContext()
							, "Missing apn entry"
							, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("apn", apn);
				}
				if (gprsUser.equals("")) {
					Toast.makeText(getApplicationContext()
							, "Missing gprsUser entry"
							, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("gprsUser", gprsUser);
				}if (gprsPass.equals("")) {
					Toast.makeText(getApplicationContext()
							, "Missing gprsPass entry"
							, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("gprsPass", gprsPass);
				}if (mVperV == 0.0) {
					Toast.makeText(getApplicationContext()
							, "Missing mVperV entry"
							, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("mVperV", mVperV);
				}if (mVperA == 0.0) {
					Toast.makeText(getApplicationContext()
							, "Missing mvPerA entry"
							, Toast.LENGTH_LONG).show();
					displayData(getResources().getString(R.string.error_credentials));
					return;
				} else {
					wifiCreds.put("mVperA", mVperA);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
			byte[] decodedData = xorCode(mmDevice.getName()
							,wifiCreds.toString().getBytes()
							,wifiCreds.toString().length());
			mBluetoothLeService.writeCustomCharacteristic(new String(decodedData));
			displayData(getResources().getString(R.string.update_config));
		}
	}

	@SuppressWarnings("unused")
	public void onClickRead(View v){
		thisMenu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
		if(mBluetoothLeService != null) {
			mBluetoothLeService.readCustomCharacteristic();
		}
	}

	@SuppressWarnings("unused")
	public void onClickErase(View v){
		thisMenu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
		if(mBluetoothLeService != null) {
			// Create JSON object
			JSONObject wifiCreds = new JSONObject();
			try {
				wifiCreds.put("erase", true);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			byte[] decodedData = xorCode(mmDevice.getName()
							,wifiCreds.toString().getBytes()
							,wifiCreds.toString().length());
			mBluetoothLeService.writeCustomCharacteristic(new String(decodedData));
			displayData(getResources().getString(R.string.erase_config));
		}
	}

	@SuppressWarnings("unused")
	public void onClickReset(View v){
		thisMenu.findItem(R.id.menu_connect).setActionView(R.layout.progress_bar);
		if(mBluetoothLeService != null) {
			// Create JSON object
			JSONObject wifiCreds = new JSONObject();
			try {
				wifiCreds.put("reset", true);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			byte[] decodedData = xorCode(mmDevice.getName()
							,wifiCreds.toString().getBytes()
							,wifiCreds.toString().length());
			mBluetoothLeService.writeCustomCharacteristic(new String(decodedData));
			displayData(getResources().getString(R.string.erase_config));
		}
	}

	@SuppressWarnings("unused")
	public void onClickSwitch(View v){
		TextView chgHdr;
		EditText chgEt;
		Switch enaUSE_WIFI = findViewById(R.id.USE_WIFISelector);
		if (enaUSE_WIFI.isChecked()) {
			USE_WIFI = true;
		} else {
			USE_WIFI = false;
		}
	}
}
