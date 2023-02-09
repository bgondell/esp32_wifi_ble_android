package tk.giesecke.esp32wifible;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Provides a high level wrapper around all the lower level bluetooth operations. Handles connecting to,
 * reestablishing connections, and reading from the serialInputStream.
 *
 * @author jpetrocik
 *
 */
class BluetoothSerial {
	private static final String BLUETOOTH_SERIAL = "BTSerial";

	public static final String BLUETOOTH_CONNECTED = "bluetooth-connection-started";

	public static final String BLUETOOTH_DISCONNECTED = "bluetooth-connection-lost";

	public static final String BLUETOOTH_FAILED = "bluetooth-connection-failed";

	boolean connected = false;

	private BluetoothDevice bluetoothDevice;

	private BluetoothSocket serialSocket;

	private InputStream serialInputStream;

	private OutputStream serialOutputStream;

	private SerialReader serialReader;

	private final MessageHandler messageHandler;

	private final Context context;

	private AsyncTask<Void, Void, BluetoothDevice> connectionTask;

	private final String devicePrefix;

	private Intent blReceiver;

	/**
	 * Listens for discount message from bluetooth system and re-establishing a connection
	 */
	private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			BluetoothDevice eventDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

			if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
				if (bluetoothDevice != null && bluetoothDevice.equals(eventDevice)){
					Log.i(BLUETOOTH_SERIAL, "Received bluetooth disconnect notice");

					//clean up any streams
					close();

					//reestablish connect
//					connect(bluetoothDevice);

					LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(BLUETOOTH_DISCONNECTED));
				}
			}
		}
	};

