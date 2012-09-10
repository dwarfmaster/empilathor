/*
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   See the COPYING file for a copy of the GNU General Public License.
 *
 */

package si.fischertechnik.empilathor;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import java.util.Set;
import java.util.UUID;
import java.lang.Thread;
import java.io.IOException;
import java.io.OutputStream;

public class RoboTXBTControllerActivity extends Activity {
	private static final int SUCCEEDED = 1, FAILED = 0;
	private static final String DEVICE_NAME = "ROBO TX-419";
	//private static final String DEVICE_NAME = "ArchYvon-0";
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private boolean connexionStarted = false;
	private static final int LEFT = 0, RIGHT = 1, STOP = 2;
	private static final int FORWARD = 0, BACKWARD = 1;
	private int xDirection, yDirection;

	private BluetoothAdapter bluetoothAdapter;
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private TextView connexionLabel;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.connexion);

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(!bluetoothAdapter.isEnabled()) {
			bluetoothAdapter.enable();
			while(!bluetoothAdapter.isEnabled()) { }
		}

		sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		connexionLabel = (TextView)findViewById(R.id.connexionLabel);
	}

	protected void onResume() {
		super.onResume();
		if(connexionStarted) {
			sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	protected void onPause() {
		super.onPause();
		if(connexionStarted) {
			sensorManager.unregisterListener(sensorEventListener);
			connectedThread.sendCommand("stop");
		}
	}

	protected void onQuit() {
		connectedThread.stop();
	}

	private SensorEventListener sensorEventListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) { }

		@Override
		public void onSensorChanged(SensorEvent se) {
			updateAccel(se.values[0], se.values[1]);
		}
	};

	public void updateAccel(float x, float y) {
		if(x < -3) {
			if(xDirection != RIGHT) {
				connectedThread.sendCommand("right");
				xDirection = RIGHT;
			}
		} else if(x > 3) {
			if(xDirection != LEFT) {
				connectedThread.sendCommand("left");
				xDirection = LEFT;
			}
		} else {
			if(xDirection != STOP) {
				connectedThread.sendCommand("stop");
				xDirection = STOP;
			}
		}

		if(y < -3) {
			if(yDirection != FORWARD) {
				connectedThread.sendCommand("forward");
				yDirection = FORWARD;
			}
		} else if(y > 3) {
			if(yDirection != BACKWARD) {
				connectedThread.sendCommand("backward");
				yDirection = BACKWARD;
			}
		} else {
			if(yDirection != STOP) {
				connectedThread.sendCommand("stop");
				yDirection = STOP;
			}
		}
	}

	private Handler connexionHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == SUCCEEDED) {
				setContentView(R.layout.controls);
				connexionStarted = true;
				sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
				connectedThread.sendCommand("stop");
			}
			if(msg.what == FAILED) {
				connexionLabel.setText("Connexion échouée");
			}
		}
	};

	public void connexion(View view) {
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		for(BluetoothDevice device : pairedDevices) {
			if(device.getName().equals(DEVICE_NAME)) {
				connexionLabel.setText("Connexion en cours...");
				connectThread = new ConnectThread(device);
				connectThread.start();
			}
		}
	}

	public void up(View view) {
		connectedThread.sendCommand("up");
	}
	public void stop(View view) {
		connectedThread.sendCommand("stop");
	}
	public void down(View view) {
		connectedThread.sendCommand("down");
	}

	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			BluetoothSocket tmp = null;
			mmDevice = device;

			try {
				tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
			} catch (IOException e) { }

			mmSocket = tmp;
		}

		public void run() {
			try {
				mmSocket.connect();
			} catch (IOException connectException) {
				try {
					mmSocket.close();
				} catch (IOException closeException) { }
				connexionHandler.sendMessage(connexionHandler.obtainMessage(FAILED));	
				return;
			}
			connectedThread = new ConnectedThread(mmSocket);
			connectedThread.start();
			connexionHandler.sendMessage(connexionHandler.obtainMessage(SUCCEEDED));
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) { }
		}
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			OutputStream tmpOut = null;

			try {
				tmpOut = socket.getOutputStream();
			} catch (IOException e) { }

			mmOutStream = tmpOut;
		}

		public void run() { }

		public void sendCommand(String command) {
			try {
				command = "\rload /flash/" + command + ".bin\r";
				mmOutStream.write(command.getBytes());
				mmOutStream.write(new String("run\r").getBytes());
			} catch (IOException e) { }
		}

		public void write(String message) {
			try {
				mmOutStream.write(message.getBytes());
			} catch (IOException e) { }
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) { }
		}
	}
}
