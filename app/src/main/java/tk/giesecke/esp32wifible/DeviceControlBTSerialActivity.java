package tk.giesecke.esp32wifible;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
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

import java.io.IOException;

import static tk.giesecke.esp32wifible.DeviceScanActivity.EXTRAS_DEVICE;
import static tk.giesecke.esp32wifible.DeviceScanActivity.EXTRAS_DEVICE_NAME;
import static tk.giesecke.esp32wifible.XorCoding.xorCode;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlBTSerialActivity extends Activity {
	private final static String TAG = DeviceControlBTSerialActivity.class.getSimpleName();

	private TextView mDataField;

	private BluetoothSerial bluetoothSerial;
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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.device_control);

		final Intent intent = getIntent();
		mmDevice = intent.getParcelableExtra(EXTRAS_DEVICE);
		String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);

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
		thisActionBar.setTitle(mDeviceName);
		thisActionBar.setDisplayHomeAsUpEnabled(true);

		clearUI();

		//MessageHandler is call when bytes are read from the serial input
		bluetoothSerial = new BluetoothSerial(this, btSerialRead, mmDevice);
	}

	private final BroadcastReceiver bluetoothDisconnectReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "SerialBT disconnected");
			invalidateOptionsMenu();
		}
	};

	private final BroadcastReceiver bluetoothConnectReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "SerialBT connected");
			readCreds();
			invalidateOptionsMenu();
		}
	};

	private final BroadcastReceiver bluetoothFailedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "SerialBT init failed");
			invalidateOptionsMenu();
		}
	};

	@Override
	protected void onResume() {
		super.onResume();

		//Fired when connection is established and also fired when onResume is called if a connection is already established.
		LocalBroadcastManager.getInstance(this)
						.registerReceiver(bluetoothConnectReceiver
										, new IntentFilter(BluetoothSerial.BLUETOOTH_CONNECTED));
		//Fired when the connection is lost
		LocalBroadcastManager.getInstance(this)
						.registerReceiver(bluetoothDisconnectReceiver
										, new IntentFilter(BluetoothSerial.BLUETOOTH_DISCONNECTED));
		//Fired when connection can not be established.
		LocalBroadcastManager.getInstance(this)
						.registerReceiver(bluetoothFailedReceiver
										, new IntentFilter(BluetoothSerial.BLUETOOTH_FAILED));

		//onResume calls connect, it is safe
		//to call connect even when already connected
		bluetoothSerial.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		bluetoothSerial.onPause();
		bluetoothSerial.close();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		bluetoothSerial.close();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.control, menu);
		if ((bluetoothSerial!= null) && bluetoothSerial.connected) {
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
				bluetoothSerial.onResume();
				return true;
			case R.id.menu_disconnect:
				bluetoothSerial.onPause();
				bluetoothSerial.close();
				return true;
			case android.R.id.home:
				thisMenu.findItem(R.id.menu_connect).setActionView(null);
				firstView = false;
				bluetoothSerial.close();
				onBackPressed();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void clearUI() {
		mDataField.setText(R.string.no_data);
	}

	private void displayData(String data) {
		if (data != null) {
			mDataField.setText(data);
		}
	}

	@SuppressWarnings("unused")
	public void onClickWrite(View v){
		if (bluetoothSerial.connected) {
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
			String dataStr = wifiCreds.toString();
			int count = dataStr.length();
			byte[] data = dataStr.getBytes();

			// Decode the data
			byte[] encryptedBuffer = xorCode(mmDevice.getName(),data,count);

			try {
				bluetoothSerial.write(encryptedBuffer,0, count);
				displayData(getResources().getString(R.string.update_config));
			} catch (IOException e) {
				displayData(getResources().getString(R.string.error_sending));
				e.printStackTrace();
			}
		} else {
			displayData(getResources().getString(R.string.error_no_connection));
		}
	}

	@SuppressWarnings("unused")
	public void onClickRead(View v){
		readCreds();
	}

	private void readCreds() {
		if (bluetoothSerial.connected) {
			// Decode the data
			byte[] data = "{\"read\":\"true\"}".getBytes();
			byte[] decodedBuffer = xorCode(mmDevice.getName(),data,data.length);
			try {
				bluetoothSerial.write(decodedBuffer,0, decodedBuffer.length);
				displayData(getResources().getString(R.string.get_config));
			} catch (IOException e) {
				displayData(getResources().getString(R.string.error_sending));
				e.printStackTrace();
			}
		} else {
			displayData(getResources().getString(R.string.error_no_connection));
		}
	}

	@SuppressWarnings("unused")
	public void onClickErase(View v){
		if ((bluetoothSerial != null) && bluetoothSerial.connected) {
			// Decode the data
			byte[] data = "{\"erase\":\"true\"}".getBytes();
			byte[] decodedBuffer = xorCode(mmDevice.getName(),data,data.length);
			try {
				bluetoothSerial.write(decodedBuffer,0, decodedBuffer.length);
				displayData(getResources().getString(R.string.erase_config));
			} catch (IOException e) {
				displayData(getResources().getString(R.string.error_sending));
				e.printStackTrace();
			}
		} else {
			displayData(getResources().getString(R.string.error_no_connection));
		}
	}

	@SuppressWarnings("unused")
	public void onClickReset(View v){
		if ((bluetoothSerial != null) && bluetoothSerial.connected) {
			// Decode the data
			byte[] data = "{\"reset\":\"true\"}".getBytes();
			byte[] decodedBuffer = xorCode(mmDevice.getName(),data,data.length);
			try {
				bluetoothSerial.write(decodedBuffer,0, decodedBuffer.length);
				displayData(getResources().getString(R.string.reset_device));
			} catch (IOException e) {
				displayData(getResources().getString(R.string.error_sending));
				e.printStackTrace();
			}
		} else {
			displayData(getResources().getString(R.string.error_no_connection));
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

	private final BluetoothSerial.MessageHandler btSerialRead = new BluetoothSerial.MessageHandler() {
		@Override
		public int read(final int bufferSize, byte[] buffer) {
			final byte[] readBuffer = new byte[bufferSize];
			System.arraycopy(buffer, 0, readBuffer, 0, bufferSize);
			final String data;
			data = new String(readBuffer);
			// Rest has to be done on UI thread
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// Display the received data
					thisMenu.findItem(R.id.menu_connect).setActionView(null);
					// Encode the data
					byte[] encodedData = xorCode(mmDevice.getName(), readBuffer, bufferSize);
					String finalData = new String(encodedData);

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
			});

			return bufferSize;
		}
	};
}
