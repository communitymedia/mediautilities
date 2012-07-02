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

package ac.robinson.util;

import java.util.List;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;

public class OrientationManager {

	private static Sensor mOrientationSensor;
	private static SensorManager mSensorManager;
	private static OrientationListener mListenerCallback;
	private static Boolean mOrientationSupported;
	private static boolean mIsListening = false;

	private enum Side {
		TOP, BOTTOM, LEFT, RIGHT;
	}

	public static boolean isListening() {
		return mIsListening;
	}

	public static void stopListening() {
		if (mIsListening) {
			try {
				if (mSensorManager != null && mSensorEventListener != null) {
					mSensorManager.unregisterListener(mSensorEventListener);
				}
			} catch (Exception e) {
			}
		}
		mIsListening = false;
	}

	public static boolean isSupported(SensorManager sensorManager) {
		if (mOrientationSupported == null) {
			mOrientationSupported = sensorManager.getSensorList(Sensor.TYPE_ORIENTATION).size() > 0;
		}
		return mOrientationSupported;
	}

	public static void startListening(SensorManager sensorManager, OrientationListener orientationListener) {
		mSensorManager = sensorManager;
		List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ORIENTATION);
		if (sensors.size() > 0) {
			mOrientationSensor = sensors.get(0);
			mIsListening = mSensorManager.registerListener(mSensorEventListener, mOrientationSensor,
					SensorManager.SENSOR_DELAY_NORMAL);
			mListenerCallback = orientationListener;
		}
	}

	// see: http://blog.androgames.net/135/android-orientation-tutorial/
	private static SensorEventListener mSensorEventListener = new SensorEventListener() {

		/** The side that is currently up */
		private Side currentSide = null;
		private Side oldSide = null;
		private float pitch;
		private float roll;

		@Override
		public void onSensorChanged(SensorEvent event) {

			pitch = event.values[1];
			roll = event.values[2];

			if (pitch < -45 && pitch > -135) {
				currentSide = Side.TOP;
			} else if (pitch > 45 && pitch < 135) {
				currentSide = Side.BOTTOM;
			} else if (roll > 45) {
				currentSide = Side.RIGHT;
			} else if (roll < -45) {
				currentSide = Side.LEFT;
			}

			if (currentSide != null && !currentSide.equals(oldSide)) {
				switch (currentSide) {
					case TOP:
						mListenerCallback.onOrientationChanged(0);
						break;
					case RIGHT:
						mListenerCallback.onOrientationChanged(90);
						break;
					case BOTTOM:
						mListenerCallback.onOrientationChanged(180);
						break;
					case LEFT:
						mListenerCallback.onOrientationChanged(270);
						break;
				}
				oldSide = currentSide;
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

	};

	public interface OrientationListener {
		public void onOrientationChanged(int newOrientationDegrees);
	}

	public static int getDisplayRotationDegrees(int displayRotation) {
		switch (displayRotation) {
			case Surface.ROTATION_90:
				return 90;
			case Surface.ROTATION_180:
				return 180;
			case Surface.ROTATION_270:
				return 270;
		}
		return 0;
	}
}