// --Commented out by Inspection START (4/8/2018 5:16 PM):
//	BluetoothSerial(Context context, MessageHandler messageHandler, String devicePrefix){
//		this.context = context;
//		this.messageHandler = messageHandler;
//		this.devicePrefix = devicePrefix.toUpperCase();
//	}
// --Commented out by Inspection STOP (4/8/2018 5:16 PM)

	BluetoothSerial(Context context, MessageHandler messageHandler, BluetoothDevice selectedDevice){
		this.context = context;
		this.messageHandler = messageHandler;
		this.bluetoothDevice = selectedDevice;
		this.devicePrefix = selectedDevice.getName().toUpperCase();
	}

	public void onPause() {
		if (blReceiver != null) {
			context.unregisterReceiver(bluetoothReceiver);
			blReceiver = null;
		}
	}

	public void onResume() {
		//listen for bluetooth disconnect
		IntentFilter disconnectIntent = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		blReceiver = context.registerReceiver(bluetoothReceiver, disconnectIntent);

		//reestablishes a connection is one doesn't exist
		if(!connected){
			if (bluetoothDevice != null) {
				connect(bluetoothDevice);
			} else {
				connect();
			}
		} else {
			Intent intent = new Intent(BLUETOOTH_CONNECTED);
			LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
		}
	}


	/**
	 * Initializes the bluetooth serial connections, uses the LocalBroadcastManager when
	 * connection is established
	 */
	@SuppressLint("StaticFieldLeak")
	private void connect(){

		if (connected){
			Log.e(BLUETOOTH_SERIAL,"Connection request while already connected");
			return;
		}

		if (connectionTask != null && connectionTask.getStatus()==AsyncTask.Status.RUNNING){
			Log.e(BLUETOOTH_SERIAL,"Connection request while attempting connection");
			return;
		}

		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter== null || !bluetoothAdapter.isEnabled()) {
			return;
		}

		final List<BluetoothDevice> pairedDevices = new ArrayList<>(bluetoothAdapter.getBondedDevices());
		if (pairedDevices.size() > 0) {
			bluetoothAdapter.cancelDiscovery();

			// AsyncTask to handle the establishing of a bluetooth connection
			connectionTask = new AsyncTask<Void, Void, BluetoothDevice>(){

				final int MAX_ATTEMPTS = 30;

				int attemptCounter = 0;

				@Override
				protected BluetoothDevice doInBackground(Void... params) {
					while(!isCancelled()){ //need to kill without calling onCancel

						for (BluetoothDevice device : pairedDevices) {
							if (device.getName().toUpperCase().startsWith(devicePrefix)){
								Log.i(BLUETOOTH_SERIAL, attemptCounter + ": Attempting connection to " + device.getName());

								try {

									try {
										// Standard SerialPortService ID
										UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
										serialSocket = device.createRfcommSocketToServiceRecord(uuid);
									} catch (Exception ce){
										serialSocket = connectViaReflection(device);
									}

									//setup the connect streams
									serialSocket.connect();
									serialInputStream = serialSocket.getInputStream();
									serialOutputStream = serialSocket.getOutputStream();

									connected = true;
									Log.i(BLUETOOTH_SERIAL,"Connected to " + device.getName());

									return device;
								} catch (Exception e) {
									serialSocket = null;
									serialInputStream=null;
									serialOutputStream=null;
									Log.i(BLUETOOTH_SERIAL, e.getMessage());
								}
							}
						}

						try {
							attemptCounter++;
							if (attemptCounter>MAX_ATTEMPTS)
								this.cancel(false);
							else
								Thread.sleep(1000);
						} catch (InterruptedException e) {
							break;
						}
					}

					Log.i(BLUETOOTH_SERIAL, "Stopping connection attempts");

					Intent intent = new Intent(BLUETOOTH_FAILED);
					LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

					return null;
				}

				@Override
				protected void onPostExecute(BluetoothDevice result) {
					super.onPostExecute(result);

					bluetoothDevice = result;

					//start thread responsible for reading from inputstream
					serialReader = new SerialReader();
					serialReader.start();

					//send connection message
					Intent intent = new Intent(BLUETOOTH_CONNECTED);
					LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
				}

			};
			connectionTask.execute();
		}
	}

	/**
	 * Initializes the bluetooth serial connections, uses the LocalBroadcastManager when
	 * connection is established
	 */
	@SuppressLint("StaticFieldLeak")
	private void connect(BluetoothDevice selectedDevice){

		if (connected){
			Log.e(BLUETOOTH_SERIAL,"Connection request while already connected");
			return;
		}

		if (connectionTask != null && connectionTask.getStatus()==AsyncTask.Status.RUNNING){
			Log.e(BLUETOOTH_SERIAL,"Connection request while attempting connection");
			return;
		}

		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter== null || !bluetoothAdapter.isEnabled()) {
			return;
		}

		final BluetoothDevice device = selectedDevice;
		// AsyncTask to handle the establishing of a bluetooth connection
		connectionTask = new AsyncTask<Void, Void, BluetoothDevice>(){

			final int MAX_ATTEMPTS = 30;

			int attemptCounter = 0;

			@Override
			protected BluetoothDevice doInBackground(Void... params) {
				while(!isCancelled()){ //need to kill without calling onCancel

					Log.i(BLUETOOTH_SERIAL, attemptCounter + ": Attempting connection to " + device.getName());

					try {

						try {
							// Standard SerialPortService ID
							UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
							serialSocket = device.createRfcommSocketToServiceRecord(uuid);
						} catch (Exception ce){
							serialSocket = connectViaReflection(device);
						}

						//setup the connect streams
						serialSocket.connect();
						serialInputStream = serialSocket.getInputStream();
						serialOutputStream = serialSocket.getOutputStream();

						connected = true;
						Log.i(BLUETOOTH_SERIAL,"Connected to " + device.getName());

						return device;
					} catch (Exception e) {
						serialSocket = null;
						serialInputStream=null;
						serialOutputStream=null;
						Log.i(BLUETOOTH_SERIAL, e.getMessage());
					}

					try {
						attemptCounter++;
						if (attemptCounter>MAX_ATTEMPTS)
							this.cancel(false);
						else
							Thread.sleep(1000);
					} catch (InterruptedException e) {
						break;
					}
				}

				Log.i(BLUETOOTH_SERIAL, "Stopping connection attempts");

				Intent intent = new Intent(BLUETOOTH_FAILED);
				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

				return null;
			}

			@Override
			protected void onPostExecute(BluetoothDevice result) {
				super.onPostExecute(result);

				bluetoothDevice = result;

				//start thread responsible for reading from inputstream
				serialReader = new SerialReader();
				serialReader.start();

				//send connection message
				Intent intent = new Intent(BLUETOOTH_CONNECTED);
				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
			}

		};
		connectionTask.execute();
	}

	// see: http://stackoverflow.com/questions/3397071/service-discovery-failed-exception-using-bluetooth-on-android
	private BluetoothSocket connectViaReflection(BluetoothDevice device) throws Exception {
//		@SuppressWarnings("JavaReflectionMemberAccess") Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
		@SuppressWarnings("JavaReflectionMemberAccess") Method m = device.getClass().getMethod("createRfcommSocket", int.class);
		return (BluetoothSocket) m.invoke(device, 1);
	}

	private int available() throws IOException, RuntimeException {
		if (connected)
			return serialInputStream.available();

		throw new RuntimeException("Connection lost, reconnecting now.");
	}

