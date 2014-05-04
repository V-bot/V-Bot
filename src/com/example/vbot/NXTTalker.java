package com.example.vbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class NXTTalker {

	public static final int STATE_NONE = 0;
	public static final int STATE_CONNECTING = 1;
	public static final int STATE_CONNECTED = 2;

	private int mState;
	private Handler mHandler;
	private BluetoothAdapter mAdapter;

	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;

	public NXTTalker(Handler handler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mHandler = handler;
		setState(STATE_NONE);
	}

	private synchronized void setState(int state) {
		mState = state;
		if (mHandler != null) {
			mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1)
					.sendToTarget();
		} else {
		}
	}

	public synchronized int getState() {
		return mState;
	}

	public synchronized void setHandler(Handler handler) {
		mHandler = handler;
	}

	private void toast(String text) {
		if (mHandler != null) {
			Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
			Bundle bundle = new Bundle();
			bundle.putString(MainActivity.TOAST, text);
			msg.setData(bundle);
			mHandler.sendMessage(msg);
		} else {
		}
	}

	public synchronized void connect(BluetoothDevice device) {

		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(STATE_CONNECTING);
	}

	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice device) {
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		setState(STATE_CONNECTED);
	}

	public synchronized void stop() {
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		setState(STATE_NONE);
	}

	private void connectionFailed() {
		setState(STATE_NONE);
	}

	private void connectionLost() {
		setState(STATE_NONE);
	}

	public void motors(byte l, byte r, boolean speedReg, boolean motorSync) {
		byte[] data = { 0x0c, 0x00, (byte) 0x80, 0x04, 0x02, 0x32, 0x07, 0x00,
				0x00, 0x20, 0x00, 0x00, 0x00, 0x00, 0x0c, 0x00, (byte) 0x80,
				0x04, 0x01, 0x32, 0x07, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00,
				0x00 };

		data[5] = l;
		data[19] = r;
		if (speedReg) {
			data[7] |= 0x01;
			data[21] |= 0x01;
		}
		if (motorSync) {
			data[7] |= 0x02;
			data[21] |= 0x02;
		}
		write(data);
	}

	public void motor(int motor, byte power, boolean speedReg, boolean motorSync) {
		byte[] data = { 0x0c, 0x00, (byte) 0x80, 0x04, 0x02, 0x32, 0x07, 0x00,
				0x00, 0x20, 0x00, 0x00, 0x00, 0x00 };

		if (motor == 0) {
			data[4] = 0x02;
		} else {
			data[4] = 0x01;
		}
		data[5] = power;
		if (speedReg) {
			data[7] |= 0x01;
		}
		if (motorSync) {
			data[7] |= 0x02;
		}
		write(data);
	}

	public void motors3(byte l, byte r, byte action, boolean speedReg,
			boolean motorSync) {
		byte[] data = { 0x0c, 0x00, (byte) 0x80, 0x04, 0x02, 0x32, 0x07, 0x00,
				0x00, 0x20, 0x00, 0x00, 0x00, 0x00, 0x0c, 0x00, (byte) 0x80,
				0x04, 0x01, 0x32, 0x07, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00,
				0x00, 0x0c, 0x00, (byte) 0x80, 0x04, 0x00, 0x32, 0x07, 0x00,
				0x00, 0x20, 0x00, 0x00, 0x00, 0x00 };

		data[5] = l;
		data[19] = r;
		data[33] = action;
		if (speedReg) {
			data[7] |= 0x01;
			data[21] |= 0x01;
		}
		if (motorSync) {
			data[7] |= 0x02;
			data[21] |= 0x02;
		}
		write(data);
	}

	private void write(byte[] out) {
		ConnectedThread r;
		synchronized (this) {
			if (mState != STATE_CONNECTED) {
				return;
			}
			r = mConnectedThread;
		}
		r.write(out);
	}

	private class ConnectThread extends Thread {
		private BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;
		}

		@Override
		public void run() {
			setName("ConnectThread");
			mAdapter.cancelDiscovery();

			try {
				mmSocket = mmDevice.createRfcommSocketToServiceRecord(UUID
						.fromString("00001101-0000-1000-8000-00805F9B34FB"));
				mmSocket.connect();
			} catch (IOException e) {
				e.printStackTrace();
				try {
					// This is a workaround that reportedly helps on some older
					// devices like HTC Desire, where using
					// the standard createRfcommSocketToServiceRecord() method
					// always causes connect() to fail.
					Method method = mmDevice.getClass().getMethod(
							"createRfcommSocket", new Class[] { int.class });
					mmSocket = (BluetoothSocket) method.invoke(mmDevice,
							Integer.valueOf(1));
					mmSocket.connect();
				} catch (Exception e1) {
					e1.printStackTrace();
					connectionFailed();
					try {
						mmSocket.close();
					} catch (IOException e2) {
						e2.printStackTrace();
					}
					return;
				}
			}

			synchronized (NXTTalker.this) {
				mConnectThread = null;
			}

			connected(mmSocket, mmDevice);
		}

		public void cancel() {
			try {
				if (mmSocket != null) {
					mmSocket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				e.printStackTrace();
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		@Override
		public void run() {
			byte[] buffer = new byte[1024];
			int bytes;

			while (true) {
				try {
					bytes = mmInStream.read(buffer);
				} catch (IOException e) {
					e.printStackTrace();
					connectionLost();
					break;
				}
			}
		}

		public void write(byte[] buffer) {
			try {
				mmOutStream.write(buffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}