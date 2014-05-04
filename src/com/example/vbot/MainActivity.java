package com.example.vbot;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements
		OnSharedPreferenceChangeListener {

	private boolean NO_BT = false;

	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_CONNECT_DEVICE = 2;
	private static final int REQUEST_SETTINGS = 3;

	public static final int MESSAGE_TOAST = 1;
	public static final int MESSAGE_STATE_CHANGE = 2;

	public static final String TOAST = "toast";

	private static final int MODE_BUTTONS = 1;
	private static final int MODE_WRITING = 2;

	private BluetoothAdapter mBluetoothAdapter;
	private PowerManager mPowerManager;
	private PowerManager.WakeLock mWakeLock;
	private NXTTalker mNXTTalker;

	private int mState = NXTTalker.STATE_NONE;
	private int mSavedState = NXTTalker.STATE_NONE;
	private boolean mNewLaunch = true;
	private String mDeviceAddress = null;
	private TextView mStateDisplay;
	private Button mTestButton;
	private Button mWriteButton;
	private Button mConnectButton;
	private Button mDisconnectButton;
	private Menu mMenu;

	private int mPower = 80;
	private int mControlsMode = MODE_BUTTONS;

	private boolean mReverse;
	private boolean mReverseLR;
	private boolean mRegulateSpeed;
	private boolean mSynchronizeMotors;

	int motorC = 0;
	boolean mFalse = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		readPreferences(prefs, null);
		prefs.registerOnSharedPreferenceChangeListener(this);

		if (savedInstanceState != null) {
			mNewLaunch = false;
			mDeviceAddress = savedInstanceState.getString("device_address");
			if (mDeviceAddress != null) {
				mSavedState = NXTTalker.STATE_CONNECTED;
			}

			if (savedInstanceState.containsKey("power")) {
				mPower = savedInstanceState.getInt("power");
			}
			if (savedInstanceState.containsKey("controls_mode")) {
				mControlsMode = savedInstanceState.getInt("controls_mode");
			}
		}

		mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE, "NXT Remote Control");

		if (!NO_BT) {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			if (mBluetoothAdapter == null) {
				Toast.makeText(this, "Bluetooth is not available",
						Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}

		setupUI();

		mNXTTalker = new NXTTalker(mHandler);
	}

	private class DirectionButtonOnTouchListener implements OnTouchListener {

		private double lmod;
		private double rmod;

		public DirectionButtonOnTouchListener(double l, double r) {
			lmod = l;
			rmod = r;
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			int action = event.getAction();
			if (action == MotionEvent.ACTION_DOWN) {
				byte power = (byte) mPower;
				if (mReverse) {
					power *= -1;
				}
				byte l = (byte) (power * lmod);
				Log.i("NXT", Byte.toString(l));
				byte r = (byte) (power * rmod);
				Log.i("NXT", Byte.toString(r));
				if (!mReverseLR) {
					mNXTTalker.motors(l, r, mRegulateSpeed, mSynchronizeMotors);
				} else {
					mNXTTalker.motors(r, l, mRegulateSpeed, mSynchronizeMotors);
				}
			} else if ((action == MotionEvent.ACTION_UP)
					|| (action == MotionEvent.ACTION_CANCEL)) {
				mNXTTalker.motors((byte) 0, (byte) 0, mRegulateSpeed,
						mSynchronizeMotors);
			}
			return true;
		}
	}

	private void updateMenu(int disabled) {
		if (mMenu != null) {
			mMenu.findItem(R.id.menuitem_buttons)
					.setEnabled(disabled != R.id.menuitem_buttons)
					.setVisible(disabled != R.id.menuitem_buttons);
		}
	}

	private void setupUI() {
		if (mControlsMode == MODE_BUTTONS) {
			setContentView(R.layout.main);

			updateMenu(R.id.menuitem_buttons);

			ImageButton buttonUp = (ImageButton) findViewById(R.id.button_up);
			buttonUp.setOnTouchListener(new DirectionButtonOnTouchListener(1, 1));
			ImageButton buttonLeft = (ImageButton) findViewById(R.id.button_left);
			buttonLeft.setOnTouchListener(new DirectionButtonOnTouchListener(
					-0.6, 0.6));
			ImageButton buttonDown = (ImageButton) findViewById(R.id.button_down);
			buttonDown.setOnTouchListener(new DirectionButtonOnTouchListener(
					-1, -1));
			ImageButton buttonRight = (ImageButton) findViewById(R.id.button_right);
			buttonRight.setOnTouchListener(new DirectionButtonOnTouchListener(
					0.6, -0.6));

			mTestButton = (Button) findViewById(R.id.test_button);
			mTestButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View arg0) {
					mControlsMode = MODE_WRITING;
					setupUI();

				}

			});

			SeekBar powerSeekBar = (SeekBar) findViewById(R.id.power_seekbar);
			powerSeekBar.setProgress(mPower);
			powerSeekBar
					.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
						@Override
						public void onProgressChanged(SeekBar seekBar,
								int progress, boolean fromUser) {
							mPower = progress;
						}

						@Override
						public void onStartTrackingTouch(SeekBar seekBar) {
						}

						@Override
						public void onStopTrackingTouch(SeekBar seekBar) {
						}

					});
		} else if (mControlsMode == MODE_WRITING) {
			setContentView(R.layout.main_writing);

			mTestButton = (Button) findViewById(R.id.test_button);
			mTestButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View arg0) {
					mControlsMode = MODE_BUTTONS;
					setupUI();

				}

			});

			mWriteButton = (Button) findViewById(R.id.write_button);
			mWriteButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View arg0) {
					EditText editText = (EditText) findViewById(R.id.edit_message);
					String input = editText.getText().toString();
					writing(input);
				}

			});
		}

		mStateDisplay = (TextView) findViewById(R.id.state_display);

		mConnectButton = (Button) findViewById(R.id.connect_button);
		mConnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!NO_BT) {
					findBrick();
				} else {
					mState = NXTTalker.STATE_CONNECTED;
					displayState();
				}
			}
		});

		mDisconnectButton = (Button) findViewById(R.id.disconnect_button);
		mDisconnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mNXTTalker.stop();
			}
		});

		displayState();
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (!NO_BT) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			} else {
				if (mSavedState == NXTTalker.STATE_CONNECTED) {
					BluetoothDevice device = mBluetoothAdapter
							.getRemoteDevice(mDeviceAddress);
					mNXTTalker.connect(device);
				} else {
					if (mNewLaunch) {
						mNewLaunch = false;
						findBrick();
					}
				}
			}
		}
	}

	private void findBrick() {
		Intent intent = new Intent(this, ChooseDeviceActivity.class);
		startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
	}

	public void writing(String l) {
		final String text = l;
		Thread writeText = new Thread() {
			public void run() {
				try {
					for (int a = 0; a < text.length(); a++) {
						String letter = String.valueOf(text.charAt(a));
						byte power;
						if (letter.equals("a")) {
							power = 50;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
							sleep(2000);
							power = -50;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
							sleep(2000);
							power = 0;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
						} else if (letter.equals("b")) {
							power = -80;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
							sleep(2000);
							power = 80;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
							sleep(2000);
							power = 0;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
						} else if (letter.equals("c")) {
							power = -20;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
							sleep(1000);
							power = 20;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
							sleep(1000);
							power = -20;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
							sleep(1000);
							power = 20;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
							sleep(1000);
							power = 0;
							mNXTTalker.motor(motorC, power, mFalse, mFalse);
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {

				}
			}
		};
		writeText.start();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == Activity.RESULT_OK) {
				findBrick();
			} else {
				Toast.makeText(this, "Bluetooth not enabled, exiting.",
						Toast.LENGTH_LONG).show();
				finish();
			}
			break;
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK) {
				String address = data.getExtras().getString(
						ChooseDeviceActivity.EXTRA_DEVICE_ADDRESS);
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				mDeviceAddress = address;
				mNXTTalker.connect(device);
			}
			break;
		case REQUEST_SETTINGS:
			break;
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mState == NXTTalker.STATE_CONNECTED) {
			outState.putString("device_address", mDeviceAddress);
		}
		outState.putInt("power", mPower);
		outState.putInt("controls_mode", mControlsMode);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setupUI();
	}

	private void displayState() {
		String stateText = null;
		int color = 0;
		switch (mState) {
		case NXTTalker.STATE_NONE:
			stateText = "Not connected";
			color = 0xffff0000;
			mConnectButton.setVisibility(View.VISIBLE);
			mDisconnectButton.setVisibility(View.GONE);
			setProgressBarIndeterminateVisibility(false);
			if (mWakeLock.isHeld()) {
				mWakeLock.release();
			}
			break;
		case NXTTalker.STATE_CONNECTING:
			stateText = "Connecting...";
			color = 0xffffff00;
			mConnectButton.setVisibility(View.GONE);
			mDisconnectButton.setVisibility(View.GONE);
			setProgressBarIndeterminateVisibility(true);
			if (!mWakeLock.isHeld()) {
				mWakeLock.acquire();
			}
			break;
		case NXTTalker.STATE_CONNECTED:
			stateText = "Connected";
			color = 0xff00ff00;
			mConnectButton.setVisibility(View.GONE);
			mDisconnectButton.setVisibility(View.VISIBLE);
			setProgressBarIndeterminateVisibility(false);
			if (!mWakeLock.isHeld()) {
				mWakeLock.acquire();
			}
			break;
		}
		mStateDisplay.setText(stateText);
		mStateDisplay.setTextColor(color);
	}

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			case MESSAGE_STATE_CHANGE:
				mState = msg.arg1;
				displayState();
				break;
			}
		}
	};

	@Override
	protected void onStop() {
		super.onStop();
		mSavedState = mState;
		mNXTTalker.stop();
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		mMenu = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuitem_buttons:
			mControlsMode = MODE_BUTTONS;
			setupUI();
			break;
		case R.id.menuitem_settings:
			Intent i = new Intent(this, SettingsActivity.class);
			startActivityForResult(i, REQUEST_SETTINGS);
			break;
		default:
			return false;
		}
		return true;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		readPreferences(sharedPreferences, key);
	}

	private void readPreferences(SharedPreferences prefs, String key) {
		if (key == null) {
			mReverse = prefs.getBoolean("PREF_SWAP_FWDREV", false);
			mReverseLR = prefs.getBoolean("PREF_SWAP_LEFTRIGHT", false);
			mRegulateSpeed = prefs.getBoolean("PREF_REG_SPEED", false);
			mSynchronizeMotors = prefs.getBoolean("PREF_REG_SYNC", false);
			if (!mRegulateSpeed) {
				mSynchronizeMotors = false;
			}
		} else if (key.equals("PREF_SWAP_FWDREV")) {
			mReverse = prefs.getBoolean("PREF_SWAP_FWDREV", false);
		} else if (key.equals("PREF_SWAP_LEFTRIGHT")) {
			mReverseLR = prefs.getBoolean("PREF_SWAP_LEFTRIGHT", false);
		} else if (key.equals("PREF_REG_SPEED")) {
			mRegulateSpeed = prefs.getBoolean("PREF_REG_SPEED", false);
			if (!mRegulateSpeed) {
				mSynchronizeMotors = false;
			}
		} else if (key.equals("PREF_REG_SYNC")) {
			mSynchronizeMotors = prefs.getBoolean("PREF_REG_SYNC", false);
		}
	}
}