// --Commented out by Inspection START (4/8/2018 5:16 PM):
//	private int read() throws IOException, RuntimeException {
//		if (connected)
//			return serialInputStream.read();
//
//		throw new RuntimeException("Connection lost, reconnecting now.");
//	}
// --Commented out by Inspection STOP (4/8/2018 5:16 PM)

// --Commented out by Inspection START (4/8/2018 5:16 PM):
//	private int read(byte[] buffer) throws IOException, RuntimeException {
//		if (connected)
//			return serialInputStream.read(buffer);
//
//		throw new RuntimeException("Connection lost, reconnecting now.");
//	}
// --Commented out by Inspection STOP (4/8/2018 5:16 PM)

	private int read(byte[] buffer, int byteOffset, int byteCount) throws IOException, RuntimeException {
		if (connected)
			return serialInputStream.read(buffer, byteOffset, byteCount);

		throw new RuntimeException("Connection lost, reconnecting now.");
	}

// --Commented out by Inspection START (4/8/2018 5:17 PM):
//	public void write(byte[] buffer) throws IOException, RuntimeException {
//		if (connected) {
//			serialOutputStream.write(buffer);
//		} else {
//			throw new RuntimeException("Connection lost, reconnecting now.");
//		}
//	}
// --Commented out by Inspection STOP (4/8/2018 5:17 PM)

// --Commented out by Inspection START (4/8/2018 5:17 PM):
//	public void write(int oneByte) throws IOException, RuntimeException {
//		if (connected) {
//			serialOutputStream.write(oneByte);
//		} else {
//			throw new RuntimeException("Connection lost, reconnecting now.");
//		}
//	}
// --Commented out by Inspection STOP (4/8/2018 5:17 PM)

	public void write(byte[] buffer, int offset, int count) throws IOException, RuntimeException {
		if (connected) {
			serialOutputStream.write(buffer, offset, count);
		} else {
			throw new RuntimeException("Connection lost, reconnecting now.");
		}
	}

	private class SerialReader extends Thread {
		private static final int MAX_BYTES = 1024;

		final byte[] buffer = new byte[MAX_BYTES];

		int bufferSize = 0;

		public void run() {
			Log.i("serialReader", "Starting serial loop");
			while (!isInterrupted()) {
				try {

					/*
					 * check for some bytes, or still bytes still left in
					 * buffer
					 */
					if (available() > 0){

						int newBytes = read(buffer, bufferSize, MAX_BYTES - bufferSize);
						if (newBytes > 0)
							bufferSize += newBytes;

						Log.d(BLUETOOTH_SERIAL, "read " + newBytes);
					}

					if (bufferSize > 0) {
						int read = messageHandler.read(bufferSize, buffer);

						if (read < bufferSize) {
							// shift unread data to start of buffer
							int index = 0;
							for (int i = read; i < bufferSize; i++) {
								buffer[index++] = buffer[i];
							}
							bufferSize = index;
						} else {
							bufferSize = 0;
						}
					} else {

						try {
							Thread.sleep(10);
						} catch (InterruptedException ie) {
							break;
						}
					}
				} catch (Exception e) {
					Log.e(BLUETOOTH_SERIAL, "Error reading serial data", e);
				}
			}
			Log.i(BLUETOOTH_SERIAL, "Shutting serial loop");
		}
	}

	/**
	 * Reads from the serial buffer, processing any available messages.  Must return the number of bytes
	 * consumer from the buffer
	 *
	 * @author jpetrocik
	 *
	 */
	public interface MessageHandler {
		int read(int bufferSize, byte[] buffer);
	}

	public void close() {

		connected = false;

		if (serialReader != null) {
			serialReader.interrupt();

			try {
				serialReader.join(1000);
			} catch (InterruptedException ignored) {}
		}

		try {
			serialInputStream.close();
		} catch (Exception e) {
			Log.e(BLUETOOTH_SERIAL, "Failed releasing inputstream connection");
		}

		try {
			serialOutputStream.close();
		} catch (Exception e) {
			Log.e(BLUETOOTH_SERIAL, "Failed releasing outputstream connection");
		}

		try {
			serialSocket.close();
		} catch (Exception e) {
			Log.e(BLUETOOTH_SERIAL, "Failed closing socket");
		}

		Log.i(BLUETOOTH_SERIAL, "Released bluetooth connections");

	}

}