/*
 * EmpilaThor : Application de contrôle pour le robot fischertechnik du 
 * projet EmpilaThor de l'année de seconde SI en 2011/2012
 * 
 * Copyright (C) 2012 Donato Pablo
 * Copyright (C) 2012 Chabassier Luc
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
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
import java.util.Set;
import java.util.UUID;
import java.lang.Thread;
import java.io.IOException;
import java.io.OutputStream;

public class EmpilaThorActivity extends Activity {
	// LES CONSTANTES
	private static final int SUCCEEDED = 1, FAILED = 0;
	private static final String DEVICE_NAME = "ArchYvon-0"; // Le nom du périphérique bluetooth
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Sert à identifier l'application lors de la connection bluetooth
	private static final int LEFT = 0, RIGHT = 1, STOP = 2; // Les variables de direction : X ...
	private static final int FORWARD = 0, BACKWARD = 1; // ... et Y
	
	// LES VARIABLES
	private boolean connexionStarted = false;
	private int xDirection, yDirection; // la direction actuelle

	// L'interface android
	private BluetoothAdapter bluetoothAdapter;
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private TextView connexionLabel;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;

	// LES MÉTHODES
	// Cette méthode est appellée par android au lancement du programme
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.connexion);

		// On récupère l'accès au bluetooth
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(!bluetoothAdapter.isEnabled()) { // On l'active si ce n'est pas déjà fait
			bluetoothAdapter.enable();
			while(!bluetoothAdapter.isEnabled()) { }
		}

		sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE); // On récupère le gestionnaires des capteurs
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); // À partir duquel on accède à l'accéléromètre

		connexionLabel = (TextView)findViewById(R.id.connexionLabel); // On récupère le texte de connection
	}

	// Appelée quand l'application sort de pause
	protected void onResume() {
        super.onResume();
		if(connexionStarted) { // On relance l'utilisation de l'accéléromètre si on est connecté
        	sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
    }

	// Appelée quand l'application entre en pause
    protected void onPause() {
        super.onPause();
		if(connexionStarted) { // On désactive l'accéléromètre
        	sensorManager.unregisterListener(sensorEventListener);
		}
    }

	private SensorEventListener sensorEventListener = new SensorEventListener() { // Classe captant les changement de l'accéléromètre
		public void onAccuracyChanged(Sensor sensor, int accuracy) { }

		public void onSensorChanged(SensorEvent se) {
			updateAccel(se.values[0], se.values[1]);
		}
	};

	public void updateAccel(float x, float y) { // Envoie des commandes adaptées en fonction de l'état de l'accéléromètre
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

	private Handler connexionHandler = new Handler() { // Récupère la réussite ou l'échec de la connection bluetooth
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == SUCCEEDED) { // Si ça réussit
				setContentView(R.layout.controls); // On change d'interface
				connexionStarted = true; // On indique le début de la connection
				sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL); // On active l'accéléromètre
			}
			if(msg.what == FAILED) { // Si ça échoue
				connexionLabel.setText("Connexion échouée"); // On l'indique
			}
		}
	};

	public void connexion(View view) { // Fonction de callback appelée par le boutton "connection" de l'interface
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices(); // On récupère la liste des périphériques bluetooth
		for(BluetoothDevice device : pairedDevices) { // On cherche s'il y en a un qui correspond à celui qu'on cherche (DEVICE_NAME)
			if(device.getName().equals(DEVICE_NAME)) {
				connexionLabel.setText("Connexion en cours..."); // On affiche la tentative de connection
				connectThread = new ConnectThread(device); // On crée un thread de connection
				connectThread.start(); // On le lance
			}
		}
	}

	// Les trois méthodes suivantes sont des callback pour les bouttons de l'interface de contrôle
	public void up(View view) {
		connectedThread.sendCommand("up");
	}
	public void stop(View view) {
		connectedThread.sendCommand("stop");
	}
	public void down(View view) {
		connectedThread.sendCommand("down");
	}

	private class ConnectThread extends Thread { // Thread de connection
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			BluetoothSocket tmp = null;
			mmDevice = device;

			try {
				tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID); // On crée le socket bluetooth
			} catch (IOException e) { }

			mmSocket = tmp;
		}

		public void run() { // Au lancement du thread
			try {
				mmSocket.connect(); // On lance la connection
			} catch (IOException connectException) { // En cas d'erreur
				try {
					mmSocket.close(); // On ferme le socket
				} catch (IOException closeException) { }
				connexionHandler.sendMessage(connexionHandler.obtainMessage(FAILED)); // On envoie le message d'erreur	
				return;
			}
			connectedThread = new ConnectedThread(mmSocket); // On crée un thread de contrôle avec le socket bluetooth
			connectedThread.start(); // On le lance
			connexionHandler.sendMessage(connexionHandler.obtainMessage(SUCCEEDED)); // On envoie le message de réussite
		}

		public void cancel() { // À la fermeture de thread
			try {
				mmSocket.close(); // On ferme le socket
			} catch (IOException e) { }
		}
	}

	private class ConnectedThread extends Thread { // Thread de contrôle
		private final BluetoothSocket mmSocket;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			OutputStream tmpOut = null;

			try {
				tmpOut = socket.getOutputStream(); // Ouvre un flux sur le socket passé en paramètre
			} catch (IOException e) { }

			mmOutStream = tmpOut;
		}

		public void run() { }

		public void sendCommand(String command) { // Envoie une commande via le socket
			try {
				command = "\rload /flash/" + command + ".bin\r"; // On adapte la commande
				mmOutStream.write(command.getBytes());
				mmOutStream.write(new String("run\r").getBytes());
			} catch (IOException e) { }
		}

		public void write(String message) { // Sert à envoyer une commande brute, sans traitement
			try {
				mmOutStream.write(message.getBytes());
			} catch (IOException e) { }
		}

		public void cancel() { // À la fin du thread
			try {
				mmSocket.close(); // On ferme le socket
			} catch (IOException e) { }
		}
	}
}
