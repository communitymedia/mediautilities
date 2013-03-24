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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;

public class CameraUtilities {

	private static final String LOG_TAG = "CameraUtilities";

	public static final int CUSTOM_CAMERA_FRONT = 1; // *must* be the same as Camera.CameraInfo.CAMERA_FACING_FRONT
	public static final int CUSTOM_CAMERA_BACK = 0; // *must* be the same as Camera.CameraInfo.CAMERA_FACING_BACK

	public static class CameraConfiguration {
		public boolean hasFrontCamera = false;
		public boolean usingFrontCamera = false;
		public int numberOfCameras = 0;
		// post-v9 orientation value; Integer rather than int so we can return null if not known
		public Integer cameraOrientationDegrees = null;

		@Override
		public String toString() {
			return this.getClass().getName() + "[" + hasFrontCamera + "," + numberOfCameras + "," + usingFrontCamera
					+ "," + cameraOrientationDegrees + "]";
		}
	}

	/**
	 * Check whether the device has a camera - <b>defaults to true</b> on SDK versions < 7
	 * 
	 * @param packageManager
	 * @return
	 */
	// @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1) is for PackageManager.FEATURE_CAMERA_ANY
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public static boolean deviceHasCamera(PackageManager packageManager) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR_MR1) {
			return true;
		} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
			return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA);
		} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
			return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
					|| packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
		} else {
			return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
		}
	}

	public static Camera initialiseCamera(boolean preferFront, CameraConfiguration cameraConfiguration) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return initialiseGingerbreadCamera(preferFront, cameraConfiguration);
		} else {
			return initialiseMultiPlatformCamera(preferFront, cameraConfiguration);
		}
	}

	// see: http://digitaldumptruck.jotabout.com/?p=797
	public static Camera initialiseMultiPlatformCamera(boolean preferFront, CameraConfiguration cameraConfiguration) {

		Camera camera = null;
		cameraConfiguration.hasFrontCamera = false;
		cameraConfiguration.usingFrontCamera = false;
		cameraConfiguration.numberOfCameras = 0;
		cameraConfiguration.cameraOrientationDegrees = null;

		// look for front-facing camera, using the Gingerbread API (v9)
		// reflection is used for backwards compatibility with pre-Gingerbread APIs
		try {
			Class<?> cameraClass = Class.forName("android.hardware.Camera");
			Object cameraInfo = null;
			Field facingField = null;
			Field orientationField = null;
			int cameraCount = 0;
			int cameraFacing = CUSTOM_CAMERA_BACK;
			int preferredFacing = (preferFront ? CUSTOM_CAMERA_FRONT : CUSTOM_CAMERA_BACK);

			Method getNumberOfCamerasMethod = cameraClass.getMethod("getNumberOfCameras");
			if (getNumberOfCamerasMethod != null) {
				cameraCount = (Integer) getNumberOfCamerasMethod.invoke(null, (Object[]) null);
			}

			Class<?> cameraInfoClass = Class.forName("android.hardware.Camera$CameraInfo");
			if (cameraInfoClass != null) {
				cameraInfo = cameraInfoClass.newInstance();
			}

			if (cameraInfo != null) {
				facingField = cameraInfo.getClass().getField("facing");
				orientationField = cameraInfo.getClass().getField("orientation");
			}

			Method getCameraInfoMethod = cameraClass.getMethod("getCameraInfo", Integer.TYPE, cameraInfoClass);
			if (getCameraInfoMethod != null && cameraInfoClass != null && facingField != null) {

				for (int camIdx = 0; camIdx < cameraCount; camIdx++) {

					getCameraInfoMethod.invoke(null, camIdx, cameraInfo);
					cameraFacing = facingField.getInt(cameraInfo);

					if (cameraInfo != null) {
						if (cameraFacing == CUSTOM_CAMERA_FRONT) {
							cameraConfiguration.hasFrontCamera = true;
						}
						// allow non-preferred camera (some devices (e.g. Nexus 7) only have front camera)
						if (cameraFacing == preferredFacing || camIdx == cameraCount - 1) {
							if (camera == null) { // so that we continue and detect a front camera even if not using it
								try {
									Method cameraOpenMethod = cameraClass.getMethod("open", Integer.TYPE);
									if (cameraOpenMethod != null) {
										camera = (Camera) cameraOpenMethod.invoke(null, camIdx);
										if (cameraFacing == CUSTOM_CAMERA_FRONT) {
											cameraConfiguration.usingFrontCamera = true;
										}

										cameraConfiguration.numberOfCameras = cameraCount;

										// Integer so that we can compare to null when checking orientation
										cameraConfiguration.cameraOrientationDegrees = Integer.valueOf(orientationField
												.getInt(cameraInfo));
									}
								} catch (RuntimeException e) {
									Log.e(LOG_TAG, "Camera failed to open: " + e.getLocalizedMessage());
								}
							}
						}
					}
				}
			}
		}
		// ignore exceptions the Java Reflection API throws - if it fails, who cares
		catch (ClassNotFoundException e) {
			Log.d(LOG_TAG, "Multi-platform camera: ClassNotFoundException " + e.getLocalizedMessage());
		} catch (NoSuchMethodException e) {
			Log.d(LOG_TAG, "Multi-platform camera: NoSuchMethodException " + e.getLocalizedMessage());
		} catch (NoSuchFieldException e) {
			Log.d(LOG_TAG, "Multi-platform camera: NoSuchFieldException " + e.getLocalizedMessage());
		} catch (IllegalAccessException e) {
			Log.d(LOG_TAG, "Multi-platform camera: IllegalAccessException " + e.getLocalizedMessage());
		} catch (InvocationTargetException e) {
			Log.d(LOG_TAG, "Multi-platform camera: InvocationTargetException " + e.getLocalizedMessage());
		} catch (InstantiationException e) {
			Log.d(LOG_TAG, "Multi-platform camera: InstantiationException " + e.getLocalizedMessage());
		} catch (SecurityException e) {
			Log.d(LOG_TAG, "Multi-platform camera: SecurityException " + e.getLocalizedMessage());
		}

		// if we failed, try using the pre-Gingerbread APIs to open the camera.
		if (camera == null) {
			try {
				camera = Camera.open();
				cameraConfiguration.hasFrontCamera = false;
				cameraConfiguration.usingFrontCamera = false;
				cameraConfiguration.numberOfCameras = 1;
				cameraConfiguration.cameraOrientationDegrees = 90; // default to most common camera orientation
			} catch (RuntimeException e) {
				Log.e(LOG_TAG, "Camera failed to open: " + e.getLocalizedMessage());
			}
		}

		return camera;
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static Camera initialiseGingerbreadCamera(boolean preferFront, CameraConfiguration cameraConfiguration) {

		Camera camera = null;
		cameraConfiguration.hasFrontCamera = false;
		cameraConfiguration.usingFrontCamera = false;
		cameraConfiguration.numberOfCameras = 0;
		cameraConfiguration.cameraOrientationDegrees = null;

		int cameraCount = Camera.getNumberOfCameras();
		int preferredFacing = (preferFront ? CUSTOM_CAMERA_FRONT : CUSTOM_CAMERA_BACK);

		for (int camIdx = 0; camIdx < cameraCount; camIdx++) {

			final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(camIdx, cameraInfo);

			if (cameraInfo != null) {
				if (cameraInfo.facing == CUSTOM_CAMERA_FRONT) {
					cameraConfiguration.hasFrontCamera = true;
				}
				// allow non-preferred camera (some devices (e.g. Nexus 7) only have front camera)
				if (cameraInfo.facing == preferredFacing || camIdx == cameraCount - 1) {
					if (camera == null) { // so that we continue and detect a front camera even if we aren't using it
						try {
							camera = Camera.open(camIdx);
							if (cameraInfo.facing == CUSTOM_CAMERA_FRONT) {
								cameraConfiguration.usingFrontCamera = true;
							}
							cameraConfiguration.numberOfCameras = cameraCount;
							// Integer so that we can compare to null when checking orientation
							cameraConfiguration.cameraOrientationDegrees = Integer.valueOf(cameraInfo.orientation);
						} catch (RuntimeException e) {
							Log.e(LOG_TAG, "Camera failed to open: " + e.getLocalizedMessage());
						}
					}
				}
			}
		}

		return camera;
	}

	// see: http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)
	public static int getPreviewOrientationDegrees(int screenOrientationDegrees, Integer cameraOrientationDegrees,
			boolean usingFrontCamera) {

		int previewOrientationDegrees = 0;

		if (cameraOrientationDegrees != null) {
			if (usingFrontCamera) { // compensate for the mirror of the front camera
				previewOrientationDegrees = (cameraOrientationDegrees + screenOrientationDegrees) % 360;
				previewOrientationDegrees = (360 - previewOrientationDegrees) % 360;
			} else { // back-facing
				previewOrientationDegrees = (cameraOrientationDegrees - screenOrientationDegrees + 360) % 360;
			}
		} else {
			// TODO: can we detect camera orientation somehow?
			Log.d(LOG_TAG, "Unable to detect camera orientation - setting to 0");
			previewOrientationDegrees = 0;// (90 - screenOrientationDegrees + 360) % 360;
		}

		return previewOrientationDegrees;
	}
}
