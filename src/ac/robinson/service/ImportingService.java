/*
 *  Copyright (C) 2012 Simon Robinson
 * 
 *  This file is part of Com-Me.
 * 
 *  Com-Me is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU Lesser General Public License as 
 *  published by the Free Software Foundation; either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  Com-Me is distributed in the hope that it will be useful, but WITHOUT 
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with Com-Me.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package ac.robinson.service;

import java.lang.reflect.Constructor;

import ac.robinson.mediautilities.MediaUtilities;
import ac.robinson.util.DebugUtilities;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

public class ImportingService extends Service {

	// see: http://developer.android.com/reference/android/app/Service.html
	private final Messenger mClientMessenger = new Messenger(new ClientMessageHandler());
	private Messenger mClient = null;

	private BluetoothAdapter mBluetoothAdapter;
	private final BluetoothStateReceiver mBluetoothStateReceiver = new BluetoothStateReceiver();
	private final BluetoothFileHandler mBluetoothFileHandler = new BluetoothFileHandler();

	private FileObserver mBluetoothObserver = null;
	private String mBluetoothObserverClassName = null;
	private String mBluetoothDirectoryPath = null;
	private boolean mRequireBluetoothEnabled = true;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// should continue running until explicitly stopped, so return sticky
		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopBluetoothTransferObserver();
		mBluetoothObserver = null;
		if (mBluetoothStateReceiver != null) {
			unregisterReceiver(mBluetoothStateReceiver);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		} catch (Exception e) {
			// continue, so we don't keep binding/unbinding
			Log.d(DebugUtilities.getLogTag(this), "Unable to instantiate BluetoothObserver - not supported");
		}
		mBluetoothObserverClassName = intent.getStringExtra(MediaUtilities.KEY_OBSERVER_CLASS);
		mBluetoothDirectoryPath = intent.getStringExtra(MediaUtilities.KEY_OBSERVER_PATH);
		mRequireBluetoothEnabled = intent.getBooleanExtra(MediaUtilities.KEY_OBSERVER_REQUIRE_BT, true);
		updateServices();
		return mClientMessenger.getBinder(); // for sending messages
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// called when bluetooth state is changed
			if (mBluetoothAdapter.isEnabled() || !mRequireBluetoothEnabled) {
				startBluetoothTransferObserver(); // may be called more than once, but this doesn't matter
			} else if (!mBluetoothAdapter.isEnabled() && mRequireBluetoothEnabled) {
				stopBluetoothTransferObserver();
			}
		}
	}

	private void stopBluetoothTransferObserver() {
		if (mBluetoothObserver != null) {
			mBluetoothObserver.stopWatching();
		}
	}

	private void startBluetoothTransferObserver() {
		if (mBluetoothObserver == null) {
			try {
				Class<?> activityClass = Class.forName(mBluetoothObserverClassName);
				Constructor<?> constructor = activityClass.getConstructor(new Class[] { String.class, Handler.class });
				mBluetoothObserver = (FileObserver) constructor.newInstance(mBluetoothDirectoryPath,
						mBluetoothFileHandler);
				forwardMessage(MediaUtilities.MSG_IMPORT_SERVICE_REGISTERED, null);
			} catch (Exception e) {
				Log.d(DebugUtilities.getLogTag(this), "Unable to instantiate BluetoothObserver ("
						+ mBluetoothObserverClassName + ")");
			}
		} else {
			mBluetoothObserver.startWatching();
			forwardMessage(MediaUtilities.MSG_IMPORT_SERVICE_REGISTERED, null);
		}
	}

	private void updateServices() {
		if (mBluetoothAdapter != null) {
			IntentFilter filter = new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED");
			registerReceiver(mBluetoothStateReceiver, filter);

			if (!mBluetoothAdapter.isEnabled() && mRequireBluetoothEnabled) {
				// removed - shouldn't be done without user permission - now start observer when bluetooth is enabled
				// may need <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
				// mBluetoothAdapter.enable();
			} else {
				startBluetoothTransferObserver();
			}
		} else {
			// bluetooth unavailable
		}
	}

	private void forwardMessage(int messageId, String fileName) {
		// must duplicate the data here, or we crash
		Message clientMessage = Message.obtain(null, messageId, 0, 0);
		Bundle messageBundle = new Bundle();
		messageBundle.putString(MediaUtilities.KEY_FILE_NAME, fileName);
		clientMessage.setData(messageBundle);
		try {
			mClient.send(clientMessage);
		} catch (Throwable t) {
			// error - couldn't send message
		}
	}

	// the callback handler that gets bluetooth file listener results
	private class BluetoothFileHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {

			// get the message data
			Bundle fileData = msg.peekData();
			if (fileData == null) {
				return; // error - no parameters passed
			}

			// get the filename
			String importedFileName = fileData.getString(MediaUtilities.KEY_FILE_NAME);
			if (importedFileName == null) {
				return; // error - no filename
			}

			switch (msg.what) {
				case MediaUtilities.MSG_RECEIVED_IMPORT_FILE:
				case MediaUtilities.MSG_RECEIVED_HTML_FILE:
				case MediaUtilities.MSG_RECEIVED_MOV_FILE:
				case MediaUtilities.MSG_RECEIVED_SMIL_FILE:
					forwardMessage(msg.what, importedFileName);
					break;
			}
		}
	}

	private class ClientMessageHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {

			switch (msg.what) {

				case MediaUtilities.MSG_REGISTER_CLIENT:
					mClient = msg.replyTo;
					updateServices();
					break;

				case MediaUtilities.MSG_HINT_NEW_FILE:
					if (mBluetoothObserver != null) {
						Bundle messageBundle = msg.getData();
						if (messageBundle != null) {
							mBluetoothObserver.onEvent(FileObserver.CLOSE_WRITE,
									messageBundle.getString(MediaUtilities.KEY_FILE_NAME));
						}
					}
					break;

				case MediaUtilities.MSG_DISCONNECT_CLIENT:
					mClient = null;
					stopSelf();
					break;

				default:
					super.handleMessage(msg);
			}
		}
	}
}
